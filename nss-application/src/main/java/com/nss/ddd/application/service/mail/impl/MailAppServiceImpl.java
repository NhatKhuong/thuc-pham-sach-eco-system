package com.nss.ddd.application.service.mail.impl;

import com.nss.ddd.application.service.mail.MailAppService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Hiện thực đường gửi mail qua SMTP (ADR 0004).
 * <p>
 * {@code JavaMailSender} do {@code spring-boot-starter-mail} tự dựng từ khối {@code spring.mail.*}
 * — artifact này nằm trong BOM {@code spring-boot-dependencies} 3.3.5 nên không khai
 * {@code <version>} và không thêm trục bảo trì nào, đúng lý do ADR 0004 loại các dịch vụ HTTP bên
 * ngoài (Resend, SES) và ADR 0003 đã loại {@code jjwt} một lần trước đó.
 *
 * <h2>Fail ngay lúc khởi động — tiền lệ {@code JwtConfig}</h2>
 * Ba giá trị cấu hình dưới đây được kiểm trong <b>constructor</b>, tức lúc dựng bean, tức lúc ứng
 * dụng khởi động. Đó là chủ ý và nó sao chép đúng kỷ luật của {@code JwtConfig.genSecretKey()}: một
 * cấu hình mail sai mà chỉ nổ ở <i>lần gửi đầu tiên</i> nghĩa là ứng dụng lên xanh, Swagger mở
 * được, và người phát hiện ra sẽ là người dùng đầu tiên bấm "Quên mật khẩu" — người duy nhất không
 * làm gì được với thông tin đó. Tệ hơn nữa ở đây so với JWT: endpoint gọi tới luôn trả 204, nên
 * người dùng ấy cũng <b>không</b> biết là mình vừa gặp lỗi; họ chỉ ngồi chờ một email không bao giờ
 * tới. Fail lúc khởi động thì lỗi thuộc về người deploy.
 *
 * <h2>Vì sao {@code @Async} và vì sao không có {@code throws}</h2>
 * Xem javadoc của {@link MailAppService} — gửi bất đồng bộ là điều kiện để hai nhánh "email có
 * thật" và "email không tồn tại" không phân biệt được bằng thời gian phản hồi. Mọi ngoại lệ bị chặn
 * lại ở method này: nó chạy trên luồng khác nên ném ra cũng không ai bắt, và biến một lỗi SMTP
 * thành 500 trên một endpoint contract khai là luôn 204 thì còn sai hơn.
 *
 * <h2>CircuitBreaker: ranh giới nằm TRONG khối catch, không phải ngoài</h2>
 * Backlog 0021 Phase 2 / ADR 0005 §Consequences điều 3. Method này <b>tự nuốt mọi exception</b>
 * (xem mục trên), nên một breaker bọc <i>ngoài</i> nó sẽ thấy <b>0 lỗi vĩnh viễn</b> và không bao
 * giờ mở — lớp bảo vệ có mặt đầy đủ trong code, có mặt đầy đủ trong config, và không làm gì cả.
 * Vì vậy breaker chỉ bọc đúng lời gọi {@code javaMailSender.send(...)}: chỗ duy nhất thật sự nói
 * chuyện với SMTP, và là chỗ nằm <i>trước</i> khi khối catch nuốt kết quả.
 * <p>
 * <b>Đường lập trình chứ không phải annotation, và đó là một lựa chọn có lý do.</b>
 * {@code @Async} là một proxy; một annotation của Resilience4j trên <i>cùng một method</i> là proxy
 * thứ hai chồng lên, và thứ tự giữa hai proxy quyết định breaker đếm cái gì — một thứ tự sai vẫn
 * biên dịch được, vẫn chạy được, và vẫn đếm 0 lỗi. Gọi thẳng
 * {@link CircuitBreaker#executeRunnable(Runnable)} thì không còn thứ tự nào để sai: ranh giới nằm
 * đúng chỗ mắt nhìn thấy nó.
 * <p>
 * <b>Breaker mở KHÔNG sinh mã HTTP nào.</b> {@code POST /api/auth/forgot-password} khai <b>luôn
 * 204</b> (API_CONTRACT §B.4), và việc gửi đã ở ngoài request thread từ lâu. Breaker mở ⇒ bỏ qua
 * lần gửi, {@code log.warn}, luồng nghiệp vụ đi tiếp — đúng {@code coding-conventions} §11 điều
 * "thất bại của một tác dụng phụ không được làm gãy luồng nghiệp vụ".
 * <p>
 * <b>Điều kiện tiên quyết đã có sẵn:</b> ba timeout SMTP ở {@code application.yml}
 * ({@code connectiontimeout} / {@code timeout} / {@code writetimeout} = 5000ms, do backlog 0017
 * thêm). Không có chúng thì một máy chủ SMTP <i>chấp nhận kết nối rồi im lặng</i> giữ luồng gửi mãi
 * mãi: breaker không đếm được một lời gọi chưa bao giờ kết thúc, nên nó vĩnh viễn CLOSED trong khi
 * đường gửi đã chết hoàn toàn.
 */
@Slf4j
@Service
public class MailAppServiceImpl implements MailAppService {

    /** Tiêu đề email — tiếng Việt, người dùng cuối đọc (§1). */
    private static final String SUBJECT_PASSWORD_RESET = "Đặt lại mật khẩu — Nông Sản Sạch";

    /**
     * Thân email dạng văn bản thuần.
     * <p>
     * <b>Văn bản thuần chứ không phải HTML, có chủ ý.</b> Không có ảnh, không có CSS bị mail client
     * cắt xén, không có link ẩn sau chữ — người nhận <i>nhìn thấy</i> đúng đường dẫn mình sắp mở, và
     * đó là thứ đáng giá nhất ở một email về bảo mật. Nó cũng khiến bằng chứng kiểm chứng đọc được
     * bằng mắt: link nằm nguyên văn trong thân thư.
     * <p>
     * Có nêu thời hạn và có nêu "nếu không phải bạn thì bỏ qua" — cả hai là nội dung bắt buộc của
     * một email đặt lại mật khẩu, không phải chữ trang trí.
     */
    private static final String BODY_PASSWORD_RESET = """
            Xin chào,

            Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản này.
            Bấm vào đường dẫn dưới đây để đặt mật khẩu mới:

            %s

            Đường dẫn có hiệu lực trong %d phút và chỉ dùng được MỘT lần.

            Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này —
            mật khẩu hiện tại của bạn vẫn giữ nguyên.

            Nông Sản Sạch
            """;

    /** Tên tham số mang token trên link đặt lại; phải khớp thứ trang frontend đọc ra. */
    private static final String TOKEN_QUERY_PARAM = "token";

    /** Tên instance breaker — xuất hiện trong mọi dòng log transition, đừng đổi mà không sửa runbook. */
    private static final String CIRCUIT_BREAKER_NAME = "mail";

    /**
     * <b>COUNT_BASED chứ không TIME_BASED, và cố ý KHÔNG nằm sau biến môi trường.</b>
     * <p>
     * Đường gửi mail ở đây là một luồng thưa và không đều — nó chỉ chạy khi có người bấm "Quên mật
     * khẩu". Một cửa sổ theo <i>thời gian</i> trên một luồng thưa thì đa số chu kỳ rỗng, và tỉ lệ
     * lỗi tính trên mẫu rỗng là một con số không có nghĩa. Đếm theo <i>số lời gọi</i> cho một mẫu
     * ổn định bất kể lưu lượng.
     * <p>
     * Không đưa ra sau biến môi trường vì đổi kiểu cửa sổ đổi luôn ý nghĩa của
     * {@code slidingWindowSize} và {@code minimumNumberOfCalls} (số lời gọi ⇄ số giây) — một lần
     * chỉnh nhầm ở đây không có triệu chứng nào nhìn thấy, đúng lý do
     * {@code ApiRateLimitInterceptor.TIMEOUT_DURATION} cũng được khai cứng.
     */
    private static final CircuitBreakerConfig.SlidingWindowType SLIDING_WINDOW_TYPE =
            CircuitBreakerConfig.SlidingWindowType.COUNT_BASED;

    /**
     * <b>Tự chuyển OPEN → HALF_OPEN theo đồng hồ, không chờ một lời gọi tới để đánh thức.</b>
     * <p>
     * Mặc định của Resilience4j là {@code false}: breaker chỉ rời trạng thái OPEN khi có <i>một
     * lời gọi mới</i> đi tới sau khi hết {@code waitDurationInOpenState}. Trên một luồng thưa như
     * gửi mail, "lời gọi mới" có thể là vài giờ sau — nghĩa là SMTP đã sống lại từ lâu mà breaker
     * vẫn OPEN, và lần bấm "Quên mật khẩu" tiếp theo <i>vẫn</i> bị bỏ qua dù không còn lý do gì.
     * Khai cứng {@code true} cùng lý do với {@link #SLIDING_WINDOW_TYPE}.
     */
    private static final boolean AUTOMATIC_TRANSITION_FROM_OPEN_TO_HALF_OPEN = true;

    /** Trần trên của một tỉ lệ phần trăm — Resilience4j nhận (0, 100]. */
    private static final int MAX_FAILURE_RATE_PERCENT = 100;

    private final JavaMailSender javaMailSender;

    /**
     * Breaker của đường gửi SMTP. Dựng trong constructor, đúng tiền lệ
     * {@code ApiRateLimitInterceptor}: cấu hình sai thì <b>fail lúc khởi động</b>, không đợi lần
     * gửi đầu tiên.
     */
    private final CircuitBreaker circuitBreaker;

    private final String fromAddress;

    private final String passwordResetUrl;

    private final long tokenTtlMinutes;

    /**
     * Constructor injection viết tay thay vì {@code @RequiredArgsConstructor} — cùng lý do đã viết
     * ở {@code AuthAppServiceImpl}: {@code @Value} chỉ dùng được trên tham số constructor, và
     * Lombok không sao chép annotation sang tham số nó sinh ra.
     *
     * @param javaMailSender bộ gửi do {@code spring-boot-starter-mail} dựng từ {@code spring.mail.*}
     * @param fromAddress địa chỉ người gửi
     * @param passwordResetUrl đường dẫn trang đặt lại phía frontend, chưa kèm query
     * @param tokenTtl thời hạn token — chỉ dùng để viết số phút vào thân thư; nguồn chân lý của
     *                 thời hạn là cột {@code expires_at}, không phải con số in ra cho người đọc
     * @param slidingWindowSize số lời gọi gần nhất dùng để tính tỉ lệ lỗi
     * @param minimumNumberOfCalls số lời gọi tối thiểu trước khi tỉ lệ lỗi được tính tới
     * @param failureRateThreshold ngưỡng tỉ lệ lỗi (phần trăm) để chuyển sang OPEN
     * @param waitDurationInOpenState thời gian ở OPEN trước khi thử lại, dạng ISO-8601 ({@code PT60S})
     * @param permittedCallsInHalfOpenState số lời gọi thăm dò được phép ở HALF_OPEN
     * @throws IllegalStateException khi cấu hình bắt buộc rỗng hoặc sai dạng
     */
    public MailAppServiceImpl(JavaMailSender javaMailSender,
                              @Value("${nss.mail.from}") String fromAddress,
                              @Value("${nss.mail.password-reset-url}") String passwordResetUrl,
                              @Value("${nss.auth.password-reset-token-ttl}") Duration tokenTtl,
                              @Value("${nss.mail.circuit-breaker.sliding-window-size}") int slidingWindowSize,
                              @Value("${nss.mail.circuit-breaker.minimum-number-of-calls}") int minimumNumberOfCalls,
                              @Value("${nss.mail.circuit-breaker.failure-rate-threshold}") int failureRateThreshold,
                              @Value("${nss.mail.circuit-breaker.wait-duration-in-open-state}") Duration waitDurationInOpenState,
                              @Value("${nss.mail.circuit-breaker.permitted-calls-in-half-open-state}") int permittedCallsInHalfOpenState) {
        // Ba phep kiem duoi day chay LUC KHOI DONG, khong doi den lan gui dau tien — xem javadoc lop.
        if (fromAddress == null || fromAddress.isBlank() || !fromAddress.contains("@")) {
            throw new IllegalStateException(
                    "nss.mail.from must be a non-blank email address; got: " + fromAddress);
        }
        if (passwordResetUrl == null || passwordResetUrl.isBlank()
                || !(passwordResetUrl.startsWith("http://") || passwordResetUrl.startsWith("https://"))) {
            throw new IllegalStateException(
                    "nss.mail.password-reset-url must be an absolute http(s) URL; got: " + passwordResetUrl);
        }
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalStateException(
                    "nss.auth.password-reset-token-ttl must be a positive ISO-8601 duration; got: " + tokenTtl);
        }
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.passwordResetUrl = passwordResetUrl;
        this.tokenTtlMinutes = tokenTtl.toMinutes();
        this.circuitBreaker = genCircuitBreaker(slidingWindowSize, minimumNumberOfCalls,
                failureRateThreshold, waitDurationInOpenState, permittedCallsInHalfOpenState);
    }

    @Override
    @Async
    public void sendPasswordResetMail(String toEmail, String rawToken) {
        try {
            // 1. Dung link. Token duoc URL-encode du Base64 URL-safe khong sinh ky tu can thoat:
            //    phep ma hoa dung cho MOI chuoi la thu khien cho nay khong hong khi cach sinh token
            //    doi o mot ticket sau.
            String link = genResetLink(rawToken);
            // 2. MimeMessage + UTF-8 tuong minh. Tieu de va than thu co dau tieng Viet; mot ban
            //    SimpleMailMessage dua vao encoding mac dinh la cho dau bi bien thanh dau hoi.
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(SUBJECT_PASSWORD_RESET);
            helper.setText(BODY_PASSWORD_RESET.formatted(link, tokenTtlMinutes), false);
            // 3. RANH GIOI BREAKER — dung MOT dong, va no nam TRONG khoi try nay chu khong ngoai.
            //    Bao ngoai method thi breaker luon thay call thanh cong (khoi catch duoi da nuot
            //    het) va vinh vien dem 0 loi. Chi boc dung loi goi send(): viec dung MimeMessage o
            //    tren la loi cua chinh minh chu khong phai loi cua SMTP, boc no vao thi mot email
            //    sai dinh dang cung lam breaker mo.
            circuitBreaker.executeRunnable(() -> javaMailSender.send(message));
            // 4. KHONG log rawToken va KHONG log link (link CHUA token). Log email va ket qua la du
            //    de lan nguoc; xem javadoc cua MailAppService ve vi sao dong log nay bat buoc phai co.
            log.info("sendPasswordResetMail: sent | to={}", toEmail);
        } catch (CallNotPermittedException e) {
            // Breaker dang OPEN: BO QUA lan gui va di tiep. KHONG sinh ma HTTP nao — endpoint goi
            // toi khai luon 204, va nguoi dung thi da nhan 204 tu truoc khi luong nay chay.
            //
            // Muc `warn` chu khong `error`: day la mot that bai nghiep vu DU KIEN DUOC (§9), va no
            // la TIN HIEU DUY NHAT phan biet "da bo qua" voi "da gui" — hai nhanh do khong phan
            // biet duoc bang ma HTTP. De exception nay roi vao nhanh catch tong quat ben duoi thi
            // no se duoc ghi thanh `failed` kem stack trace, va bang chung dem-dong-log cua backlog
            // 0021 evidence #7 khong con phan biet duoc hai nhanh nua.
            log.warn("sendPasswordResetMail: skipped, circuit breaker is open | to={} breaker={}",
                    toEmail, CIRCUIT_BREAKER_NAME);
        } catch (Exception e) {
            // Chay tren luong khac nen nem ra khong ai bat. Nuot MA KHONG LOG thi endpoint 204 tro
            // thanh mot cai hop den hoan toan — dung thu §11 cam.
            log.error("sendPasswordResetMail: failed | to={}", toEmail, e);
        }
    }

    /**
     * Dựng breaker của đường gửi SMTP và gắn listener log transition.
     * <p>
     * <b>Mọi phép kiểm dưới đây chạy LÚC KHỞI ĐỘNG</b>, đúng tiền lệ {@code JwtConfig},
     * {@code ForgotPasswordRateLimiter} và {@code ApiRateLimitInterceptor}. Một breaker cấu hình
     * sai không nổ ở đâu cả: nó chỉ lặng lẽ không bao giờ mở, hoặc mở ngay từ lời gọi đầu tiên —
     * và cả hai đều đi kèm một endpoint vẫn trả 204.
     *
     * @param slidingWindowSize             số lời gọi gần nhất dùng để tính tỉ lệ lỗi
     * @param minimumNumberOfCalls          số lời gọi tối thiểu trước khi tỉ lệ lỗi được tính tới
     * @param failureRateThreshold          ngưỡng tỉ lệ lỗi (phần trăm) để chuyển sang OPEN
     * @param waitDurationInOpenState       thời gian ở OPEN trước khi thử lại
     * @param permittedCallsInHalfOpenState số lời gọi thăm dò được phép ở HALF_OPEN
     * @return breaker đã cấu hình, đã gắn listener
     * @throws IllegalStateException khi cấu hình không dùng được — kèm đúng tên khoá cấu hình
     */
    private CircuitBreaker genCircuitBreaker(int slidingWindowSize, int minimumNumberOfCalls,
                                             int failureRateThreshold, Duration waitDurationInOpenState,
                                             int permittedCallsInHalfOpenState) {
        if (slidingWindowSize <= 0) {
            throw new IllegalStateException("nss.mail.circuit-breaker.sliding-window-size must be"
                    + " positive; got: " + slidingWindowSize);
        }
        if (minimumNumberOfCalls <= 0) {
            throw new IllegalStateException("nss.mail.circuit-breaker.minimum-number-of-calls must be"
                    + " positive; got: " + minimumNumberOfCalls);
        }
        // minimumNumberOfCalls > slidingWindowSize la mot cau hinh HONG IM LANG: voi cua so
        // COUNT_BASED, Resilience4j tu ha no xuong bang slidingWindowSize va khong noi gi. Nguoi
        // van hanh doc lai config thay "toi thieu N loi goi" trong khi breaker that su dung it hon.
        if (minimumNumberOfCalls > slidingWindowSize) {
            throw new IllegalStateException("nss.mail.circuit-breaker.minimum-number-of-calls must be"
                    + " <= nss.mail.circuit-breaker.sliding-window-size for a COUNT_BASED window;"
                    + " got: " + minimumNumberOfCalls + " > " + slidingWindowSize);
        }
        if (failureRateThreshold <= 0 || failureRateThreshold > MAX_FAILURE_RATE_PERCENT) {
            throw new IllegalStateException("nss.mail.circuit-breaker.failure-rate-threshold must be"
                    + " a percentage in (0, 100]; got: " + failureRateThreshold);
        }
        if (waitDurationInOpenState == null || waitDurationInOpenState.isZero()
                || waitDurationInOpenState.isNegative()) {
            throw new IllegalStateException("nss.mail.circuit-breaker.wait-duration-in-open-state must"
                    + " be a positive ISO-8601 duration; got: " + waitDurationInOpenState);
        }
        if (permittedCallsInHalfOpenState <= 0) {
            throw new IllegalStateException("nss.mail.circuit-breaker.permitted-calls-in-half-open-state"
                    + " must be positive; got: " + permittedCallsInHalfOpenState);
        }
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(SLIDING_WINDOW_TYPE)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedCallsInHalfOpenState)
                .automaticTransitionFromOpenToHalfOpenEnabled(AUTOMATIC_TRANSITION_FROM_OPEN_TO_HALF_OPEN)
                // GHI NHAN DUNG HAI HO NGOAI LE, moi thu khac tinh la THANH CONG.
                // - MailException: moi that bai SMTP di qua JavaMailSender deu ra day
                //   (MailSendException / MailAuthenticationException / MailParseException...).
                // - MessagingException: ho goc cua jakarta.mail. Hom nay JavaMailSenderImpl boc no
                //   lai thanh MailSendException nen nhanh nay khong can toi; giu no de ranh gioi
                //   breaker khong am tham hong neu sau nay co ai goi thang API jakarta.mail.
                // Mac dinh cua Resilience4j la ghi nhan MOI exception — tuc mot loi lap trinh
                // trong lambda cung lam breaker mo. Khai tuong minh de breaker chi phan ung voi
                // dung thu no duoc dung de bao ve: duong SMTP.
                .recordExceptions(MailException.class, MessagingException.class)
                .build();
        CircuitBreaker breaker = CircuitBreaker.of(CIRCUIT_BREAKER_NAME, config);
        // Resilience4j KHONG tu log transition nao. Khong co dong nay thi trang thai breaker chi ton
        // tai trong bo nho: endpoint van 204 o ca hai nhanh, va khong con cach nao NGOAI BANG de
        // biet breaker dang mo hay dong. Dinh dang [TAG] theo §9 cho luong bat dong bo.
        breaker.getEventPublisher().onStateTransition(event -> log.warn(
                "[MAIL_CB] state transition | name={} from={} to={}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()));
        log.info("MailAppServiceImpl: mail circuit breaker wired | name={} windowType={} windowSize={}"
                        + " minCalls={} failureRate={}% wait={} halfOpenCalls={} autoHalfOpen={}",
                CIRCUIT_BREAKER_NAME, SLIDING_WINDOW_TYPE, slidingWindowSize, minimumNumberOfCalls,
                failureRateThreshold, waitDurationInOpenState, permittedCallsInHalfOpenState,
                AUTOMATIC_TRANSITION_FROM_OPEN_TO_HALF_OPEN);
        return breaker;
    }

    /**
     * Dựng link đặt lại mật khẩu.
     * <p>
     * <b>Đường dẫn này trỏ tới một trang frontend CHƯA TỒN TẠI</b>, và đó là điều đã biết chứ không
     * phải một bất ngờ lúc ghép (backlog 0017 §Non-goals): lỗ hổng #3 nằm ở repo khác. Giá trị nằm
     * sau biến môi trường {@code PASSWORD_RESET_URL} nên nó sẽ đúng khi frontend dựng trang, và sai
     * một cách hiển nhiên — 404 ngay khi bấm — nếu frontend chọn đường khác.
     *
     * @param rawToken chuỗi token thô
     * @return link đầy đủ kèm query {@code ?token=...}
     */
    private String genResetLink(String rawToken) {
        String separator = passwordResetUrl.contains("?") ? "&" : "?";
        return passwordResetUrl + separator + TOKEN_QUERY_PARAM + "="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
