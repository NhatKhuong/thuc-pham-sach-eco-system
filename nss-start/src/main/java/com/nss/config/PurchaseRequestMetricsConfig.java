package com.nss.config;

import com.nss.ddd.domain.repository.PurchaseRequestRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Gauge nghiệp vụ {@code purchase_request_pending_age_seconds} (backlog 0039 Phase 7).
 * <p>
 * <b>Vì sao cần một gauge tự viết thay vì chỉ dựa vào
 * {@code kafka_consumer_fetch_manager_records_lag*}.</b> Đo thật trên {@code /actuator/prometheus}
 * sau khi nối dây {@code PurchaseRequestedConsumer} (backlog 0039, xem Outcome của ticket) xác nhận
 * metric lag ĐÃ tự động expose qua {@code micrometer-core}/{@code spring-kafka} — không cần thêm
 * cấu hình. Nhưng lag đo <b>khoảng cách offset</b>, không đo <b>thời gian một request đứng yên</b>:
 * một consumer "treo nhưng còn sống" (block ở một lời gọi downstream, ví dụ DB lock hoặc I/O treo)
 * vẫn đang xử lý offset hiện tại — lag có thể trông vẫn thấp hoặc bằng 0 trong khi request đó không
 * bao giờ resolve. Gauge này đo trực tiếp trên chính tài nguyên nghiệp vụ
 * ({@code purchase_request.status = PENDING}), nên bắt được đúng ca lag không bắt được.
 * <p>
 * <b>Pull-based, không giữ state.</b> {@link Gauge#builder(String, Object, java.util.function.ToDoubleFunction)}
 * đăng ký một hàm được GỌI LẠI mỗi lần Prometheus scrape (15s, {@code environment/prometheus/prometheus.yml})
 * — mỗi lần scrape là một lần {@code SELECT MIN(created_at) ... WHERE status = 0} qua
 * {@code idx_status_created}, không phải một giá trị cache có thể lệch.
 * <p>
 * Cùng khuôn với {@code ResilienceMetricsConfig}: dựng ở {@code nss-start} vì đây là chỗ duy nhất
 * {@code MeterRegistry} (Actuator + micrometer-registry-prometheus, backlog 0038) sẵn có trên
 * classpath, tránh phải thêm dependency micrometer tường minh vào {@code nss-application}.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PurchaseRequestMetricsConfig {

    private static final String METRIC_NAME = "purchase_request_pending_age_seconds";

    private static final String METRIC_DESCRIPTION =
            "Tuoi (giay) cua purchase_request PENDING cu nhat — 0 khi khong co request nao dang PENDING";

    private final PurchaseRequestRepository purchaseRequestRepository;

    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void bindPurchaseRequestPendingAgeGauge() {
        Gauge.builder(METRIC_NAME, purchaseRequestRepository, this::genOldestPendingAgeSeconds)
                .description(METRIC_DESCRIPTION)
                .register(meterRegistry);
        log.info("PurchaseRequestMetricsConfig: da dang ky gauge {}", METRIC_NAME);
    }

    /**
     * @param repository port đọc {@code purchase_request} — nhận qua tham số vì đây là state object
     *                   {@link Gauge#builder(String, Object, java.util.function.ToDoubleFunction)}
     *                   giữ tham chiếu YẾU tới (khuyến nghị của Micrometer, tránh gauge tự nó giữ
     *                   sống một bean); bean này là singleton sống suốt vòng đời app nên không có
     *                   rủi ro bị GC giữa hai lần scrape
     * @return tuổi (giây) của request PENDING cũ nhất; {@code 0} khi không có request nào PENDING
     */
    private double genOldestPendingAgeSeconds(PurchaseRequestRepository repository) {
        return repository.findOldestPendingCreatedAt()
                .map(createdAt -> (double) Duration.between(createdAt, LocalDateTime.now(ZoneOffset.UTC)).getSeconds())
                .orElse(0.0);
    }
}
