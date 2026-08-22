package com.nss.ddd.controller.exception;

/**
 * Slug đã có sản phẩm khác giữ — {@code GlobalExceptionHandler} dịch thành <b>409</b>.
 * <p>
 * Ràng buộc {@code uk_slug} nằm trên toàn bảng, nên bản ghi đã xoá mềm vẫn giữ chỗ của slug.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class DuplicateSlugException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public DuplicateSlugException(String message) {
        super(message);
    }
}
