package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload trả về của {@code POST /api/auth/register} kể từ backlog 0037 — <b>KHÔNG còn</b>
 * {@code AuthResponse} (không {@code user}, không {@code token}, không {@code refreshToken}).
 * <p>
 * <b>Đây là breaking change đã được Owner duyệt tường minh</b> (backlog 0037 §Contract điều 1):
 * đăng ký không còn tự đăng nhập — tài khoản mới phải xác nhận email trước khi
 * {@code POST /api/auth/login} chấp nhận. DTO trần, không bọc {@code ResultMessage} (ADR 0001).
 * <p>
 * <b>Chỉ một trường, và nó là văn xuôi cho người dùng đọc — không phải mã lỗi.</b> Không có payload
 * nghiệp vụ nào khác để trả ở bước này (không phiên, không id), nên hình dạng đơn giản nhất đúng ý
 * ticket ("chỉ báo thành công / kiểm tra email") là một câu xác nhận tiếng Việt.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    /** Câu xác nhận tiếng Việt — ví dụ "Đăng ký thành công, vui lòng kiểm tra email để xác nhận tài khoản." */
    private String message;
}
