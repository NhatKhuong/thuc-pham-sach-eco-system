package com.nss;

import com.nss.ddd.domain.model.entity.Product;
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
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        // Tran thong luong /api/** (backlog 0021) nam TREN chuoi filter va no la tran THEO TIEN
        // TRINH: file nay ban ~48 request, trong do ~19 vao /api/auth/** — voi nguong san xuat
        // 10/giay thi ket qua cua no phu thuoc vao MAY CHAY NHANH BAO NHIEU, chu khong phu thuoc ma
        // tran quyen ma no sinh ra de kiem. Nang nguong o day de bien mot ca do do timing thanh mot
        // ca do that. Lop limit duoc kiem rieng o ApiRateLimitWireTest voi nguong dat rat thap.
        "nss.rate-limit.auth.limit-for-period=100000",
        "nss.rate-limit.write.limit-for-period=100000",
        "nss.rate-limit.read.limit-for-period=100000"
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

    private final JwtEncoder jwtEncoder;

    @Autowired
    SecurityRulesTest(MockMvc mockMvc, JwtEncoder jwtEncoder) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * <b>Năm</b> endpoint quản trị sản phẩm, kèm mã trạng thái mà <b>ADMIN</b> phải nhận được.
     * <p>
     * Mã của cột ADMIN cố ý <i>không</i> phải mã thành công: đó là mã của tầng <i>sau</i> tầng bảo
     * mật ({@code 422} do validate body rỗng, {@code 404} do id không khớp dòng nào, {@code 200} do
     * mapper giả trả về trang rỗng). Nó chứng minh đúng điều cần chứng minh — luật đã cho ADMIN đi
     * qua — mà không cần một database sống.
     * <p>
     * <b>Ba đường ghi ở đây từng nằm dưới {@code /api/products} và đã CHUYỂN sang
     * {@code /api/admin/products} ở backlog 0018.</b> Danh sách này đổi theo <i>hành vi mới</i>,
     * không phải đổi cho test xanh: hai đường đọc được cộng thêm vì chúng cũng nằm sau cùng một
     * hàng rào, và ma trận quyền phải phủ cả năm chứ không riêng ba đường ghi.
     *
     * @return bộ bốn (method, path, body, mã trạng thái của ADMIN)
     */
    private static Stream<Arguments> adminProductEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/admin/products", "{}", 200),
                Arguments.of(HttpMethod.GET, "/api/admin/products/1", "{}", 404),
                Arguments.of(HttpMethod.POST, "/api/admin/products", "{}", 422),
                Arguments.of(HttpMethod.PUT, "/api/admin/products/1", "{}", 422),
                Arguments.of(HttpMethod.DELETE, "/api/admin/products/1", "{}", 404));
    }

    /**
     * Ba đường ghi <b>cũ</b> dưới {@code /api/products}, kèm mã mà một ADMIN phải nhận được.
     * <p>
     * <b>Đây là control dương của việc "chuyển hẳn, không nhân bản".</b> Gỡ ba dòng {@code hasRole}
     * mà quên gỡ ba mapping sẽ mở lại đúng lỗ hổng backlog 0012 — và ca duy nhất phát hiện ra là ca
     * này: nếu handler còn sống, một token ADMIN sẽ nhận {@code 422} / {@code 404} (tức đã vào tới
     * business logic) thay vì {@code 405}.
     * <p>
     * <b>{@code 405} chứ không phải {@code 404}</b>, và khác biệt đó nói đúng điều cần biết: đường
     * dẫn {@code /api/products} và {@code /api/products/{slug}} <i>vẫn tồn tại</i> cho {@code GET}
     * công khai, chỉ là không còn động từ ghi nào trên chúng. Một {@code 404} ở đây sẽ có nghĩa là
     * đường đọc công khai cũng biến mất — một regression khác hẳn.
     *
     * @return bộ ba (method, path, body)
     */
    private static Stream<Arguments> removedProductWriteEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/products", "{}"),
                Arguments.of(HttpMethod.PUT, "/api/products/1", "{}"),
                Arguments.of(HttpMethod.DELETE, "/api/products/1", "{}"));
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
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

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
        when(productJPAMapper.findPublicPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/hello")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isOk());
    }

    // ========== TANG 2: GHI CHI ADMIN ==========

    /**
     * Nguyên một cột của ma trận quyền cho mỗi endpoint quản trị: không token → 401,
     * CUSTOMER → 403, ADMIN → đi qua được tầng bảo mật.
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
    @MethodSource("adminProductEndpoints")
    @DisplayName("Nam endpoint quan tri san pham: khong token 401, CUSTOMER 403, ADMIN di qua")
    void adminProductEndpointEnforcesAdminOnly(HttpMethod method, String path, String body, int adminStatus)
            throws Exception {
        when(productJPAMapper.markInactive(any(), any())).thenReturn(0);
        when(productJPAMapper.findAdminPage(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(productJPAMapper.findActiveById(any())).thenReturn(Optional.empty());

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

    /**
     * Body rỗng với token ADMIN dừng ở tầng validate với <b>đúng năm</b> trường lỗi — và
     * {@code slug} <b>KHÔNG</b> nằm trong số đó.
     * <p>
     * <b>Sáu trở thành năm ở backlog 0018, và đó là hành vi mới chứ không phải một test được sửa
     * cho xanh.</b> {@code @NotBlank} và {@code @Pattern} trên {@code slug} đã được gỡ khỏi hai DTO
     * vì chúng từ chối đúng thứ frontend gửi: §B.12.1 nói {@code slug} bỏ trống thì backend tự sinh
     * từ {@code name}, nên "bỏ trống" phải là ca hợp lệ nhất của cả endpoint chứ không phải một lỗi
     * validate.
     * <p>
     * <b>Dòng {@code doesNotExist()} cho {@code slug} là phần quan trọng nhất của ca này.</b> Năm
     * dòng {@code exists()} phía trên vẫn xanh y nguyên nếu ai đó vô tình thêm lại
     * {@code @NotBlank}; chỉ dòng cuối bắt được điều đó. Và năm dòng {@code exists()} chính là
     * positive control cho nó — chúng chứng minh phép đo <i>nhìn thấy được</i> map {@code errors},
     * nên một {@code doesNotExist()} xanh không phải vì {@code jsonPath} viết sai đường dẫn.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("POST /api/admin/products voi ADMIN + body rong: 422 du 5 truong, slug KHONG con bat buoc")
    void adminReachesValidationWithFiveFieldErrorsAndNoSlugError() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                // 1. POSITIVE CONTROL: phep do nhin thay duoc map `errors` khi no CO mat
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.stock").exists())
                .andExpect(jsonPath("$.errors.unit").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists())
                // 2. slug KHONG con bat buoc — khang dinh nay chi co nghia sau buoc 1
                .andExpect(jsonPath("$.errors.slug").doesNotExist());
    }

    /**
     * <b>Ba đường ghi cũ dưới {@code /api/products} không còn tồn tại.</b>
     * <p>
     * Ca này là nửa còn lại của "chuyển hẳn, không nhân bản" — xem javadoc
     * {@link #removedProductWriteEndpoints()}. Không token phải là <b>401</b> (rơi vào
     * {@code anyRequest().authenticated()}, đúng hành vi đã biết của bugs/0002), và có token ADMIN
     * phải là <b>405</b>: đường dẫn còn sống cho {@code GET} nhưng không còn động từ ghi nào trên nó.
     * <p>
     * <b>Vế ADMIN mới là vế bắt được lỗi.</b> Vế 401 vẫn xanh kể cả khi handler còn nguyên — nó chỉ
     * nói "cần token". Chỉ khi đã đi qua tầng bảo mật thì mới phân biệt được "handler đã biến mất"
     * (405) với "handler vẫn ở đó và vừa nhận request" (422 / 404).
     *
     * @param method HTTP method của đường ghi đã gỡ
     * @param path đường dẫn cũ
     * @param body thân request
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1} da bi go: khong token->401, ADMIN->405")
    @MethodSource("removedProductWriteEndpoints")
    @DisplayName("Ba duong ghi cu duoi /api/products khong con handler nao")
    void removedProductWriteEndpointsAreGone(HttpMethod method, String path, String body)
            throws Exception {
        // 1. Khong token: roi vao anyRequest().authenticated()
        mockMvc.perform(request(method, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // 2. ADMIN: di qua duoc tang bao mat va dung o tang dinh tuyen — 405, KHONG phai 422/404
        mockMvc.perform(request(method, path)
                        .header(HttpHeaders.AUTHORIZATION, genBearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * <b>Hàng rào gác cả tiền tố {@code /api/admin/**} — kể cả một đường dẫn KHÔNG CÓ HANDLER
     * NÀO.</b> Đây là bằng chứng của §C.4.3a, và nó vẫn có nghĩa sau backlog 0019.
     * <p>
     * <b>Ca này từng mang tên "GET /api/admin/orders CHUA TON TAI"; backlog 0019 đã dựng đúng
     * endpoint đó, nên cái tên cũ trở thành một lời nói dối.</b> Điều nó đo thì <i>không</i> mất đi
     * — chỉ cần một đường dẫn không có handler để đo, và {@code /api/admin/khong-ton-tai} là đường
     * đó. Sửa tên chứ không xoá ca: thứ nó khoá là "luật gắn vào TIỀN TỐ, không gắn vào từng
     * handler", và đó vẫn là điều duy nhất giữ cho endpoint quản trị <i>tiếp theo</i> ra đời đã
     * được gác sẵn.
     * <p>
     * Một token CUSTOMER phải nhận <b>403</b> — không phải 401 (người gọi đã đăng nhập rồi), và
     * <b>không phải 404</b> (luật chạy trước khi Spring đi tìm handler). Nếu nó ra 404 thì nghĩa là
     * luật đang gắn vào từng handler chứ không vào tiền tố.
     * <p>
     * Kèm hai control. Một: không token ra 401, chứng minh hàng rào phân biệt được "chưa đăng nhập"
     * với "sai vai trò". Hai — <b>control âm</b>: {@code /api/adminx} <i>không</i> bị nuốt. Thiếu
     * nó, một mẫu viết nhầm thành {@code /api/admin**} (thiếu dấu gạch chéo) vẫn cho mọi dòng phía
     * trên xanh, trong khi nó đang khoá cả những đường dẫn chỉ tình cờ trùng tiền tố chuỗi.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("Duong /api/admin KHONG CO HANDLER van bi hang rao chan: CUSTOMER->403, khong token->401")
    void adminPrefixGuardsPathsWithoutHandler() throws Exception {
        // 1. CUSTOMER: 403 — luat cua tien to chay TRUOC khi Spring di tim handler.
        //    Duong dan nay khong co @RequestMapping nao; neu luat gan vao handler thi day se la 404.
        mockMvc.perform(get("/api/admin/khong-ton-tai")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(MESSAGE_FORBIDDEN));

        // 2. Khong token: 401 — hang rao van phan biet duoc hai ca
        mockMvc.perform(get("/api/admin/khong-ton-tai"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED));

        // 3. Ke ca mot dong tu khong ai dinh mo tren mot duong dan quan tri co that
        mockMvc.perform(request(HttpMethod.DELETE, "/api/admin/orders/NSS-20260101-0001")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isForbidden());

        // 4. CONTROL AM: mau khong duoc nuot mot duong chi TRUNG TIEN TO CHUOI
        mockMvc.perform(get("/api/adminx")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    /**
     * <b>Ma trận quyền của SÁU endpoint quản trị mới</b> (backlog 0019): không token {@literal ->}
     * 401, CUSTOMER {@literal ->} 403, ADMIN {@literal ->} mã của tầng <i>sau</i> bảo mật.
     * <p>
     * Cùng khuôn — và cùng lý do gộp ba khẳng định vào một ca — với
     * {@link #adminProductEndpointEnforcesAdminOnly}: ba ô chỉ có nghĩa khi <b>cùng đúng</b>. Ô
     * CUSTOMER {@literal ->} <b>403</b> là ô quan trọng nhất: một 401 ở đó sẽ khiến
     * {@code client.ts} tưởng access token hết hạn, gọi {@code /auth/refresh}, rồi <i>đăng xuất</i>
     * một khách chỉ vì họ bấm nhầm nút quản trị.
     * <p>
     * <b>{@code PATCH} có mặt trong ma trận này, và đó là điểm cần đo.</b> Dòng
     * {@code PATH_ADMIN_ALL} cố ý <i>không</i> khai {@code HttpMethod} — nếu nó khai, một động từ
     * không được liệt kê sẽ lọt lưới. Đây là động từ đầu tiên của dự án nằm ngoài bộ
     * {@code GET/POST/PUT/DELETE}, nên nó là ca duy nhất chứng minh điều đó.
     * <p>
     * <b>Mã của cột ADMIN cố ý không phải mã thành công ở mọi dòng</b>: {@code 404} do mapper giả
     * trả về rỗng, {@code 422} do validate body rỗng, {@code 200} do trang rỗng. Nó chứng minh đúng
     * điều cần chứng minh — luật đã cho ADMIN đi qua — mà không cần một database sống.
     * <p>
     * <b>Context này KHÔNG có {@code TransactionManager}</b> (autoconfig JPA bị loại), nên dòng
     * {@code PATCH} cố ý gửi body rỗng để dừng ở tầng validate. Hành vi nghiệp vụ đầy đủ của máy
     * trạng thái thuộc {@code OrderStatusMachineTest} và ma trận request thật ở mục Verification
     * của ticket.
     *
     * @param method HTTP method
     * @param path đường dẫn
     * @param body thân request
     * @param adminStatus mã trạng thái ADMIN phải nhận — mã của tầng sau bảo mật
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "{0} {1}: khong token->401, CUSTOMER->403, ADMIN->{3}")
    @MethodSource("adminEndpointsOf0019")
    @DisplayName("Sau endpoint quan tri moi: khong token 401, CUSTOMER 403, ADMIN di qua")
    void newAdminEndpointsEnforceAdminOnly(HttpMethod method, String path, String body, int adminStatus)
            throws Exception {
        when(orderJPAMapper.findAdminPage(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(userJPAMapper.findAdminPage(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

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

    /**
     * <b>Sáu</b> endpoint quản trị của backlog 0019, kèm mã trạng thái mà <b>ADMIN</b> phải nhận.
     *
     * @return bộ bốn (method, path, body, mã trạng thái của ADMIN)
     */
    private static Stream<Arguments> adminEndpointsOf0019() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/admin/orders", "{}", 200),
                Arguments.of(HttpMethod.GET, "/api/admin/orders/NSS-20260101-0001", "{}", 404),
                Arguments.of(HttpMethod.PATCH, "/api/admin/orders/NSS-20260101-0001/status", "{}", 422),
                Arguments.of(HttpMethod.GET, "/api/admin/customers", "{}", 200),
                Arguments.of(HttpMethod.GET, "/api/admin/customers/1", "{}", 404),
                Arguments.of(HttpMethod.GET, "/api/admin/stats/overview", "{}", 200));
    }

    /**
     * {@code days} ngoài dải trả <b>400</b>, không phải một khoảng bị kẹp im lặng — §B.12.4.
     * <p>
     * <b>Kèm control dương ngay trong cùng ca:</b> {@code days=30} phải ra 200. Không có nó, một
     * cấu hình làm mọi lời gọi tới endpoint này hỏng cũng cho ra 400 và ca vẫn xanh.
     * <p>
     * Phép kiểm dải nằm ở tầng controller nên nó chạy được ở context không có JPA — nó không chạm
     * tới truy vấn nào.
     *
     * @param days giá trị ngoài dải
     * @throws Exception khi MockMvc lỗi
     */
    @ParameterizedTest(name = "days={0} -> 400")
    @ValueSource(ints = {0, -1, 366, 9999})
    @DisplayName("days ngoai dai 1..365 tra 400, KHONG am tham kep gia tri")
    void statsRejectsDaysOutsideRange(int days) throws Exception {
        // 1. CONTROL DUONG: mot `days` hop le van ra 200
        mockMvc.perform(get("/api/admin/stats/overview")
                        .param("days", "30")
                        .header(HttpHeaders.AUTHORIZATION, genBearer("ADMIN")))
                .andExpect(status().isOk());

        // 2. Ngoai dai -> 400, va KHONG kem map `errors` (day la loi tham so, khong theo truong)
        mockMvc.perform(get("/api/admin/stats/overview")
                        .param("days", String.valueOf(days))
                        .header(HttpHeaders.AUTHORIZATION, genBearer("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").doesNotExist());
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
                // Khu quan tri DA TON TAI tu backlog 0019, va van phai doi token: mot dong
                // permitAll viet rong tay o nhom /api/orders khong duoc cham toi no.
                Arguments.of(HttpMethod.GET, "/api/admin/orders"),
                Arguments.of(HttpMethod.PATCH, "/api/admin/orders/NSS-20260101-0001/status"));
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

    /**
     * <b>Năm operation quản trị có mặt trong tài liệu và CẢ NĂM mang {@code security}; ba operation
     * ghi cũ dưới {@code /api/products} đã biến mất khỏi tài liệu.</b>
     * <p>
     * Ca này khoá cả hai nửa của việc chuyển namespace ở backlog 0018, và hai nửa đó hỏng theo hai
     * kiểu khác nhau:
     * <ul>
     *   <li><b>Thiếu {@code security} trên một operation quản trị</b> — endpoint vẫn được bảo vệ
     *       đúng (hàng rào nằm ở {@code SecurityConfig}, không ở annotation), nhưng Swagger UI sẽ
     *       không gửi header {@code Authorization} và người đọc tài liệu đọc ra một sự thật sai.
     *       Dự án không có security requirement toàn cục, nên mỗi {@code @Operation} phải tự khai.</li>
     *   <li><b>Ba operation ghi cũ còn sót lại trong tài liệu</b> — nghĩa là mapping chưa được gỡ
     *       thật, tức vẫn còn một cửa thứ hai vào cùng chỗ ghi.</li>
     * </ul>
     * <b>Khẳng định "đường đọc công khai KHÔNG mang {@code security}" đi kèm positive control ngay
     * trong cùng ca:</b> năm dòng {@code exists()} phía trên chứng minh phép đo nhìn thấy được
     * thuộc tính đó, nên hai dòng {@code doesNotExist()} cuối không phải hệ quả của một
     * {@code jsonPath} viết sai đường dẫn.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs: nam operation quan tri deu mang security, ba operation ghi cu da bien mat")
    void apiDocsDeclareSecurityOnAdminProductOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. Ca NAM operation quan tri mang security
                .andExpect(jsonPath("$.paths['/api/admin/products'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products/{id}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products/{id}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products/{id}'].delete.security[0].bearerAuth").exists())
                // 2. Ca nam deu khai 401 va 403 — tai lieu noi dung ve hang rao
                .andExpect(jsonPath("$.paths['/api/admin/products'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products/{id}'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products/{id}'].put.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products/{id}'].delete.responses['403']").exists())
                // 3. Hai operation ho so cua backlog 0016 — khong lien quan namespace, giu nguyen
                .andExpect(jsonPath("$.paths['/api/auth/me'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/password'].put.security[0].bearerAuth").exists())
                // 4. BA OPERATION GHI CU DA BIEN MAT khoi tai lieu — nua con lai cua viec chuyen han
                .andExpect(jsonPath("$.paths['/api/products'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products/{id}']").doesNotExist())
                // 5. Duong doc cong khai van con va KHONG mang security (§B.1) — chi co nghia sau (1)
                .andExpect(jsonPath("$.paths['/api/products'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products/{slug}'].get.security").doesNotExist());
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
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.security[0].bearerAuth").exists())
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
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.security[0].bearerAuth").exists())
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
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.security[0].bearerAuth").exists())
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

    /**
     * <b>Sáu operation quản trị mới của backlog 0019 có mặt trong tài liệu và CẢ SÁU mang
     * {@code security}.</b>
     * <p>
     * Dự án <i>không</i> có security requirement toàn cục, nên mỗi {@code @Operation} phải tự khai.
     * Thiếu {@code security} thì endpoint vẫn được bảo vệ đúng — hàng rào nằm ở
     * {@code SecurityConfig}, không ở annotation — nhưng Swagger UI sẽ không gửi header
     * {@code Authorization}, và người đọc tài liệu đọc ra một sự thật sai.
     * <p>
     * <b>Ca này cũng khoá SỐ ĐỘNG TỪ trên ba đường dẫn mới.</b> Ba đường đơn hàng quản trị mở đúng
     * {@code GET} / {@code GET} / {@code PATCH}; §B.12.2 cấm tường minh việc xoá đơn và sửa
     * items/tiền, nên một {@code DELETE} hay {@code PUT} xuất hiện trong tài liệu là dấu hiệu ai đó
     * vừa mở đúng cái cửa hợp đồng đóng lại. Tài liệu khi ấy sẽ hứa những thứ contract cấm.
     * <p>
     * <b>Positive control</b> nằm ở dòng đầu: một operation đã biết là có {@code security}. Không
     * có nó, một {@code jsonPath} viết sai đường dẫn cũng cho ra {@code doesNotExist()} màu xanh.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs: sau operation quan tri moi deu mang security; khong co dong tu ghi nao thua")
    void apiDocsDeclareSecurityOnNewAdminOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. POSITIVE CONTROL: phep do nhin thay duoc thuoc tinh `security` khi no CO mat
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.security[0].bearerAuth").exists())
                // 2. Ca SAU operation moi co mat va mang security
                .andExpect(jsonPath("$.paths['/api/admin/orders'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}/status'].patch"
                        + ".security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/stats/overview'].get"
                        + ".security[0].bearerAuth").exists())
                // 3. Ca sau deu khai 401 va 403 — tai lieu noi dung ve hang rao
                .andExpect(jsonPath("$.paths['/api/admin/orders'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/orders'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}/status'].patch"
                        + ".responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/stats/overview'].get.responses['400']").exists())
                // 4. KHONG co dong tu ghi nao tren don hang quan tri (§B.12.2 cam xoa don va sua don)
                .andExpect(jsonPath("$.paths['/api/admin/orders'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/orders'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{code}'].delete").doesNotExist())
                // 5. Khach hang CHI DOC (§B.12.3) — khong sua, khong xoa, va KHONG doi vai tro
                .andExpect(jsonPath("$.paths['/api/admin/customers'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}/role']").doesNotExist())
                // 6. Khong co duong don-hang-cua-mot-khach rieng (§B.12.3)
                .andExpect(jsonPath("$.paths['/api/admin/customers/{id}/orders']").doesNotExist())
                // 7. Tong quan CHI DOC va se luon chi doc (§B.12.4)
                .andExpect(jsonPath("$.paths['/api/admin/stats/overview'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/stats/overview'].put").doesNotExist());
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

    // ========== DANH GIA: HAI CONG KHAI, MOT CAN TOKEN (§B.8, ADR 0008) ==========

    /**
     * <b>Ba đường của §B.8 KHÔNG kèm theo một dòng nào trong {@code SecurityConfig}, và ca này là
     * bằng chứng cho điều đó.</b>
     * <p>
     * ADR 0008 làm hàng rào biến mất: hai {@code GET} rơi vào
     * {@code requestMatchers(HttpMethod.GET, PATHS_PRODUCT_READ).permitAll()} vì
     * {@code PATHS_PRODUCT_READ} có mẫu {@code /api/products/**}, còn {@code POST} rơi vào
     * {@code .anyRequest().authenticated()} — <i>đúng</i> luật ta muốn.
     * <p>
     * <b>"Không phải sửa" không có nghĩa là "không phải chứng minh".</b> Hàng rào phủ ngầm thì bằng
     * chứng cũng phải ngầm-nhưng-đo-được, cùng kiểu ma trận mà backlog 0018 đã dùng để chứng minh
     * §C.4.3a phủ cả endpoint chưa ra đời.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("Hai GET danh gia CONG KHAI — khong token van 200, khong phai 401")
    void reviewReadEndpointsStayPublic() throws Exception {
        when(productJPAMapper.findActiveById(any()))
                .thenReturn(Optional.of(new Product().setId(11L)));
        when(reviewJPAMapper.findByProductId(any())).thenReturn(List.of());
        when(reviewJPAMapper.countGroupedByRating(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/products/11/reviews"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products/11/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                // Zero-fill: nam muc sao van co mat du khong co danh gia nao
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.distribution['5']").value(0));
    }

    /**
     * <b>{@code POST} không token phải là 401, và CUSTOMER phải ĐI QUA được tầng bảo mật.</b>
     * <p>
     * Hai khẳng định chỉ có nghĩa khi cùng đúng. Một luật trả 401 cho cả CUSTOMER trông "an toàn"
     * nhưng nó khoá mất chính người dùng mà ADR 0008 muốn cho phép; một luật cho khách vãng lai đi
     * qua thì đánh giá lại thành công khai, đúng thứ ADR 0008 dựng ra để chống.
     * <p>
     * Mã của cột CUSTOMER là <b>422</b> — mã của tầng <i>sau</i> tầng bảo mật (body rỗng trượt
     * validate). Nó chứng minh đúng điều cần chứng minh mà không cần một database sống.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("POST danh gia: khong token 401, token CUSTOMER di qua duoc tang bao mat")
    void reviewWriteRequiresToken() throws Exception {
        String path = "/api/products/11/reviews";

        // 1. Khach vang lai: 401 — tin hieu "hay dang nhap"
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(MESSAGE_UNAUTHENTICATED))
                .andExpect(jsonPath("$.instance").value(path));

        // 2. CUSTOMER: di qua tang bao mat va dung o tang validate — 422, KHONG phai 401/403
        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, genBearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                // 422 cua validate CO khoa `errors` — day la thu phan biet no voi 409
                .andExpect(jsonPath("$.errors").exists())
                .andExpect(jsonPath("$.errors.content").exists())
                .andExpect(jsonPath("$.errors.rating").exists())
                .andExpect(jsonPath("$.errors.authorName").exists());
    }

    /**
     * Ba operation mới có mặt trong api-docs, và <b>đúng MỘT</b> trong ba mang {@code security}.
     * <p>
     * Positive control đi kèm là bắt buộc: một khẳng định {@code doesNotExist()} sẽ xanh cả khi
     * phép dò hỏng hoàn toàn. Dòng đầu chứng minh phép dò <i>nhìn thấy được</i> {@code security} ở
     * chỗ khác.
     * <p>
     * <b>Ràng buộc {@code rating} 1–5 cũng được khẳng định ở đây</b> — {@code FE-0031} xin đích
     * danh điều đó, và một ràng buộc chỉ sống trong annotation mà không ra tới tài liệu thì
     * frontend không có cách nào biết.
     *
     * @throws Exception khi MockMvc lỗi
     */
    @Test
    @DisplayName("api-docs khai du ba operation danh gia, va DUNG MOT mang security")
    void apiDocsDeclareReviewOperationsWithCorrectSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 1. POSITIVE CONTROL: phep do nhin thay duoc `security` o mot operation khac
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").exists())
                // 2. Ba operation moi co mat
                .andExpect(jsonPath("$.paths['/api/products/{id}/reviews'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/reviews/summary'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/reviews'].post.summary").exists())
                // 3. DUNG MOT mang security — chi co nghia sau buoc 1
                .andExpect(jsonPath("$.paths['/api/products/{id}/reviews'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/reviews'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products/{id}/reviews/summary'].get.security").doesNotExist())
                // 4. Rang buoc rating 1..5 ra toi tai lieu (FE-0031 xin dich danh)
                .andExpect(jsonPath("$.components.schemas.CreateReviewRequest.properties.rating.minimum").value(1))
                .andExpect(jsonPath("$.components.schemas.CreateReviewRequest.properties.rating.maximum").value(5));
    }
}
