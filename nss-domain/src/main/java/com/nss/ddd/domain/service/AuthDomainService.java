package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.entity.PasswordResetToken;
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
     * Tra tài khoản theo khoá chính — đường dùng khi định danh đến từ claim {@code sub} của JWT.
     * <p>
     * Tồn tại vì {@code PUT /auth/me} và {@code PUT /auth/password} chỉ được biết người gọi qua
     * token (§C.4.1). Một {@code sub} hợp lệ về chữ ký vẫn có thể trỏ tới một dòng không còn tồn
     * tại, nên bước tra lại này là chỗ duy nhất biến một con số trong token thành một bản ghi thật.
     *
     * @param id khoá chính, lấy từ claim {@code sub}
     * @return tài khoản, hoặc {@code null} khi id không khớp dòng nào
     */
    User findById(Long id);

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

    /**
     * Ghi hồ sơ đã sửa và đóng dấu {@code updatedAt} theo <b>giờ UTC</b>.
     * <p>
     * <b>Nhận vào một entity đã được áp bản vá, không phải một bản nháp.</b> Việc quyết định trường
     * nào bị ghi đè là của tầng application ({@code UserMapper.applyPatch}); việc của method này chỉ
     * là mốc thời gian và lệnh ghi — hai thứ mà cả aggregate này chỉ được phép sinh ra ở một chỗ.
     * <p>
     * <b>Cổng kiểm email trùng KHÔNG nằm ở đây.</b> Nó phải chạy <i>trước</i> khi entity bị sửa,
     * nên nó sống ở app service — xem {@code AuthAppServiceImpl.updateProfile}.
     *
     * @param user entity đã sửa, đang được transaction của tầng gọi quản lý
     * @return bản ghi sau khi ghi
     */
    User updateProfile(User user);

    /**
     * Đặt mật khẩu mới: băm chuỗi thô rồi đóng dấu {@code updatedAt} theo <b>giờ UTC</b>.
     * <p>
     * <b>Đối chiếu mật khẩu hiện tại KHÔNG nằm ở đây.</b> Nó phải chạy <i>trước</i> khi hash bị ghi
     * đè, vì entity đọc trong một transaction là entity được quản lý — sửa nó rồi trả về một giá
     * trị thất bại thì transaction <i>vẫn commit</i>. Thứ tự đó là của app service.
     *
     * @param user chủ tài khoản, đang được transaction của tầng gọi quản lý
     * @param rawNewPassword mật khẩu mới dạng thô; <b>không bao giờ được ghi xuống DB</b> và không
     *                       bao giờ được đưa vào log
     * @return bản ghi sau khi ghi
     */
    User changePassword(User user, String rawNewPassword);

    /**
     * Thu hồi mọi phiên còn sống của người dùng <b>trừ phiên đang gọi</b> — đường của
     * {@code PUT /auth/password}.
     *
     * @param userId chủ tài khoản, lấy từ claim {@code sub}
     * @param keepSessionId id dòng {@code refresh_token} của phiên đang gọi, lấy từ claim
     *                      {@code sid}; {@code null} thì <b>thu hồi tất cả</b> — token cấp trước
     *                      khi claim này ra đời phải hỏng về phía an toàn
     * @return số phiên vừa bị thu hồi; {@code 0} là kết quả hợp lệ (người dùng một thiết bị)
     */
    int revokeOtherSessions(Long userId, Long keepSessionId);

    /**
     * Thu hồi <b>MỌI</b> phiên còn sống của người dùng, không chừa dòng nào — đường của
     * {@code POST /auth/reset-password}.
     * <p>
     * <b>Đây là chỗ đường đặt lại mật khẩu cố ý đi khác {@link #revokeOtherSessions}</b>, và khác
     * biệt đó là nghiệp vụ chứ không phải sơ suất (backlog 0017 §Contract điều 5): ở
     * {@code PUT /auth/password} người dùng <i>đang đăng nhập</i> nên có đúng một phiên đáng giữ;
     * ở đây họ <i>không</i> đăng nhập, và giả định phải là tài khoản đã bị chiếm — phiên nào còn
     * sống cũng có thể là phiên của kẻ chiếm.
     * <p>
     * Dùng lại đúng phép {@code revokeAllOfUserExcept} của port: giá trị canh gác không khớp id nào
     * biến "trừ một dòng" thành "không trừ dòng nào". Xem {@code RefreshTokenRepositoryImpl}.
     *
     * @param userId chủ tài khoản
     * @return số phiên vừa bị thu hồi; {@code 0} là kết quả hợp lệ (không có phiên nào đang mở)
     */
    int revokeAllSessions(Long userId);

    /**
     * Phát một token đặt lại mật khẩu mới và ghi <b>hash</b> của nó xuống DB.
     * <p>
     * <b>Trả về chuỗi THÔ, và đó là giá trị duy nhất trong cả hệ thống còn giữ bí mật này.</b> Cột
     * {@code token_hash} chỉ giữ SHA-256; không có đường nào đọc ngược ra chuỗi thô. Vì vậy giá trị
     * trả về phải đi thẳng vào email và <b>không bao giờ</b> được đưa vào log, vào response, hay
     * vào một cột nào khác — xem javadoc của {@code PasswordResetToken}.
     *
     * @param user chủ tài khoản, đã có id
     * @param ttl thời hạn tính từ bây giờ
     * @return chuỗi token thô để đặt vào link trong email
     */
    String issuePasswordResetToken(User user, Duration ttl);

    /**
     * @param rawToken chuỗi token thô client gửi lên; được băm ở đây rồi mới đem đi tra
     * @return bản ghi kèm chủ sở hữu đã nạp sẵn, hoặc {@code null} khi không tồn tại / đã dùng /
     *         đã hết hạn — <b>ba ca gộp làm một</b>, đúng nếp {@link #findUsableRefreshToken}
     */
    PasswordResetToken findUsablePasswordResetToken(String rawToken);

    /**
     * Tiêu token đặt lại: đánh dấu đã dùng bằng UPDATE có điều kiện.
     * <p>
     * <b>Phải gọi trong cùng transaction với việc ghi mật khẩu mới</b>, và phải gọi <i>trước</i> —
     * giá trị trả về là thứ quyết định request này có được phép đổi mật khẩu hay không. Hai request
     * đồng thời cầm cùng một chuỗi thì đúng một cái nhận {@code true}.
     *
     * @param rawToken chuỗi token thô client gửi lên
     * @return true nếu có đúng một dòng vừa chuyển sang trạng thái đã dùng
     */
    boolean consumePasswordResetToken(String rawToken);
}
