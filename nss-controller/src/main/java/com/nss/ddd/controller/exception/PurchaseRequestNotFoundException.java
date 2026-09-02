package com.nss.ddd.controller.exception;

/**
 * Không tìm thấy yêu cầu mua hàng theo {@code requestId} — {@code GlobalExceptionHandler} dịch
 * thành <b>404</b> (backlog 0039 §Contract, {@code GET /orders/requests/{requestId}}).
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class PurchaseRequestNotFoundException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public PurchaseRequestNotFoundException(String message) {
        super(message);
    }
}
