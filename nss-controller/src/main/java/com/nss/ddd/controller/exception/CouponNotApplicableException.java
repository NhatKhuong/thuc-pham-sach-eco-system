package com.nss.ddd.controller.exception;

/**
 * Mã giảm giá có thật nhưng không dùng được cho đơn này — {@code GlobalExceptionHandler} dịch
 * thành <b>422</b>.
 * <p>
 * Phủ bốn tình huống: mã đã tắt ({@code isActive = false}), ngoài cửa sổ
 * {@code startsAt}–{@code endsAt}, đã chạm {@code usageLimit}, và {@code subtotal} chưa đạt
 * {@code minOrderValue}. Ba ca đầu là phần <b>mở rộng có chủ ý</b> ngoài hợp đồng gốc — §B.7 chỉ
 * nhắc 404 và 422-chưa-đủ-giá-trị-tối-thiểu — vì các cột đó đã có trong schema từ ticket 0004 và
 * để không cưỡng chế thì chúng là lời hứa suông (backlog 0014 §Contract 8).
 * <p>
 * <b>422 chứ không phải 400.</b> Request hợp lệ về cú pháp; thứ không hợp lệ là ngữ nghĩa nghiệp
 * vụ. Frontend phân biệt hai mã này: 400 đọc như "client gửi sai", 422 đọc như "hãy nói cho người
 * dùng biết vì sao".
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class CouponNotApplicableException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public CouponNotApplicableException(String message) {
        super(message);
    }
}
