package com.nss.ddd.controller.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

/**
 * Bộ ba bean mật mã của vòng phiên xác thực: ký JWT, kiểm JWT, băm mật khẩu (ADR 0003).
 * <p>
 * <b>Vì sao là {@code JwtEncoder}/{@code JwtDecoder} của Spring chứ không phải {@code jjwt}:</b>
 * cả hai đến từ {@code spring-boot-starter-oauth2-resource-server}, đã nằm trong BOM
 * {@code spring-boot-dependencies} 3.3.5 — không thêm property phiên bản, không thêm trục bảo trì
 * nào ngoài những thứ Spring Boot đã quản (ADR 0003 §Alternatives).
 * <p>
 * <b>{@code BCryptPasswordEncoder} mặc định (strength 10) không phải lựa chọn tự do.</b> Nó bị
 * <i>pin bởi dữ liệu</i>: hash đã seed ở ticket 0006 là {@code $2a$10$...}. Đổi strength hay thuật
 * toán ở dòng này làm hai tài khoản seed không đăng nhập được nữa, và triệu chứng là "sai mật
 * khẩu" chứ không phải một lỗi cấu hình.
 * <p>
 * <b>Khoá bí mật đến từ cấu hình, không hardcode</b> (coding-conventions §14, §17). Giá trị mặc
 * định trong {@code application.yml} chỉ dành cho máy dev và có comment cảnh báo tại chỗ.
 */
@Configuration
public class JwtConfig {

    /** HMAC-SHA256 cần khoá tối thiểu 256 bit — 32 ký tự ASCII. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    /** Tên thuật toán JCA cho khoá HMAC-SHA256. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Không hardcode secret (§14): giá trị thật đến từ biến môi trường {@code JWT_SECRET}. */
    @Value("${nss.auth.jwt-secret}")
    private String jwtSecret;

    /**
     * Bộ ký access token.
     *
     * @return encoder HMAC dùng khoá đối xứng từ cấu hình
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(genSecretKey()));
    }

    /**
     * Bộ kiểm access token dùng cho filter chain của resource server.
     * <p>
     * Khai bean tường minh khiến {@code OAuth2ResourceServerAutoConfiguration} lùi lại — nếu không,
     * nó sẽ đi tìm {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} và fail khi khởi
     * động vì thuật toán ở đây là HMAC đối xứng, không có JWK endpoint nào để tra.
     *
     * @return decoder HMAC, ràng buộc đúng một thuật toán
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(genSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * @return bộ băm mật khẩu; xem javadoc cấp class về việc strength bị pin bởi dữ liệu seed
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Dựng khoá HMAC từ chuỗi bí mật.
     * <p>
     * Kiểm độ dài ngay lúc khởi động là có chủ ý: một khoá ngắn hơn 256 bit khiến Nimbus ném lỗi ở
     * <i>lần ký đầu tiên</i> — nghĩa là ứng dụng lên xanh, Swagger mở được, và chỉ người dùng đầu
     * tiên bấm Đăng nhập mới phát hiện ra. Fail lúc khởi động thì lỗi thuộc về người deploy chứ
     * không thuộc về người dùng.
     *
     * @return khoá đối xứng cho HS256
     * @throws IllegalStateException khi khoá cấu hình ngắn hơn 32 byte
     */
    private SecretKey genSecretKey() {
        byte[] keyBytes = jwtSecret == null
                ? new byte[0]
                : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "nss.auth.jwt-secret must be at least " + MINIMUM_SECRET_BYTES + " bytes for HS256");
        }
        return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }
}
