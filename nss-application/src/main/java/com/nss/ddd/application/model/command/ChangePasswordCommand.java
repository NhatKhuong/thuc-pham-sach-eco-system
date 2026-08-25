package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh đổi mật khẩu — {@code PUT /api/auth/password}.
 * <p>
 * <b>{@code userId} và {@code sessionId} là tham số riêng, không đến từ body</b> — cả hai đọc từ
 * access token ({@code sub} và {@code sid}). Cùng kỷ luật đã đặt ở {@code LogoutCommand}: định danh
 * đi trong token, không đi trong thứ client tự khai.
 * <p>
 * <b>{@code sessionId} có thể {@code null}, và ca đó có ý nghĩa cụ thể.</b> Claim {@code sid} chỉ
 * ra đời từ ticket 0016; mọi access token cấp trước đó không mang nó. Khi rỗng, phép thu hồi phải
 * hỏng về <b>phía an toàn</b> — đá tất cả các phiên, kể cả phiên đang gọi — chứ không phải bỏ qua.
 * <p>
 * <b>Hai chuỗi mật khẩu ở đây là dữ liệu thô: không bao giờ đưa vào log</b> (coding-conventions §9).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordCommand {

    /** Chủ tài khoản, <b>lấy từ claim {@code sub}</b>. */
    private Long userId;

    /**
     * Id dòng {@code refresh_token} của phiên đang gọi, <b>lấy từ claim {@code sid}</b>.
     * {@code null} khi token được cấp trước ticket 0016, hoặc khi claim không đọc được thành số.
     */
    private Long sessionId;

    /** Mật khẩu hiện tại, dùng để đối chiếu <b>trước khi</b> ghi đè hash. */
    private String currentPassword;

    /** Mật khẩu mới dạng thô. */
    private String newPassword;
}
