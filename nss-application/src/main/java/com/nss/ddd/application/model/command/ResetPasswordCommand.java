package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh đặt lại mật khẩu bằng token — {@code POST /api/auth/reset-password}.
 * <p>
 * <b>Không có {@code userId}, và không được phép có.</b> Endpoint này công khai: người gọi đang
 * <i>không</i> đăng nhập nên không có claim {@code sub} nào để đọc. Chủ tài khoản được suy ra từ
 * chính dòng token — đó là toàn bộ lý do bảng {@code password_reset_token} mang khoá ngoại tới
 * {@code user}. Nhận {@code userId} từ body ở đây sẽ cho phép bất kỳ ai đổi mật khẩu của bất kỳ ai
 * chỉ bằng một token hợp lệ của chính mình.
 * <p>
 * <b>Cả hai trường là dữ liệu thô: không bao giờ đưa vào log</b> (coding-conventions §9). Với
 * {@code token} thì lý do mạnh hơn cả mật khẩu — DB chỉ giữ hash, nên một dòng log là <i>nơi duy
 * nhất</i> trong hệ thống chuỗi thô còn tồn tại sau khi email đã gửi đi.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordCommand {

    /** Chuỗi token thô lấy từ link trong email. */
    private String token;

    /** Mật khẩu mới dạng thô; chịu cùng ràng buộc độ dài như {@code register} và {@code changePassword}. */
    private String newPassword;
}
