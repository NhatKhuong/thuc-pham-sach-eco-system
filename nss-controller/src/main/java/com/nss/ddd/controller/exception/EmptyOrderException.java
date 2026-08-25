package com.nss.ddd.controller.exception;

/**
 * Đặt hàng với giỏ không có dòng nào — {@code GlobalExceptionHandler} dịch thành <b>400</b>
 * (API_CONTRACT §B.6 khai đúng chữ "400 giỏ trống").
 * <p>
 * <b>400 chứ không phải 422, và khác biệt đó không phải chuyện thẩm mỹ.</b> Mọi lỗi
 * {@code jakarta.validation} trong dự án này ra 422 kèm map {@code errors} theo từng ô nhập; giỏ
 * trống thì không có ô nhập nào để chỉ vào — nó là một yêu cầu vô nghĩa ở mức tổng thể. Đó cũng là
 * lý do {@code CreateOrderRequest.items} khai {@code @NotNull} chứ không {@code @NotEmpty}: một
 * {@code @NotEmpty} sẽ trả đúng thông điệp nhưng sai mã, và frontend phân biệt hai mã đó.
 * <p>
 * <b>Chỉ {@code POST /orders} chặn giỏ trống.</b> {@code POST /cart/validate} cố ý trả 200 kèm mảng
 * rỗng cho cùng tình huống: câu hỏi "giỏ này có vấn đề gì không" vẫn hợp lệ khi giỏ chưa có gì, còn
 * "hãy đặt đơn hàng này" thì không.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class EmptyOrderException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public EmptyOrderException(String message) {
        super(message);
    }
}
