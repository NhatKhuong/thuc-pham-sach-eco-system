package com.nss;

import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CouponJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderItemJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderStatusHistoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.PasswordResetTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test này gánh hai việc cùng lúc:
 * <p>
 * 1. Spring context nạp được với đủ bean của cả 5 module.
 * 2. Luồng `hello` chạy xuyên module — `source` bằng `infrastructure` chứng minh
 *    chuỗi thật sự đi ra từ adapter, không phải hardcode ở controller.
 * <p>
 * <b>Cố ý loại bỏ autoconfiguration của DataSource và JPA.</b> Từ khi `nss-domain` khai
 * `spring-boot-starter-data-jpa`, một `@SpringBootTest` trần sẽ dựng `EntityManagerFactory`,
 * và việc đó mở kết nối MySQL thật ngay lúc build. Câu hỏi mà test này trả lời — "5 module
 * có ráp lại thành một context chạy được không" — không liên quan gì tới database, nên nó
 * không được phép hỏng chỉ vì máy chạy build chưa cài MySQL.
 * <p>
 * Phần kiểm schema thật nằm ở {@link SchemaSmokeTest}, đánh {@code @Tag("db")} và chạy riêng.
 * <p>
 * <b>Filter chain của Spring Security KHÔNG bị tắt ở đây</b> (backlog 0010). {@code @AutoConfigureMockMvc}
 * gắn {@code springSecurityFilterChain} thật vào MockMvc, nên request bên dưới đi qua đúng chuỗi
 * filter mà production dùng. Đó là chủ ý: {@code /api/hello} là endpoint công khai theo contract,
 * và nếu ai đó xoá dòng {@code permitAll} của nó thì test này phải đỏ. Tắt security trong test sẽ
 * biến chính thứ cần kiểm thành thứ không được kiểm.
 * <p>
 * Phần kiểm luật {@code permitAll} đầy đủ nằm ở {@link SecurityRulesTest}.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class HelloEndpointTest {

    /**
     * <b>Mọi</b> interface Spring Data phải có bản giả ở đây, và lý do phải viết ra vì nó không
     * hiển nhiên: context này <b>cố ý loại</b> {@code JpaRepositoriesAutoConfiguration}, mà đó
     * chính là thứ biến {@code *JPAMapper} thành bean. Không có bản giả thì {@code *RepositoryImpl}
     * tương ứng không dựng được và cả context sập — nghĩa là câu hỏi "5 module có ráp lại được
     * không" sẽ trả lời "không" chỉ vì máy chạy build không có MySQL, đúng thứ mà việc tách lane
     * test sinh ra để tránh.
     * <p>
     * <b>Thêm một {@code *JPAMapper} mới ở bất kỳ ticket nào thì phải thêm một dòng vào đây, trong
     * cùng lần sửa.</b> {@code CouponJPAMapper} đến từ backlog 0014 phase 1; ba mapper tiếp theo
     * ({@code order}, {@code order_item}, {@code order_status_history}) đến từ phase 3 của cùng
     * ticket đó. Phase 2 <i>không</i> phải sửa file này vì nó dùng lại đường đọc sản phẩm sẵn có —
     * đúng dấu hiệu cho thấy quy tắc này chỉ động vào khi thật sự có bảng mới.
     * <p>
     * <b>{@code PasswordResetTokenJPAMapper} đến từ backlog 0017, và nó là ca đối chứng cho câu
     * trên.</b> Backlog 0016 <i>không</i> phải sửa file này dù cũng động vào tầng persistence: nó
     * chỉ thêm method vào một interface đã có ({@code revokeAllOfUserExcept} trên
     * {@code RefreshTokenJPAMapper}). Ở 0017 thì có một bảng mới thật, nên có một bean mới thật — và
     * thiếu dòng tương ứng thì {@code PasswordResetTokenRepositoryImpl} không dựng được và <b>cả
     * context sập</b>, chứ không phải một ca đỏ đọc ra được nguyên nhân.
     * <p>
     * Bản giả không được gọi trong test này; luồng {@code hello} không chạm tới chúng.
     */
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

    private final MockMvc mockMvc;

    @Autowired
    HelloEndpointTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("GET /api/hello tra 200 va payload sinh ra tu infrastructure")
    void getHelloReturnsGreetingFromInfrastructure() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello DDD"))
                .andExpect(jsonPath("$.source").value("infrastructure"));
    }
}
