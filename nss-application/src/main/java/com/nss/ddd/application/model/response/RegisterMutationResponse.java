package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của lệnh đăng ký ({@code POST /api/auth/register}, backlog 0037) — thành công thì mang
 * {@code register}, thất bại thì mang {@code code} và {@code message}.
 * <p>
 * <b>Kiểu RIÊNG, tách khỏi {@code AuthMutationResponse}</b> — trước backlog 0037, {@code register}
 * dùng chung {@code AuthMutationResponse} với {@code login}/{@code refresh} vì cả ba đều trả
 * {@code AuthResponse}. Từ ticket này {@code register} không còn cấp phiên, nên payload thành công
 * của nó (`RegisterResponse`) không còn cùng hình dạng — dùng chung kiểu cũ sẽ để một trường
 * {@code auth} không bao giờ được set nằm cạnh một trường {@code register} mới, gây nhầm.
 * <p>
 * <b>Tập mã ở đây RỜI với {@code AuthMutationResponse}</b>, cùng nguyên tắc đã áp cho
 * {@code PasswordResetMutationResponse} / {@code ProfileMutationResponse}: mỗi lệnh xác thực dịch
 * mã lỗi của riêng nó ở đúng một chỗ trong controller, không rơi vào nhánh {@code default} của một
 * lệnh khác.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class RegisterMutationResponse {

    /** Email đã có tài khoản khác giữ — {@code uk_email} nằm trên toàn bảng. */
    public static final String CODE_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";

    /** Payload thành công; {@code null} khi thất bại. */
    private RegisterResponse register;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param register payload thành công
     * @return kết quả thành công
     */
    public static RegisterMutationResponse success(RegisterResponse register) {
        return new RegisterMutationResponse().setRegister(register);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static RegisterMutationResponse failed(String code, String message) {
        return new RegisterMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
