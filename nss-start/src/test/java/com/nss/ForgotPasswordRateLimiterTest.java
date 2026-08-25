package com.nss;

import com.nss.ddd.controller.config.ForgotPasswordRateLimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm cổng chống dò tần suất của {@code POST /auth/forgot-password} (backlog 0017 điều 8).
 * <p>
 * <b>Ba bất biến ở đây đều hỏng im lặng nếu ai đó "dọn dẹp" cho gọn:</b> đếm theo hai khoá độc lập,
 * không short-circuit giữa hai khoá, và chuẩn hoá hoa thường của email. Mỗi cái đều để lại một
 * đường lách mà endpoint vẫn trả đúng mã trong mọi ca thử thủ công.
 */
class ForgotPasswordRateLimiterTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private static final String IP = "10.0.0.1";

    private static final String EMAIL = "demo@nongsansach.vn";

    /**
     * @return bộ giới hạn với ngưỡng nhỏ để test đọc được
     */
    private ForgotPasswordRateLimiter genLimiter() {
        return new ForgotPasswordRateLimiter(MAX_ATTEMPTS, WINDOW);
    }

    // ========== KHOI DONG: FAIL-FAST ==========

    /**
     * <b>Ngưỡng {@code <= 0} chặn MỌI request kể cả request đầu tiên</b> — endpoint chết hoàn toàn
     * nhưng vẫn trả đúng hình dạng lỗi 429, nên không ai đọc ra được nguyên nhân. Cửa sổ {@code <= 0}
     * thì ngược lại: bộ đếm không bao giờ tích luỹ được gì và giới hạn thành vô tác dụng. Cả hai
     * phải nổ lúc khởi động.
     */
    @Test
    @DisplayName("Cau hinh khong dung duoc: fail NGAY luc dung bean")
    void unusableConfigFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new ForgotPasswordRateLimiter(0, WINDOW));
        assertThrows(IllegalStateException.class, () -> new ForgotPasswordRateLimiter(-1, WINDOW));
        assertThrows(IllegalStateException.class,
                () -> new ForgotPasswordRateLimiter(MAX_ATTEMPTS, Duration.ZERO));
        assertThrows(IllegalStateException.class,
                () -> new ForgotPasswordRateLimiter(MAX_ATTEMPTS, Duration.ofMinutes(-1)));
    }

    // ========== NGUONG ==========

    @Test
    @DisplayName("Dung nguong thi cho qua, vuot mot lan thi chan")
    void limitAllowsExactlyMaxAttempts() {
        ForgotPasswordRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertFalse(limiter.hasExceededLimit(IP, EMAIL),
                    "Lan goi thu " + attempt + " van trong nguong");
        }
        assertTrue(limiter.hasExceededLimit(IP, EMAIL), "Lan goi thu " + (MAX_ATTEMPTS + 1) + " phai bi chan");
    }

    /**
     * <b>Đếm theo IP chặn một người dò NHIỀU địa chỉ</b> — đây là vector dò tài khoản.
     */
    @Test
    @DisplayName("Cung IP, doi email lien tuc: van bi chan — do la vector do tai khoan")
    void sameIpCannotEnumerateManyEmails() {
        ForgotPasswordRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertFalse(limiter.hasExceededLimit(IP, "dia-chi-" + attempt + "@nongsansach.vn"));
        }
        assertTrue(limiter.hasExceededLimit(IP, "dia-chi-moi@nongsansach.vn"));
    }

    /**
     * <b>Đếm theo email chặn NHIỀU nguồn cùng nhắm vào một hộp thư</b> — đây là vector bắt hệ thống
     * gửi thư rác hộ, và giới hạn theo IP một mình không chặn được nó.
     * <p>
     * Ca này cũng khoá lại việc <b>không short-circuit</b>: nếu {@code hasExceededLimit} dùng
     * {@code ||} giữa hai phép kiểm thì khoá email sẽ không được tăng khi khoá IP chưa vượt... và
     * ngược lại, một cách viết sai khác sẽ khiến khoá email không được tăng khi khoá IP đã vượt.
     * Đổi IP mỗi lần gọi là cách duy nhất chứng minh bộ đếm theo email vẫn chạy độc lập.
     */
    @Test
    @DisplayName("Doi IP moi lan nhung cung mot email: van bi chan — do la vector gui thu rac ho")
    void rotatingIpCannotFloodOneMailbox() {
        ForgotPasswordRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertFalse(limiter.hasExceededLimit("10.0.0." + attempt, EMAIL));
        }
        assertTrue(limiter.hasExceededLimit("10.0.0.99", EMAIL));
    }

    /**
     * <b>Chuẩn hoá hoa thường là bắt buộc, không phải cho gọn.</b> Cột {@code user.email} dùng
     * collation {@code utf8mb4_unicode_ci} nên MySQL coi hai cách viết là một địa chỉ; nếu bộ đếm
     * phân biệt hoa thường thì đổi kiểu chữ là lách được giới hạn, mà mail vẫn tới đúng một hộp thư.
     */
    @Test
    @DisplayName("Doi hoa thuong cua email KHONG lach duoc gioi han")
    void emailKeyIsCaseInsensitive() {
        ForgotPasswordRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertFalse(limiter.hasExceededLimit("10.0.0." + attempt, EMAIL));
        }
        assertTrue(limiter.hasExceededLimit("10.0.0.99", "DEMO@NongSanSach.VN"));
    }

    /**
     * Hai người dùng khác nhau ở hai IP khác nhau <b>không</b> được ảnh hưởng lẫn nhau — nếu không,
     * bộ chống lạm dụng tự nó trở thành một sự cố từ chối dịch vụ.
     */
    @Test
    @DisplayName("Nguoi dung khac, IP khac: khong anh huong lan nhau")
    void separateCallersHaveSeparateBudgets() {
        ForgotPasswordRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 2; attempt++) {
            limiter.hasExceededLimit(IP, EMAIL);
        }

        assertFalse(limiter.hasExceededLimit("10.0.0.2", "nguoi-khac@nongsansach.vn"),
                "Mot nguoi bi chan khong duoc phep keo theo nguoi khac");
    }

    /**
     * {@code null} không được biến thành một đường vòng qua giới hạn: mọi lời gọi thiếu IP gom vào
     * <i>một</i> khoá chung thay vì mỗi cái một khoá riêng.
     */
    @Test
    @DisplayName("IP null gom vao mot khoa chung, khong phai mot duong vong qua gioi han")
    void nullIpSharesOneBucket() {
        ForgotPasswordRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertFalse(limiter.hasExceededLimit(null, "dia-chi-" + attempt + "@nongsansach.vn"));
        }
        assertTrue(limiter.hasExceededLimit(null, "dia-chi-moi@nongsansach.vn"));
    }

    /**
     * Cửa sổ trôi qua thì hạn mức phải được cấp lại — nếu không, một người gõ nhầm ba lần sẽ bị khoá
     * vĩnh viễn khỏi chính tài khoản của mình.
     */
    @Test
    @DisplayName("Cua so troi qua thi cap lai han muc")
    void budgetResetsAfterWindow() throws Exception {
        // Cua so 1 mili-giay: kiem duoc hanh vi het han ma khong phai cho that.
        ForgotPasswordRateLimiter limiter =
                new ForgotPasswordRateLimiter(MAX_ATTEMPTS, Duration.ofMillis(1));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            limiter.hasExceededLimit(IP, EMAIL);
        }

        Thread.sleep(20);

        assertFalse(limiter.hasExceededLimit(IP, EMAIL),
                "Het cua so thi phai duoc goi lai — khong ai bi khoa vinh vien khoi tai khoan cua minh");
    }
}
