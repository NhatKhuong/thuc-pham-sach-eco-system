package com.nss.ddd.controller.exception;

/**
 * Dữ liệu đánh giá vi phạm quy tắc nghiệp vụ — {@code GlobalExceptionHandler} dịch thành
 * <b>422</b>.
 * <p>
 * Hiện có đúng một ca dẫn tới đây: access token đúng chữ ký và còn hạn nhưng claim {@code sub} trỏ
 * tới một tài khoản không còn tồn tại. Cùng ca và cùng mã HTTP với
 * {@code OrderMutationResponse.CODE_INVALID_ORDER_DATA} ở {@code POST /orders}.
 * <p>
 * <b>422 chứ không phải 401.</b> 401 là tín hiệu "access token hỏng" mà {@code client.ts} phản ứng
 * bằng cách gọi {@code /auth/refresh} rồi đăng xuất — vô ích cho một tài khoản đã biến mất, và nó
 * biến một lỗi dữ liệu thành một vòng đăng xuất mà người dùng không hiểu vì sao.
 * <p>
 * <b>Cũng là 422 nhưng KHÔNG có map {@code errors}</b>, khác 422 của validate: không có ô nhập nào
 * để chỉ vào. Cùng quy ước phân biệt đã chốt ở {@link InvalidCurrentPasswordException}.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào
 * {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class InvalidReviewDataException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidReviewDataException(String message) {
        super(message);
    }
}
