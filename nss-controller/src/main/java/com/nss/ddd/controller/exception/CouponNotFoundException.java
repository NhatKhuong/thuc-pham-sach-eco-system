package com.nss.ddd.controller.exception;

/**
 * Không tìm thấy mã giảm giá — {@code GlobalExceptionHandler} dịch thành <b>404</b>.
 * <p>
 * Chỉ dùng cho ca "không có dòng nào mang mã này". Mã <i>có thật</i> nhưng đã tắt / hết hạn / hết
 * lượt / chưa đạt giá trị tối thiểu thuộc về {@link CouponNotApplicableException} và trả <b>422</b>
 * — hai câu trả lời khác nhau cho hai tình huống khác nhau, đúng như §B.7 phân biệt.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class CouponNotFoundException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public CouponNotFoundException(String message) {
        super(message);
    }
}
