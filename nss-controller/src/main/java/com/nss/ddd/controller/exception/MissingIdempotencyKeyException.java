package com.nss.ddd.controller.exception;

/**
 * Header {@code Idempotency-Key} bắt buộc nhưng vắng mặt hoặc rỗng — {@code GlobalExceptionHandler}
 * dịch thành <b>400</b> (backlog 0039 §Contract, {@code POST /orders/async}).
 * <p>
 * <b>Cố ý tự ném thay vì khai {@code @RequestHeader(required = true)} mặc định.</b>
 * {@code MissingRequestHeaderException} mặc định của Spring không đi qua bảng dịch nào ở đây và trả
 * một {@code ProblemDetail} tiếng Anh do {@code ProblemDetailsExceptionHandler} (order 0) dựng —
 * lệch quy ước "mọi {@code detail} phải tiếng Việt" (coding-conventions §1). Controller nhận header
 * qua {@code required = false} rồi tự kiểm và ném exception này để đi đúng qua
 * {@link GlobalExceptionHandler}.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class MissingIdempotencyKeyException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
