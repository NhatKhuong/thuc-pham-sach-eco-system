package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/auth/forgot-password}.
 * <p>
 * Đúng một trường, và tên trường <b>đọc thẳng ra từ nguồn</b>: API_CONTRACT §B.4 khai
 * {@code forgotPassword} nhận {@code { email }}. Đây là nửa duy nhất của luồng đặt lại mật khẩu
 * <i>không</i> phải suy diễn — xem {@code ResetPasswordRequest} cho nửa kia.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b>; thông điệp viết <b>tiếng Anh</b> theo §1.
 * <p>
 * <b>422 ở đây KHÔNG mâu thuẫn với chữ "400" trong bảng §B.4.</b> §A.3 của cùng tài liệu nêu ví dụ
 * lỗi theo trường là {@code status: 422} kèm map {@code errors}, và toàn dự án đã đi theo đúng quy
 * ước đó từ ticket 0008 — {@code register} cũng chỉ khai "409 email trùng" trong bảng nhưng vẫn trả
 * 422 cho lỗi validate. Con số trong cột "Lỗi" của bảng là lỗi <i>nghiệp vụ</i> đặc trưng của
 * endpoint, không phải danh sách đầy đủ mọi mã nó có thể trả.
 * <p>
 * <b>Một email đúng định dạng nhưng không ứng với tài khoản nào KHÔNG phải lỗi validate</b> — nó
 * trả 204 như mọi lần khác (§B.4 điều 5). Ràng buộc ở đây chỉ chặn thứ không phải địa chỉ email.
 */
@Data
public class ForgotPasswordRequest {

    /**
     * <b>Trần 160 khớp cột {@code user.email}</b>, không phải một con số tuỳ ý: một chuỗi dài hơn
     * không thể khớp tài khoản nào, nên chặn nó ở đây rẻ hơn là mang xuống tận câu truy vấn.
     */
    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 160, message = "Email không được vượt quá 160 ký tự.")
    private String email;
}
