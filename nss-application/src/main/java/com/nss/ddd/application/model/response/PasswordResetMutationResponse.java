package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của lệnh đặt lại mật khẩu bằng token ({@code POST /auth/reset-password}).
 * <p>
 * <b>Không có payload, nên thành công phải được khai bằng một cờ tường minh</b> — cùng lý do đã
 * viết ở {@code PasswordMutationResponse}: contract trả {@code 204} với thân rỗng, nên không có
 * đối tượng nào dùng làm dấu hiệu "đã xong".
 * <p>
 * <b>Tập mã ở đây RỜI với cả {@code AuthMutationResponse} lẫn {@code PasswordMutationResponse}</b>,
 * và sự tách bạch đó có hậu quả thật: đường dịch của {@code AuthMutationResponse} rơi về 401 ở
 * nhánh {@code default}, mà 401 trên endpoint này là sai — người gọi <i>đang không đăng nhập</i>,
 * nên 401 không nói được điều gì có nghĩa và {@code client.ts} sẽ phản ứng bằng cách gọi
 * {@code /auth/refresh} với một phiên không tồn tại.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetMutationResponse {

    /**
     * Token không dùng được — <b>một mã duy nhất cho BA ca</b>: không tồn tại, đã dùng, đã hết hạn.
     * <p>
     * Gộp ba ca là chủ ý, đúng nếp {@code AuthMutationResponse.CODE_INVALID_REFRESH_TOKEN} đang làm
     * với ba ca của refresh token (backlog 0017 §Contract điều 2). Phân biệt chúng sẽ nói cho người
     * cầm một chuỗi bịa biết chuỗi đó <i>có tồn tại</i> hay không, và nói cho người cầm một chuỗi
     * đã tiêu biết rằng nó <i>từng</i> hợp lệ — cả hai đều là thông tin chỉ có ích cho kẻ tấn công.
     * <p>
     * Ba ca cũng phải dùng <b>cùng một chuỗi {@code message}</b>, không chỉ cùng mã.
     */
    public static final String CODE_INVALID_RESET_TOKEN = "INVALID_RESET_TOKEN";

    /** Đã đặt lại xong; {@code false} khi thất bại. */
    private boolean success;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @return kết quả thành công
     */
    public static PasswordResetMutationResponse success() {
        return new PasswordResetMutationResponse().setSuccess(true);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static PasswordResetMutationResponse failed(String code, String message) {
        return new PasswordResetMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
