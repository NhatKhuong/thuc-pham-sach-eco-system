package com.nss.ddd.application.service.mail.impl;

import com.nss.ddd.application.service.mail.MailAppService;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
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

    private final JavaMailSender javaMailSender;

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
     * @throws IllegalStateException khi cấu hình bắt buộc rỗng hoặc sai dạng
     */
    public MailAppServiceImpl(JavaMailSender javaMailSender,
                              @Value("${nss.mail.from}") String fromAddress,
                              @Value("${nss.mail.password-reset-url}") String passwordResetUrl,
                              @Value("${nss.auth.password-reset-token-ttl}") Duration tokenTtl) {
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
            javaMailSender.send(message);
            // 3. KHONG log rawToken va KHONG log link (link CHUA token). Log email va ket qua la du
            //    de lan nguoc; xem javadoc cua MailAppService ve vi sao dong log nay bat buoc phai co.
            log.info("sendPasswordResetMail: sent | to={}", toEmail);
        } catch (Exception e) {
            // Chay tren luong khac nen nem ra khong ai bat. Nuot MA KHONG LOG thi endpoint 204 tro
            // thanh mot cai hop den hoan toan — dung thu §11 cam.
            log.error("sendPasswordResetMail: failed | to={}", toEmail, e);
        }
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
