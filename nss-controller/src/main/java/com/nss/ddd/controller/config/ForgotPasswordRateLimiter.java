package com.nss.ddd.controller.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cổng chống dò tần suất của {@code POST /api/auth/forgot-password} (backlog 0017 §Contract điều 8).
 *
 * <h2>Vì sao KHÔNG dùng Resilience4j dù nó có tên trong bảng stack §2</h2>
 * <b>Đây là một lựa chọn có chủ ý và PM nên biết để phủ quyết nếu muốn.</b> Bảng stack của
 * {@code architecture/01-overview.md} §2 có nhắc Resilience4j 2.1.0 cho đúng loại việc này, nhưng
 * đo trên source thì nó ở đúng tư thế của Redis: <b>0 hit</b> {@code resilience} trong cả 5
 * {@code pom.xml} (control dương {@code mysql} ra 2 file). Nối dây nó lên nghĩa là thêm một
 * dependency <b>nằm ngoài BOM {@code spring-boot-dependencies} 3.3.5</b>, tức phải tự khai
 * {@code <version>} và tự theo dõi tương thích — <i>đúng trục bảo trì mà ADR 0003 đã từ chối một
 * lần</i> khi loại {@code jjwt}, và đúng thứ backlog 0017 dặn phải flag trước khi thêm.
 * <p>
 * Thứ cần ở đây là một bộ đếm cửa sổ cố định trong bộ nhớ — khoảng ba chục dòng, không có trạng
 * thái nào đáng để một thư viện quản lý. Khi nào dự án cần circuit breaker, bulkhead, retry policy
 * thật thì Resilience4j đáng cái giá của nó; tiêu một dependency mới cho một {@code Map} đếm số thì
 * không.
 *
 * <h2>Hai khoá, và cả hai đều cần</h2>
 * <ul>
 *   <li><b>Theo IP</b> chặn một người dò <i>nhiều</i> địa chỉ để tìm xem địa chỉ nào đã đăng ký.</li>
 *   <li><b>Theo email</b> chặn <i>nhiều</i> nguồn cùng nhắm vào một hộp thư — tức dùng hệ thống này
 *       làm máy phát tán thư rác. Giới hạn theo IP một mình không chặn được ca đó.</li>
 * </ul>
 * <b>Giới hạn theo email KHÔNG tạo ra kênh rò rỉ mới:</b> nó đếm mọi địa chỉ như nhau, kể cả địa
 * chỉ không ứng với tài khoản nào, nên một 429 không nói gì về việc email đó có tồn tại hay không.
 * Nếu chỉ đếm những email <i>có thật</i> thì chính bộ đếm sẽ trở thành công cụ dò — đó là cái bẫy ở
 * đây.
 *
 * <h2>Giới hạn đã biết của cách này</h2>
 * Bộ đếm sống <b>trong bộ nhớ của một tiến trình</b>. Chạy nhiều instance thì mỗi instance đếm
 * riêng, nên ngưỡng thực tế nhân lên theo số instance; và mọi lần khởi động lại là một lần xoá
 * sạch. Cả hai đều chấp nhận được ở tư thế hôm nay (một tiến trình, một máy) và cả hai đều là lý do
 * <i>thật</i> để chuyển sang bộ đếm dùng chung sau này — chỗ đó thì Redis, thứ đã có tên trong §2,
 * mới là câu trả lời đúng chứ không phải Resilience4j.
 */
@Slf4j
@Component
public class ForgotPasswordRateLimiter {

    /** Tiền tố khoá theo địa chỉ IP người gọi. */
    private static final String KEY_PREFIX_IP = "ip:";

    /** Tiền tố khoá theo email đích. */
    private static final String KEY_PREFIX_EMAIL = "email:";

    /**
     * Trần số khoá giữ trong bộ nhớ.
     * <p>
     * <b>Không có trần thì chính bộ chống lạm dụng trở thành đường lạm dụng:</b> mỗi IP giả hoặc
     * mỗi email bịa là một khoá mới, và một vòng lặp đủ dài sẽ làm hết bộ nhớ. Chạm trần thì dọn
     * các cửa sổ đã hết hạn trước; dọn xong vẫn đầy thì <b>cho request đi qua</b> chứ không chặn —
     * một bộ đếm quá tải không được phép biến thành một sự cố từ chối dịch vụ do chính mình gây ra.
     */
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private final int maxAttempts;

    private final Duration window;

