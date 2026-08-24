package com.nss.ddd.application.service.auth.impl;

import com.nss.ddd.application.mapper.UserMapper;
import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.LogoutCommand;
import com.nss.ddd.application.model.command.RefreshCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.response.AuthMutationResponse;
import com.nss.ddd.application.model.response.AuthResponse;
import com.nss.ddd.application.service.auth.AuthAppService;
import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.AuthDomainService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
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

    private static final String MESSAGE_DUPLICATE_EMAIL =
            "Email này đã được sử dụng, vui lòng dùng email khác hoặc đăng nhập.";

    /**
     * <b>Một chuỗi duy nhất cho cả hai ca</b> "email không tồn tại" và "sai mật khẩu" — xem javadoc
     * của {@code AuthMutationResponse.CODE_INVALID_CREDENTIALS}.
     */
    private static final String MESSAGE_INVALID_CREDENTIALS = "Email hoặc mật khẩu không đúng.";

    private static final String MESSAGE_INVALID_REFRESH_TOKEN =
            "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.";

    private final AuthDomainService authDomainService;

    private final JwtEncoder jwtEncoder;

    private final Duration accessTokenTtl;

    private final Duration refreshTokenTtl;

    /**
     * Constructor injection viết tay thay vì {@code @RequiredArgsConstructor}.
     * <p>
     * Lý do phải viết ra: hai TTL đến từ cấu hình chứ không phải từ bean, và {@code @Value} chỉ
     * dùng được trên tham số constructor — Lombok không sao chép annotation sang tham số nó sinh
     * ra. Đặt {@code @Value} lên field thì thành field injection, thứ §5 cấm.
     *
     * @param authDomainService quy tắc nghiệp vụ về mật khẩu và refresh token
     * @param jwtEncoder bộ ký HMAC do {@code JwtConfig} dựng
     * @param accessTokenTtl thời hạn access token, dạng ISO-8601 ({@code PT30M})
     * @param refreshTokenTtl thời hạn refresh token, dạng ISO-8601 ({@code P14D})
     */
    public AuthAppServiceImpl(AuthDomainService authDomainService,
                              JwtEncoder jwtEncoder,
                              @Value("${nss.auth.access-token-ttl}") Duration accessTokenTtl,
                              @Value("${nss.auth.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.authDomainService = authDomainService;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
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

    // ========== HELPERS ==========

    /**
     * Dựng một phiên hoàn chỉnh: access token JWT mới cộng một refresh token mới.
     *
     * @param user chủ phiên, đã có id
     * @param roles mã vai trò đi vào claim {@code roles}
     * @return payload {@code AuthResponse} của bề mặt dây
     */
    private AuthResponse genSession(User user, List<String> roles) {
        RefreshToken refreshToken = authDomainService.issueRefreshToken(user, refreshTokenTtl);
        return new AuthResponse()
                .setUser(UserMapper.toResponse(user))
                .setToken(genAccessToken(user, roles))
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
     * @return chuỗi JWT đã ký
     */
    private String genAccessToken(User user, List<String> roles) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TOKEN_ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
