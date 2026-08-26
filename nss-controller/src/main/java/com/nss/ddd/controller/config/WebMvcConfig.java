package com.nss.ddd.controller.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Nơi đăng ký interceptor của tầng web (backlog 0021 Phase 1).
 * <p>
 * <b>Đây là chỗ duy nhất nối {@link ApiRateLimitInterceptor} vào chuỗi xử lý.</b> Gỡ đúng một dòng
 * {@code addInterceptor} ở đây là gỡ toàn bộ lớp trần thông lượng — chủ ý, để bước kiểm chứng "gỡ
 * đăng ký thì mọi 429 biến mất, khôi phục thì về đúng số cũ" có một mối nối duy nhất thay vì phải
 * lần theo nhiều chỗ.
 * <p>
 * <b>{@code /api/**} chứ không phải {@code /**}:</b> Swagger UI ({@code /swagger-ui/**}) và
 * {@code /v3/api-docs} nằm ngoài trần — chúng là tài liệu, không phải bề mặt nghiệp vụ, và một
 * trang tài liệu kéo hàng chục tài nguyên tĩnh sẽ tự đốt hết permit của tier read.
 * <p>
 * <b>Cố ý KHÔNG khai {@code @EnableWebMvc}</b>: annotation đó tắt toàn bộ autoconfiguration MVC của
 * Spring Boot (content negotiation, message converter, static resource, và chính
 * {@code ProblemDetail}), tức đổi hình dạng mọi response chỉ để đăng ký một interceptor.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** Chỉ bề mặt nghiệp vụ nằm dưới trần; xem javadoc cấp class. */
    private static final String PATH_PATTERN_API = "/api/**";

    private final ApiRateLimitInterceptor apiRateLimitInterceptor;

    /**
     * @param registry sổ đăng ký interceptor của Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRateLimitInterceptor).addPathPatterns(PATH_PATTERN_API);
    }
}
