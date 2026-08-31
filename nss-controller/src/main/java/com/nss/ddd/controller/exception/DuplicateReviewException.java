package com.nss.ddd.controller.exception;

/**
 * Tài khoản đã đánh giá sản phẩm này rồi — {@code GlobalExceptionHandler} dịch thành <b>409</b>.
 * <p>
 * <b>409 chứ không phải 422</b>, và sự phân biệt đó là contract: đây là <i>xung đột trạng thái</i>
 * (ràng buộc {@code uk_review_product_user} của ADR 0008), không phải lỗi ô nhập. Response vì vậy
 * mang {@code detail} tiếng Việt và <b>KHÔNG có map {@code errors}</b> — frontend phân biệt hai
 * loại lỗi bằng đúng sự có mặt của khoá đó (§A.3), không bằng cách đoán theo mã HTTP. Cùng quy ước
 * với {@link DuplicateSlugException} (backlog 0018) và {@link InvalidCurrentPasswordException}
 * (backlog 0016).
 * <p>
 * <b>Exception này chỉ ra đời từ 0 dòng bị ảnh hưởng của {@code INSERT IGNORE}</b>, tức từ chính
 * ràng buộc của DB — không phải từ một phép {@code SELECT} chạy trước. Đọc-rồi-ghi là cơ chế đã
 * sinh ra {@code bugs/0004}.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class DuplicateReviewException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public DuplicateReviewException(String message) {
        super(message);
    }
}
