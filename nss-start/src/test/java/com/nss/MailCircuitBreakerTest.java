package com.nss;

import com.nss.ddd.application.service.mail.impl.MailAppServiceImpl;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CircuitBreaker của đường gửi SMTP (backlog 0021 Phase 2, ADR 0005).
 * <p>
 * Bốn thứ được khoá ở đây, và <b>cả bốn đều hỏng im lặng</b> — endpoint gọi tới
 * {@code MailAppServiceImpl} trả <b>204 ở cả hai nhánh</b>, nên không có mã HTTP nào phân biệt
 * "đã gửi" với "đã bỏ qua" với "đã thất bại":
 * <ul>
 *   <li><b>Breaker phải THẬT SỰ ĐẾM.</b> Ranh giới đặt <i>ngoài</i> khối catch của
 *       {@code sendPasswordResetMail} sẽ thấy 0 lỗi vĩnh viễn (khối catch nuốt hết) và không bao
 *       giờ mở. Ca {@link #breakerOpensAfterEnoughSmtpFailures} là ca duy nhất phân biệt được.</li>
 *   <li><b>Breaker mở phải BỎ QUA lần gửi, chứ không phải thất bại chậm.</b> Đo bằng số lần
 *       {@code JavaMailSender} thật sự được gọi, không bằng exception.</li>
 *   <li><b>Breaker mở không được ném ra ngoài method.</b> Method chạy {@code @Async} nên một
 *       {@code CallNotPermittedException} thoát ra là một luồng chết không ai bắt.</li>
 *   <li><b>Cấu hình sai phải fail lúc khởi động</b>, kèm đúng tên khoá — tiền lệ {@code JwtConfig},
 *       {@code ForgotPasswordRateLimiter}, {@code ApiRateLimitInterceptor}.</li>
 * </ul>
 */
class MailCircuitBreakerTest {

    private static final String FROM = "no-reply@nongsansach.local";

    private static final String RESET_URL = "http://localhost:5173/dat-lai-mat-khau";

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final Duration WAIT_IN_OPEN = Duration.ofSeconds(60);

    private static final String TO_EMAIL = "demo@nongsansach.vn";

    private static final String RAW_TOKEN = "raw-token-for-test";

    /**
     * Bộ gửi giả đếm số lần {@code send} được gọi thật.
     * <p>
     * <b>Số lần gọi là bằng chứng, không phải exception.</b> Một breaker "mở" mà vẫn để lời gọi đi
     * xuống SMTP thì không bảo vệ được gì, và nhìn từ bên ngoài nó y hệt một breaker đang hoạt
     * động: cùng không có exception thoát ra, cùng một endpoint 204.
     */
    private static final class CountingMailSender extends JavaMailSenderImpl {

        private final AtomicInteger sendCount = new AtomicInteger();

        private final boolean shouldFail;

        private CountingMailSender(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            sendCount.incrementAndGet();
            if (shouldFail) {
                // Dung ho MailException — dung thu Resilience4j duoc khai de ghi nhan, va cung la
                // thu JavaMailSenderImpl that su nem khi SMTP khong tra loi.
                throw new MailSendException("simulated smtp failure");
            }
        }
    }

    /**
     * @param sender               bộ gửi
     * @param slidingWindowSize    kích thước cửa sổ đếm
     * @param minimumNumberOfCalls số lời gọi tối thiểu
     * @param failureRatePercent   ngưỡng tỉ lệ lỗi
     * @param halfOpenCalls        số lời gọi thăm dò
     * @return service đã dựng
     */
    private MailAppServiceImpl genService(JavaMailSender sender, int slidingWindowSize,
                                          int minimumNumberOfCalls, int failureRatePercent,
                                          int halfOpenCalls) {
        return new MailAppServiceImpl(sender, FROM, RESET_URL, TOKEN_TTL,
                slidingWindowSize, minimumNumberOfCalls, failureRatePercent, WAIT_IN_OPEN,
                halfOpenCalls);
    }

    // ========== BREAKER DEM THAT ==========

    @Test
    @DisplayName("BAY 2: breaker DEM duoc loi SMTP du sendPasswordResetMail tu nuot exception")
    void breakerOpensAfterEnoughSmtpFailures() {
        // Day la ca duy nhat phan biet "breaker dat trong khoi catch" voi "breaker dat ngoai khoi
        // catch". Dat ngoai thi khoi catch nuot het loi truoc khi breaker nhin thay, breaker vinh
        // vien CLOSED, va MOI assertion khac trong file nay VAN XANH.
        CountingMailSender sender = new CountingMailSender(true);
        MailAppServiceImpl service = genService(sender, 4, 4, 50, 3);

        // 4 lan dau: breaker con CLOSED nen ca 4 deu di xuong SMTP that va deu that bai.
        for (int i = 0; i < 4; i++) {
            service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);
        }
        assertEquals(4, sender.sendCount.get());

        // Ti le loi 100% > 50% va da du minimumNumberOfCalls => OPEN. Cac lan sau BI BO QUA:
        // so lan gui KHONG tang.
        service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);
        service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);
        assertEquals(4, sender.sendCount.get());
    }

    @Test
    @DisplayName("breaker mo KHONG nem ra ngoai method — luong @Async khong duoc gay")
    void openBreakerNeverThrowsOutOfTheMethod() {
        CountingMailSender sender = new CountingMailSender(true);
        MailAppServiceImpl service = genService(sender, 2, 2, 50, 3);
        service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);
        service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);

        // Luc nay breaker OPEN. CallNotPermittedException phai bi bat ngay trong method: no chay
        // tren luong khac nen nem ra la mot luong chet khong ai bat, va endpoint goi toi da tra 204
        // tu lau.
        assertDoesNotThrow(() -> service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN));
    }

    @Test
    @DisplayName("chua du minimumNumberOfCalls thi breaker KHONG mo du 100% that bai")
    void breakerStaysClosedBelowMinimumNumberOfCalls() {
        CountingMailSender sender = new CountingMailSender(true);
        MailAppServiceImpl service = genService(sender, 20, 10, 50, 3);

        for (int i = 0; i < 9; i++) {
            service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);
        }

        // Ca 9 lan deu that bai (100% > 50%) nhung chua cham nguong 10 loi goi toi thieu, nen moi
        // lan deu phai di xuong SMTP that.
        assertEquals(9, sender.sendCount.get());
    }

    @Test
    @DisplayName("SMTP song thi breaker khong bao gio can — moi lan gui deu di xuong")
    void breakerNeverInterferesWhenSmtpIsHealthy() {
        CountingMailSender sender = new CountingMailSender(false);
        MailAppServiceImpl service = genService(sender, 4, 4, 50, 3);

        for (int i = 0; i < 12; i++) {
            service.sendPasswordResetMail(TO_EMAIL, RAW_TOKEN);
        }

        // Control duong cua ca test dau tien: neu con so nay khac 12 thi breaker dang chan ca luong
        // lanh, va moi bang chung "breaker mo" o tren mat y nghia.
        assertEquals(12, sender.sendCount.get());
    }

    // ========== FAIL LUC KHOI DONG ==========

    @Test
    @DisplayName("sliding-window-size <= 0 fail luc khoi dong, kem ten khoa")
    void invalidSlidingWindowSizeFailsAtStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> genService(new CountingMailSender(false), 0, 1, 50, 3));
        assertTrue(e.getMessage().contains("nss.mail.circuit-breaker.sliding-window-size"),
                e.getMessage());
    }

    @Test
    @DisplayName("minimum-number-of-calls <= 0 fail luc khoi dong, kem ten khoa")
    void invalidMinimumNumberOfCallsFailsAtStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> genService(new CountingMailSender(false), 20, 0, 50, 3));
        assertTrue(e.getMessage().contains("nss.mail.circuit-breaker.minimum-number-of-calls"),
                e.getMessage());
    }

    @Test
    @DisplayName("minimum-number-of-calls > sliding-window-size fail thay vi bi ha xuong im lang")
    void minimumNumberOfCallsAboveWindowSizeFailsAtStartup() {
        // Khong co phep kiem nay thi Resilience4j tu ha minimumNumberOfCalls xuong bang
        // slidingWindowSize va KHONG noi gi — config doc mot dang, breaker chay mot dang khac.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> genService(new CountingMailSender(false), 5, 10, 50, 3));
        assertTrue(e.getMessage().contains("nss.mail.circuit-breaker.minimum-number-of-calls"),
                e.getMessage());
        assertTrue(e.getMessage().contains("nss.mail.circuit-breaker.sliding-window-size"),
                e.getMessage());
    }

    @Test
    @DisplayName("failure-rate-threshold ngoai (0, 100] fail luc khoi dong, kem ten khoa")
    void invalidFailureRateThresholdFailsAtStartup() {
        IllegalStateException tooLow = assertThrows(IllegalStateException.class,
                () -> genService(new CountingMailSender(false), 20, 10, 0, 3));
        assertTrue(tooLow.getMessage().contains("nss.mail.circuit-breaker.failure-rate-threshold"),
                tooLow.getMessage());

        IllegalStateException tooHigh = assertThrows(IllegalStateException.class,
                () -> genService(new CountingMailSender(false), 20, 10, 101, 3));
        assertTrue(tooHigh.getMessage().contains("nss.mail.circuit-breaker.failure-rate-threshold"),
                tooHigh.getMessage());
    }

    @Test
    @DisplayName("wait-duration-in-open-state <= 0 fail luc khoi dong, kem ten khoa")
    void invalidWaitDurationFailsAtStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new MailAppServiceImpl(new CountingMailSender(false), FROM, RESET_URL,
                        TOKEN_TTL, 20, 10, 50, Duration.ZERO, 3));
        assertTrue(e.getMessage().contains("nss.mail.circuit-breaker.wait-duration-in-open-state"),
                e.getMessage());
    }

    @Test
    @DisplayName("permitted-calls-in-half-open-state <= 0 fail luc khoi dong, kem ten khoa")
    void invalidHalfOpenCallsFailsAtStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> genService(new CountingMailSender(false), 20, 10, 50, 0));
        assertTrue(e.getMessage()
                        .contains("nss.mail.circuit-breaker.permitted-calls-in-half-open-state"),
                e.getMessage());
    }
}
