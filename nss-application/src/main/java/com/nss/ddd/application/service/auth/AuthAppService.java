package com.nss.ddd.application.service.auth;

import com.nss.ddd.application.model.command.ChangePasswordCommand;
import com.nss.ddd.application.model.command.ForgotPasswordCommand;
import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.LogoutCommand;
import com.nss.ddd.application.model.command.RefreshCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.command.ResetPasswordCommand;
import com.nss.ddd.application.model.command.UpdateProfileCommand;
import com.nss.ddd.application.model.response.AuthMutationResponse;
import com.nss.ddd.application.model.response.PasswordMutationResponse;
import com.nss.ddd.application.model.response.PasswordResetMutationResponse;
import com.nss.ddd.application.model.response.ProfileMutationResponse;

/**
 * Use case của vòng phiên xác thực — API_CONTRACT §B.4.
 * <p>
 * Tầng này điều phối: hỏi {@code AuthDomainService} (băm / đối chiếu mật khẩu, phát và thu hồi
 * refresh token), rồi đúc access token và lắp kết quả thành kiểu của bề mặt dây.
 * <p>
 * <b>Access token được đúc ở đây chứ không ở domain</b>: thuật toán ký, TTL và tên claim là khái
 * niệm của bề mặt dây, không phải quy tắc nghiệp vụ.
 */
public interface AuthAppService {

    /**
     * Đăng ký tài khoản mới và <b>đăng nhập luôn</b> — contract trả {@code AuthResponse} chứ không
     * trả 201 rỗng, nên người dùng không phải nhập lại mật khẩu ngay sau khi đăng ký.
     * <p>
     * Tài khoản mới nhận vai trò {@code CUSTOMER}.
     *
     * @param command lệnh đăng ký
     * @return phiên vừa cấp, hoặc {@link AuthMutationResponse#CODE_DUPLICATE_EMAIL}
     */
    AuthMutationResponse register(RegisterCommand command);

    /**
     * @param command lệnh đăng nhập
     * @return phiên vừa cấp, hoặc {@link AuthMutationResponse#CODE_INVALID_CREDENTIALS} — dùng
     *         chung cho cả email không tồn tại lẫn sai mật khẩu
     */
    AuthMutationResponse login(LoginCommand command);

    /**
     * Gia hạn phiên bằng cơ chế <b>xoay vòng</b> (ADR 0003): cấp cặp token mới và thu hồi refresh
     * token vừa dùng, cả hai trong <b>một</b> transaction.
     *
     * @param command lệnh gia hạn
     * @return phiên mới, hoặc {@link AuthMutationResponse#CODE_INVALID_REFRESH_TOKEN}
     */
    AuthMutationResponse refresh(RefreshCommand command);

    /**
     * Thu hồi refresh token của phiên đang đăng xuất (§B.4 #3).
     * <p>
     * <b>Kết quả không đổi mã HTTP</b>: contract trả 204 cho mọi trường hợp có access token hợp lệ.
     * Giá trị trả về chỉ để ghi log — trả 404 khi không tìm thấy dòng nào sẽ biến endpoint này
     * thành công cụ dò xem một chuỗi refresh token có tồn tại hay không.
     *
     * @param command lệnh đăng xuất; {@code userId} lấy từ JWT, không từ body (§C.2)
     * @return true nếu có đúng một dòng vừa bị thu hồi
     */
    boolean logout(LogoutCommand command);

    /**
     * Sửa hồ sơ của chính người đang đăng nhập (§B.4).
     * <p>
     * <b>Ngữ nghĩa vá từng phần:</b> trường {@code null} trong command nghĩa là giữ nguyên giá trị
     * cũ. Chuỗi rỗng đã bị chặn ở tầng validate và không tới được đây.
     * <p>
     * <b>Đổi email KHÔNG thu hồi phiên nào.</b> Access token cũ còn mang claim {@code email} cũ tối
     * đa 30 phút — vô hại hôm nay vì phân quyền đọc {@code roles} và định danh đọc {@code sub},
     * không ai đọc {@code email}. Điều này thành vấn đề thật ngay khi có thứ gì đó bắt đầu phân
     * quyền theo email.
     *
     * @param command lệnh sửa hồ sơ; {@code userId} lấy từ JWT, không từ body (§C.4.1)
     * @return hồ sơ sau khi ghi, hoặc {@link ProfileMutationResponse#CODE_USER_NOT_FOUND} /
     *         {@link ProfileMutationResponse#CODE_DUPLICATE_EMAIL}
     */
    ProfileMutationResponse updateProfile(UpdateProfileCommand command);

