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
 * Cổng chống dò tần suất của {@code POST /api/auth/resend-confirmation} (backlog 0037 — "tái dùng
 * pattern rate-limit của {@code ForgotPasswordRateLimiter}", theo đúng chữ ticket).
 * <p>
 * <b>Đọc javadoc của {@link ForgotPasswordRateLimiter} trước — lý do tồn tại, hai khoá (IP +
 * email), vì sao không dùng {@code RateLimiter} của Resilience4j, và giới hạn "sống trong bộ nhớ một
 * tiến trình" đều giống hệt ở đây và không lặp lại ở file này.</b> Class riêng thay vì tái dùng
 * instance của {@code ForgotPasswordRateLimiter} vì hai endpoint là hai ngân sách <b>độc lập</b>: ai
 * đó bấm "gửi lại email xác nhận" nhiều lần không được phép ăn vào hạn mức "quên mật khẩu" của cùng
 * địa chỉ đó, và ngược lại — hai hành vi nghiệp vụ khác nhau, hai bộ đếm khác nhau.
 */
@Slf4j
@Component
public class ResendConfirmationRateLimiter {

    private static final String KEY_PREFIX_IP = "ip:";

    private static final String KEY_PREFIX_EMAIL = "email:";

    /** Cùng lý do và cùng giá trị với {@code ForgotPasswordRateLimiter.MAX_TRACKED_KEYS}. */
    private static final int MAX_TRACKED_KEYS = 100_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private final int maxAttempts;

    private final Duration window;

    /**
     * @param maxAttempts số lần gọi tối đa trong một cửa sổ, cho mỗi khoá
     * @param window độ dài cửa sổ, dạng ISO-8601 ({@code PT15M})
     * @throws IllegalStateException khi cấu hình không dùng được — fail lúc khởi động, đúng tiền lệ
     *                               {@code ForgotPasswordRateLimiter}
     */
    public ResendConfirmationRateLimiter(
            @Value("${nss.auth.resend-confirmation-max-attempts}") int maxAttempts,
            @Value("${nss.auth.resend-confirmation-window}") Duration window) {
        if (maxAttempts <= 0) {
            throw new IllegalStateException(
                    "nss.auth.resend-confirmation-max-attempts must be positive; got: " + maxAttempts);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("nss.auth.resend-confirmation-window must be a positive"
                    + " ISO-8601 duration; got: " + window);
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /**
     * Ghi nhận một lời gọi và cho biết nó có vượt ngưỡng hay không — cùng khuôn
     * {@code ForgotPasswordRateLimiter.hasExceededLimit}: đếm TRƯỚC khi kiểm, kiểm cả hai khoá không
     * short-circuit.
     *
     * @param clientIp địa chỉ IP người gọi; {@code null} được gom vào một khoá chung
     * @param email email đích, so sánh không phân biệt hoa thường
     * @return true nếu lời gọi này vượt ngưỡng và phải bị chặn
     */
    public boolean hasExceededLimit(String clientIp, String email) {
        Instant now = Instant.now();
        boolean ipExceeded = hasExceededKey(KEY_PREFIX_IP + genIpKey(clientIp), now);
        boolean emailExceeded = hasExceededKey(KEY_PREFIX_EMAIL + genEmailKey(email), now);
        if (ipExceeded || emailExceeded) {
            log.warn("hasExceededLimit: resend-confirmation rate limit hit | ip={} byIp={} byEmail={}",
                    clientIp, ipExceeded, emailExceeded);
            return true;
        }
        return false;
    }

    private boolean hasExceededKey(String key, Instant now) {
        ensureCapacity(now);
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || existing.hasExpired(now) ? new Window(now) : existing);
        return current.count.incrementAndGet() > maxAttempts;
    }

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

    private String genIpKey(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }

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

        private boolean hasExpired(Instant now) {
            return startedAt.plus(window).isBefore(now);
        }
    }
}
