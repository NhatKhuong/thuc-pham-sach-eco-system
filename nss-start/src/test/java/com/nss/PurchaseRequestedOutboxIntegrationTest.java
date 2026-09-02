package com.nss;

import com.nss.ddd.application.cronjob.OutboxPublisherJob;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.PurchaseRequestResponse;
import com.nss.ddd.application.service.purchaserequest.PurchaseRequestAppService;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test toàn luồng <b>submit → outbox → publish → Kafka → consumer → order thật</b>
 * (backlog 0039 §Test & evidence bar).
 * <p>
 * <b>@Tag("db")</b> — cần MySQL thật, cùng lane với mọi test khác đánh tag này; bị loại khỏi build
 * mặc định, chạy riêng bằng {@code mvn -pl nss-start test -Dexcluded.test.groups= -Dgroups=db}.
 * <p>
 * <b>{@code @EmbeddedKafka}</b> — cùng pattern đã chọn ở {@code OrderStatusChangedOutboxIntegrationTest}
 * (backlog 0032): broker nhúng trong JVM test, không cần {@code environment/docker-compose-dev.yml}
 * đang chạy sẵn dịch vụ {@code kafka}.
 * <p>
 * <b>Không mock bất cứ thứ gì trong pipeline</b> — khác integration test của backlog 0032 (nơi
 * {@code MailAppService} bị {@code @MockBean}): không có ranh giới ngoài nào ở luồng này cần cách
 * ly (không SMTP, không gateway ngoài), nên toàn bộ đường ống chạy thật 100%, kể cả
 * {@code OrderAppService.createOrderInNewTransaction}. Poll trực tiếp {@code purchase_request.status}
 * qua {@link PurchaseRequestAppService#findByRequestId} thay vì một hook mock.
 */
@SpringBootTest
@Tag("db")
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.PURCHASE_REQUESTED_TOPIC)
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class PurchaseRequestedOutboxIntegrationTest {

    private static final long AWAIT_SECONDS = 20;

    private static final long POLL_INTERVAL_MILLIS = 200;

    @Autowired
    private PurchaseRequestAppService purchaseRequestAppService;

    @Autowired
    private OutboxPublisherJob outboxPublisherJob;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DataSource dataSource;

    private Product genSeedProduct(int stock) {
        Category category = categoryRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed data thieu category id=1 — kiem tra environment/mysql/init/02-seed-data.sql"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        return productRepository.save(new Product()
                .setSlug("purchase-request-test-" + System.nanoTime())
                .setName("San pham luong async (test)")
                .setPrice(10_000L)
                .setUnit("cai")
                .setStock(stock)
                .setSold(0)
                .setRating(BigDecimal.ZERO)
                .setReviewCount(0)
                .setIsFeatured(false)
                .setIsBestSeller(false)
                .setIsActive(true)
                .setCategory(category)
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private CreateOrderCommand genCommand(Long productId) {
        return new CreateOrderCommand()
                .setUserId(null)
                .setItems(List.of(new CartItemCommand()
                        .setProductId(productId).setName("San pham test").setQuantity(1)))
                .setShipping(new ShippingInfoCommand()
                        .setFullName("Khach Async").setPhone("0900000000")
                        .setEmail("purchase-request-" + System.nanoTime() + "@vidu.vn")
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod("cod");
    }

    /**
     * Chạy {@code OutboxPublisherJob} thủ công (không chờ {@code @Scheduled}) rồi poll
     * {@code purchase_request.status} tới khi khác {@code PENDING} — cùng lý do đã ghi ở
     * {@code OrderStatusChangedOutboxIntegrationTest}: test tất định, không phụ thuộc
     * {@code fixedDelay}.
     */
    private PurchaseRequestResponse awaitResolved(String requestId) throws InterruptedException {
        outboxPublisherJob.publishPendingEvents();
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(AWAIT_SECONDS);
        while (System.currentTimeMillis() < deadline) {
            PurchaseRequestResponse response = purchaseRequestAppService.findByRequestId(requestId);
            if (response != null && !"PENDING".equals(response.getStatus())) {
                return response;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        fail("purchase_request " + requestId + " van con PENDING sau " + AWAIT_SECONDS + "s");
        return null;
    }

    private int genCustomerOrderCountForProduct(Long productId) throws Exception {
        String sql = "SELECT COUNT(*) FROM order_item WHERE product_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int genPurchaseRequestCountForIdempotencyKey(String idempotencyKey) throws Exception {
        String sql = "SELECT COUNT(*) FROM purchase_request WHERE idempotency_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, idempotencyKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("Toan luong THANH CONG: submit -> outbox -> Kafka -> consumer -> don that duoc tao")
    void fullPipelineCreatesARealOrder() throws Exception {
        Product product = genSeedProduct(10);
        String idempotencyKey = "it-success-" + System.nanoTime();

        PurchaseRequestResponse submitted = purchaseRequestAppService.submitAsync(
                genCommand(product.getId()), idempotencyKey);
        assertEquals("PENDING", submitted.getStatus());

        PurchaseRequestResponse resolved = awaitResolved(submitted.getRequestId());

        assertEquals("SUCCESS", resolved.getStatus());
        assertNotNull(resolved.getOrderCode(), "SUCCESS phai kem orderCode");
        assertNull(resolved.getFailureCode());
        // Doi chieu: dung 1 dong order_item cho san pham nay — don THAT su duoc tao, khong phai
        // chi mot dong purchase_request noi "SUCCESS" suong.
        assertEquals(1, genCustomerOrderCountForProduct(product.getId()));
    }

    /**
     * <b>Assertion quan trọng nhất của cả ticket</b> — chứng minh {@code REQUIRES_NEW} rollback hoạt
     * động thật trên DB thật: hết hàng thì {@code purchase_request.status = FAILED} VÀ không có
     * {@code customer_order}/{@code order_item} nào được tạo cho sản phẩm đó, dù transaction của
     * consumer (idempotency_key + purchase_request update) vẫn commit bình thường.
     */
    @Test
    @DisplayName("Het hang: purchase_request=FAILED/OUT_OF_STOCK VA KHONG co order_item nao duoc tao (SQL truc tiep)")
    void outOfStockMarksFailedAndCreatesNoOrderRow() throws Exception {
        Product product = genSeedProduct(0);
        String idempotencyKey = "it-oos-" + System.nanoTime();

        PurchaseRequestResponse submitted = purchaseRequestAppService.submitAsync(
                genCommand(product.getId()), idempotencyKey);

        PurchaseRequestResponse resolved = awaitResolved(submitted.getRequestId());

        assertEquals("FAILED", resolved.getStatus());
        assertEquals("OUT_OF_STOCK", resolved.getFailureCode());
        assertNotNull(resolved.getFailureMessage());
        assertNull(resolved.getOrderCode());
        assertEquals(0, genCustomerOrderCountForProduct(product.getId()),
                "REQUIRES_NEW phai rollback SACH — khong duoc co order_item mo coi nao cho san pham het hang");
    }

    @Test
    @DisplayName("Redeliver CUNG eventId qua Kafka that: KHONG xu ly lai (chi 1 don duoc tao)")
    void redeliveringSameEventDoesNotReprocess() throws Exception {
        Product product = genSeedProduct(10);
        String idempotencyKey = "it-redeliver-" + System.nanoTime();

        PurchaseRequestResponse submitted = purchaseRequestAppService.submitAsync(
                genCommand(product.getId()), idempotencyKey);
        // Lay dung payload + partition key + eventId da publish, gui lai THU CONG qua Kafka that —
        // mo phong Kafka tu redeliver (rebalance/retry), khong phai goi Java hai lan.
        OutboxRow row = genOutboxRowByAggregateId(submitted.getRequestId());

        awaitResolved(submitted.getRequestId());
        assertEquals(1, genCustomerOrderCountForProduct(product.getId()));

        var record = new org.apache.kafka.clients.producer.ProducerRecord<>(
                KafkaTopicConfig.PURCHASE_REQUESTED_TOPIC, null, row.partitionKey, row.payload);
        record.headers().add(KafkaTopicConfig.HEADER_EVENT_ID,
                String.valueOf(row.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

        // Cho mot khoang the ma NEU redeliver tao them don thi da kip xay ra, roi khang dinh van
        // dung 1 dong order_item — khong tang len 2.
        Thread.sleep(3000);
        assertEquals(1, genCustomerOrderCountForProduct(product.getId()),
                "redeliver da tao THEM mot don — idempotency (INSERT IGNORE idempotency_key) hong");
    }

    @Test
    @DisplayName("Gui trung Idempotency-Key 2 lan: SELECT COUNT(*) purchase_request WHERE idempotency_key=... = 1")
    void duplicateIdempotencyKeyCreatesOnlyOneRow() throws Exception {
        Product product = genSeedProduct(10);
        String idempotencyKey = "it-dup-key-" + System.nanoTime();

        PurchaseRequestResponse first = purchaseRequestAppService.submitAsync(genCommand(product.getId()), idempotencyKey);
        PurchaseRequestResponse second = purchaseRequestAppService.submitAsync(genCommand(product.getId()), idempotencyKey);

        assertEquals(first.getRequestId(), second.getRequestId(),
                "Cung Idempotency-Key phai tra ve DUNG requestId cua lan dau");
        assertEquals(1, genPurchaseRequestCountForIdempotencyKey(idempotencyKey));
    }

    /**
     * @param aggregateId {@code OutboxEvent.aggregateId} — {@code requestId} của {@code PurchaseRequested}
     * @return dòng outbox tương ứng, đọc trực tiếp bằng JDBC — không phụ thuộc {@code JpaRepository}
     *         nào để tránh vòng phụ thuộc test → application → infrastructure không cần thiết
     */
    private OutboxRow genOutboxRowByAggregateId(String aggregateId) throws Exception {
        String sql = "SELECT id, partition_key, payload FROM outbox_event WHERE aggregate_id = ? AND event_type = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, aggregateId);
            statement.setString(2, KafkaTopicConfig.EVENT_TYPE_PURCHASE_REQUESTED);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "khong tim thay outbox_event cho aggregateId=" + aggregateId);
                return new OutboxRow(resultSet.getLong("id"), resultSet.getString("partition_key"),
                        resultSet.getString("payload"));
            }
        }
    }

    private record OutboxRow(long id, String partitionKey, String payload) {
    }
}
