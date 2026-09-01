package com.nss;

import com.nss.ddd.controller.config.ForgotPasswordRateLimiter;
import com.nss.ddd.controller.config.ResendConfirmationRateLimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm cổng chống dò tần suất của {@code POST /api/auth/resend-confirmation} (backlog 0037).
 * <p>
 * <b>Mirror nguyên cấu trúc của {@code ForgotPasswordRateLimiterTest}</b> — hai lớp giống hệt nhau
 * về hành vi (đọc javadoc {@code ResendConfirmationRateLimiter}: lý do là hai ngân sách <b>độc
 * lập</b>, không phải hai cách viết khác nhau), nên ba bất biến hay hỏng im lặng khi "dọn dẹp" cũng
 * giống hệt: đếm theo hai khoá độc lập, không short-circuit giữa hai khoá, và chuẩn hoá hoa thường
 * của email.
 */
class ResendConfirmationRateLimiterTest {

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private static final String IP = "10.0.0.1";

    private static final String EMAIL = "demo@nongsansach.vn";

    /**
     * @return bộ giới hạn với ngưỡng nhỏ để test đọc được
     */
    private ResendConfirmationRateLimiter genLimiter() {
        return new ResendConfirmationRateLimiter(MAX_ATTEMPTS, WINDOW);
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
        assertThrows(IllegalStateException.class, () -> new ResendConfirmationRateLimiter(0, WINDOW));
        assertThrows(IllegalStateException.class, () -> new ResendConfirmationRateLimiter(-1, WINDOW));
        assertThrows(IllegalStateException.class,
                () -> new ResendConfirmationRateLimiter(MAX_ATTEMPTS, Duration.ZERO));
        assertThrows(IllegalStateException.class,
                () -> new ResendConfirmationRateLimiter(MAX_ATTEMPTS, Duration.ofMinutes(-1)));
    }

    // ========== NGUONG ==========

    @Test
    @DisplayName("Dung nguong thi cho qua, vuot mot lan thi chan")
    void limitAllowsExactlyMaxAttempts() {
        ResendConfirmationRateLimiter limiter = genLimiter();

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
        ResendConfirmationRateLimiter limiter = genLimiter();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertFalse(limiter.hasExceededLimit(IP, "dia-chi-" + attempt + "@nongsansach.vn"));
        }
        assertTrue(limiter.hasExceededLimit(IP, "dia-chi-moi@nongsansach.vn"));
    }

    /**
     * <b>Đếm theo email chặn NHIỀU nguồn cùng nhắm vào một hộp thư</b> — đây là vector bắt hệ thống
     * gửi thư rác hộ, và giới hạn theo IP một mình không chặn được ca đó.
     * <p>
     * Ca này cũng khoá lại việc <b>không short-circuit</b>: đổi IP mỗi lần gọi là cách duy nhất
     * chứng minh bộ đếm theo email vẫn chạy độc lập, cùng lý do đã viết ở
     * {@code ForgotPasswordRateLimiterTest}.
     */
    @Test
    @DisplayName("Doi IP moi lan nhung cung mot email: van bi chan — do la vector gui thu rac ho")
    void rotatingIpCannotFloodOneMailbox() {
        ResendConfirmationRateLimiter limiter = genLimiter();

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
        ResendConfirmationRateLimiter limiter = genLimiter();

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
        ResendConfirmationRateLimiter limiter = genLimiter();

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
        ResendConfirmationRateLimiter limiter = genLimiter();

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
        ResendConfirmationRateLimiter limiter =
                new ResendConfirmationRateLimiter(MAX_ATTEMPTS, Duration.ofMillis(1));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            limiter.hasExceededLimit(IP, EMAIL);
        }

        Thread.sleep(20);

        assertFalse(limiter.hasExceededLimit(IP, EMAIL),
                "Het cua so thi phai duoc goi lai — khong ai bi khoa vinh vien khoi tai khoan cua minh");
    }

    /**
     * <b>Hai endpoint là hai ngân sách độc lập</b> — chạm ngưỡng {@code resend-confirmation} không
     * được phép ăn vào hạn mức {@code forgot-password} của cùng địa chỉ đó, đúng lý do lớp này tồn
     * tại riêng thay vì tái dùng instance của {@code ForgotPasswordRateLimiter} (xem javadoc cấp
     * class của {@code ResendConfirmationRateLimiter}).
     */
    @Test
    @DisplayName("Ngan sach doc lap voi ForgotPasswordRateLimiter, du cung IP va cung email")
    void budgetIsIndependentFromForgotPasswordRateLimiter() {
        ForgotPasswordRateLimiter forgotPasswordLimiter =
                new ForgotPasswordRateLimiter(MAX_ATTEMPTS, WINDOW);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 2; attempt++) {
            forgotPasswordLimiter.hasExceededLimit(IP, EMAIL);
        }

        ResendConfirmationRateLimiter resendLimiter = genLimiter();

        assertFalse(resendLimiter.hasExceededLimit(IP, EMAIL),
                "resend-confirmation phai con nguyen han muc du forgot-password da bi chan");
    }
}
