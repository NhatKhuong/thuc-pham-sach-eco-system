package com.nss;

import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CouponJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.EmailConfirmationTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.IdempotencyKeyJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderItemJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderStatusHistoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OutboxEventJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.PasswordResetTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.PurchaseRequestJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ReviewJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke assertion lúc khởi động: xác nhận scheduling <b>thực sự</b> được bật — không chờ
 * {@code fixedDelay} nào chạy, không cần timer thật.
 * <p>
 * <b>Ca thật nó canh — backlog 0032.</b> {@code StartApplication} thiếu {@code @EnableScheduling}
 * nên {@code OutboxPublisherJob#publishPendingEvents} ({@code @Scheduled(fixedDelay = 1000)}) không
 * bao giờ chạy. Không có lỗi nào nổ ra lúc khởi động, build xanh, cả unit test lẫn integration test
 * đều xanh — vì cả hai đều gọi thẳng {@code publishPendingEvents()} thay vì chờ Spring tự gọi qua
 * timer. Triệu chứng duy nhất: {@code outbox_event} chất đống ở {@code status=PENDING} mãi mãi, chỉ
 * lộ ra khi kiểm hành vi thật qua request thật (đúng cách ticket 0032 bắt được nó). Ticket 0033 mở
 * ra để lỗi cùng họ không tái diễn im lặng lần thứ hai — xem thêm checklist ở
 * {@code coding-conventions.md} §17.
 * <p>
 * <b>Tín hiệu được kiểm: bean {@code ScheduledAnnotationBeanPostProcessor}</b>, đăng ký bởi
 * {@code @EnableScheduling} dưới tên cố định
 * {@link TaskManagementConfigUtils#SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME}. Bean này CHỈ tồn tại
 * khi {@code @EnableScheduling} có mặt trên context — hoàn toàn không liên quan tới việc có
 * {@code @Scheduled} method nào đang bị GỌI hay không, nên nó bắt đúng loại lỗi của 0032: annotation
 * đăng ký bị thiếu, không phải job chạy sai logic. Bean có mặt (hoặc không) ngay khi context load
 * xong — không cần {@code Thread.sleep} hay chờ {@code fixedDelay}.
 * <p>
 * Dùng chung shape context với {@link HelloEndpointTest} (loại DataSource/JPA autoconfiguration,
 * mock mọi {@code *JPAMapper}) vì câu hỏi ở đây cũng không liên quan gì tới database — chỉ cần
 * context của cả 5 module ráp lại được.
 * <p>
 * <b>Positive control đã chạy tay khi viết test này (ticket 0033):</b> gỡ tạm
 * {@code @EnableScheduling} khỏi {@code StartApplication} → test này đỏ đúng như kỳ vọng
 * ({@code containsBean} trả {@code false}) → khôi phục annotation → test xanh lại. Xem Outcome của
 * backlog 0033 để biết bằng chứng cụ thể.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class SchedulingEnabledSmokeTest {

    /** Xem javadoc của {@link HelloEndpointTest#productJPAMapper} — cùng lý do, cùng danh sách. */
    @MockBean
    private ProductJPAMapper productJPAMapper;

    @MockBean
    private ProductImageJPAMapper productImageJPAMapper;

    @MockBean
    private CategoryJPAMapper categoryJPAMapper;

    @MockBean
    private BrandJPAMapper brandJPAMapper;

    @MockBean
    private UserJPAMapper userJPAMapper;

    @MockBean
    private RefreshTokenJPAMapper refreshTokenJPAMapper;

    @MockBean
    private ReviewJPAMapper reviewJPAMapper;

    @MockBean
    private UserRoleJPAMapper userRoleJPAMapper;

    @MockBean
    private CouponJPAMapper couponJPAMapper;

    @MockBean
    private OrderJPAMapper orderJPAMapper;

    @MockBean
    private OrderItemJPAMapper orderItemJPAMapper;

    @MockBean
    private OrderStatusHistoryJPAMapper orderStatusHistoryJPAMapper;

    @MockBean
    private PasswordResetTokenJPAMapper passwordResetTokenJPAMapper;

    /** Backlog 0037 — xac nhan email: cung ly do voi passwordResetTokenJPAMapper o tren. */
    @MockBean
    private EmailConfirmationTokenJPAMapper emailConfirmationTokenJPAMapper;

    @MockBean
    private OutboxEventJPAMapper outboxEventJPAMapper;

    @MockBean
    private IdempotencyKeyJPAMapper idempotencyKeyJPAMapper;

    /** Backlog 0039 — purchase_request co adapter tu ticket do tro di (luong async). */
    @MockBean
    private PurchaseRequestJPAMapper purchaseRequestJPAMapper;

    private final ApplicationContext applicationContext;

    @Autowired
    SchedulingEnabledSmokeTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Test
    @DisplayName("Context co bean ScheduledAnnotationBeanPostProcessor -> @EnableScheduling dang bat")
    void schedulingAnnotationProcessorBeanIsRegistered() {
        assertTrue(applicationContext.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME),
                "Thieu bean ScheduledAnnotationBeanPostProcessor: @EnableScheduling khong co tren"
                        + " StartApplication, nen moi @Scheduled job (vi du OutboxPublisherJob) se"
                        + " KHONG BAO GIO chay ma khong co loi nao no ra luc khoi dong (backlog 0032).");
    }
}
