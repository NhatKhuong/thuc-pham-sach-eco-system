package com.nss;

import com.nss.ddd.controller.config.ApiRateLimitInterceptor;
import com.nss.ddd.controller.exception.TooManyRequestsException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trần thông lượng của biên vào — hành vi của chính cổng (backlog 0021 Phase 1).
 * <p>
 * Ba thứ được khoá ở đây, cả ba đều <b>hỏng im lặng</b> nếu không có test:
 * <ul>
 *   <li><b>Từ chối chứ không xếp hàng.</b> {@code timeoutDuration} mặc định của Resilience4j là 5
 *       giây: hết permit thì caller bị park chờ, không ai nhận 429, mọi người cùng chậm. Mọi
 *       request vẫn trả 200 nên <i>không test nào đỏ</i> — trừ test đo thời gian ở dưới.</li>
 *   <li><b>Ngưỡng {@code <= 0} phải fail lúc khởi động.</b> Ngưỡng 0 chặn cả request đầu tiên,
 *       endpoint chết hoàn toàn nhưng vẫn trả đúng hình dạng lỗi.</li>
 *   <li><b>Ba tier là ba bể permit riêng.</b> Gộp nhầm thì một cơn tăng lưu lượng đường đọc làm
 *       chết luôn đường đăng nhập.</li>
 * </ul>
 */
class ApiRateLimitInterceptorTest {

    /** Chu kỳ đủ dài để không có lần nạp lại nào chen vào giữa một ca test. */
    private static final Duration PERIOD = Duration.ofSeconds(10);

    /** Trần trên của "từ chối ngay". Mặc định xếp hàng của Resilience4j là 5000ms. */
    private static final long IMMEDIATE_REJECT_MAX_MILLIS = 500L;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    /**
     * @param authLimit  ngưỡng tier auth
     * @param writeLimit ngưỡng tier write
     * @param readLimit  ngưỡng tier read
     * @return interceptor đã cấu hình
     */
    private ApiRateLimitInterceptor genInterceptor(int authLimit, int writeLimit, int readLimit) {
        return new ApiRateLimitInterceptor(authLimit, PERIOD, writeLimit, PERIOD, readLimit, PERIOD);
    }

    /**
     * @param method verb HTTP
     * @param path   đường dẫn
     * @return request giả đã set verb và URI
     */
    private MockHttpServletRequest genRequest(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    @Test
    @DisplayName("het permit thi nem TooManyRequestsException, khong xep hang")
    void preHandleRejectsWhenPermitsAreExhausted() {
        ApiRateLimitInterceptor interceptor = genInterceptor(100, 100, 2);

        assertTrue(interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));
        assertTrue(interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));
        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));
    }

    @Test
    @DisplayName("BAY 1: mot request bi tu choi tra ve NGAY, khong cho ~5000ms")
    void preHandleRejectsImmediatelyInsteadOfQueueing() {
        // Day la ca duy nhat phan biet "tu choi" voi "xep hang". Neu ai do doi TIMEOUT_DURATION ve
        // mac dinh 5 giay cua Resilience4j thi moi assertion khac trong file nay VAN XANH — chi con
        // so duoi day do duoc.
        ApiRateLimitInterceptor interceptor = genInterceptor(100, 100, 1);
        interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object());

        long startedAt = System.nanoTime();
        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(elapsedMillis < IMMEDIATE_REJECT_MAX_MILLIS,
                "rejected call must return immediately, took " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("ba tier la ba be permit rieng")
    void tiersHoldSeparatePermitPools() {
        ApiRateLimitInterceptor interceptor = genInterceptor(1, 1, 1);
        interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object());
        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));

        // Tier read da can permit; hai tier con lai phai con nguyen.
        assertTrue(interceptor.preHandle(genRequest("POST", "/api/orders"), response, new Object()));
        assertTrue(interceptor.preHandle(genRequest("POST", "/api/auth/login"), response, new Object()));
    }

    @Test
    @DisplayName("POST /api/auth/** tieu permit cua tier auth, khong phai cua tier write")
    void authPathConsumesAuthTier() {
        ApiRateLimitInterceptor interceptor = genInterceptor(1, 100, 100);
        assertTrue(interceptor.preHandle(genRequest("POST", "/api/auth/login"), response, new Object()));
        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(genRequest("POST", "/api/auth/register"), response, new Object()));
        // Tier write van con nguyen: neu auth muon nham be cua write thi dong duoi day cung do.
        assertTrue(interceptor.preHandle(genRequest("POST", "/api/orders"), response, new Object()));
    }

    @Test
    @DisplayName("het chu ky thi permit duoc nap lai")
    void permitsRefreshAfterThePeriod() throws Exception {
        ApiRateLimitInterceptor interceptor =
                new ApiRateLimitInterceptor(100, PERIOD, 100, PERIOD, 1, Duration.ofMillis(50));
        assertTrue(interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));
        assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));

        Thread.sleep(120L);

        assertTrue(interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));
    }

    @Test
    @DisplayName("nguong <= 0 fail luc khoi dong, khong doi den request dau tien")
    void nonPositiveLimitFailsFast() {
        assertThrows(IllegalStateException.class, () -> genInterceptor(0, 30, 100));
        assertThrows(IllegalStateException.class, () -> genInterceptor(10, -1, 100));
        assertThrows(IllegalStateException.class, () -> genInterceptor(10, 30, 0));
    }

    @Test
    @DisplayName("chu ky <= 0 hoac thieu fail luc khoi dong, kem ten khoa cau hinh")
    void nonPositivePeriodFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ApiRateLimitInterceptor(10, Duration.ZERO, 30, PERIOD, 100, PERIOD));
        assertTrue(e.getMessage().contains("nss.rate-limit.auth.limit-refresh-period"),
                "message must name the config key, got: " + e.getMessage());

        assertThrows(IllegalStateException.class,
                () -> new ApiRateLimitInterceptor(10, PERIOD, 30, Duration.ofSeconds(-1), 100, PERIOD));
        assertThrows(IllegalStateException.class,
                () -> new ApiRateLimitInterceptor(10, PERIOD, 30, PERIOD, 100, null));
    }

    @Test
    @DisplayName("thong diep 429 viet tieng Viet va khong neu nguong")
    void rejectionMessageIsVietnameseAndHidesTheThreshold() {
        ApiRateLimitInterceptor interceptor = genInterceptor(100, 100, 1);
        interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object());

        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> interceptor.preHandle(genRequest("GET", "/api/products"), response, new Object()));

        assertEquals("Hệ thống đang nhận quá nhiều yêu cầu, vui lòng thử lại sau giây lát.",
                e.getMessage());
    }
}
