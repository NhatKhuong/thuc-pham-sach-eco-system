package com.nss;

import com.nss.ddd.application.cronjob.OutboxPublisherJob;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.model.entity.ShippingInfo;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.infrastructure.config.KafkaTopicConfig;
import com.nss.ddd.infrastructure.mq.OrderStatusChangedMessage;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * Integration test toàn luồng <b>outbox → publish → consume → MailAppService</b> (backlog 0032
 * Phase 4, architecture/01-overview.md §6).
 * <p>
 * <b>@Tag("db")</b> — cần MySQL thật ({@code environment/docker-compose-dev.yml}) cho
 * {@code Order}/{@code OutboxEvent}/{@code IdempotencyKey}, giống mọi test khác đánh tag này; bị
 * loại khỏi build mặc định, chạy riêng bằng {@code mvn -pl nss-start test -Dexcluded.test.groups= -Dgroups=db}.
 * <p>
 * <b>{@code @EmbeddedKafka} thay vì container Kafka thật</b> — đây là "pattern test Kafka" chọn cho
 * repo này (chưa có tiền lệ nào trước ticket 0032): tự dựng broker trong JVM test, không cần
 * {@code environment/docker-compose-dev.yml} phải đang chạy sẵn dịch vụ {@code kafka}, và không tốn
 * thời gian khởi động container. {@code spring.kafka.bootstrap-servers} được trỏ lại vào broker nhúng
 * qua {@code @TestPropertySource}.
 * <p>
 * <b>{@code MailAppService} là {@code @MockBean}</b> — Phase 4 của ticket chỉ đòi hỏi bằng chứng tới
 * đúng ranh giới {@code MailAppService} ("toàn luồng outbox → publish → consume → MailAppService"),
 * không đòi một SMTP thật; email HTML thật đã được xác nhận thủ công qua Mailpit ở phần "Xác minh"
 * của ticket. Vì {@code @MockBean} thay hẳn bean bằng Mockito mock, lời gọi
 * {@code sendOrderStatusEmail} chạy ĐỒNG BỘ ngay trên luồng của {@code @KafkaListener} — không cần
 * chờ thêm một vòng {@code @Async} nữa để bắt được nó.
 */
