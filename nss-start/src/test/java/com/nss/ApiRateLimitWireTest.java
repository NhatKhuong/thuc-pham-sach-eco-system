package com.nss;

import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CouponJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.IdempotencyKeyJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderItemJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderStatusHistoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OutboxEventJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.PasswordResetTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ReviewJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bề mặt dây của trần thông lượng: từ chối phải ra <b>đúng hình dạng lỗi của hệ thống</b>
 * (backlog 0021 Phase 1, §Contract).
 * <p>
 * <b>Đây là lý do cổng phải là {@code HandlerInterceptor} chứ không phải {@code Filter}.</b> Một
 * {@code Filter} từ chối request thì việc từ chối xảy ra trước {@code DispatcherServlet}, không đi
 * qua {@code @RestControllerAdvice}, và phải tự serialize JSON — ra một thân lỗi <i>khác hình
 * dạng</i>: body vẫn parse được nên frontend không vỡ, nó chỉ hiển thị sai. Test này chạy qua
 * {@code MockMvc} thật nên nó đo đúng đường đi đó.
 * <p>
 * <b>Khoá {@code errors} vắng mặt là một phần của contract, không phải chi tiết.</b> Sự vắng mặt
 * của nó là thứ phân biệt lỗi này với 422 của validate — quy ước đã chốt ở
 * {@code GlobalExceptionHandler}. Vì vậy ca 422 nằm ngay trong cùng file này làm
 * <b>đối chứng dương</b>: nếu {@code errors} biến mất khỏi cả hai thì phép đo đã hỏng chứ không
 * phải hệ thống đã đúng.
 * <p>
 * Ngưỡng ở đây cố ý đặt rất thấp qua {@code properties} để một cơn bùng nổ 4 request là đủ chạm
 * trần; tier {@code auth} đặt cao để nó là đối chứng "không phải mọi thứ đều 429".
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "nss.rate-limit.read.limit-for-period=3",
        "nss.rate-limit.read.limit-refresh-period=PT30S",
        "nss.rate-limit.auth.limit-for-period=500",
        "nss.rate-limit.write.limit-for-period=500"
})
@AutoConfigureMockMvc
class ApiRateLimitWireTest {

    /** Ngưỡng tier read của context này; xem {@code properties} ở trên. */
    private static final int READ_LIMIT = 3;

    /** Xem javadoc {@code HelloEndpointTest}: context này cố ý loại {@code JpaRepositoriesAutoConfiguration}. */
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

    /** Backlog 0027 — bang `review` co adapter tu ADR 0008 tro di. */
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

    /** Backlog 0032 — Outbox + Kafka: outbox_event/idempotency_key co adapter tu ticket do tro di. */
    @MockBean
    private OutboxEventJPAMapper outboxEventJPAMapper;

    @MockBean
    private IdempotencyKeyJPAMapper idempotencyKeyJPAMapper;

    private final MockMvc mockMvc;

    @Autowired
    ApiRateLimitWireTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * <b>Ba phép đo nằm trong CÙNG một method, và đó là chủ ý.</b> Bể permit gắn với bean
     * interceptor, tức nó dùng chung giữa các method của cùng một context Spring — tách ra thì kết
     * quả phụ thuộc thứ tự chạy, và một ca đỏ sẽ đọc như "lớp limit hỏng" trong khi thứ hỏng là
     * chính phép đo.
     */
    @Test
    @DisplayName("vuot tran tra 429 problem+json khong co khoa errors; tier khac va khoa errors cua 422 con nguyen")
    void burstBeyondTheReadLimitReturns429WithoutErrorsKey() throws Exception {
        // 1. Dung N request dau tien: phai qua het.
        for (int i = 0; i < READ_LIMIT; i++) {
            mockMvc.perform(get("/api/hello")).andExpect(status().isOk());
        }

        // 2. Request thu N+1: 429, dung hinh dang loi cua he thong, KHONG co khoa `errors`.
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail")
                        .value("Hệ thống đang nhận quá nhiều yêu cầu, vui lòng thử lại sau giây lát."))
                .andExpect(jsonPath("$.errors").doesNotExist());

        // 3. DOI CHUNG DUONG, cung lan chay: tier auth (nguong 500) van phuc vu, va 422 cua validate
        //    VAN co khoa `errors`. Neu buoc nay cung ra 429 thi ba tier dang dung chung mot be
        //    permit; neu no mat luon khoa `errors` thi phep do o buoc 2 khong chung minh duoc gi.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors").exists());
    }
}
