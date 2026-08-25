package com.nss.ddd.controller.exception;

/**
 * Sai mật khẩu hiện tại khi đổi mật khẩu — {@code GlobalExceptionHandler} dịch thành <b>422</b>.
 * <p>
 * <b>422, KHÔNG phải 401 — và đây là ranh giới có hậu quả thật, không phải một lựa chọn thẩm mỹ.</b>
 * 401 chỉ dành cho "thiếu / hỏng / hết hạn access token"; đó chính là tín hiệu {@code client.ts} dựa
 * vào để quyết định có tự gọi {@code /auth/refresh} hay không. Trả 401 ở đây khiến một người gõ
 * nhầm mật khẩu cũ bị <i>đốt mất một refresh token</i> rồi bị đá về trang đăng nhập — cùng loại lỗi
 * với bugs/0002.
 * <p>
 * <b>Phân biệt với 422 của validate bằng sự vắng mặt của khoá {@code errors}.</b> Lỗi validate
 * <i>có</i> map {@code errors} (tên trường → thông điệp); lỗi này thì <i>không</i>, vì nó không
 * thuộc về một ô nhập cụ thể nào mà là kết quả của một phép đối chiếu. Frontend dùng đúng dấu hiệu
 * đó để chọn hiển thị lỗi theo trường hay một thông báo chung.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> và <b>không được gợi ý luật độ
 * dài mật khẩu</b> — nó đi thẳng ra màn hình (§A.3).
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}
