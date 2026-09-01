package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh gửi lại email xác nhận tài khoản — {@code POST /api/auth/resend-confirmation} sau khi qua
 * validate (backlog 0037).
 * <p>
 * Đúng một trường, cùng khuôn {@code ForgotPasswordCommand}: endpoint công khai nên không có claim
 * nào định danh người gọi — email trong body chỉ chọn hộp thư nhận link, không định danh người gọi.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ResendConfirmationCommand {

    private String email;
}
