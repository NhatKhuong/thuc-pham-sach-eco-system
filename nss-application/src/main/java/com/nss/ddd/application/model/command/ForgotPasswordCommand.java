package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh yêu cầu đặt lại mật khẩu — {@code POST /api/auth/forgot-password}.
 * <p>
 * <b>Đúng một trường, và endpoint này công khai</b> — không có claim nào để đọc, nên email đến từ
 * body. Đó là ngoại lệ so với kỷ luật "định danh đi trong token" của {@code LogoutCommand} /
 * {@code ChangePasswordCommand}, và nó an toàn vì email ở đây <i>không</i> định danh người gọi: nó
 * chỉ chọn hộp thư nhận link. Không có quyền nào được cấp dựa trên giá trị này.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordCommand {

    /** Email nhận link đặt lại; có thể không ứng với tài khoản nào, và ca đó vẫn trả 204. */
    private String email;
}
