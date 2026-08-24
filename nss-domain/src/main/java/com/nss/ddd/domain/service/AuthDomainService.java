package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;

import java.time.Duration;
import java.util.List;

/**
 * Domain service của vòng phiên xác thực — nơi ở của quy tắc nghiệp vụ về mật khẩu và refresh token.
 * <p>
 * Chỉ biết port ({@code UserRepository}, {@code UserRoleRepository},
 * {@code RefreshTokenRepository}) và {@code PasswordEncoder}; không biết JWT, không biết HTTP.
 * <b>Access token không được đúc ở đây</b>: nó là khái niệm của bề mặt dây (thuật toán ký, TTL,
 * tên claim), nên nó sống ở tầng application. Thứ sống ở đây là những gì đúng bất kể token được
 * đóng gói bằng công nghệ nào: mật khẩu băm thế nào, refresh token còn dùng được hay không.
 * <p>
 * <b>Thất bại nghiệp vụ ở đây là giá trị trả về, không phải exception</b> (coding-conventions §11
 * Pattern A): {@code null} / {@code false} thay cho "không tồn tại" và "không hợp lệ". Việc dịch
 * chúng thành mã HTTP là của tầng controller.
 */
public interface AuthDomainService {

    /**
     * @param email email cần kiểm
     * @return true nếu email đã có tài khoản giữ — cổng chặn trước {@code INSERT} để trả 409 thay
     *         vì lỗi ràng buộc {@code uk_email}
     */
    boolean hasEmailTaken(String email);

    /**
     * @param email email đăng nhập
     * @return tài khoản, hoặc {@code null} khi email chưa được đăng ký
     */
    User findByEmail(String email);

    /**
     * @param code mã vai trò UPPER_SNAKE
     * @return vai trò, hoặc {@code null} khi mã không tồn tại
     */
    Role findRoleByCode(String code);

    /**
     * Tạo tài khoản mới: băm mật khẩu, đóng dấu {@code createdAt} / {@code updatedAt} theo
     * <b>giờ UTC</b>, rồi gán vai trò trong cùng transaction của tầng gọi.
     *
     * @param draft bản nháp dựng từ command — chỉ có {@code fullName}, {@code email}, {@code phone}
     * @param rawPassword mật khẩu thô client gửi lên; <b>không bao giờ được ghi xuống DB</b>
     * @param role vai trò mặc định của tài khoản mới
     * @return tài khoản đã ghi, đã có id
     */
    User register(User draft, String rawPassword, Role role);

    /**
     * Đối chiếu mật khẩu thô với hash đã lưu.
     * <p>
     * <b>Thuật toán bị pin bởi dữ liệu seed, không phải bởi lựa chọn hôm nay</b> (ADR 0003): hash
     * đã commit ở ticket 0006 là bcrypt {@code $2a$10$}. Đổi thuật toán hay strength làm hai tài
     * khoản seed không đăng nhập được nữa.
     *
     * @param rawPassword mật khẩu thô
     * @param passwordHash hash đã lưu; {@code null} coi như không khớp
     * @return true nếu khớp
     */
    boolean hasMatchingPassword(String rawPassword, String passwordHash);

    /**
     * @param userId khóa chính của người dùng
     * @return mã vai trò của người dùng — nội dung claim {@code roles} của access token
     */
    List<String> findRoleCodes(Long userId);

    /**
     * Phát một refresh token mới cho người dùng.
     *
     * @param user chủ sở hữu, đã có id
     * @param ttl thời hạn tính từ bây giờ
     * @return bản ghi đã ghi, mang chuỗi token để trả cho client
     */
    RefreshToken issueRefreshToken(User user, Duration ttl);

    /**
     * @param token chuỗi refresh token client gửi lên
     * @return bản ghi kèm chủ sở hữu đã nạp sẵn, hoặc {@code null} khi không tồn tại / đã bị thu
     *         hồi / đã hết hạn — <b>ba ca gộp làm một</b>, vì client không được biết ca nào
     */
    RefreshToken findUsableRefreshToken(String token);

    /**
     * Thu hồi refresh token — đường của {@code refresh} khi xoay vòng.
     *
     * @param token chuỗi refresh token
     * @return true nếu có đúng một dòng vừa chuyển sang đã thu hồi
     */
    boolean revokeRefreshToken(String token);

    /**
     * Thu hồi refresh token <b>của đúng người đang đăng nhập</b> — đường của {@code logout}.
     *
     * @param token chuỗi refresh token
     * @param userId chủ sở hữu, lấy từ JWT (§C.2)
     * @return true nếu có đúng một dòng vừa chuyển sang đã thu hồi
     */
    boolean revokeRefreshTokenOfUser(String token, Long userId);
}
