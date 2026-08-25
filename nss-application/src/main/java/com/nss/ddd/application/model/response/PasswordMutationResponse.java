package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của lệnh đổi mật khẩu ({@code PUT /auth/password}).
 * <p>
 * <b>Không có payload, nên thành công phải được khai bằng một cờ tường minh.</b> Contract trả
 * {@code 204} với thân rỗng, nên không có đối tượng nào để dùng làm dấu hiệu "đã xong" theo kiểu
 * {@code AuthMutationResponse.getAuth() != null}. Cờ {@code success} thay vào đó; suy ra thành công
 * bằng {@code code == null} sẽ biến một mã bị quên set thành một lần đổi mật khẩu "thành công".
 * <p>
 * <b>Tập mã ở đây RỜI với tập của {@code AuthMutationResponse}</b> — lý do đầy đủ nằm ở javadoc của
 * {@code ProfileMutationResponse}: đường dịch của kiểu kia rơi về 401 ở nhánh {@code default}, và
 * một lần sai mật khẩu hiện tại <b>không được</b> trả 401 (xem {@link #CODE_INVALID_CURRENT_PASSWORD}).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PasswordMutationResponse {

    /** Dòng {@code user} ứng với claim {@code sub} không còn tồn tại. */
    public static final String CODE_USER_NOT_FOUND = "USER_NOT_FOUND";

    /**
     * Sai mật khẩu hiện tại — <b>422, tuyệt đối không phải 401</b>.
     * <p>
     * 401 chỉ dành cho "thiếu / hỏng / hết hạn access token": đó chính là ranh giới
     * {@code client.ts} dựa vào để quyết định có tự gọi {@code /auth/refresh} hay không. Trả 401 ở
     * đây khiến người gõ nhầm mật khẩu cũ bị <i>đốt một refresh token</i> rồi đăng xuất — cùng loại
     * lỗi với bugs/0002.
     */
    public static final String CODE_INVALID_CURRENT_PASSWORD = "INVALID_CURRENT_PASSWORD";

    /** Đã đổi xong; {@code false} khi thất bại. */
    private boolean success;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @return kết quả thành công
     */
    public static PasswordMutationResponse success() {
        return new PasswordMutationResponse().setSuccess(true);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static PasswordMutationResponse failed(String code, String message) {
        return new PasswordMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
