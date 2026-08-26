package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/auth/register}.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b> — {@code javax.validation} bị cấm
 * (coding-conventions §7, §17). Thông điệp validation viết <b>tiếng Anh</b> theo §1, giữ đúng như
 * ticket 0008 đã làm; chuỗi tiếng Việt mà người dùng cuối đọc là {@code detail} của
 * {@code ProblemDetail}, do {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Danh sách trường ở đây chính là cổng chặn.</b> {@code id}, {@code avatar}, {@code createdAt}
 * và mọi thứ liên quan tới vai trò cố ý không có mặt: Spring Boot tắt
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} nên client gửi chúng lên thì Jackson bỏ qua trong im lặng —
 * nghĩa là không ai tự cấp cho mình vai trò {@code ADMIN} bằng một trường thừa trong body.
 * <p>
 * Giới hạn độ dài khớp cột trong DB ({@code full_name} 128, {@code email} 160, {@code phone} 20),
 * để dữ liệu quá dài trả 422 kèm tên trường thay vì lỗi cắt chuỗi ở tầng JDBC.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập họ và tên.")
    @Size(max = 128, message = "Họ và tên không được vượt quá 128 ký tự.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 160, message = "Email không được vượt quá 160 ký tự.")
    private String email;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự.")
    private String phone;

    /**
     * Trần 72 ký tự là <b>giới hạn của thuật toán</b>, không phải con số tuỳ ý: bcrypt chỉ băm 72
     * byte đầu tiên và <i>im lặng</i> bỏ phần còn lại, nghĩa là hai mật khẩu dài khác nhau vẫn đăng
     * nhập được cho nhau. Chặn ở đây để điều đó không xảy ra.
     */
    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự.")
    private String password;
}
