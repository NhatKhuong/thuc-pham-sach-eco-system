package com.nss.ddd.infrastructure.distributed.redisson.config;

import lombok.extern.slf4j.Slf4j;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Client Redisson dùng cho {@code DistributedLockService} (backlog 0035 Phase 0/3,
 * architecture/01-overview.md §2/§4).
 * <p>
 * <b>Đọc {@code spring.data.redis.host}/{@code .port} qua {@code @Value} — KHÔNG tự khai địa chỉ
 * riêng.</b> Đây là chỗ sửa đúng nợ kỹ thuật đã ghi ở architecture §11 "Redisson hardcode address,
 * tách rời {@code spring.data.redis}" của dự án tham chiếu: một nguồn cấu hình duy nhất cho cả
 * Lettuce ({@code StringRedisTemplate}, tự động cấu hình bởi {@code spring-boot-starter-data-redis})
 * lẫn Redisson (thủ công ở đây, vì Redisson không có Spring Boot starter chính chủ).
 * <p>
 * <b>{@code @Lazy} là điều kiện bắt buộc, không phải tối ưu khởi động.</b> {@code Redisson.create()}
 * kết nối tới Redis <i>ngay lập tức</i> và ném exception nếu không kết nối được — khác hẳn
 * {@code LettuceConnectionFactory} (lazy theo mặc định, chỉ kết nối khi có lệnh Redis đầu tiên). Nếu
 * bean này không lazy, MỌI {@code @SpringBootTest} nạp context đầy đủ (kể cả những test không đụng gì
 * tới cache/lock, ví dụ {@code SecurityRulesTest}, {@code PublicCatalogEndpointsTest}) sẽ làm
 * {@code mvn clean package} mặc định (không có Redis chạy) đỏ toàn bộ — đúng thứ evidence bar của
 * ticket 0035 cấm ("mvn -o clean package xanh"). Với {@code @Lazy}, Spring tiêm một proxy JDK (
 * {@code RedissonClient} là interface) và chỉ thật sự gọi {@code Redisson.create()} ở lần gọi
 * method ĐẦU TIÊN — luôn nằm trong khối {@code try/catch} của
 * {@code RedissonDistributedLockServiceImpl} (coding-conventions §11: "Thất bại của cache làm gãy
 * luồng nghiệp vụ" là cấm tuyệt đối), nên một Redis chết không làm vỡ context, chỉ làm
 * stampede-protection rơi về "đọc DB không khoá" đúng như architecture §4/Phase 3 của ticket mô tả.
 * <p>
 * <b>Timeout ngắn có chủ ý</b> — mặc định của Redisson (10s connect, 3 lần retry) biến MỘT lần gọi
 * đầu tiên khi Redis chết thành hàng chục giây treo, nhân lên mỗi {@code @SpringBootTest} khác context
 * đụng tới lock/cache. 1 giây kết nối, không retry, là đủ nhanh để phát hiện "Redis không tới được"
 * mà không làm bộ test mặc định (chạy không cần {@code docker compose up}) chậm bất thường; khi Redis
 * thật sự chạy (dev, CI có service), 1 giây dư sức cho một kết nối localhost.
 */
@Slf4j
@Configuration
public class RedissonConfig {

    /** Kết nối trong 1s hoặc bỏ cuộc — xem javadoc cấp class về lý do timeout ngắn. */
    private static final int CONNECT_TIMEOUT_MS = 1000;

    /** Lệnh Redis quá 1s coi như treo — Redisson mặc định là lock TTL/thao tác đều đi qua timeout này. */
    private static final int COMMAND_TIMEOUT_MS = 1000;

    /** Không retry: một lần thất bại là đủ để rơi về nhánh "đọc DB không khoá" của Phase 3. */
    private static final int RETRY_ATTEMPTS = 0;

    @Bean
    @Lazy
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                         @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setTimeout(COMMAND_TIMEOUT_MS)
                .setRetryAttempts(RETRY_ATTEMPTS);
        log.info("redissonClient: dung cau hinh Redis | host={} port={}", host, port);
        return Redisson.create(config);
    }
}
