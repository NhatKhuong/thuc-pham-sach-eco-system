package com.nss.ddd.controller.exception;

/**
 * Không tìm thấy danh mục — {@code GlobalExceptionHandler} dịch thành <b>404</b> (API_CONTRACT §B.2).
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class CategoryNotFoundException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public CategoryNotFoundException(String message) {
        super(message);
    }
}
