package com.nss.ddd.controller.exception;

/**
 * Dữ liệu không qua được quy tắc nghiệp vụ — {@code GlobalExceptionHandler} dịch thành <b>422</b>.
 * <p>
 * Dùng cho các quy tắc mà bean validation không diễn đạt được gọn: cặp {@code salePrice} / {@code price},
 * và {@code categoryId} / {@code brandId} trỏ tới bản ghi không tồn tại.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class InvalidProductDataException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidProductDataException(String message) {
        super(message);
    }
}
