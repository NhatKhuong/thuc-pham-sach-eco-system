package com.nss;

import com.nss.ddd.domain.model.entity.Product;
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

    // ========== MA GIAM GIA: HAI ENDPOINT CONG KHAI (§B.7) ==========

    /**
     * {@code POST /api/coupons/validate} gọi được khi <b>không</b> có token.
     * <p>
     * Body rỗng là một phần của phép kiểm, cùng lý do với ba endpoint xác thực bên dưới: 422 chỉ
     * phát ra từ tầng validate, tức request đã đi <i>xuyên qua</i> filter chain. Xoá dòng
     * {@code permitAll} tương ứng sẽ đổi mã này thành 401 và ca đỏ ngay.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("POST /api/coupons/validate cong khai — khong token van toi duoc tang validate")
    void couponValidateStaysPublic() throws Exception {
        mockMvc.perform(post("/api/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.subtotal").exists());
    }

    @Test
    @DisplayName("GET /api/coupons/active cong khai — khong token van tra 200")
    void couponActiveStaysPublic() throws Exception {
        mockMvc.perform(get("/api/coupons/active"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Duong ma giam gia khong te hon khi CO token CUSTOMER")
    void couponEndpointsStayOpenForCustomerToken() throws Exception {
        mockMvc.perform(get("/api/coupons/active")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isOk());
    }

    /**
     * Hai dòng {@code permitAll} của mã giảm giá khai <b>đúng một method mỗi cái</b>, không mở cả
     * đường dẫn.
     * <p>
     * <b>Đây là ca có giá trị nhất trong nhóm này</b>, và nó tồn tại vì đúng cái bẫy đã cắn ở
     * backlog 0012: {@code requestMatchers(String...)} không phân biệt HTTP method, nên một dòng
     * viết thiếu {@code HttpMethod} sẽ mở công khai <i>mọi</i> verb trên cùng đường dẫn — kể cả
     * {@code DELETE}. Ba ca dưới đây phải trả 401 (chưa xác thực), không phải 404 hay 405: 404/405
     * nghĩa là request đã lọt qua tầng bảo mật rồi mới dừng ở tầng định tuyến.
     *
     * @param method verb không được mở trên đường dẫn mã giảm giá
     * @param path đường dẫn mã giảm giá
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1} khong duoc cong khai -> 401")
    @MethodSource("couponMethodsThatMustStayClosed")
    @DisplayName("permitAll cua ma giam gia chi mo DUNG method cua no, khong mo ca duong dan")
    void couponPathsOpenOnlyTheirOwnMethod(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * @return các cặp (method, path) trên đường dẫn mã giảm giá mà luật <b>không</b> được mở
     */
    private static Stream<Arguments> couponMethodsThatMustStayClosed() {
        return Stream.of(
                // GET tren duong validate: chi POST duoc mo
                Arguments.of(HttpMethod.GET, "/api/coupons/validate"),
                Arguments.of(HttpMethod.DELETE, "/api/coupons/validate"),
                // POST tren duong active: chi GET duoc mo
                Arguments.of(HttpMethod.POST, "/api/coupons/active"),
                // Duong ghi ma giam gia cua khu quan tri chua ton tai — va phai dong san
                Arguments.of(HttpMethod.POST, "/api/coupons"));
    }

    // ========== GIO HANG: MOT ENDPOINT CONG KHAI (§B.6) ==========

    /**
     * {@code POST /api/cart/validate} gọi được khi <b>không</b> có token.
     * <p>
     * Body rỗng là một phần của phép kiểm, cùng lý do với các ca bên trên: 422 chỉ phát ra từ tầng
     * validate, tức request đã đi <i>xuyên qua</i> filter chain. Xoá dòng {@code permitAll} tương
     * ứng sẽ đổi mã này thành 401 và ca đỏ ngay.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("POST /api/cart/validate cong khai — khong token van toi duoc tang validate")
    void cartValidateStaysPublic() throws Exception {
        mockMvc.perform(post("/api/cart/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    /**
     * Giỏ rỗng trả <b>200 kèm mảng rỗng</b>, không phải 400 — và vẫn không cần token.
     * <p>
     * Ca này chạy được trong context đã loại JPA vì giỏ rỗng dừng lại ở domain <i>trước</i> khi có
     * truy vấn nào — đó chính là điều {@code CartDomainServiceTest} khoá lại. Nó ở đây vì vừa là
     * một khẳng định về contract, vừa là bằng chứng mạnh nhất rằng đường này xuyên hết filter chain
     * tới tận controller: một mã 200 không thể phát ra từ tầng bảo mật.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("Gio rong -> 200 va mang rong, khong phai 400")
    void emptyCartReturnsTwoHundredWithEmptyArray() throws Exception {
        mockMvc.perform(post("/api/cart/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Duong gio hang khong te hon khi CO token CUSTOMER")
    void cartEndpointStaysOpenForCustomerToken() throws Exception {
        mockMvc.perform(post("/api/cart/validate")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk());
    }

    /**
     * Dòng {@code permitAll} của giỏ hàng khai <b>đúng một method</b>, không mở cả đường dẫn.
     * <p>
     * Cùng cái bẫy của backlog 0012, và ở đây nó nguy hiểm hơn một bậc: giỏ hàng là namespace mà
     * các đường <i>ghi</i> tương lai ({@code DELETE /api/cart/items/{id}}) sẽ mọc vào. Một
     * {@code permitAll} viết bằng mẫu {@code /api/cart/**} sẽ mở sẵn chúng trước khi chúng kịp ra
     * đời. Năm ca dưới đây phải trả 401, không phải 404 hay 405: 404/405 nghĩa là request đã lọt
     * qua tầng bảo mật rồi mới dừng ở tầng định tuyến.
     *
     * @param method verb không được mở trên đường dẫn giỏ hàng
     * @param path đường dẫn giỏ hàng
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1} khong duoc cong khai -> 401")
    @MethodSource("cartMethodsThatMustStayClosed")
    @DisplayName("permitAll cua gio hang chi mo DUNG method cua no, khong mo ca duong dan")
    void cartPathOpensOnlyItsOwnMethod(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * @return các cặp (method, path) trên đường dẫn giỏ hàng mà luật <b>không</b> được mở
     */
    private static Stream<Arguments> cartMethodsThatMustStayClosed() {
        return Stream.of(
                // Chi POST duoc mo tren chinh duong validate
                Arguments.of(HttpMethod.GET, "/api/cart/validate"),
                Arguments.of(HttpMethod.PUT, "/api/cart/validate"),
                Arguments.of(HttpMethod.DELETE, "/api/cart/validate"),
                // Duong gio hang tuong lai chua ton tai — va phai dong san
                Arguments.of(HttpMethod.POST, "/api/cart"),
                Arguments.of(HttpMethod.GET, "/api/cart"));
    }

    // ========== DON HANG: HAI CONG KHAI, MOT CAN TOKEN (§B.6) ==========

    /**
     * {@code POST /api/orders} gọi được khi <b>không</b> có token — khách vãng lai đặt hàng được.
     * <p>
     * Body rỗng là một phần của phép kiểm, cùng lý do với các ca bên trên: 422 chỉ phát ra từ tầng
     * validate, tức request đã đi <i>xuyên qua</i> filter chain. Đây là đường <b>ghi</b> công khai
     * đầu tiên của dự án, nên ca này cũng là chỗ duy nhất chứng minh việc mở nó ra là có chủ ý chứ
     * không phải một dòng {@code permitAll} lỡ tay.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("POST /api/orders cong khai — khong token van toi duoc tang validate")
    void createOrderStaysPublic() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.items").exists())
                .andExpect(jsonPath("$.errors.shipping").exists())
                .andExpect(jsonPath("$.errors.paymentMethod").exists());
    }

    /**
     * <b>Ca quan trọng nhất của nhóm này: công khai nghĩa là đi được khi KHÔNG có token, không phải
     * khi token sai.</b>
     * <p>
     * {@code permitAll} chỉ nói "đường này không đòi xác thực"; nó <i>không</i> tắt bộ lọc bearer
     * của resource server. Một chuỗi {@code Authorization} hỏng vẫn bị chặn 401 trước khi tới
     * handler, và điều đó phải đúng — nếu không thì một token hết hạn sẽ im lặng biến thành một đơn
     * hàng của khách vãng lai, tức khách đã đăng nhập mất đơn của chính mình vào lịch sử của không ai.
     * <p>
     * Backlog 0014 §Contract 5 gọi đây là ca test bắt buộc, không phải chi tiết cài đặt.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("POST /api/orders voi token HONG -> 401, permitAll khong cuu duoc token sai")
    void createOrderRejectsBrokenToken() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer khong-phai-mot-jwt-hop-le")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * {@code GET /api/orders/me} <b>bắt buộc có token</b> (§C.4.1).
     * <p>
     * <b>Ca này là bằng chứng về THỨ TỰ matcher, không chỉ về sự tồn tại của một dòng luật.</b>
     * Mẫu {@code /api/orders/*} của {@code GET /orders/{code}} khớp cả đường dẫn này; nếu dòng
     * {@code permitAll} ấy đứng trước thì request không token sẽ đi thẳng tới handler và nhận
     * <b>200 kèm mảng rỗng</b> — một endpoint trả lịch sử mua hàng cho bất kỳ ai, không exception
     * nào và không dòng log nào. 401 ở đây nghĩa là dòng {@code authenticated()} vẫn đứng trên.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("GET /api/orders/me khong token -> 401, KHONG bi /api/orders/* nuot mat")
    void myOrdersRequireToken() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED));
    }

    /**
     * {@code GET /api/orders/me} với token CUSTOMER đi tới được handler.
     * <p>
     * <b>Positive control cho ca ngay trên:</b> nó chứng minh 401 kia đến từ việc <i>thiếu token</i>
     * chứ không phải từ một đường dẫn viết sai hay một handler không tồn tại — hai nguyên nhân đó
     * cũng cho ra "không phải 200" và sẽ khiến ca trên xanh vì lý do sai.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("GET /api/orders/me co token CUSTOMER -> 200 va mang rong")
    void myOrdersReachHandlerWithToken() throws Exception {
        mockMvc.perform(get("/api/orders/me")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * {@code GET /api/orders/{code}} công khai — mã không có thật thì <b>404, không phải 401</b>.
     * <p>
     * 404 chỉ phát ra từ handler, nên nó chứng minh request đã xuyên hết filter chain. Đặt cạnh ca
     * {@link #myOrdersRequireToken()}, hai ca này khoá lại đúng ranh giới tinh tế nhất của cả
     * ticket: cùng một hình dạng đường dẫn, hai kết quả khác nhau, và khác biệt ấy do <i>thứ tự</i>
     * hai dòng luật quyết định chứ không do ý định của ai.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("GET /api/orders/{code} cong khai — ma khong co that thi 404, KHONG phai 401")
    void orderByCodeStaysPublic() throws Exception {
        mockMvc.perform(get("/api/orders/NSS-20260101-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * Ba dòng {@code permitAll} / {@code authenticated} của đơn hàng khai <b>đúng method của
     * chúng</b>, không mở cả đường dẫn.
     * <p>
     * Cùng cái bẫy của backlog 0012, và ở đây nó nguy hiểm nhất trong ba nhóm: namespace này chứa
     * một đường <i>ghi</i> công khai, nên một mẫu viết thiếu {@code HttpMethod} sẽ mở luôn
     * {@code PUT} và {@code DELETE} trên chính đơn hàng — tức cho phép sửa hoặc xoá một chứng từ,
     * đúng thứ §B.12.2 nói là không bao giờ được mở.
     * <p>
     * Mọi ca dưới đây phải trả 401, không phải 404 hay 405: 404/405 nghĩa là request đã lọt qua
     * tầng bảo mật rồi mới dừng ở tầng định tuyến.
     *
     * @param method verb không được mở trên đường dẫn đơn hàng
     * @param path đường dẫn đơn hàng
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1} khong duoc cong khai -> 401")
    @MethodSource("orderMethodsThatMustStayClosed")
    @DisplayName("permitAll cua don hang chi mo DUNG method cua no, khong mo ca duong dan")
    void orderPathsOpenOnlyTheirOwnMethod(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * @return các cặp (method, path) trên đường dẫn đơn hàng mà luật <b>không</b> được mở
     */
    private static Stream<Arguments> orderMethodsThatMustStayClosed() {
        return Stream.of(
                // Chi POST duoc mo tren /api/orders — liet ke moi don la viec cua /admin/orders (§C.4.3b)
                Arguments.of(HttpMethod.GET, "/api/orders"),
                Arguments.of(HttpMethod.PUT, "/api/orders"),
                Arguments.of(HttpMethod.DELETE, "/api/orders"),
                // Chi GET duoc mo tren /api/orders/{code} — don da dat la chung tu, khong sua khong xoa
                Arguments.of(HttpMethod.PUT, "/api/orders/NSS-20260101-0001"),
                Arguments.of(HttpMethod.PATCH, "/api/orders/NSS-20260101-0001"),
                Arguments.of(HttpMethod.DELETE, "/api/orders/NSS-20260101-0001"),
                Arguments.of(HttpMethod.POST, "/api/orders/NSS-20260101-0001"),
                // Duong long tuong lai chua ton tai — mot dau sao giu chung dong san
                Arguments.of(HttpMethod.POST, "/api/orders/NSS-20260101-0001/cancel"),
                // Khu quan tri chua ton tai va phai dong san
                Arguments.of(HttpMethod.GET, "/api/admin/orders"));
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
    @ValueSource(strings = {"/api/auth/register", "/api/auth/login", "/api/auth/refresh",
            "/api/auth/forgot-password", "/api/auth/reset-password"})
    @DisplayName("Nam endpoint xac thuc cong khai van qua duoc filter chain khi khong co token")
    void authPublicEndpointsReachableWithoutToken(String path) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Hai endpoint hồ sơ của backlog 0016 <b>cần token</b>, và không dòng luật riêng nào khai điều
     * đó — chúng rơi vào {@code .anyRequest().authenticated()}. Ca này khoá lại chính điều đó: thêm
     * một {@code permitAll} rộng phía trên (một mẫu {@code /api/auth/**} chẳng hạn) sẽ mở công khai
     * cả hai đường ghi vào hồ sơ người dùng, và nó sẽ đỏ ở đây.
     * <p>
     * Body <b>không rỗng</b> để chứng minh 401 đến từ filter chain chứ không từ tầng validate.
     *
     * @param path endpoint hồ sơ
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "PUT {0} khong kem token tra 401")
    @ValueSource(strings = {"/api/auth/me", "/api/auth/password"})
    @DisplayName("Hai endpoint ho so khong kem token tra 401 dang ProblemDetail, KHONG phai than rong")
    void profileEndpointsRequireToken(String path) throws Exception {
        mockMvc.perform(request(HttpMethod.PUT, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"123456\",\"newPassword\":\"matkhaumoi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                // Chuoi tieng Viet, khong rong: frontend do thang `detail` ra man hinh (§A.3)
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED))
                .andExpect(jsonPath("$.instance").value(path));
    }

    /**
     * Có token thì request đi <i>xuyên qua</i> filter chain và dừng ở tầng validate.
     * <p>
     * <b>Hai ca dưới đây cố ý dừng ở tầng validate, và đó là ràng buộc của chính file này</b>:
     * context ở đây loại autoconfig JPA nên <i>không có</i> {@code TransactionManager}, và mọi ca
     * chạm tới một method {@code @Transactional} của app service sẽ ra 500 chứ không ra business
     * response. Hành vi nghiệp vụ đầy đủ thuộc ma trận request thật ở mục Verification của ticket.
     * <p>
     * <b>Token đúc ở đây KHÔNG mang claim {@code sid}</b> — xem {@link #genToken}. Đó vừa là ca
     * "token cấp trước khi claim ra đời" trong đời thật, vừa là lý do đường đọc {@code sid} bắt buộc
     * phải chịu được {@code null}.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("PUT /api/auth/password kem token di qua duoc filter chain, dung o tang validate")
    void changePasswordWithTokenReachesValidationLayer() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT, "/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                // Hai loai 422 phan biet bang su co mat cua khoa `errors`; day la loai VALIDATE
                .andExpect(jsonPath("$.errors.currentPassword").exists())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    @DisplayName("PUT /api/auth/me kem token: chuoi rong tra 422 kem errors, khong phai 200")
    void updateProfileRejectsBlankStringAtValidationLayer() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT, "/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \",\"email\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.fullName").exists())
                // Bay @Email: mot minh no coi chuoi rong la hop le
                .andExpect(jsonPath("$.errors.email").exists());
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
                // Hai operation ho so cua backlog 0016. Du an KHONG co security requirement toan
                // cuc, nen thieu `security` tren @Operation lam api-docs sai trong khi endpoint van
                // duoc bao ve dung — Swagger UI se khong gui header va nguoi doc doc ra thong tin sai.
                .andExpect(jsonPath("$.paths['/api/auth/me'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/password'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.responses['403']").exists())
                // Duong doc cong khai thi KHONG duoc mang security — Swagger noi dung §B.1
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist());
    }

    /**
     * Hai operation mã giảm giá có mặt trong tài liệu và <b>không</b> mang {@code security}.
     * <p>
     * Khẳng định "không có {@code security}" đi kèm <b>positive control</b> ngay trong cùng ca:
     * trước hết xác nhận đường ghi sản phẩm <i>có</i> khai {@code security} — chứng minh phép đo
     * nhìn thấy được thuộc tính đó — rồi mới khẳng định hai đường mã giảm giá vắng nó. Không có
     * bước control, một {@code jsonPath} viết sai đường dẫn cũng cho ra {@code doesNotExist()} và
     * ca vẫn xanh.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs khai du hai operation ma giam gia va KHONG khai security cho chung")
    void apiDocsDeclareCouponOperationsAsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. POSITIVE CONTROL: phep do nhin thay duoc thuoc tinh `security` khi no CO mat
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").exists())
                // 2. Hai operation moi co mat trong tai lieu, kem summary
                .andExpect(jsonPath("$.paths['/api/coupons/validate'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/coupons/active'].get.summary").exists())
                // 3. Ca hai deu cong khai (§B.7) — khang dinh nay chi co nghia sau buoc 1
                .andExpect(jsonPath("$.paths['/api/coupons/validate'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/coupons/active'].get.security").doesNotExist());
    }

    /**
     * Operation giỏ hàng có mặt trong tài liệu và <b>không</b> mang {@code security}.
     * <p>
     * Cùng khuôn positive control với ca mã giảm giá ngay trên: xác nhận đường ghi sản phẩm
     * <i>có</i> khai {@code security} trước, rồi mới khẳng định đường giỏ hàng vắng nó. Không có
     * bước control, một {@code jsonPath} viết sai đường dẫn cũng cho ra {@code doesNotExist()} và
     * ca vẫn xanh.
     * <p>
     * Ca này cũng khoá luôn <b>số operation</b> ở mức đủ dùng: {@code POST} có mặt trên
     * {@code /api/cart/validate} và không có động từ nào khác trên cùng đường dẫn — một
     * {@code @RequestMapping} rộng tay sẽ sinh ra cả bảy động từ trong tài liệu.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs khai operation gio hang, KHONG khai security, va chi dung mot dong tu")
    void apiDocsDeclareCartOperationAsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. POSITIVE CONTROL: phep do nhin thay duoc thuoc tinh `security` khi no CO mat
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").exists())
                // 2. Operation moi co mat trong tai lieu, kem summary tieng Viet
                .andExpect(jsonPath("$.paths['/api/cart/validate'].post.summary").exists())
                // 3. Cong khai (§B.6) — khang dinh nay chi co nghia sau buoc 1
                .andExpect(jsonPath("$.paths['/api/cart/validate'].post.security").doesNotExist())
                // 4. Dung MOT dong tu tren duong dan nay
                .andExpect(jsonPath("$.paths['/api/cart/validate'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/cart/validate'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/cart/validate'].delete").doesNotExist());
    }

    /**
     * Ba operation đơn hàng có mặt trong tài liệu, và <b>đúng một</b> trong ba mang {@code security}.
     * <p>
     * Đây là ca duy nhất trong file khẳng định cả hai chiều trên cùng một nhóm endpoint, nên nó cần
     * <b>hai</b> positive control chứ không một:
     * <ol>
     *   <li>đường ghi sản phẩm <i>có</i> {@code security} — chứng minh phép đo nhìn thấy được thuộc
     *       tính đó khi nó có mặt;</li>
     *   <li>{@code /api/orders/me} <i>có</i> {@code security} — chứng minh nhóm đơn hàng
     *       <i>cũng</i> khai được nó, nên hai lần {@code doesNotExist()} phía sau không phải hệ quả
     *       của việc springdoc bỏ sót cả nhóm.</li>
     * </ol>
     * Không có bước 2, một lỗi cấu hình khiến mọi operation đơn hàng mất {@code security} vẫn cho
     * ra hai dòng cuối màu xanh.
     * <p>
     * Ca này cũng khoá <b>số động từ</b> trên hai đường dẫn mới: một {@code @RequestMapping} rộng
     * tay sẽ sinh cả bảy động từ trong tài liệu, và tài liệu khi ấy sẽ hứa những thứ
     * {@code SecurityConfig} đóng.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs khai du ba operation don hang; CHI /orders/me mang security")
    void apiDocsDeclareOrderOperationsWithCorrectSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. POSITIVE CONTROL A: phep do nhin thay duoc `security` o mot nhom khac
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").exists())
                // 2. Ba operation moi co mat, kem summary tieng Viet
                .andExpect(jsonPath("$.paths['/api/orders'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/orders/me'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{code}'].get.summary").exists())
                // 3. POSITIVE CONTROL B: chinh nhom don hang CO khai duoc `security`
                .andExpect(jsonPath("$.paths['/api/orders/me'].get.security[0].bearerAuth").exists())
                // 4. Hai cai con lai cong khai (§B.6) — chi co nghia sau buoc 3
                .andExpect(jsonPath("$.paths['/api/orders'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{code}'].get.security").doesNotExist())
                // 5. Dung mot dong tu tren moi duong dan moi
                .andExpect(jsonPath("$.paths['/api/orders'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{code}'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{code}'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{code}'].delete").doesNotExist());
    }

    // ========== DAT LAI MAT KHAU: HAI ENDPOINT CONG KHAI (backlog 0017) ==========

    /**
     * <b>Hai endpoint đặt lại mật khẩu của backlog 0017 phải CÔNG KHAI, và ca này là chỗ duy nhất
     * chứng minh điều đó không mất đi.</b>
     * <p>
     * Khác backlog 0016: ở đó {@code PUT /auth/me} và {@code PUT /auth/password} <i>cần</i> token
     * nên {@code .anyRequest().authenticated()} đã phủ sẵn và {@code SecurityConfig} không phải sửa
     * gì. Ở đây thì ngược lại — thiếu một dòng trong {@code PATHS_AUTH_PUBLIC} là một endpoint đặt
     * lại mật khẩu <b>đòi đăng nhập</b>, tức vô nghĩa theo đúng nghĩa đen, trong khi build vẫn xanh
     * và endpoint vẫn có trong Swagger.
     * <p>
     * Khẳng định là 422 kèm map {@code errors}: 422 chỉ phát ra từ tầng validate, tức request đã đi
     * <i>xuyên qua</i> filter chain. Một 401 ở đây nghĩa là dòng luật đã biến mất.
     *
     * @param path endpoint đặt lại mật khẩu
     * @param field tên trường bắt buộc phải có trong map {@code errors}
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "POST {0} khong token -> 422 kem errors.{1}")
    @MethodSource("passwordResetPublicEndpoints")
    @DisplayName("Hai endpoint dat lai mat khau cong khai — khong token van toi duoc tang validate")
    void passwordResetEndpointsStayPublic(String path, String field) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    /**
     * @return các cặp (đường dẫn, tên trường bắt buộc) của hai endpoint đặt lại mật khẩu
     */
    private static Stream<Arguments> passwordResetPublicEndpoints() {
        return Stream.of(
                Arguments.of("/api/auth/forgot-password", "email"),
                Arguments.of("/api/auth/reset-password", "token"),
                Arguments.of("/api/auth/reset-password", "newPassword"));
    }

    /**
     * <b>Công khai nghĩa là đi được khi KHÔNG có token, không phải khi token sai.</b>
     * <p>
     * Cùng ca đã khoá cho {@code POST /api/orders} ở backlog 0014 §Contract 5: {@code permitAll}
     * chỉ nói "đường này không đòi xác thực", nó <i>không</i> tắt bộ lọc bearer của resource server.
     *
     * @param path endpoint đặt lại mật khẩu
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "POST {0} voi token HONG -> 401")
    @ValueSource(strings = {"/api/auth/forgot-password", "/api/auth/reset-password"})
    @DisplayName("Endpoint dat lai mat khau voi token HONG -> 401, permitAll khong cuu duoc token sai")
    void passwordResetEndpointsRejectBrokenToken(String path) throws Exception {
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer khong-phai-mot-jwt-hop-le")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * <b>Năm dòng {@code permitAll} của nhóm xác thực KHÔNG được nuốt {@code logout} và hai đường
     * ghi vào hồ sơ.</b>
     * <p>
     * Đây là ca đối trọng của {@link #passwordResetEndpointsStayPublic}: backlog 0017 vừa
     * <i>thêm</i> hai đường dẫn vào {@code PATHS_AUTH_PUBLIC}, và cách sai để làm việc đó là thay
     * năm literal bằng một mẫu {@code /api/auth/**} — gọn hơn hẳn, build xanh, hai endpoint mới chạy
     * đúng, và <b>toàn bộ khu xác thực trở thành công khai</b>. Ba ca dưới đây đỏ ngay nếu ai đó làm
     * vậy.
     *
     * @param method verb của endpoint phải giữ nguyên trạng thái cần token
     * @param path đường dẫn xác thực phải giữ nguyên trạng thái cần token
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1} van phai can token -> 401")
    @MethodSource("authPathsThatMustStayClosed")
    @DisplayName("Them hai duong cong khai KHONG duoc mo ca khu /api/auth")
    void authNamespaceStaysClosedApartFromItsPublicPaths(HttpMethod method, String path)
            throws Exception {
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"khong-quan-trong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED));
    }

    /**
     * @return các cặp (method, path) trong khu {@code /api/auth} mà luật <b>không</b> được mở
     */
    private static Stream<Arguments> authPathsThatMustStayClosed() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/auth/logout"),
                Arguments.of(HttpMethod.PUT, "/api/auth/me"),
                Arguments.of(HttpMethod.PUT, "/api/auth/password"));
    }

    /**
     * Hai operation mới có mặt trong api-docs và <b>không</b> mang {@code security} — chúng công
     * khai.
     * <p>
     * Positive control đi kèm là bắt buộc, cùng kỷ luật với
     * {@link #apiDocsDeclareOrderOperationsWithCorrectSecurity()}: một khẳng định
     * {@code doesNotExist()} sẽ xanh cả khi phép dò bị hỏng hoàn toàn (đường dẫn viết sai, tài liệu
     * không sinh ra). Dòng đầu chứng minh phép dò <i>nhìn thấy được</i> {@code security} ở chỗ khác.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs khai du hai operation dat lai mat khau, va CA HAI khong mang security")
    void apiDocsDeclarePasswordResetOperationsAsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. POSITIVE CONTROL: phep do nhin thay duoc `security` o mot operation khac
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").exists())
                // 2. Hai operation moi co mat, kem summary tieng Viet
                .andExpect(jsonPath("$.paths['/api/auth/forgot-password'].post.summary").exists())
                .andExpect(jsonPath("$.paths['/api/auth/reset-password'].post.summary").exists())
                // 3. Ca hai CONG KHAI — chi co nghia sau buoc 1
                .andExpect(jsonPath("$.paths['/api/auth/forgot-password'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/reset-password'].post.security").doesNotExist());
    }
}
