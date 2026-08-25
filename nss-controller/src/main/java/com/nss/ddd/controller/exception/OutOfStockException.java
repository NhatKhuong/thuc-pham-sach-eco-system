package com.nss.ddd.controller.exception;

/**
 * Một dòng hàng không mua được lúc đặt đơn — {@code GlobalExceptionHandler} dịch thành <b>409</b>
 * (API_CONTRACT §B.6 khai đúng chữ "409 hết hàng").
 * <p>
 * Phủ ba tình huống, gộp có chủ ý vì cả ba dẫn tới cùng một hành động của người dùng — bỏ hoặc giảm
 * món đó trong giỏ: tồn kho không còn đủ, sản phẩm không tồn tại, và sản phẩm đã bị gỡ khỏi cửa
 * hàng. Sự khác nhau nằm trong {@code detail}, chỗ người dùng thật sự đọc. Đây cũng đúng quy ước mà
 * {@code POST /cart/validate} đã dùng cho {@code out_of_stock}.
 * <p>
 * <b>409 chứ không phải 422.</b> Request hợp lệ hoàn toàn — cả về cú pháp lẫn nghiệp vụ — tại thời
 * điểm khách bấm nút; thứ đã đổi là <i>trạng thái của tài nguyên</i>, và ai đó vừa mua mất món cuối
 * cùng. 409 nói đúng chuyện đó, còn 422 sẽ đọc như "dữ liệu bạn gửi sai".
 * <p>
 * <b>Kèm theo exception này luôn là một rollback.</b> Tồn kho được trừ bằng conditional UPDATE
 * (§Contract 8), nên một đơn hỏng ở giữa phải hoàn lại phần đã trừ — nếu không thì hàng biến mất
 * khỏi kho mà không có đơn nào tương ứng.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class OutOfStockException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối, có nêu tên món hàng
     */
    public OutOfStockException(String message) {
        super(message);
    }
}
