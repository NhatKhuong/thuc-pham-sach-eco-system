package com.nss.ddd.controller.exception;

/**
 * Không tìm thấy sản phẩm — {@code GlobalExceptionHandler} dịch thành <b>404</b>.
 * <p>
 * Sản phẩm đã bị xoá mềm cũng rơi vào đây: với người gọi, nó không tồn tại.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public ProductNotFoundException(String message) {
        super(message);
    }
}
