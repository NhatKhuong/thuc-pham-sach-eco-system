package com.nss;

import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lưới chặn regression cho ma trận quyền của {@code SecurityConfig} (backlog 0010, siết ở 0012).
 * <p>
 * <b>Đây là test regression, không phải test tính năng.</b> Nó khoá cứng hai chiều, và cả hai chiều
 * đều hỏng <i>im lặng</i>:
 * <ul>
 *   <li><b>Khoá nhầm thứ phải mở.</b> Bật Spring Security là mặc định khoá sạch, nên xoá một dòng
 *       {@code permitAll} làm {@code GET /api/products} trả 401 trong khi build vẫn xanh và doc vẫn
 *       liệt kê endpoint đó.</li>
 *   <li><b>Mở nhầm thứ phải khoá.</b> Đúng lỗi của backlog 0012: {@code requestMatchers(String...)}
 *       không phân biệt HTTP method, nên một dòng {@code permitAll} cho {@code /api/products/**}
 *       mở công khai cả {@code POST} / {@code PUT} / {@code DELETE}. Owner phải tự tìm ra bằng
 *       Postman; nó không được phép lọt lần thứ hai.</li>
 * </ul>
 * <b>Ô quan trọng nhất là CUSTOMER → 403, tuyệt đối không phải 401.</b> {@code client.ts} coi 401 là
 * tín hiệu access token hết hạn: nó gọi {@code /auth/refresh}, đốt mất refresh token, rồi
 * {@code clearSession()} và đá người dùng về {@code /dang-nhap}. Một khách bấm nhầm nút quản trị sẽ
 * bị <i>đăng xuất</i> thay vì thấy thông báo.
 * <p>
 * <b>Token ở đây là JWT thật, ký bằng chính {@code JwtEncoder} của ứng dụng</b> — không dùng
 * {@code @WithMockUser} và không dùng post-processor {@code jwt()}. Lý do: cả hai cách kia <i>nhét
 * thẳng authority vào security context</i> và do đó <b>nhảy qua {@code JwtAuthenticationConverter}</b>
 * — đúng mắt xích mà backlog 0012 thêm vào, và đúng chỗ dễ sai nhất (tiền tố {@code ROLE_}). Chỉ khi
 * đi qua header {@code Authorization} thật thì test mới trả lời được câu hỏi thật: claim
 * {@code roles: ["ADMIN"]} có biến thành quyền gọi được đường ghi hay không.
 * <p>
 * Cùng cách loại autoconfig JPA như {@link HelloEndpointTest} — câu hỏi ở đây là luật của filter
 * chain, không liên quan tới database, nên nó không được phép cần một MySQL sống.
 * <p>
 * <b>Hệ quả của việc loại JPA, cần biết trước khi thêm ca mới:</b> context này không có
 * {@code TransactionManager}, nên những endpoint mà thân xử lý chạm thật vào write path nghiệp vụ
 * (đăng ký, đăng nhập, gia hạn, đăng xuất) được kiểm ở đây bằng <i>body không hợp lệ</i> — chúng
 * dừng ở tầng validate trước khi tới service. Hành vi nghiệp vụ đầy đủ của những đường đó thuộc ma
 * trận chạy trên server thật ở mục Verification của ticket, không thuộc file này.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class SecurityRulesTest {

    /** Thông điệp 401 của {@code SecurityProblemDetailHandler} — frontend đổ thẳng ra màn hình. */
    private static final String MESSAGE_UNAUTHENTICATED =
            "Bạn cần đăng nhập để thực hiện thao tác này.";

    /** Thông điệp 403 của {@code SecurityProblemDetailHandler} — frontend đổ thẳng ra màn hình. */
    private static final String MESSAGE_FORBIDDEN =
            "Tài khoản của bạn không có quyền thực hiện thao tác này.";

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

    private final MockMvc mockMvc;

    private final JwtEncoder jwtEncoder;

    @Autowired
    SecurityRulesTest(MockMvc mockMvc, JwtEncoder jwtEncoder) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * Ba đường ghi sản phẩm, kèm mã trạng thái mà <b>ADMIN</b> phải nhận được.
     * <p>
     * Mã của cột ADMIN cố ý <i>không</i> phải mã thành công: đó là mã của tầng <i>sau</i> tầng bảo
     * mật ({@code 422} do validate body rỗng, {@code 404} do id không khớp dòng nào). Nó chứng minh
     * đúng điều cần chứng minh — luật đã cho ADMIN đi qua — mà không cần một database sống.
     *
     * @return bộ bốn (method, path, body, mã trạng thái của ADMIN)
     */
    private static Stream<Arguments> writeEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/products", "{}", 422),
                Arguments.of(HttpMethod.PUT, "/api/products/1", "{}", 422),
                Arguments.of(HttpMethod.DELETE, "/api/products/1", "{}", 404));
    }

    /**
     * Đúc access token thật, ký bằng khoá của chính ứng dụng.
     *
     * @param roles mã vai trò trần đi vào claim {@code roles} — đúng như {@code AuthAppServiceImpl}
     *              vẫn đúc: chuỗi trần {@code ADMIN} / {@code CUSTOMER}, không tiền tố
     * @return chuỗi JWT dùng được cho header {@code Authorization: Bearer}
     */
    private String genToken(String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nss-api")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .subject("1")
                .claim("email", "kiem-thu@nongsansach.vn")
                .claim("roles", List.of(roles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * @param roles mã vai trò của chủ token
     * @return giá trị header {@code Authorization}
     */
    private String genBearer(String... roles) {
        return "Bearer " + genToken(roles);
    }

    // ========== TANG 1: DOC CONG KHAI ==========

    @Test
    @DisplayName("GET /api/products van cong khai sau khi siet duong ghi")
    void productListStaysPublic() throws Exception {
        Page<Product> emptyPage = new PageImpl<>(List.of());
        when(productJPAMapper.findActivePage(any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("GET /api/products/{slug} van cong khai — slug khong co that thi 404, KHONG phai 401")
    void productDetailStaysPublic() throws Exception {
        when(productJPAMapper.findActiveBySlug(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/khong-ton-tai"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Duong doc cong khai khong te hon khi CO token CUSTOMER")
    void publicReadStaysOpenForCustomerToken() throws Exception {
        Page<Product> emptyPage = new PageImpl<>(List.of());
        when(productJPAMapper.findActivePage(any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isOk());
    }

    // ========== TANG 2: GHI CHI ADMIN ==========

    /**
     * Nguyên một cột của ma trận quyền cho mỗi đường ghi: không token → 401, CUSTOMER → 403,
     * ADMIN → đi qua được tầng bảo mật.
     * <p>
     * Ba khẳng định nằm chung một ca là có chủ ý: chúng chỉ có nghĩa khi <b>cùng đúng</b>. Một luật
     * trả 401 cho cả CUSTOMER trông "an toàn" nhưng làm người dùng bị đăng xuất; một luật trả 403
     * cho cả khách vãng lai thì che mất tín hiệu "hãy đăng nhập". Tách rời ra thì hai lỗi đó đọc
     * như một ca đỏ lẻ, chứ không đọc như một luật sai.
     *
     * @param method HTTP method của đường ghi
     * @param path đường dẫn
     * @param body thân request
     * @param adminStatus mã trạng thái ADMIN phải nhận — mã của tầng sau bảo mật
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1}: khong token->401, CUSTOMER->403, ADMIN->{3}")
    @MethodSource("writeEndpoints")
    @DisplayName("Duong ghi san pham: khong token 401, CUSTOMER 403, ADMIN di qua")
    void writeEndpointEnforcesAdminOnly(HttpMethod method, String path, String body, int adminStatus)
            throws Exception {
        when(productJPAMapper.markInactive(any(), any())).thenReturn(0);

        // 1. Khach vang lai: 401 — tin hieu "hay dang nhap"
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED))
                .andExpect(jsonPath("$.instance").value(path));

        // 2. CUSTOMER: 403 — KHONG duoc la 401, neu khong client.ts se dang xuat nguoi dung
        mockMvc.perform(request(method, path)
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(MESSAGE_FORBIDDEN))
                .andExpect(jsonPath("$.instance").value(path));

        // 3. ADMIN: luat cho di qua — request dung o tang nghiep vu, khong dung o tang bao mat
        mockMvc.perform(request(method, path)
                        .header(HttpHeaders.AUTHORIZATION, genBearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(adminStatus));
    }

    @Test
    @DisplayName("POST /api/products voi token ADMIN + body rong: 422 du 6 truong, khong phai 403")
    void adminReachesValidationWithAllSixFieldErrors() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.stock").exists())
                .andExpect(jsonPath("$.errors.unit").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }

    // ========== DUONG XAC THUC ==========

    @Test
    @DisplayName("POST /api/auth/logout khong kem token tra 401 dang ProblemDetail, KHONG phai than rong")
    void logoutWithoutTokenReturnsProblemDetail() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"khong-quan-trong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                // Chuoi tieng Viet, khong rong: frontend do thang `detail` ra man hinh (§A.3)
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED))
                .andExpect(jsonPath("$.instance").value("/api/auth/logout"));
    }

    /**
     * Ba endpoint xác thực công khai vẫn gọi được khi chưa có token.
     * <p>
     * Body rỗng là <b>một phần của phép kiểm</b>, không phải cẩu thả: 422 chỉ phát ra từ tầng
     * validate, tức là request đã đi <i>xuyên qua</i> filter chain. Một dòng {@code permitAll} bị
     * xoá sẽ đổi mã này thành 401 và ca đỏ ngay.
     *
     * @param path endpoint xác thực công khai
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "POST {0} khong can token")
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/refresh"})
    @DisplayName("Ba endpoint xac thuc cong khai van qua duoc filter chain khi khong co token")
    void authPublicEndpointsReachableWithoutToken(String path) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ========== TAI LIEU NOI DUNG SU THAT ==========

    @Test
    @DisplayName("Duong dan tai lieu OpenAPI van mo duoc khong can token")
    void apiDocsStayPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    @DisplayName("api-docs khai security cho ba lenh ghi va khong khai cho duong doc")
    void apiDocsDeclareSecurityOnProductWriteOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.responses['403']").exists())
                // Duong doc cong khai thi KHONG duoc mang security — Swagger noi dung §B.1
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist());
    }
}
