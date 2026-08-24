package com.nss.ddd.application.service.auth;

import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.LogoutCommand;
import com.nss.ddd.application.model.command.RefreshCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.response.AuthMutationResponse;

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
}
