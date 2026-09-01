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
 * <b>Tầng 2 — cả khu quản trị chỉ ADMIN, bằng MỘT dòng luật trên cả tiền tố
 * {@code /api/admin/**}</b> (§C.4.3a, backlog 0018). Xem javadoc {@link #PATH_ADMIN_ALL}: đó là
 * dòng duy nhất trong file này <i>siết</i> quyền thay vì <i>nới</i>, và vì vậy nó là dòng duy nhất
 * cố ý dùng mẫu rộng không kèm {@code HttpMethod}.
 * <p>
 * <b>Ba dòng {@code hasRole} cho {@code POST|PUT|DELETE /api/products} của backlog 0012 đã được gỡ
 * ở backlog 0018</b>, cùng lúc với việc ba mapping tương ứng chuyển sang
 * {@code AdminProductController}. Hai việc đó phải đi cùng nhau: gỡ luật mà giữ mapping là mở lại
 * đúng lỗ hổng 0012 vừa vá, còn gỡ mapping mà giữ luật thì để lại ba dòng gác một thứ không còn
 * tồn tại — vô hại hôm nay, nhưng nó sẽ âm thầm gác nhầm vào ngày ai đó dựng lại một endpoint
 * trùng đường dẫn.
 * <p>
 * <b>Vì sao phải khai method tường minh — cái bẫy này đã cắn một lần.</b>
 * {@code requestMatchers(String...)} <i>không phân biệt HTTP method</i>, nên dòng
 * {@code requestMatchers("/api/products", "/api/products/**").permitAll()} của phiên bản trước mở
 * công khai cả ba đường ghi. Bằng chứng regression của ticket 0010: không kèm token,
 * {@code PUT /api/products/1} trả 422 (đã lọt qua tầng bảo mật, chỉ dừng ở validate) và
 * {@code DELETE /api/products/9999} trả 404 — cả hai đều đã vào tới business logic.
 * <p>
 * <b>Thứ tự các dòng là một phần của luật, không phải thẩm mỹ.</b> {@code authorizeHttpRequests}
 * áp dòng <i>khớp đầu tiên</i>, nên kỷ luật là <b>luật hẹp trước, luật rộng sau</b>: một
 * {@code permitAll} rộng đặt nhầm lên trên sẽ nuốt luôn một luật siết bên dưới, và khi đó build vẫn
 * xanh, test tính năng vẫn xanh, chỉ có hàng rào là không còn. Hai chỗ mà thứ tự đang thật sự
 * load-bearing hôm nay:
 * <ul>
 *   <li>{@link #PATH_ORDER_ME} phải đứng <b>trên</b> {@link #PATH_ORDER_BY_CODE} — mẫu một sao
 *       khớp cả hai, đảo lại thì lịch sử mua hàng riêng của từng khách thành công khai;</li>
 *   <li>{@link #PATH_ADMIN_ALL} phải đứng <b>trên</b> {@code anyRequest()} — nếu không thì mọi
 *       đường quản trị chỉ còn đòi "đã đăng nhập", tức mọi CUSTOMER đọc được dữ liệu của mọi
 *       người, và mã trả về là 200 chứ không phải một lỗi nào.</li>
 * </ul>
 * {@link #PATH_ADMIN_ALL} không giao với nhóm nào khác (không nhóm nào nằm dưới {@code /api/admin}),
 * nên vị trí của nó so với các nhóm còn lại là tự do — nhưng chỉ vì điều đó đúng <i>hôm nay</i>,
 * không phải vì thứ tự thôi quan trọng.
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

    /**
     * Đường <b>đọc</b> danh mục — công khai theo API_CONTRACT §B.2, và <b>chỉ với {@code GET}</b>
     * (backlog 0024). Không có endpoint ghi nào trong namespace này.
     * <p>
     * Hai mẫu chứ không một, cùng lý do với {@link #PATHS_PRODUCT_READ}: {@code /api/categories} là
     * danh sách (kèm {@code ?root=true}), {@code /api/categories/**} phủ {@code /{slug}}.
     */
    public static final String[] PATHS_CATEGORY_READ = {"/api/categories", "/api/categories/**"};

    /**
     * <b>Toàn bộ khu quản trị — MỘT dòng luật cho cả tiền tố, chỉ ADMIN</b> (API_CONTRACT §C.4.3a,
     * backlog 0018).
     * <p>
     * <b>Đây là hằng khác kiểu với mọi hằng còn lại trong file này, và sự khác biệt đó là chủ ý.</b>
     * Mọi dòng khác ở đây khai đường dẫn <i>literal</i> kèm {@code HttpMethod} tường minh, vì chúng
     * <b>nới</b> quyền — và §C.4.3a nói thẳng rằng nới quyền phải là một quyết định viết ra, không
     * bao giờ là hệ quả phụ của một mẫu rộng hơn mức cần. Dòng này thì ngược lại: nó <b>siết</b>.
     * Với luật siết, mẫu rộng mới là mẫu an toàn — một endpoint quản trị ra đời sau này mặc định
     * <i>đã bị khoá</i> thay vì mặc định lọt lưới.
     * <p>
     * <b>Vì thế nó phủ sẵn cả những endpoint CHƯA TỒN TẠI.</b> Chín endpoint của §B.12.2 (đơn
     * hàng), §B.12.3 (khách hàng) và §B.12.4 (tổng quan) chưa được dựng, nhưng
     * {@code GET /api/admin/orders} <i>hôm nay</i> đã trả 403 cho một token CUSTOMER. Đó chính là
     * điều §C.4.3a mua về: <i>"Một lần quên {@code @PreAuthorize} là rò dữ liệu toàn bộ khách
     * hàng"</i> — ở đây không có {@code @PreAuthorize} nào để quên, vì không có cái nào tồn tại.
     * <p>
     * <b>Cố ý KHÔNG khai {@code HttpMethod}.</b> Mọi động từ trên mọi đường dẫn dưới
     * {@code /api/admin} đều cần ADMIN; khai method ở đây là mở một khe cho động từ không được liệt
     * kê. Đây đúng là cái bẫy {@code requestMatchers(String...)} của backlog 0012 — chỉ khác dấu:
     * lần đó việc bỏ method làm một dòng {@code permitAll} mở cả ba đường ghi, lần này việc bỏ
     * method làm một dòng {@code hasRole} khoá đúng mọi thứ cần khoá.
     * <p>
     * <b>Thay thế ba dòng {@code hasRole} của backlog 0012 chứ không bổ sung cho chúng.</b> Ba
     * dòng đó gác {@code POST|PUT|DELETE /api/products}, và ba mapping tương ứng đã chuyển sang
     * {@code AdminProductController} trong cùng ticket. <b>Gỡ luật mà giữ mapping — hoặc ngược
     * lại — là mở lại đúng lỗ hổng backlog 0012 vừa vá</b>, nên hai việc đó phải đi cùng nhau và
     * đã đi cùng nhau. Sau khi chuyển, {@code POST /api/products} không còn handler nào: không
     * token thì rơi vào {@code anyRequest().authenticated()} và trả 401 (hành vi đã biết của
     * bugs/0002), có token thì dừng ở tầng định tuyến với 405 vì đường dẫn đó chỉ còn động từ
     * {@code GET}.
     */
    public static final String PATH_ADMIN_ALL = "/api/admin/**";

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
     * Đặt hàng — công khai theo API_CONTRACT §B.6, và <b>chỉ với {@code POST}</b>.
     * <p>
     * <b>Đây là đường GHI công khai đầu tiên của dự án, và lý do là nghiệp vụ chứ không phải sơ
     * suất.</b> §B.6 đánh dấu {@code createOrder} là ⬜ vì khách vãng lai phải mua được hàng trước
     * khi có tài khoản. Thứ được bảo vệ ở đây không phải "ai được gọi" mà là "cái gì được ghi": mọi
     * con số tiền do backend tự tính lại từ database (§C.1) và {@code userId} chỉ đến từ claim
     * {@code sub} (§C.2), nên một lời gọi ẩn danh không đặt được đơn hộ người khác và không tự chọn
     * được giá.
     * <p>
     * <b>Công khai nghĩa là đi được khi KHÔNG có token, không phải khi token sai.</b> Request vẫn
     * qua bộ lọc bearer của resource server: một token hỏng hoặc hết hạn bị chặn <b>401</b> trước
     * khi tới handler. Đây là ca test bắt buộc của backlog 0014 §Contract 5, không phải chi tiết
     * cài đặt.
     */
    public static final String PATH_ORDER_CREATE = "/api/orders";

    /**
     * Đơn hàng của chính người đang đăng nhập — <b>bắt buộc có token</b> (§B.6, §C.4.1).
     * <p>
     * <b>Dòng luật của đường dẫn này PHẢI đứng trên dòng {@code permitAll} của
     * {@link #PATH_ORDER_BY_CODE}.</b> Mẫu {@code /api/orders/*} khớp cả {@code /api/orders/me}, và
     * {@code authorizeHttpRequests} áp dòng <i>khớp đầu tiên</i> — đảo lại thì lịch sử mua hàng
     * riêng của từng khách thành công khai, chỉ cần biết đường dẫn. Không exception nào, không dòng
     * log nào: endpoint vẫn trả 200, chỉ là trả cho bất kỳ ai. Luật hẹp trước, luật rộng sau — đúng
     * kỷ luật đã ghi ở javadoc cấp class.
     */
    public static final String PATH_ORDER_ME = "/api/orders/me";

    /**
     * Tra đơn theo mã — công khai theo §B.6, và <b>chỉ với {@code GET}</b>.
     * <p>
     * <b>Một dấu sao chứ không hai.</b> {@code /api/orders/*} khớp đúng một đoạn đường dẫn, tức chỉ
     * {@code /api/orders/{code}}. {@code /api/orders/**} sẽ mở công khai mọi đường lồng ra đời sau
     * này — {@code POST /api/orders/{code}/cancel} chẳng hạn — <i>trước khi</i> chúng kịp tồn tại.
     * Nới quyền phải là một quyết định viết ra, không phải hệ quả phụ của việc chọn một mẫu rộng
     * hơn mức cần (backlog 0012).
     */
    public static final String PATH_ORDER_BY_CODE = "/api/orders/*";

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

    /**
     * <b>Bảy</b> endpoint xác thực công khai; {@code /api/auth/logout} cố ý không nằm trong đây.
     * <p>
     * <b>Hai dòng đến từ backlog 0017</b> ({@code forgot-password}, {@code reset-password}) <b>và
     * hai dòng đến từ backlog 0037</b> ({@code confirm-email}, {@code resend-confirmation}) đều là
     * NGOẠI LỆ so với backlog 0016. Ở ticket đó, {@code PUT /auth/me} và {@code PUT /auth/password}
     * <i>cần</i> token nên {@code .anyRequest().authenticated()} đã phủ sẵn và file này không phải
     * sửa gì. Ở đây thì ngược lại: người gọi bốn endpoint này <b>đang không đăng nhập</b> — đó là
     * toàn bộ lý do chúng tồn tại. Thiếu một dòng ở đây là một endpoint đòi đăng nhập, tức vô nghĩa
     * theo đúng nghĩa đen, và triệu chứng là một 401 chứ không phải một lỗi nào đọc ra được.
     * <p>
     * <b>Cả bảy khai bằng đường dẫn literal, không phải mẫu {@code /api/auth/**}.</b> Một mẫu rộng
     * ở đây sẽ mở công khai <i>mọi</i> đường xác thực ra đời sau này — kể cả {@code logout} và hai
     * đường ghi vào hồ sơ người dùng của backlog 0016 — theo đúng kiểu lỗi mà backlog 0012 sinh ra
     * để vá. Nới quyền phải là một quyết định viết ra, không phải một hệ quả phụ.
     * <p>
     * <b>Cả bảy cố ý KHÔNG khai {@code HttpMethod}, khác các nhóm phía trên</b>, và sự khác biệt đó
     * cũng có lý do: mỗi đường dẫn ở đây chỉ có duy nhất <b>một</b> handler — sáu cái là
     * {@code POST}, riêng {@code confirm-email} là {@code GET} (backlog 0037, nó trả HTML chứ không
     * JSON) — và không nằm trong namespace nào mà một đường ghi khác có thể mọc vào;
     * {@code /api/auth/me} và {@code /api/auth/password} là hai đường dẫn <i>khác</i>, không phải
     * đường lồng bên dưới bảy cái này. Một verb khác trên chúng dừng ở tầng định tuyến với 405,
     * không chạm được gì.
     */
    public static final String[] PATHS_AUTH_PUBLIC = {
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
            "/api/auth/forgot-password", "/api/auth/reset-password",
            "/api/auth/confirm-email", "/api/auth/resend-confirmation"
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
                        // 1. Luat SIET dau tien: ca khu quan tri chi ADMIN (§C.4.3a, backlog 0018).
                        //    MOT dong cho ca tien to, khong khai method, khong rai @PreAuthorize.
                        //    Dong nay THAY the ba dong hasRole cua backlog 0012 — ba mapping ghi
                        //    tuong ung da chuyen sang AdminProductController trong cung ticket.
                        //    Vi tri: no khong giao voi bat ky nhom nao ben duoi (khong nhom nao
                        //    nam duoi /api/admin), nen thu tu giua chung khong anh huong gi; dieu
                        //    BAT BUOC duy nhat la no dung TREN anyRequest().
                        .requestMatchers(PATH_ADMIN_ALL).hasRole(ROLE_ADMIN)
                        // 2. Luat RONG sau: doc san pham cong khai, va CHI voi GET (§B.1).
                        .requestMatchers(HttpMethod.GET, PATHS_PRODUCT_READ).permitAll()
                        // 2b. Doc danh muc cong khai, va CHI voi GET (§B.2, backlog 0024). Khong
                        //     giao voi nhom nao khac (khong nhom nao nam duoi /api/categories) nen
                        //     vi tri giua cac nhom con lai khong anh huong gi.
                        .requestMatchers(HttpMethod.GET, PATHS_CATEGORY_READ).permitAll()
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
                        // 5. Don hang (§B.6, backlog 0014 phase 3). BA dong, va THU TU giua chung
                        //    la mot phan cua luat:
                        //    - /api/orders/me phai dung TRUOC /api/orders/* vi mau mot-sao khop ca
                        //      hai. Dao lai thi lich su mua hang rieng cua tung khach thanh cong
                        //      khai, endpoint van tra 200 va khong co gi bao loi.
                        //    - Mot dau sao chu khong hai: /api/orders/** se mo san moi duong long
                        //      ra doi sau nay (POST /api/orders/{code}/cancel chang han).
                        //    - POST /api/orders la duong GHI cong khai — co y, xem javadoc hang.
                        //      "Cong khai" o day nghia la di duoc khi KHONG co token; mot token
                        //      hong van bi resource server chan 401 truoc khi toi handler.
                        .requestMatchers(HttpMethod.GET, PATH_ORDER_ME).authenticated()
                        .requestMatchers(HttpMethod.POST, PATH_ORDER_CREATE).permitAll()
                        .requestMatchers(HttpMethod.GET, PATH_ORDER_BY_CODE).permitAll()
                        // 6. Ba nhom con lai giu nguyen nhu truoc backlog 0012 — khong giao voi
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
