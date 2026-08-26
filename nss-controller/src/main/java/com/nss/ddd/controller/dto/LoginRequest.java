package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/auth/login}.
 * <p>
 * <b>Cố ý ràng buộc lỏng hơn {@code RegisterRequest}.</b> Ở đây chỉ kiểm "có nhập hay không", không
 * kiểm độ dài tối thiểu và không kiểm định dạng email. Lý do: mọi ràng buộc thêm vào form đăng nhập
 * đều tự nó tiết lộ chính sách mật khẩu, và tệ hơn, nó biến một lần nhập sai thành <b>422</b> trong
 * khi mọi lần nhập sai khác là <b>401</b> — chênh lệch mã trạng thái đó đủ để dò ra một chuỗi có
 * phải mật khẩu hợp lệ về mặt hình thức hay không.
 * <p>
 * Thông điệp validation viết tiếng Anh theo §1.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Vui lòng nhập email.")
    @Size(max = 160, message = "Email không được vượt quá 160 ký tự.")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(max = 72, message = "Mật khẩu không được vượt quá 72 ký tự.")
    private String password;
}
