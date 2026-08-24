package com.nss.ddd.controller.exception;

/**
 * Thông tin xác thực không dùng được — {@code GlobalExceptionHandler} dịch thành <b>401</b>.
 * <p>
 * Phủ cả hai đường: sai thông tin đăng nhập ({@code /auth/login}) và refresh token không còn dùng
 * được ({@code /auth/refresh}). Gộp làm một kiểu là có chủ ý — cả hai đều trả 401, và
 * {@code client.ts} phản ứng với 401 bằng đúng một hành vi.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3). Với đường đăng nhập, message
 * đó còn phải <b>giống hệt nhau</b> giữa ca "email không tồn tại" và ca "sai mật khẩu", nếu không
 * endpoint trở thành công cụ dò xem địa chỉ nào đã đăng ký.
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