    /**
     * Đổi mật khẩu của chính người đang đăng nhập (§B.4).
     * <p>
     * <b>Thành công thì thu hồi mọi refresh token còn sống của người đó TRỪ phiên đang gọi</b> —
     * đổi mật khẩu phải đá được thiết bị khác ra mà không đá chính mình. Khi claim {@code sid} vắng
     * mặt (token cấp trước ticket 0016) thì thu hồi <b>tất cả</b>: ca thiếu thông tin phải hỏng về
     * phía an toàn.
     * <p>
     * <b>Access token của thiết bị bị đá vẫn dùng được tới {@code exp}</b> — tối đa 30 phút. Thu
     * hồi một refresh token không huỷ được một JWT đã ký; đó là đánh đổi của ADR 0003, không phải
     * một lỗ hổng của method này.
     *
     * @param command lệnh đổi mật khẩu; {@code userId} và {@code sessionId} lấy từ JWT
     * @return kết quả thành công, hoặc {@link PasswordMutationResponse#CODE_USER_NOT_FOUND} /
     *         {@link PasswordMutationResponse#CODE_INVALID_CURRENT_PASSWORD}
     */
    PasswordMutationResponse changePassword(ChangePasswordCommand command);

    /**
     * Nhận yêu cầu quên mật khẩu: phát token đặt lại và gửi link qua email (§B.4, ADR 0004).
     *
     * <h2>Toàn bộ method này chạy BẤT ĐỒNG BỘ, và lý do là bảo mật chứ không phải hiệu năng</h2>
     * Contract khai {@code 204} cho <i>mọi</i> trường hợp, kể cả email không ứng với tài khoản nào
     * (§B.4 điều 5) — trả 404 biến endpoint này thành công cụ dò xem địa chỉ nào đã đăng ký. Nhưng
     * <b>mã trạng thái giống nhau chưa đủ</b>: §Contract điều 1 của backlog 0017 nói rõ <i>thời gian
     * phản hồi cũng không được tố cáo sự khác biệt</i>.
     * <p>
     * <b>Đây là một con số đo được, không phải một lo ngại lý thuyết.</b> Ở bản dựng đầu của ticket
     * 0017, chỉ riêng việc gửi mail chạy {@code @Async} còn phần tra người dùng và ghi dòng token
     * vẫn nằm trên luồng request; đo 40 mẫu xen kẽ mỗi nhánh cho ra <b>median 25.2ms cho email có
     * thật so với 9.3ms cho email không tồn tại</b>. Khoảng cách đó lớn hơn nhiễu mạng thông thường
     * và lặp lại được, tức là một công cụ dò tài khoản hoàn chỉnh chỉ cần một đồng hồ bấm giờ —
     * đúng thứ điều 5 của hợp đồng sinh ra để chặn, chỉ đổi kênh đọc từ mã HTTP sang thời gian.
     * <p>
     * Vì vậy <b>mọi</b> việc bất đối xứng — tra email, sinh token, ghi dòng, gửi mail — đều nằm sau
     * ranh giới {@code @Async}. Luồng request làm đúng một lượng việc như nhau cho cả hai nhánh rồi
     * trả 204.
     * <p>
     * <b>Trả về {@code void}, và đó là hệ quả bắt buộc của {@code @Async}</b>: một method
     * {@code @Async} trả giá trị thường sẽ đưa về {@code null}/{@code false} một cách <i>im lặng</i>
     * qua proxy. Kết quả thật của luồng này nằm ở <b>log</b>, và đó cũng là nơi duy nhất nó có thể
     * nằm — xem javadoc của {@code MailAppService} về việc endpoint 204 không tự nói được rằng nó
     * hỏng.
     *
     * @param command lệnh quên mật khẩu
     */
    void forgotPassword(ForgotPasswordCommand command);

    /**
     * Đặt lại mật khẩu bằng token nhận qua email (ADR 0004).
     * <p>
     * <b>Endpoint này là SUY DIỄN của backend, không đọc ra từ nguồn</b> — §B.4 chỉ khai
     * {@code forgotPassword}. Tên endpoint, tên hai trường và mã lỗi đều do backlog 0017 chọn; đã
     * route lên backlog 0015. Ghi ở đây để nó không bị nhầm thành hợp đồng đã chốt.
     * <p>
     * <b>Ba việc phải nằm trong MỘT transaction</b>: tiêu token, ghi hash mật khẩu mới, thu hồi
     * phiên. Tách rời thì một lần lỗi giữa chừng để lại một tài khoản đã đổi mật khẩu mà token vẫn
     * dùng lại được, hoặc một token đã tiêu mà mật khẩu chưa đổi.
     * <p>
     * <b>Thành công thì thu hồi TẤT CẢ refresh token của tài khoản, không chừa dòng nào</b> — khác
     * {@link #changePassword} (giữ phiên hiện tại) vì ở đây <i>không có phiên nào đáng giữ</i>:
     * người dùng đang không đăng nhập, và giả định phải là tài khoản đã bị chiếm.
     *
     * @param command lệnh đặt lại mật khẩu
     * @return kết quả thành công, hoặc
     *         {@link PasswordResetMutationResponse#CODE_INVALID_RESET_TOKEN} — <b>một mã duy nhất
     *         cho cả ba ca</b> token không tồn tại / đã dùng / đã hết hạn
     */
    PasswordResetMutationResponse resetPassword(ResetPasswordCommand command);
}
