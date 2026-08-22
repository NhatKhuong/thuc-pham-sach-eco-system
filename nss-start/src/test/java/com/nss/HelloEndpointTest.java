package com.nss;

import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;

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
     * Bốn interface Spring Data phải có bản giả ở đây, và lý do phải viết ra vì nó không hiển nhiên:
     * context này <b>cố ý loại</b> {@code JpaRepositoriesAutoConfiguration}, mà đó chính là thứ
     * biến {@code *JPAMapper} thành bean. Không có bản giả thì {@code ProductRepositoryImpl} không
     * dựng được và cả context sập — nghĩa là câu hỏi "5 module có ráp lại được không" sẽ trả lời
     * "không" chỉ vì máy chạy build không có MySQL, đúng thứ mà việc tách lane test sinh ra để tránh.
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
