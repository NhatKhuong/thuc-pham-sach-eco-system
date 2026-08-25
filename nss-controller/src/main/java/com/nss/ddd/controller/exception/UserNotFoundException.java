package com.nss.ddd.controller.exception;

/**
 * Dòng {@code user} ứng với claim {@code sub} không còn tồn tại —
 * {@code GlobalExceptionHandler} dịch thành <b>404</b>.
 * <p>
 * <b>Trạng thái này hôm nay không thể xảy ra</b>: không có đường nào xoá tài khoản. Kiểu này tồn
 * tại vì một {@code sub} hợp lệ về chữ ký không đồng nghĩa với một bản ghi có thật — tin thẳng vào
 * con số trong token là cách để một ngày nào đó ghi được dữ liệu gắn với một tài khoản đã biến mất.
 * <p>
 * <b>404 cho {@code PUT /auth/password} là một suy diễn có chủ ý.</b> §B.4 chỉ liệt kê 401 và 422
 * cho endpoint đó; ticket 0016 chọn nhất quán với {@code /auth/me} thay vì chọn đúng nguyên văn hợp
 * đồng, vì trạng thái này không thể xảy ra hôm nay nên cái giá của việc chọn sai bằng không. Owner
 * phủ quyết được.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} (§A.3).
 */
public class UserNotFoundException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}
