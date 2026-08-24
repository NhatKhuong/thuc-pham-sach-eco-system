package com.nss.ddd.controller.exception;

/**
 * Email đã có tài khoản khác giữ — {@code GlobalExceptionHandler} dịch thành <b>409</b> (§B.4).
 * <p>
 * Ràng buộc {@code uk_email} nằm trên toàn bảng {@code user}, nên cổng kiểm phải chạy trước
 * {@code INSERT}: để lỗi ràng buộc nổi lên từ tầng JDBC thì response là 500 với thông điệp kỹ thuật.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn ở form đăng ký (§A.3).
 */
public class DuplicateEmailException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public DuplicateEmailException(String message) {
        super(message);
    }
}
