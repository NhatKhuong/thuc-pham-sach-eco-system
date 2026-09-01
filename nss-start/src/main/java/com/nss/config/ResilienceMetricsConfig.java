package com.nss.config;

import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.controller.config.ApiRateLimitInterceptor;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;

/**
 * Nối trạng thái RateLimiter/CircuitBreaker (backlog 0021, ADR 0005) vào Micrometer, để chúng lên
 * được {@code /actuator/prometheus} (backlog 0038).
 * <p>
 * <b>Vì sao cần một class riêng thay vì tự Resilience4j tự đăng ký.</b>
 * {@code resilience4j-ratelimiter}/{@code resilience4j-circuitbreaker} KHÔNG tự động publish metric
 * — đó là việc của {@code resilience4j-spring-boot3} (starter tự động wiring qua AOP), thứ ADR 0005
 * đã cố ý loại bỏ ở backlog 0021 để tránh kéo theo Actuator sớm. Dự án này dùng Resilience4j qua API
 * lập trình thuần tuý (không autoconfiguration), nên việc bind vào {@code MeterRegistry} phải làm
 * tay — đây chính là chỗ làm tay đó, và CHỈ một chỗ.
 * <p>
 * <b>Vì sao cần sửa {@code ApiRateLimitInterceptor}/{@code MailAppServiceImpl} thay vì chỉ thêm
 * class này.</b> {@code TaggedRateLimiterMetrics}/{@code TaggedCircuitBreakerMetrics}
 * (resilience4j-micrometer) chỉ nhận một {@code RateLimiterRegistry}/{@code CircuitBreakerRegistry}
 * — không có overload cho một instance {@code RateLimiter}/{@code CircuitBreaker} rời. Hai class
 * kia trước đây dựng limiter/breaker bằng factory tĩnh ({@code RateLimiter.of}/
 * {@code CircuitBreaker.of}), không để lại registry nào. Backlog 0038 đổi chúng sang dựng qua một
 * registry nội bộ (getter {@code getRateLimiterRegistry()}/{@code getCircuitBreakerRegistry()}) —
 * đổi CÁCH tạo, config/ngưỡng/hành vi giữ nguyên 100%.
 * <p>
 * <b>Tiêm {@link MailAppService} theo INTERFACE, không phải {@code MailAppServiceImpl}.</b> Ca thật
 * đã đo: {@code sendPasswordResetMail}/{@code sendEmailConfirmationMail}/{@code sendOrderStatusEmail}
 * đều {@code @Async} nên Spring bọc bean thật bằng một JDK dynamic proxy — tiêm theo type cụ thể
 * ném {@code BeanNotOfRequiredTypeException} ngay lúc context khởi động ({@code HelloEndpointTest}
 * và mọi {@code @SpringBootTest} khác đỏ hết, không riêng gì test liên quan tới mail). Xem javadoc
 * {@link MailAppService#getCircuitBreakerRegistry()}.
 * <p>
 * <b>Dev-only</b>: registry Micrometer đích ({@code meterRegistry}) chỉ export qua
 * {@code /actuator/prometheus} trên management port riêng (§Contract backlog 0038) — không chạm
 * {@code SecurityConfig}, không chạm {@code /api/**}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ResilienceMetricsConfig {

    private final ApiRateLimitInterceptor apiRateLimitInterceptor;

    private final MailAppService mailAppService;

    private final MeterRegistry meterRegistry;

    /**
     * Bind cả hai registry vào {@link #meterRegistry} ngay khi bean này được dựng xong — trước bất
     * kỳ request nào tới, nên số liệu (kể cả {@code state=CLOSED}/{@code 0 reject}) đã có mặt từ
     * request scrape đầu tiên, không phải đợi một sự kiện xảy ra trước.
     * <p>
     * <b>Null-safe cho registry — ca thật đã cắn.</b> {@code OrderStatusChangedOutboxIntegrationTest}
     * khai {@code @MockBean MailAppService}: Mockito trả {@code null} cho một method không được
     * stub trả kiểu object, nên {@code mailAppService.getCircuitBreakerRegistry()} là {@code null}
     * trong CHÍNH context đó — không đo bằng {@code @SpringBootTest} thông thường vì hầu hết test
     * khác không mock {@code MailAppService}. Thiếu guard này thì {@code TaggedCircuitBreakerMetrics
     * .ofCircuitBreakerRegistry(null)} ném {@code NullPointerException} ngay lúc context khởi động,
     * làm gãy MỌI test trong lớp đó (context load failure, không riêng gì test liên quan tới mail).
     * Bỏ qua bind (kèm {@code log.warn}) khi registry null — không đổi hành vi ở production, nơi cả
     * hai bean luôn là instance thật.
     */
    @PostConstruct
    public void bindResilienceMetrics() {
        RateLimiterRegistry rateLimiterRegistry = apiRateLimitInterceptor.getRateLimiterRegistry();
        if (rateLimiterRegistry != null) {
            TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry).bindTo(meterRegistry);
        } else {
            log.warn("bindResilienceMetrics: rateLimiterRegistry null (mock trong test?), bo qua bind");
        }
        CircuitBreakerRegistry circuitBreakerRegistry = mailAppService.getCircuitBreakerRegistry();
        if (circuitBreakerRegistry != null) {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);
        } else {
            log.warn("bindResilienceMetrics: circuitBreakerRegistry null (mock trong test?), bo qua bind");
        }
        log.info("ResilienceMetricsConfig: da bind RateLimiter + CircuitBreaker metrics vao Micrometer");
    }
}
