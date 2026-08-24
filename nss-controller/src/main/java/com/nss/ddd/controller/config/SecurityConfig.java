package com.nss.ddd.controller.config;

import com.nss.ddd.controller.exception.SecurityProblemDetailHandler;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Filter chain của toàn hệ (ADR 0003) — <b>hai tầng luật, không còn một</b> (backlog 0012).
 * <p>
 * <b>Đọc danh sách bên dưới như một phần của contract, không như cấu hình.</b> Bật Spring Security
 * là <i>mặc định khoá sạch mọi thứ</i>: {@code /api/hello}, sáu endpoint <i>đọc</i> sản phẩm và
 * toàn bộ đường dẫn Swagger đều công khai theo API_CONTRACT, nên thiếu <b>một</b> dòng
 * {@code permitAll} là một regression im lặng — endpoint vẫn tồn tại, vẫn có trong doc, nhưng trả
 * 401 cho mọi người. Chiều ngược lại cũng im lặng: <i>thừa</i> một dòng {@code permitAll} là mở
 * đường ghi cho cả internet, và đó chính là lỗ hổng mà backlog 0012 sinh ra để vá.
 * <p>
 * <b>Tầng 1 — đọc công khai.</b> API_CONTRACT §B.1 đánh dấu cả sáu endpoint sản phẩm là ⬜ (không
 * cần token) vì một storefront phải cho khách xem hàng trước khi đăng nhập. Tất cả đều là
 * {@code GET}. §B.7 đánh dấu hai endpoint mã giảm giá cũng là ⬜ vì cùng một lý do: giỏ hàng của
 * khách vãng lai sống trong localStorage và phải áp được mã trước khi đăng nhập. Một trong hai là
 * {@code POST} — nó mang body chứ không ghi gì, xem {@link #PATH_COUPON_VALIDATE}. §B.6 đánh dấu
 * {@code POST /api/cart/validate} là ⬜ vì cùng một lý do và cùng một tính chất: mang body, chỉ đọc,
 * xem {@link #PATH_CART_VALIDATE}.
 * <p>
 * <b>Tầng 2 — ghi chỉ ADMIN.</b> {@code POST} / {@code PUT} / {@code DELETE} trên sản phẩm là thao
 * tác quản trị. Ba endpoint này ra đời ở ticket 0008 để chứng minh lát cắt dọc chạy được và
 * <i>không</i> nằm trong API_CONTRACT, nên siết chúng lại không phá hợp đồng với consumer nào.
 * <p>
 * <b>Vì sao phải khai method tường minh — cái bẫy này đã cắn một lần.</b>
 * {@code requestMatchers(String...)} <i>không phân biệt HTTP method</i>, nên dòng
 * {@code requestMatchers("/api/products", "/api/products/**").permitAll()} của phiên bản trước mở
 * công khai cả ba đường ghi. Bằng chứng regression của ticket 0010: không kèm token,
 * {@code PUT /api/products/1} trả 422 (đã lọt qua tầng bảo mật, chỉ dừng ở validate) và
 * {@code DELETE /api/products/9999} trả 404 — cả hai đều đã vào tới business logic.
 * <p>
 * <b>Thứ tự các dòng là một phần của luật, không phải thẩm mỹ.</b> {@code authorizeHttpRequests}
 * áp dòng <i>khớp đầu tiên</i>. Ba dòng ghi phải đứng <b>trên</b> dòng {@code GET} công khai; đảo
 * lại thì một {@code permitAll} rộng nuốt luôn đường ghi — build vẫn xanh, test tính năng vẫn
 * xanh, và lỗ hổng quay lại y nguyên. Luật hẹp trước, luật rộng sau.
 * <p>
 * <b>Quy ước tiền tố vai trò: {@code ROLE_}, chọn một lần và không trộn.</b> Xem javadoc của
 * {@link #jwtAuthenticationConverter()} — đó là chỗ sai <i>im lặng</i> nhất của cả cơ chế.
 * <p>
 * Stateless: không session, không CSRF token. {@code client.ts} gắn {@code Authorization: Bearer}
 * vào từng request (§A.2), nên không có cookie phiên nào để bảo vệ, và một CSRF filter bật lên chỉ
 * làm mọi {@code POST} công khai trả 403.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Endpoint hello của khung — công khai từ ticket 0002. */
    public static final String PATH_HELLO = "/api/hello";

    /**
     * Đường <b>đọc</b> sản phẩm — công khai theo API_CONTRACT §B.1, và <b>chỉ với {@code GET}</b>.
     * <p>
     * Hai mẫu chứ không một: {@code /api/products} là danh sách, {@code /api/products/**} phủ
     * {@code /{slug}}, {@code /{slug}/related}, {@code /suggest}, {@code /price-range}.
     */
    public static final String[] PATHS_PRODUCT_READ = {"/api/products", "/api/products/**"};

    /** Tạo sản phẩm — {@code POST} trên chính đường danh sách; chỉ ADMIN (backlog 0012). */
    public static final String PATH_PRODUCT_CREATE = "/api/products";

    /**
     * Sửa / xoá sản phẩm — {@code PUT} và {@code DELETE} theo {@code id}; chỉ ADMIN (backlog 0012).
     * <p>
     * Dùng {@code /**} chứ không {@code /*} là có chủ ý: một đường ghi lồng ra đời sau này
     * ({@code PUT /api/products/1/images} chẳng hạn) sẽ <b>mặc định thuộc ADMIN</b> thay vì mặc
     * định lọt lưới. Nới quyền là một quyết định phải viết ra; siết quyền thì không được phụ thuộc
     * vào việc ai đó nhớ thêm một dòng.
     */
    public static final String PATH_PRODUCT_WRITE_BY_ID = "/api/products/**";

    /**
     * Xác thực mã giảm giá — công khai theo API_CONTRACT §B.7, và <b>chỉ với {@code POST}</b>.
     * <p>
     * Là {@code POST} vì nó mang body {@code { code, subtotal }}, không phải vì nó ghi gì: endpoint
     * này chỉ đọc và không tăng {@code usedCount} (xem javadoc {@code CouponController}).
     * <p>
     * <b>Đường dẫn literal, cố ý không dùng mẫu {@code /api/coupons/**}.</b> Một mẫu rộng ở đây sẽ
     * mở công khai mọi đường mã giảm giá ra đời sau này — {@code POST /api/coupons} của khu quản
     * trị chẳng hạn — theo đúng kiểu lỗi mà backlog 0012 sinh ra để vá. Hai endpoint công khai thì
     * khai đúng hai dòng; nới quyền phải là một quyết định viết ra, không phải một hệ quả phụ.
     */
    public static final String PATH_COUPON_VALIDATE = "/api/coupons/validate";

    /** Danh sách mã đang chạy — công khai theo §B.7, và <b>chỉ với {@code GET}</b>. */
    public static final String PATH_COUPON_ACTIVE = "/api/coupons/active";

    /**
     * Đối chiếu giỏ hàng — công khai theo API_CONTRACT §B.6, và <b>chỉ với {@code POST}</b>.
     * <p>
     * Là {@code POST} vì nó mang body {@code { items: CartItem[] }}, không phải vì nó ghi gì:
     * endpoint này chỉ đọc, không trừ kho và không giữ chỗ (xem javadoc {@code CartController}).
     * <p>
     * <b>Đường dẫn literal, cố ý không dùng mẫu {@code /api/cart/**}.</b> Cùng lý do đã viết ở
     * {@link #PATH_COUPON_VALIDATE}: một mẫu rộng ở đây mở công khai <i>mọi</i> đường giỏ hàng ra
     * đời sau này. Và giỏ hàng của một khách đã đăng nhập là dữ liệu riêng của họ, nên một
     * {@code GET /api/cart} hay {@code DELETE /api/cart/items/{id}} thêm vào về sau phải mặc định
     * bị khoá chứ không mặc định lọt lưới.
     */
    public static final String PATH_CART_VALIDATE = "/api/cart/validate";

    /**
     * Mã vai trò quản trị — <b>không</b> kèm tiền tố {@code ROLE_}.
     * <p>
     * {@code hasRole(...)} tự thêm tiền tố; truyền {@code "ROLE_ADMIN"} vào đây sẽ thành
     * {@code ROLE_ROLE_ADMIN} và mọi ADMIN đều nhận 403.
     */
    public static final String ROLE_ADMIN = "ADMIN";

    /** Tiền tố authority Spring quy ước cho vai trò — xem {@link #jwtAuthenticationConverter()}. */
    public static final String ROLE_PREFIX = "ROLE_";

    /** Tên claim mang mã vai trò trong access token; phải khớp hằng cùng tên ở tầng application. */
    public static final String CLAIM_ROLES = "roles";

    /** Ba endpoint xác thực công khai; {@code /api/auth/logout} cố ý không nằm trong đây. */
    public static final String[] PATHS_AUTH_PUBLIC = {
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh"
    };

    /**
     * Đường dẫn tài liệu của ticket 0009.
     * <p>
     * Bốn mục, không phải một: {@code /swagger-ui/**} là trang và webjar, {@code /swagger-ui.html}
     * là đường chuyển hướng vào trang đó, {@code /v3/api-docs/**} là JSON (mẫu {@code /**} khớp cả
     * {@code /v3/api-docs} không hậu tố), và {@code /v3/api-docs.yaml} là bản YAML. Thiếu mục nào
     * thì Swagger UI chết bằng 401 giữa chừng — trang mở được nhưng trống rỗng.
     */
    public static final String[] PATHS_SWAGGER = {
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    private final SecurityProblemDetailHandler securityProblemDetailHandler;

    /**
     * Dịch claim {@code roles} của access token thành authority của Spring Security.
     * <p>
     * <b>Quy ước đã chọn: authority mang tiền tố {@code ROLE_}, luật viết bằng
     * {@code hasRole("ADMIN")}.</b> Claim trong token là mã vai trò trần ({@code ["ADMIN"]} /
     * {@code ["CUSTOMER"]}, đọc từ bảng {@code role}), nên converter này thêm tiền tố; luật ở
     * {@link #securityFilterChain} viết mã trần và để {@code hasRole} tự thêm lại. Hai đầu gặp nhau
     * ở đúng chuỗi {@code ROLE_ADMIN}.
     * <p>
     * <b>Vì sao phải chốt một quy ước và viết lý do ra đây:</b> {@code hasRole("ADMIN")} ngầm tìm
     * authority tên {@code ROLE_ADMIN}, còn {@code hasAuthority("ADMIN")} tìm đúng chuỗi
     * {@code ADMIN}. Trộn hai kiểu — converter giữ mã trần nhưng luật dùng {@code hasRole}, hoặc
     * ngược lại — cho ra một hệ <i>chạy được, build xanh, test tính năng xanh</i>, chỉ có điều mọi
     * ADMIN đều nhận 403 và <b>không có dòng log nào nói vì sao</b>: Spring chỉ thấy "authority
     * không khớp", nó không biết hai bên đang nói về cùng một vai trò.
     * <p>
     * Chọn {@code ROLE_} thay vì mã trần vì đó là quy ước gốc của Spring Security —
     * {@code hasRole}, {@code hasAnyRole}, {@code @WithMockUser(roles = ...)} và
     * {@code RoleHierarchy} đều mặc định theo nó. Đi ngược quy ước của framework thì mỗi tiện ích
     * dựng sẵn sau này lại là một cái bẫy phải nhớ.
     * <p>
     * <b>Hệ quả khi thêm luật mới:</b> vai trò dùng {@code hasRole(<mã trần>)}. Nếu về sau có luật
     * theo <i>quyền</i> (bảng {@code permission} đã seed 8 dòng) thì đó là một claim khác và một
     * converter khác — <b>không</b> nhét quyền vào claim {@code roles} để mượn tiền tố này.
     *
     * @return converter đọc claim {@code roles} và sinh authority {@code ROLE_<mã>}
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(CLAIM_ROLES);
        authoritiesConverter.setAuthorityPrefix(ROLE_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * @param http bộ dựng filter chain
     * @param jwtAuthenticationConverter bộ đọc claim {@code roles}; truyền tường minh chứ không để
     *                                   Spring tự dò trong context — dò ngầm thì một bean bị đổi
     *                                   tên hay bị loại khỏi context sẽ làm mọi ADMIN im lặng rơi
     *                                   xuống 403
     * @return filter chain duy nhất của ứng dụng
     * @throws Exception khi cấu hình không hợp lệ
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Luat HEP truoc: ba duong ghi san pham chi ADMIN. Ba dong nay PHAI dung
                        //    tren dong GET permitAll ben duoi — dao lai thi permitAll rong nuot ca
                        //    duong ghi, lo hong cua backlog 0012 quay lai, va build van xanh.
                        .requestMatchers(HttpMethod.POST, PATH_PRODUCT_CREATE).hasRole(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.PUT, PATH_PRODUCT_WRITE_BY_ID).hasRole(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, PATH_PRODUCT_WRITE_BY_ID).hasRole(ROLE_ADMIN)
                        // 2. Luat RONG sau: doc san pham cong khai, va CHI voi GET (§B.1).
                        .requestMatchers(HttpMethod.GET, PATHS_PRODUCT_READ).permitAll()
                        // 3. Ma giam gia: hai endpoint cong khai (§B.7), moi cai khai DUNG method
                        //    cua no. requestMatchers(String...) khong phan biet method, nen bo
                        //    HttpMethod di la mo luon moi verb tren cung duong dan — ke ca DELETE.
                        .requestMatchers(HttpMethod.POST, PATH_COUPON_VALIDATE).permitAll()
                        .requestMatchers(HttpMethod.GET, PATH_COUPON_ACTIVE).permitAll()
                        // 4. Gio hang: doi chieu gio la duong CONG KHAI va CHI DOC (§B.6). Cung
                        //    mot ky luat nhu hai dong tren — literal + method, khong /api/cart/**:
                        //    gio hang cua khach da dang nhap la du lieu rieng, nen moi duong gio
                        //    hang ra doi sau nay phai mac dinh bi khoa.
                        .requestMatchers(HttpMethod.POST, PATH_CART_VALIDATE).permitAll()
                        // 5. Ba nhom con lai giu nguyen nhu truoc backlog 0012 — khong giao voi
                        //    ba nhom tren nen thu tu giua chung khong anh huong gi.
                        .requestMatchers(PATH_HELLO).permitAll()
                        .requestMatchers(PATHS_AUTH_PUBLIC).permitAll()
                        .requestMatchers(PATHS_SWAGGER).permitAll()
                        .anyRequest().authenticated())
                // Resource server giu entry point rieng cho ca "token hong / het han"; exceptionHandling
                // giu ca "khong co token" va 403. Dat ca hai ve cung mot handler de moi loi auth ra
                // dung mot hinh dang ProblemDetail.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityProblemDetailHandler))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityProblemDetailHandler)
                        .accessDeniedHandler(securityProblemDetailHandler));
        return http.build();
    }
}
