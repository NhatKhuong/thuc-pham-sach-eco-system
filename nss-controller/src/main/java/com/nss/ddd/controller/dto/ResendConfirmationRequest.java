package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/auth/resend-confirmation} (backlog 0037).
 * <p>
 * Đúng một trường, cùng khuôn {@code ForgotPasswordRequest}. Validation dùng
 * <b>{@code jakarta.validation}</b>; thông điệp viết <b>tiếng Anh</b> theo §1.
 * <p>
 * <b>Một email đúng định dạng nhưng không ứng với tài khoản nào, hoặc ứng với một tài khoản đã xác
 * nhận rồi, KHÔNG phải lỗi validate</b> — cả hai đều trả 204 như mọi lần khác (anti-enumeration).
 */
@Data
public class ResendConfirmationRequest {

    /** Trần 160 khớp cột {@code user.email}, cùng lý do đã viết ở {@code ForgotPasswordRequest}. */
    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 160, message = "Email không được vượt quá 160 ký tự.")
    private String email;
}
