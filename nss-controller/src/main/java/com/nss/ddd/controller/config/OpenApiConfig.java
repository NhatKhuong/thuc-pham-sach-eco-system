package com.nss.ddd.controller.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình OpenAPI 3 — phần mô tả cấp tài liệu (title, version, server).
 * <p>
 * <b>Chỉ mô tả, không đổi hành vi.</b> Doc chỉ nói về 6 endpoint đang chạy thật; springdoc suy
 * path và schema bằng reflection trên chính controller nên doc không bao giờ lệch runtime.
 * <p>
 * <b>Không khai {@code @SecurityScheme}.</b> Spring Security chưa tồn tại trong repo — khai bearer
 * JWT bây giờ chỉ tạo một nút <i>Authorize</i> không làm gì, tức là doc nói dối. Việc đó thuộc
 * ticket dựng Spring Security, và ticket đó phải {@code permitAll} cho {@code /swagger-ui/**},
 * {@code /swagger-ui.html}, {@code /v3/api-docs/**}, {@code /v3/api-docs.yaml} — thiếu là Swagger
 * UI chết với 401/403 chứ không phải một lỗi cấu hình rõ ràng.
 * <p>
 * <b>Server URL lấy qua {@code @Value}</b>: coding-conventions §14 cấm hardcode host/port trong
 * {@code @Configuration}. Default chỉ để chạy dev trên máy cá nhân.
 * <p>
 * Chuỗi fluent {@code new OpenAPI().info(...)} là setter của {@code io.swagger.v3.oas.models},
 * <b>không phải</b> Lombok {@code @Builder} (bị cấm ở §5).
 */
@Configuration
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
            - Chưa có xác thực: mọi endpoint dưới đây đều công khai.""";

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