@SpringBootTest
@Tag("db")
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC)
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class OrderStatusChangedOutboxIntegrationTest {

    private static final long AWAIT_SECONDS = 20;

    @Autowired
    private OrderDomainService orderDomainService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherJob outboxPublisherJob;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private MailAppService mailAppService;

    /**
     * Dựng một đơn tối thiểu, đủ để {@code OrderStatusChangedConsumer} tra lại được qua
     * {@code findByCode} — không đi qua {@code OrderAppService.createOrder} (đã kiểm ở
     * {@code OrderAppServiceOutboxEventTest}, không cần lặp lại cổng nghiệp vụ ở đây).
     */
    private Order genPersistedOrder(String code, String email) {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        Order draft = new Order()
                .setCode(code)
                .setShipping(new ShippingInfo()
                        .setFullName("Nguyễn Văn Tích Hợp").setPhone("0900000000").setEmail(email)
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod(0)
                .setStatus(OrderDomainService.STATUS_PENDING)
                .setSubtotal(0L).setDiscount(0L).setShippingFee(0L).setTotal(0L)
                .setCreatedAt(nowUtc).setUpdatedAt(nowUtc);
        return orderDomainService.create(draft);
    }

    private OutboxEvent genPendingOutboxEvent(Order order, Integer fromStatus, Integer toStatus) throws Exception {
        OrderStatusChangedMessage message = new OrderStatusChangedMessage()
                .setOrderId(order.getId())
                .setCode(order.getCode())
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setShippingEmail(order.getShipping().getEmail())
                .setChangedAt(DateTimeFormatter.ISO_INSTANT.format(order.getCreatedAt().toInstant(ZoneOffset.UTC)));
        return outboxEventRepository.save(new OutboxEvent()
                .setAggregateId(order.getCode())
                .setEventType("OrderStatusChanged")
                .setPayload(objectMapper.writeValueAsString(message))
                .setStatus(OutboxEvent.STATUS_PENDING)
                .setCreatedAt(order.getCreatedAt()));
    }

    private int genIdempotencyKeyRowCount(Long eventId) throws Exception {
        String sql = "SELECT COUNT(*) FROM idempotency_key WHERE event_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("Toan luong: outbox PENDING -> OutboxPublisherJob publish -> Kafka -> consumer -> MailAppService")
    void fullPipelineDeliversExactlyOneEmail() throws Exception {
        String code = "NSS-IT-" + System.nanoTime();
        Order order = genPersistedOrder(code, "khach-it@vidu.vn");
        OutboxEvent event = genPendingOutboxEvent(order, null, OrderDomainService.STATUS_PENDING);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mailAppService).sendOrderStatusEmail(any(), any(), anyList(), any());

        // KHONG cho @Scheduled tu chay — goi truc tiep de test tat dinh, khong phu thuoc fixedDelay.
        outboxPublisherJob.publishPendingEvents();

        assertTrue(latch.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "MailAppService.sendOrderStatusEmail khong duoc goi trong " + AWAIT_SECONDS + "s");
        verify(mailAppService, after(500).times(1))
                .sendOrderStatusEmail(eq("khach-it@vidu.vn"), any(), anyList(), eq(OrderDomainService.STATUS_PENDING));

        // Bang chung Idempotency phia DB: dung 1 dong idempotency_key cho event nay.
        assertEquals(1, genIdempotencyKeyRowCount(event.getId()));
    }

    @Test
    @DisplayName("Idempotency: redeliver CUNG key qua Kafka that KHONG tao them email thu hai")
    void redeliveringTheSameKeyDoesNotSendASecondEmail() throws Exception {
        String code = "NSS-IT-DUP-" + System.nanoTime();
        Order order = genPersistedOrder(code, "khach-dup@vidu.vn");
        OutboxEvent event = genPendingOutboxEvent(order, null, OrderDomainService.STATUS_PENDING);

        AtomicInteger callCount = new AtomicInteger();
        CountDownLatch firstCall = new CountDownLatch(1);
        doAnswer(inv -> {
            callCount.incrementAndGet();
            firstCall.countDown();
            return null;
        }).when(mailAppService).sendOrderStatusEmail(any(), any(), anyList(), any());

        // Lan 1: publish that qua OutboxPublisherJob (danh dau PUBLISHED).
        outboxPublisherJob.publishPendingEvents();
        assertTrue(firstCall.await(AWAIT_SECONDS, TimeUnit.SECONDS), "lan gui dau tien khong toi consumer");

        // Lan 2: REDELIVER thu cong — gui lai CHINH XAC cung key + payload thang qua Kafka that,
        // mo phong Kafka tu redeliver (rebalance/retry). Cong idempotency phai chan lai o day.
        kafkaTemplate.send(KafkaTopicConfig.ORDER_STATUS_CHANGED_TOPIC, String.valueOf(event.getId()),
                event.getPayload()).get(10, TimeUnit.SECONDS);

        // Cho them mot khoang the ma NEU co goi mail lan hai thi no da kip xay ra, roi khang dinh
        // tong so lan goi VAN LA 1.
        verify(mailAppService, after(3000).times(1))
                .sendOrderStatusEmail(eq("khach-dup@vidu.vn"), any(), anyList(), any());
        assertEquals(1, callCount.get(), "message redeliver da tao THEM mot email — idempotency hong");
        assertEquals(1, genIdempotencyKeyRowCount(event.getId()),
                "van chi duoc DUNG 1 dong idempotency_key cho event nay du redeliver");
    }

    @Test
    @DisplayName("changeOrderStatus toi CANCELLED cung di qua duoc toan luong (moi trang thai, ke ca huy don)")
    void cancelledTransitionAlsoFlowsThroughThePipeline() throws Exception {
        String code = "NSS-IT-CANCEL-" + System.nanoTime();
        Order order = genPersistedOrder(code, "khach-cancel@vidu.vn");
        OutboxEvent event = genPendingOutboxEvent(order, OrderDomainService.STATUS_PENDING,
                OrderDomainService.STATUS_CANCELLED);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(mailAppService).sendOrderStatusEmail(any(), any(), anyList(), eq(OrderDomainService.STATUS_CANCELLED));

        outboxPublisherJob.publishPendingEvents();

        assertTrue(latch.await(AWAIT_SECONDS, TimeUnit.SECONDS),
                "email CANCELLED khong toi MailAppService trong " + AWAIT_SECONDS + "s");
        verify(mailAppService, after(500).times(1))
                .sendOrderStatusEmail(eq("khach-cancel@vidu.vn"), any(), anyList(),
                        eq(OrderDomainService.STATUS_CANCELLED));
    }
}
