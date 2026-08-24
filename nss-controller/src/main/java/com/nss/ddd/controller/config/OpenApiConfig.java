package com.nss.ddd.controller.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình OpenAPI 3 — phần mô tả cấp tài liệu (title, version, server, security scheme).
 * <p>
 * <b>Chỉ mô tả, không đổi hành vi.</b> Doc chỉ nói về những endpoint đang chạy thật; springdoc suy
 * path và schema bằng reflection trên chính controller nên doc không bao giờ lệch runtime.
 * <p>
 * <b>{@code @SecurityScheme} chỉ được khai từ backlog 0010 trở đi</b>, và thứ tự đó là có lý do:
 * chừng nào Spring Security chưa tồn tại thì một nút <i>Authorize</i> chỉ là doc nói dối — nó nhận
 * token rồi gắn header mà không endpoint nào kiểm. Cùng ticket đó đã {@code permitAll} bốn đường
 * dẫn tài liệu ({@code /swagger-ui/**}, {@code /swagger-ui.html}, {@code /v3/api-docs/**},
 * {@code /v3/api-docs.yaml}) trong {@link SecurityConfig} — thiếu là Swagger UI chết với 401/403
 * chứ không phải một lỗi cấu hình rõ ràng.
 * <p>
 * <b>Scheme khai ở cấp tài liệu nhưng KHÔNG áp cho toàn bộ API.</b> Không có
 * {@code addSecurityItem(...)} nào ở đây: phần lớn endpoint hiện tại là công khai, nên gắn yêu cầu
 * token lên tất cả sẽ khiến Swagger UI hiện ổ khoá ở những chỗ không cần token. Endpoint nào cần
 * thì tự khai {@code @SecurityRequirement} tại chỗ — hiện chỉ có {@code POST /api/auth/logout}.
 * <p>
 * <b>Server URL lấy qua {@code @Value}</b>: coding-conventions §14 cấm hardcode host/port trong
 * {@code @Configuration}. Default chỉ để chạy dev trên máy cá nhân.
 * <p>
 * Chuỗi fluent {@code new OpenAPI().info(...)} là setter của {@code io.swagger.v3.oas.models},
 * <b>không phải</b> Lombok {@code @Builder} (bị cấm ở §5).
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Dán **access token** lấy từ `POST /api/auth/login` (trường `token` trong "
                + "response, không phải `refreshToken`). Swagger UI tự thêm tiền tố `Bearer `.")
public class OpenApiConfig {

    private static final String API_TITLE = "NSS API — Thực phẩm sạch";

    private static final String API_VERSION = "1.0.0-SNAPSHOT";

    private static final String API_DESCRIPTION = """
            Tài liệu REST của backend NSS. Chỉ liệt kê những endpoint **đang chạy thật**; \
            endpoint chưa dựng thì chưa có mặt ở đây.

            **Quy ước chung của bề mặt dây (API_CONTRACT §A):**
            - Mọi endpoint nằm dưới tiền tố `/api`.
            - Phân trang: `{items, total, page, limit, totalPages}`; **`page` đánh số từ 1**, \
            mặc định `page=1`, `limit=12`.
            - Tiền là **số nguyên VNĐ** — không thập phân, không đơn vị nghìn. Áp dụng cho \
            `price`, `salePrice`, và cho `effectivePrice` mà **client tự tính** \
            `salePrice ?? price`: cột `effective_price` có trong bảng nhưng **cố ý không trả ra \
            response**, nên không tìm thấy nó ở schema bên dưới là đúng.
            - Ngày giờ là chuỗi **ISO-8601 có hậu tố `Z`**, ví dụ `2026-08-17T10:30:00Z`.
            - Đường dẫn ảnh là **tương đối**, bắt đầu bằng `/images/`.
            - Giá trị không có là **`null`**, không dùng chuỗi rỗng; `salePrice = null` nghĩa là \
            không giảm giá.
            - Lỗi trả về theo **`ProblemDetail` (RFC 7807)** với `content-type` \
            `application/problem+json`; `detail` viết tiếng Việt cho người dùng cuối đọc. \
            Lỗi validate 422 kèm phần mở rộng `errors` — map `tên trường → thông điệp` \
            (thông điệp validate hiện là tiếng Anh).

            **Xác thực (§A.2):** access token là JWT, gắn bằng header \
            `Authorization: Bearer <token>`. Lấy token ở `POST /api/auth/login` — trường tên là \
            **`token`**, không phải `accessToken` — rồi dán vào nút *Authorize* ở góc trên.
            - Access token hết hạn sau **30 phút**; đổi lấy cặp mới bằng \
            `POST /api/auth/refresh`, và refresh token **xoay vòng**: chuỗi cũ bị thu hồi ngay khi \
            chuỗi mới được cấp.
            - Hiện chỉ `POST /api/auth/logout` cần token; mọi endpoint khác trong tài liệu này đều \
            công khai.
            - Thiếu token hoặc token sai trả `401` dạng `ProblemDetail` với `detail` tiếng Việt, \
            **không phải thân rỗng**.""";

    private static final String SERVER_DESCRIPTION = "Server hiện hành";

    /** Không hardcode host/port (§14): giá trị thật đến từ cấu hình, default chỉ dành cho dev. */
    @Value("${springdoc.server.url:http://localhost:8080}")
    private String serverUrl;

    /**
     * @return mô tả cấp tài liệu của OpenAPI 3
     */
    @Bean
    public OpenAPI nssOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .version(API_VERSION)
                        .description(API_DESCRIPTION))
                .servers(List.of(new Server()
                        .url(serverUrl)
                        .description(SERVER_DESCRIPTION)));
    }
}
