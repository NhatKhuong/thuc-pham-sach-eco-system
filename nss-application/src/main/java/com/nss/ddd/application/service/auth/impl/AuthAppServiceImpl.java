package com.nss.ddd.application.service.auth.impl;

import com.nss.ddd.application.mapper.UserMapper;
import com.nss.ddd.application.model.command.ChangePasswordCommand;
import com.nss.ddd.application.model.command.ForgotPasswordCommand;
import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.LogoutCommand;
import com.nss.ddd.application.model.command.RefreshCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.command.ResetPasswordCommand;
import com.nss.ddd.application.model.command.UpdateProfileCommand;
import com.nss.ddd.application.model.response.AuthMutationResponse;
import com.nss.ddd.application.model.response.AuthResponse;
import com.nss.ddd.application.model.response.PasswordMutationResponse;
import com.nss.ddd.application.model.response.PasswordResetMutationResponse;
import com.nss.ddd.application.model.response.ProfileMutationResponse;
import com.nss.ddd.application.service.auth.AuthAppService;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.domain.model.entity.PasswordResetToken;
import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.AuthDomainService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hiện thực use case của vòng phiên xác thực.
 * <p>
 * Tầng này chỉ điều phối; quy tắc về mật khẩu và refresh token sống trong {@code AuthDomainService}.
 * <p>
 * <b>Ba ranh giới cần giữ khi sửa file này:</b>
 * <ul>
 *   <li>{@code register} / {@code refresh} / {@code logout} là write path nghiệp vụ nên mang
 *       {@code @Transactional(rollbackFor = Exception.class)}. Với {@code refresh}, transaction
 *       <i>là</i> yêu cầu chức năng chứ không phải thói quen: thu hồi dòng cũ và ghi dòng mới tách
 *       rời nhau thì một lần lỗi giữa chừng là người dùng mất phiên (ADR 0003).</li>
 *   <li>Thất bại nghiệp vụ trả về {@code AuthMutationResponse.failed(...)}, không ném exception —
 *       kiểu {@code *Exception} sống ở module controller (§3).</li>
 *   <li>Không log mật khẩu thô, không log chuỗi token. Log {@code userId} và {@code email} là đủ
 *       để lần ngược.</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthAppServiceImpl implements AuthAppService {

    /** Vai trò mặc định của tài khoản tự đăng ký — đã có sẵn trong bảng {@code role} từ 0006. */
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    /** Định danh người phát hành, đi vào claim {@code iss}. */
    private static final String TOKEN_ISSUER = "nss-api";

    private static final String CLAIM_ROLES = "roles";

    private static final String CLAIM_EMAIL = "email";

    /**
     * Tên claim mang id dòng {@code refresh_token} của phiên hiện tại.
     * <p>
     * <b>Đúc và đọc dưới dạng CHUỖI, không phải số.</b> {@code sub} đã là
     * {@code String.valueOf(user.getId())}, và ép kiểu một claim số đọc ngược ra
     * ({@code (Long) jwt.getClaim("sid")}) là chỗ {@code ClassCastException} nằm chờ — kiểu Java
     * thật của một số JSON tuỳ vào cách nó được khử. {@code getClaimAsString} trên claim chuỗi thì
     * không mơ hồ.
     */
    private static final String CLAIM_SESSION_ID = "sid";

    private static final String MESSAGE_DUPLICATE_EMAIL =
            "Email này đã được sử dụng, vui lòng dùng email khác hoặc đăng nhập.";

    /**
     * <b>Một chuỗi duy nhất cho cả hai ca</b> "email không tồn tại" và "sai mật khẩu" — xem javadoc
     * của {@code AuthMutationResponse.CODE_INVALID_CREDENTIALS}.
     */
    private static final String MESSAGE_INVALID_CREDENTIALS = "Email hoặc mật khẩu không đúng.";

    private static final String MESSAGE_INVALID_REFRESH_TOKEN =
            "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.";

    private static final String MESSAGE_USER_NOT_FOUND =
            "Không tìm thấy tài khoản, vui lòng đăng nhập lại.";

    /**
     * <b>Không nêu mật khẩu đúng là gì và không gợi ý độ dài.</b> Chuỗi này là {@code detail} của
     * một 422 mà frontend đổ thẳng ra màn hình.
     */
    private static final String MESSAGE_INVALID_CURRENT_PASSWORD =
            "Mật khẩu hiện tại không đúng.";

    /**
     * <b>Một chuỗi duy nhất cho cả BA ca</b> token không tồn tại / đã dùng / đã hết hạn — xem
     * javadoc của {@code PasswordResetMutationResponse.CODE_INVALID_RESET_TOKEN}.
     * <p>
     * Câu chữ cố ý gộp cả ba mà vẫn nói được việc phải làm tiếp: người dùng thật đọc nó sau khi bấm
     * một link cũ, và họ cần biết đường quay lại chứ không cần biết token của mình hỏng vì lý do gì.
     */
    private static final String MESSAGE_INVALID_RESET_TOKEN =
            "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn, vui lòng yêu cầu liên kết mới.";

    private final AuthDomainService authDomainService;

    private final MailAppService mailAppService;

    private final JwtEncoder jwtEncoder;

    private final Duration accessTokenTtl;

    private final Duration refreshTokenTtl;

    private final Duration passwordResetTokenTtl;

    /**
     * Constructor injection viết tay thay vì {@code @RequiredArgsConstructor}.
     * <p>
     * Lý do phải viết ra: hai TTL đến từ cấu hình chứ không phải từ bean, và {@code @Value} chỉ
     * dùng được trên tham số constructor — Lombok không sao chép annotation sang tham số nó sinh
     * ra. Đặt {@code @Value} lên field thì thành field injection, thứ §5 cấm.
     *
     * @param authDomainService quy tắc nghiệp vụ về mật khẩu và refresh token
     * @param mailAppService đường gửi mail; bean riêng chứ không phải method của chính class này —
     *                       {@code @Async} không hoạt động qua self-call
     * @param jwtEncoder bộ ký HMAC do {@code JwtConfig} dựng
     * @param accessTokenTtl thời hạn access token, dạng ISO-8601 ({@code PT30M})
     * @param refreshTokenTtl thời hạn refresh token, dạng ISO-8601 ({@code P14D})
     * @param passwordResetTokenTtl thời hạn token đặt lại mật khẩu, dạng ISO-8601 ({@code PT15M});
     *                              <b>ngắn hơn access token là có chủ ý</b> — một link nằm trong
     *                              hộp thư lâu hơn nhiều so với một token trong bộ nhớ trình duyệt
     * @throws IllegalStateException khi TTL token đặt lại không dương — fail lúc khởi động, đúng
     *                               tiền lệ {@code JwtConfig} với {@code jwt-secret}
     */
    public AuthAppServiceImpl(AuthDomainService authDomainService,
                              MailAppService mailAppService,
                              JwtEncoder jwtEncoder,
                              @Value("${nss.auth.access-token-ttl}") Duration accessTokenTtl,
                              @Value("${nss.auth.refresh-token-ttl}") Duration refreshTokenTtl,
                              @Value("${nss.auth.password-reset-token-ttl}") Duration passwordResetTokenTtl) {
        // TTL khong duong nghia la moi token dat lai chet ngay khi vua sinh ra: nguoi dung nhan mail,
        // bam link, va nhan 422 — mot he thong hong hoan toan ma khong endpoint nao bao loi. Fail o
        // day thi loi thuoc ve nguoi deploy, dung ky luat JwtConfig.genSecretKey().
        if (passwordResetTokenTtl == null || passwordResetTokenTtl.isZero()
                || passwordResetTokenTtl.isNegative()) {
            throw new IllegalStateException("nss.auth.password-reset-token-ttl must be a positive"
                    + " ISO-8601 duration; got: " + passwordResetTokenTtl);
        }
        this.authDomainService = authDomainService;
        this.mailAppService = mailAppService;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.passwordResetTokenTtl = passwordResetTokenTtl;
    }

    // ========== WRITE ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthMutationResponse register(RegisterCommand command) {
        // 1. Email la khoa duy nhat tren toan bang — kiem truoc de tra 409 thay vi loi rang buoc
        if (authDomainService.hasEmailTaken(command.getEmail())) {
            log.warn("register: duplicate email | email={}", command.getEmail());
            return AuthMutationResponse.failed(AuthMutationResponse.CODE_DUPLICATE_EMAIL,
                    MESSAGE_DUPLICATE_EMAIL);
        }
        // 2. Vai tro mac dinh phai co san trong DB. Thieu no la sai cau hinh he thong, khong phai
        //    loi cua nguoi dung — de UnexpectedExceptionHandler tra 500 thay vi bia ra mot loi 4xx
        Role customerRole = authDomainService.findRoleByCode(ROLE_CUSTOMER);
        if (customerRole == null) {
            log.error("register: role {} missing in database", ROLE_CUSTOMER);
            throw new IllegalStateException("Role " + ROLE_CUSTOMER + " is missing in database");
        }
        // 3. Bam mat khau, ghi user va dong user_role trong CUNG transaction cua method nay
        User saved = authDomainService.register(UserMapper.toEntity(command),
                command.getPassword(), customerRole);
        log.info("register: success | userId={} email={}", saved.getId(), saved.getEmail());
        return AuthMutationResponse.success(genSession(saved, List.of(customerRole.getCode())));
    }

    @Override
    public AuthMutationResponse login(LoginCommand command) {
        // 1. Hai ca that bai — email khong ton tai va sai mat khau — tra ve CUNG mot ket qua
        User user = authDomainService.findByEmail(command.getEmail());
        if (user == null || !authDomainService.hasMatchingPassword(command.getPassword(),
                user.getPasswordHash())) {
            log.warn("login: invalid credentials | email={}", command.getEmail());
            return AuthMutationResponse.failed(AuthMutationResponse.CODE_INVALID_CREDENTIALS,
                    MESSAGE_INVALID_CREDENTIALS);
        }
        // 2. Vai tro nap bang truy van tuong minh — open-in-view: false nen quan he LAZY khong doc
        //    duoc sau khi session dong
        List<String> roles = authDomainService.findRoleCodes(user.getId());
        log.info("login: success | userId={} roles={}", user.getId(), roles);
        return AuthMutationResponse.success(genSession(user, roles));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthMutationResponse refresh(RefreshCommand command) {
        // 1. Khong ton tai / da thu hoi / da het han — gop lam mot, client khong duoc biet ca nao
        RefreshToken usable = authDomainService.findUsableRefreshToken(command.getRefreshToken());
        if (usable == null) {
            log.warn("refresh: no usable refresh token");
            return AuthMutationResponse.failed(AuthMutationResponse.CODE_INVALID_REFRESH_TOKEN,
                    MESSAGE_INVALID_REFRESH_TOKEN);
        }
        // 2. XOAY VONG: thu hoi dong vua dung NGAY trong transaction nay. UPDATE co dieu kien nen
        //    hai request dung cung mot refresh token thi chi mot request thang
        if (!authDomainService.revokeRefreshToken(usable.getToken())) {
            log.warn("refresh: token was revoked concurrently | userId={}", usable.getUser().getId());
            return AuthMutationResponse.failed(AuthMutationResponse.CODE_INVALID_REFRESH_TOKEN,
                    MESSAGE_INVALID_REFRESH_TOKEN);
        }
        // 3. Chu so huu da duoc JOIN FETCH san cung cau truy van o buoc 1
        User user = usable.getUser();
        List<String> roles = authDomainService.findRoleCodes(user.getId());
        log.info("refresh: success | userId={}", user.getId());
        return AuthMutationResponse.success(genSession(user, roles));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean logout(LogoutCommand command) {
        boolean revoked = authDomainService.revokeRefreshTokenOfUser(command.getRefreshToken(),
                command.getUserId());
        if (revoked) {
            log.info("logout: success | userId={}", command.getUserId());
        } else {
            // Van tra 204: dong da bi thu hoi truoc do, hoac chuoi token khong thuoc ve nguoi nay.
            // Phan biet hai ca o day se bien logout thanh cong cu do token.
            log.warn("logout: nothing to revoke | userId={}", command.getUserId());
        }
        return revoked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileMutationResponse updateProfile(UpdateProfileCommand command) {
        // THU TU CUA METHOD NAY LA MOT PHAN CUA TINH DUNG DAN, khong phai thu tu tuy y.
        // Entity doc trong @Transactional la entity DUOC QUAN LY: sua no roi `return failed(...)`
        // thi transaction VAN COMMIT — tra ve mot gia tri khong phai la nem exception, nen khong co
        // rollback. Ket qua se la: response noi 409, database noi da doi.
        // Vi vay: doc -> kiem het moi cong that bai -> ROI MOI cham vao entity.

        // 1. Doc — chua sua gi
        User user = authDomainService.findById(command.getUserId());
        if (user == null) {
            log.warn("updateProfile: user not found | userId={}", command.getUserId());
            return ProfileMutationResponse.failed(ProfileMutationResponse.CODE_USER_NOT_FOUND,
                    MESSAGE_USER_NOT_FOUND);
        }
        // 2. Cong email trung — VAN chua sua gi.
        //    equalsIgnoreCase chu KHONG phai equals: bang dung utf8mb4_unicode_ci nen MySQL so sanh
        //    chuoi khong phan biet hoa thuong, con Java thi co. Dung equals thi nguoi doi
        //    demo@x.vn -> Demo@x.vn bi coi la "co doi", existsByEmail tim thay CHINH DONG CUA HO,
        //    va ho nhan 409 bao rang dia chi cua chinh minh da bi chiem.
        //    Va chi kiem KHI email that su doi — dung nep ProductAppServiceImpl.updateProduct voi slug.
        String newEmail = command.getEmail();
        if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail())
                && authDomainService.hasEmailTaken(newEmail)) {
            log.warn("updateProfile: duplicate email | userId={} email={}",
                    command.getUserId(), newEmail);
            return ProfileMutationResponse.failed(ProfileMutationResponse.CODE_DUPLICATE_EMAIL,
                    MESSAGE_DUPLICATE_EMAIL);
        }
        // 3. Moi bat dau sua. Sau dong nay khong con cong that bai nao duoc phep xuat hien.
        //    He qua kem theo: vi pham uk_email neu co se no LUC COMMIT, tuc sau khi than method da
        //    chay xong — khong handler nao trong method nay bat duoc.
        User saved = authDomainService.updateProfile(UserMapper.applyPatch(user, command));
        log.info("updateProfile: success | userId={}", saved.getId());
        return ProfileMutationResponse.success(UserMapper.toResponse(saved));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordMutationResponse changePassword(ChangePasswordCommand command) {
        // Cung mot luat thu tu nhu updateProfile: doi chieu mat khau cu TRUOC, dat hash moi SAU.
        // Dao lai thi mot lan go nham mat khau cu van ghi duoc hash moi xuong DB kem theo mot
        // response 422 — nguoi dung bi doi mat khau ma khong biet.

        // 1. Doc — chua sua gi
        User user = authDomainService.findById(command.getUserId());
        if (user == null) {
            log.warn("changePassword: user not found | userId={}", command.getUserId());
            return PasswordMutationResponse.failed(PasswordMutationResponse.CODE_USER_NOT_FOUND,
                    MESSAGE_USER_NOT_FOUND);
        }
        // 2. Cong doi chieu — VAN chua sua gi. 422 chu khong phai 401: xem javadoc cua
        //    PasswordMutationResponse.CODE_INVALID_CURRENT_PASSWORD.
        if (!authDomainService.hasMatchingPassword(command.getCurrentPassword(),
                user.getPasswordHash())) {
            log.warn("changePassword: current password mismatch | userId={}", command.getUserId());
            return PasswordMutationResponse.failed(
                    PasswordMutationResponse.CODE_INVALID_CURRENT_PASSWORD,
                    MESSAGE_INVALID_CURRENT_PASSWORD);
        }
        // 3. Moi dat hash moi
        authDomainService.changePassword(user, command.getNewPassword());
        // 4. Da cac thiet bi khac ra, giu lai phien dang goi. sessionId = null (token cap truoc khi
        //    claim `sid` ra doi) thi thu hoi TAT CA — ca thieu thong tin hong ve phia an toan.
        int revoked = authDomainService.revokeOtherSessions(command.getUserId(),
                command.getSessionId());
        log.info("changePassword: success | userId={} revokedSessions={} keptSession={}",
                command.getUserId(), revoked, command.getSessionId());
        return PasswordMutationResponse.success();
    }

    // ========== PASSWORD RESET ==========

    /**
     * <b>Toàn bộ thân method chạy sau ranh giới {@code @Async}</b> — lý do đầy đủ (kèm con số đo
     * được) nằm ở javadoc của {@link AuthAppService#forgotPassword}. Tóm tắt: luồng request phải
     * làm đúng một lượng việc như nhau cho email có thật và email không tồn tại, nếu không thì
     * chênh lệch thời gian trở thành đúng công cụ dò mà §B.4 điều 5 sinh ra để chặn.
     * <p>
     * <b>CỐ Ý không mang {@code @Transactional}</b>, và đừng "thống nhất" nó với các write path
     * khác của class này. Nó ghi đúng <i>một</i> dòng nên không có gì để nguyên tử hoá —
     * {@code login} ở trên cũng phát một refresh token mà không cần transaction bao ngoài. Cái được
     * thêm vào thì không có, còn cái mất thì có thật: {@link MailAppService} cũng chạy
     * {@code @Async}, nên một mail có thể rời đi <i>trước khi</i> transaction commit; nếu
     * transaction ấy rollback thì người dùng cầm một link trỏ tới một dòng token <b>chưa bao giờ
     * tồn tại</b>, và họ nhận 422 ở bước sau mà không ai hiểu vì sao.
     * <p>
     * <b>Bắt mọi ngoại lệ, vì không còn ai ở phía trên để bắt.</b> Method chạy trên luồng khác nên
     * một exception ném ra sẽ chỉ tới bộ xử lý mặc định của executor; và người dùng thì <i>đã</i>
     * nhận 204 rồi. Nuốt mà không log biến endpoint này thành một hộp đen hoàn toàn — đúng thứ §11
     * cấm và đúng thứ ADR 0004 cảnh báo.
     *
     * @param command lệnh quên mật khẩu
     */
    @Override
    @Async
    public void forgotPassword(ForgotPasswordCommand command) {
        try {
            // 1. Doc — email den tu body vi endpoint nay cong khai, khong co claim nao de doc.
            //    Buoc nay da nam sau ranh gioi @Async: mot cau SELECT trung va mot cau SELECT truot
            //    khong ton cung mot thoi gian, va tren luong request thi khac biet do do duoc.
            User user = authDomainService.findByEmail(command.getEmail());
            // 2. Khong co tai khoan: KHONG ghi dong nao, KHONG gui mail.
            //    Muc `warn` chu khong phai `info`: mot chuoi dong nay lien tiep chinh la dau hieu
            //    cua mot lan do dia chi, va do la thu nguoi van hanh can nhin thay.
            if (user == null) {
                log.warn("forgotPassword: no account for requested email | email={}",
                        command.getEmail());
                return;
            }
            // 3. Phat token. Chuoi tho chi ton tai o bien nay va o email — DB chi giu hash.
            String rawToken = authDomainService.issuePasswordResetToken(user, passwordResetTokenTtl);
            // 4. Gui mail. Ket qua that xuat hien o log cua MailAppServiceImpl, khong o day.
            mailAppService.sendPasswordResetMail(user.getEmail(), rawToken);
            log.info("forgotPassword: reset token issued and mail queued | userId={}", user.getId());
        } catch (Exception e) {
            // Nguoi dung DA nhan 204 tu lau. Dong log nay la tin hieu duy nhat con lai.
            log.error("forgotPassword: failed to issue reset token | email={}",
                    command.getEmail(), e);
        }
    }

    /**
     * <b>THỨ TỰ CỦA METHOD NÀY LÀ MỘT PHẦN CỦA TÍNH ĐÚNG ĐẮN</b>, cùng một luật đã viết ở
     * {@link #changePassword}: mọi cổng thất bại phải đóng lại <i>trước</i> khi entity bị chạm vào.
     * Entity đọc trong {@code @Transactional} là entity được quản lý, nên sửa nó rồi
     * {@code return failed(...)} thì transaction <b>vẫn commit</b> — response nói 422 còn database
     * nói đã đổi.
     * <p>
     * Ở đây cổng ấy là <b>phép tiêu token</b>, và nó phải là một UPDATE có điều kiện chứ không phải
     * một câu {@code if} trên kết quả đọc. Đọc-rồi-ghi là một cuộc đua: hai request cầm cùng một
     * chuỗi cùng đọc thấy dòng còn sống, cả hai cùng ghi, và một token đổi được <b>hai</b> mật khẩu.
     *
     * @param command lệnh đặt lại mật khẩu
     * @return kết quả thành công, hoặc mã token không hợp lệ
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetMutationResponse resetPassword(ResetPasswordCommand command) {
        // 1. Doc — chua sua gi. Ba ca that bai (khong ton tai / da dung / da het han) gop lam mot.
        PasswordResetToken usable =
                authDomainService.findUsablePasswordResetToken(command.getToken());
        if (usable == null) {
            log.warn("resetPassword: no usable reset token");
            return PasswordResetMutationResponse.failed(
                    PasswordResetMutationResponse.CODE_INVALID_RESET_TOKEN,
                    MESSAGE_INVALID_RESET_TOKEN);
        }
        // 2. CONG: tieu token bang UPDATE co dieu kien, NGAY trong transaction nay. Thua cuoc dua
        //    thi ra dung mot ket qua voi buoc 1 — client khong phan biet duoc hai ca.
        if (!authDomainService.consumePasswordResetToken(command.getToken())) {
            log.warn("resetPassword: token was consumed concurrently | userId={}",
                    usable.getUser().getId());
            return PasswordResetMutationResponse.failed(
                    PasswordResetMutationResponse.CODE_INVALID_RESET_TOKEN,
                    MESSAGE_INVALID_RESET_TOKEN);
        }
        // 3. Moi bat dau sua. Sau dong nay khong con cong that bai nao duoc phep xuat hien.
        //    Chu so huu da duoc JOIN FETCH san cung cau truy van o buoc 1.
        User user = usable.getUser();
        authDomainService.changePassword(user, command.getNewPassword());
        // 4. Thu hoi TAT CA phien, khong chua dong nao — khac changePassword (giu phien hien tai).
        //    Nguoi dung dang KHONG dang nhap, nen gia dinh phai la tai khoan da bi chiem: phien nao
        //    con song cung co the la phien cua ke chiem (backlog 0017 §Contract dieu 5).
        int revoked = authDomainService.revokeAllSessions(user.getId());
        log.info("resetPassword: success | userId={} revokedSessions={}", user.getId(), revoked);
        return PasswordResetMutationResponse.success();
    }

    // ========== HELPERS ==========

    /**
     * Dựng một phiên hoàn chỉnh: access token JWT mới cộng một refresh token mới.
     * <p>
     * <b>THỨ TỰ HAI DÒNG DƯỚI ĐÂY LÀ LOAD-BEARING — đừng "dọn dẹp" nó.</b> Dòng
     * {@code refresh_token} phải ra đời <i>trước</i> khi access token được ký, vì id của chính dòng
     * đó là nội dung claim {@code sid} ({@code RefreshTokenRepository.save} trả về bản ghi đã có
     * id). Đảo lại — ký token trước rồi mới phát refresh token — thì {@code sid} thành {@code null}
     * một cách <i>im lặng</i>: đăng nhập vẫn 200, mọi test tính năng vẫn xanh, chỉ có điều mỗi lần
     * đổi mật khẩu sau đó sẽ đá luôn cả phiên hiện tại của người dùng.
     * <p>
     * Đây cũng là <b>nơi duy nhất</b> gọi {@link #genAccessToken}, nên cả ba đường cấp phiên
     * ({@code register} / {@code login} / {@code refresh}) nhận {@code sid} mà không phải sửa một
     * call-site nào.
     *
     * @param user chủ phiên, đã có id
     * @param roles mã vai trò đi vào claim {@code roles}
     * @return payload {@code AuthResponse} của bề mặt dây
     */
    private AuthResponse genSession(User user, List<String> roles) {
        RefreshToken refreshToken = authDomainService.issueRefreshToken(user, refreshTokenTtl);
        return new AuthResponse()
                .setUser(UserMapper.toResponse(user))
                .setToken(genAccessToken(user, roles, refreshToken.getId()))
                .setRefreshToken(refreshToken.getToken());
    }

    /**
     * Đúc access token JWT ký HMAC (ADR 0003).
     * <p>
     * {@code JwtClaimsSet.builder()} là builder <b>của Spring Security</b>, không phải Lombok
     * {@code @Builder} — thứ bị cấm ở §5.
     * <p>
     * Mốc thời gian cắt về giây: {@code exp} và {@code iat} của JWT là số giây kể từ epoch, phần lẻ
     * dưới giây bị mất khi mã hoá. Cắt sẵn để giá trị trong bộ nhớ và giá trị trong token là một.
     *
     * @param user chủ token
     * @param roles mã vai trò
     * @param sessionId id dòng {@code refresh_token} của phiên này — nội dung claim {@code sid},
     *                  <b>đúc dưới dạng chuỗi</b>
     * @return chuỗi JWT đã ký
     */
    private String genAccessToken(User user, List<String> roles, Long sessionId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roles);
        // Bo qua claim thay vi duc chuoi "null": phia doc coi sid vang mat la "thu hoi tat ca", nen
        // mot chuoi "null" khong parse duoc cung ra cung ket qua — nhung mot claim vang mat thi
        // trung thuc, con mot claim mang chu "null" thi trong nhu du lieu that.
        if (sessionId != null) {
            claims.claim(CLAIM_SESSION_ID, String.valueOf(sessionId));
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
