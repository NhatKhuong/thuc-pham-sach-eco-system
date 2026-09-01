package com.nss.ddd.controller.exception;

/**
 * Thông tin đăng nhập đúng, nhưng tài khoản chưa xác nhận email — {@code GlobalExceptionHandler}
 * dịch thành <b>403</b> (backlog 0037).
 * <p>
 * <b>403, KHÔNG phải 401 — và lý do khác với mọi lỗi 401 khác trong dự án.</b> 401 ở
 * {@code /auth/login} nghĩa là "sai thông tin đăng nhập" ({@link InvalidCredentialsException}); ở
 * đây thông tin đăng nhập <i>đúng hoàn toàn</i>, thứ còn thiếu là một bước xác nhận. HTTP 403 đúng
 * ngữ nghĩa "đã xác thực được danh tính, nhưng chưa được phép" — khác 401 "chưa xác thực được".
 * Chọn 403 thay vì 401 còn tránh đè lên cơ chế tự refresh của {@code client.ts} khi gặp 401
 * (API_CONTRACT §A.2): một 401 ở đây sẽ khiến frontend nghĩ token vừa hết hạn và gọi
 * {@code /auth/refresh} với một phiên chưa từng tồn tại, trong khi đây là một lần <i>đăng nhập</i>
 * chứ không phải một request tới tài nguyên đã bảo vệ.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3), nói rõ việc phải làm tiếp (kiểm
 * tra email / bấm gửi lại) chứ không mơ hồ như một lỗi "sai mật khẩu".
 */
public class EmailNotVerifiedException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
