package com.nss.ddd.controller.exception;

/**
 * Không tìm thấy đơn hàng theo mã — {@code GlobalExceptionHandler} dịch thành <b>404</b>
 * (API_CONTRACT §B.6).
 * <p>
 * <b>Chỉ dùng cho {@code GET /orders/{code}}, và ở đó 404 là câu trả lời đúng cho mọi mã không tra
 * ra đơn.</b> Endpoint này công khai có chủ ý — nó là lối duy nhất để khách vãng lai xem lại đơn của
 * mình — nên nó không phân biệt "mã không tồn tại" với "mã của người khác": không có phép kiểm
 * quyền sở hữu nào ở đây để mà thất bại. Rủi ro đoán mã đã được ghi nhận và <b>cố ý hoãn</b>
 * (backlog 0014 §Contract 6, {@code decisions/CANDIDATES.md}).
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class OrderNotFoundException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public OrderNotFoundException(String message) {
        super(message);
    }
}
