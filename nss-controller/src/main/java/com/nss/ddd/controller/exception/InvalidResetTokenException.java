package com.nss.ddd.controller.exception;

/**
 * Token đặt lại mật khẩu không dùng được — {@code GlobalExceptionHandler} dịch thành <b>422</b>.
 * <p>
 * <b>Một exception cho BA ca</b>: token không tồn tại, đã dùng, đã hết hạn. Gộp là chủ ý, đúng nếp
 * {@code refresh} đang làm với ba ca của refresh token (backlog 0017 §Contract điều 2). Phân biệt
 * chúng sẽ nói cho người cầm một chuỗi bịa biết chuỗi đó <i>có tồn tại</i> hay không.
 * <p>
 * <b>422, KHÔNG phải 401 — và ở đây lý do khác với {@link InvalidCurrentPasswordException}.</b> Ở
 * endpoint đó, 401 sai vì nó khiến {@code client.ts} đốt mất một refresh token. Ở đây thì 401 sai
 * vì nó <i>vô nghĩa</i>: người gọi đang <b>không đăng nhập</b> — họ vừa bấm một link trong email —
 * nên không có phiên nào để hết hạn và không có gì để gia hạn. Một 401 ở đây sẽ khiến frontend chạy
 * đúng nhánh xử lý duy nhất nó có cho 401: gọi {@code /auth/refresh} với một phiên không tồn tại,
 * rồi đá người dùng về trang đăng nhập — trong lúc họ đang cố lấy lại chính tài khoản đó.
 * <p>
 * <b>Phân biệt với 422 của validate bằng sự vắng mặt của khoá {@code errors}</b>, đúng quy ước đã
 * chốt ở backlog 0016: lỗi validate <i>có</i> map {@code errors}; lỗi này thì <i>không</i>, vì nó
 * là kết quả của một phép tra chứ không thuộc về một ô nhập nào.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> và phải <b>giống hệt nhau cho cả
 * ba ca</b> — nó đi thẳng ra màn hình (§A.3), và một câu chữ khác nhau là cùng một rò rỉ, chỉ đổi
 * nơi đọc.
 */
public class InvalidResetTokenException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidResetTokenException(String message) {
        super(message);
    }
}