    /**
     * @param maxAttempts số lần gọi tối đa trong một cửa sổ, cho mỗi khoá
     * @param window độ dài cửa sổ, dạng ISO-8601 ({@code PT15M})
     * @throws IllegalStateException khi cấu hình không dùng được — fail lúc khởi động, đúng tiền lệ
     *                               {@code JwtConfig} với {@code jwt-secret}
     */
    public ForgotPasswordRateLimiter(
            @Value("${nss.auth.forgot-password-max-attempts}") int maxAttempts,
            @Value("${nss.auth.forgot-password-window}") Duration window) {
        // NgUONG <= 0 se chan MOI request ke ca request dau tien — endpoint chet hoan toan nhung van
        // tra dung hinh dang loi, nen khong ai nhin ra. Cua so <= 0 thi bo dem khong bao gio tich
        // luy duoc gi va gioi han thanh vo tac dung. Ca hai deu la hong IM LANG, nen fail o day.
        if (maxAttempts <= 0) {
            throw new IllegalStateException(
                    "nss.auth.forgot-password-max-attempts must be positive; got: " + maxAttempts);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("nss.auth.forgot-password-window must be a positive"
                    + " ISO-8601 duration; got: " + window);
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /**
     * Ghi nhận một lời gọi và cho biết nó có vượt ngưỡng hay không.
     * <p>
     * <b>Đếm TRƯỚC khi kiểm, và đếm cả lời gọi đã bị chặn.</b> Nhờ vậy một kẻ đang bị chặn mà vẫn
     * gõ tiếp sẽ tự giữ cửa sổ của mình luôn đầy, thay vì được "nghỉ" đủ lâu để lại đủ hạn mức.
     *
     * @param clientIp địa chỉ IP người gọi; {@code null} được gom vào một khoá chung
     * @param email email đích, so sánh không phân biệt hoa thường
     * @return true nếu lời gọi này vượt ngưỡng và phải bị chặn
     */
    public boolean hasExceededLimit(String clientIp, String email) {
        Instant now = Instant.now();
        // Kiem CA HAI khoa va tang CA HAI, khong short-circuit: dung `||` o day se khien khoa thu
        // hai khong duoc tang khi khoa dau tien da vuot, va nguoi goi lach duoc gioi han theo email
        // bang cach doi IP lien tuc.
        boolean ipExceeded = hasExceededKey(KEY_PREFIX_IP + genIpKey(clientIp), now);
        boolean emailExceeded = hasExceededKey(KEY_PREFIX_EMAIL + genEmailKey(email), now);
        if (ipExceeded || emailExceeded) {
            // Khong log email o day: mot dong log gan email vao moi lan bi chan bien file log thanh
            // danh sach cac dia chi bi nham toi. IP la du de nguoi van hanh chan o tang tren.
            log.warn("hasExceededLimit: forgot-password rate limit hit | ip={} byIp={} byEmail={}",
                    clientIp, ipExceeded, emailExceeded);
            return true;
        }
        return false;
    }

    /**
     * Tăng bộ đếm của một khoá và cho biết khoá đó đã vượt ngưỡng chưa.
     *
     * @param key khoá đã mang tiền tố
     * @param now thời điểm hiện tại
     * @return true nếu số lần gọi trong cửa sổ hiện tại đã vượt ngưỡng
     */
    private boolean hasExceededKey(String key, Instant now) {
        ensureCapacity(now);
        // compute() chay atomic tren tung khoa cua ConcurrentHashMap, nen viec "het han thi thay cua
        // so moi" khong co khe hoi giua doc va ghi.
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.hasExpired(now) ? new Window(now) : existing);
        return current.count.incrementAndGet() > maxAttempts;
    }

    /**
     * Dọn các cửa sổ đã hết hạn khi số khoá chạm trần.
     * <p>
     * Chỉ quét khi thật sự chạm trần: quét mỗi lời gọi là biến một endpoint rẻ thành một vòng lặp
     * trên toàn bộ map.
     *
     * @param now thời điểm hiện tại
     */
    private void ensureCapacity(Instant now) {
        if (windows.size() < MAX_TRACKED_KEYS) {
            return;
        }
        windows.values().removeIf(existing -> existing.hasExpired(now));
        if (windows.size() >= MAX_TRACKED_KEYS) {
            log.error("ensureCapacity: rate limiter is saturated with {} live keys,"
                    + " new callers are not limited", windows.size());
        }
    }

    /**
     * @param clientIp IP người gọi
     * @return khoá theo IP; {@code null} gom vào một khoá chung thay vì bỏ qua giới hạn
     */
    private String genIpKey(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }

    /**
     * <b>Chuẩn hoá về chữ thường là bắt buộc, không phải cho gọn.</b> Cột {@code user.email} dùng
     * collation {@code utf8mb4_unicode_ci} nên MySQL coi {@code A@x.vn} và {@code a@x.vn} là một
     * địa chỉ; nếu bộ đếm ở đây phân biệt hoa thường thì đổi kiểu chữ là lách được giới hạn theo
     * email, mà vẫn gửi mail tới đúng một hộp thư.
     *
     * @param email email đích
     * @return khoá theo email đã chuẩn hoá
     */
    private String genEmailKey(String email) {
        return email == null ? "unknown" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Một cửa sổ đếm cố định: mốc bắt đầu cộng số lần gọi kể từ mốc đó.
     */
    private final class Window {

        private final Instant startedAt;

        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }

        /**
         * @param now thời điểm hiện tại
         * @return true nếu cửa sổ đã trôi qua và phải được thay bằng một cửa sổ mới
         */
        private boolean hasExpired(Instant now) {
            return startedAt.plus(window).isBefore(now);
        }
    }
}
