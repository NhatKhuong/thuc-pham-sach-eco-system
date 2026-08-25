package com.nss.ddd.controller.exception;

/**
 * Dữ liệu đơn hàng đúng cú pháp nhưng sai quy tắc nghiệp vụ — {@code GlobalExceptionHandler} dịch
 * thành <b>422</b>.
 * <p>
 * Hiện phủ hai tình huống:
 * <ul>
 *   <li><b>{@code paymentMethod} không nằm trong bốn giá trị hợp lệ</b> ({@code cod},
 *       {@code bank_transfer}, {@code momo}, {@code vnpay} — §D #1). Ca này ra 422 <i>không kèm</i>
 *       map {@code errors}, khác với lỗi validate theo trường, vì bảng dịch giá trị sống ở
 *       {@code OrderMapper} chứ không ở một {@code @Pattern} trên DTO (§Contract 4).</li>
 *   <li><b>Token hợp lệ nhưng claim {@code sub} trỏ tới một tài khoản không còn tồn tại.</b> Đây là
 *       422 chứ không phải 401: chữ ký token vẫn đúng và phiên vẫn còn hạn, nên trả 401 sẽ khiến
 *       {@code client.ts} gọi {@code /auth/refresh}, đốt refresh token rồi đăng xuất người dùng —
 *       một vòng lặp không lối ra cho một tình huống mà việc đăng nhập lại giải quyết được.</li>
 * </ul>
 * <b>Cố ý tách khỏi {@link InvalidProductDataException}</b> dù cùng ra 422: hai kiểu đó nói về hai
 * aggregate khác nhau, và dùng chung một kiểu sẽ khiến một handler tương lai muốn xử lý riêng ca
 * đơn hàng không tách được nữa.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class InvalidOrderDataException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidOrderDataException(String message) {
        super(message);
    }
}
