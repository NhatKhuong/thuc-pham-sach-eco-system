package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.TextNormalizer;
import com.nss.ddd.domain.model.entity.PasswordResetToken;
import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.model.entity.UserRole;
import com.nss.ddd.domain.repository.PasswordResetTokenRepository;
import com.nss.ddd.domain.repository.RefreshTokenRepository;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.repository.UserRoleRepository;
import com.nss.ddd.domain.service.AuthDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * Hiện thực domain service của vòng phiên xác thực.
 * <p>
 * Phụ thuộc là ba port cộng {@code PasswordEncoder} — {@code spring-security-crypto} là thư viện
 * mã hoá thuần, không kéo theo servlet hay filter, nên nó không phá bất biến "domain không phụ
 * thuộc module nào" (maven-enforcer chỉ chặn {@code com.nss:*}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthDomainServiceImpl implements AuthDomainService {

    /**
     * 32 byte ngẫu nhiên — 256 bit entropy, Base64 URL không đệm ra 43 ký tự, thừa chỗ trong cột
     * {@code varchar(512)}. Không dùng {@code UUID.randomUUID()} nối chuỗi: refresh token là bí mật
     * dài hạn nên nó phải đến từ nguồn ngẫu nhiên mã hoá.
     */
    private static final int REFRESH_TOKEN_BYTES = 32;

    /** Dùng chung: khởi tạo {@code SecureRandom} tốn kém và bản thân nó đã thread-safe. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * Hàm băm của cột {@code password_reset_token.token_hash}.
     * <p>
     * <b>SHA-256 chứ không phải bcrypt, và lý do phải viết ra vì nó đi ngược trực giác "mật khẩu thì
     * dùng bcrypt".</b> Thứ bcrypt bảo vệ là bí mật <i>entropy thấp</i> do người đặt: nó cố ý chậm để
     * một cuộc dò từ điển trở nên đắt. Chuỗi ở đây là {@value #RESET_TOKEN_BYTES} byte ngẫu nhiên mã
     * hoá — không có từ điển nào để dò, nên cái giá của bcrypt mua về đúng con số không. Đổi lại,
     * salt riêng từng dòng của bcrypt làm cột này <b>không tra ngược được</b>: một câu
     * {@code WHERE token_hash = :hash} sẽ không bao giờ khớp, và đường tra buộc phải quét cả bảng
     * rồi {@code matches} từng dòng.
     */
    private static final String TOKEN_HASH_ALGORITHM = "SHA-256";

    /**
     * 32 byte ngẫu nhiên cho token đặt lại — cùng mức entropy với refresh token, và cùng lý do:
     * đây là bí mật cho phép chiếm tài khoản, nên nó phải đến từ nguồn ngẫu nhiên mã hoá.
     */
    private static final int RESET_TOKEN_BYTES = 32;

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private final PasswordEncoder passwordEncoder;

    // ========== READ ==========

    @Override
    public boolean hasEmailTaken(String email) {
        return email != null && userRepository.existsByEmail(email);
    }

    @Override
    public User findByEmail(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public Role findRoleByCode(String code) {
        if (code == null) {
            return null;
        }
        return userRoleRepository.findRoleByCode(code).orElse(null);
    }

    @Override
    public List<String> findRoleCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleRepository.findRoleCodesByUserId(userId);
    }

    // ========== PASSWORD ==========

    @Override
    public boolean hasMatchingPassword(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    // ========== WRITE ==========

    @Override
    public User register(User draft, String rawPassword, Role role) {
        // 1. Thoi diem: now(ZoneOffset.UTC) va cat ve MICROS — cung ly do da viet o
        //    ProductDomainServiceImpl.genUtcNow(): now() lay dong ho may, lech 7 tieng o VN va
        //    khong co gi bao loi; cot la datetime(6) nen phan le nano se lech giua RAM va DB.
        LocalDateTime now = genUtcNow();
        // 2. Bam mat khau — mat khau tho khong bao gio di xuong DB.
        //    full_name_normalized la cot phai sinh, dien bang CUNG mot ham voi product.name_normalized
        //    (coding-conventions §18): thieu no thi tai khoan moi khong bao gio tim ra o
        //    GET /admin/customers, va khong co gi bao loi.
        draft.setPasswordHash(passwordEncoder.encode(rawPassword))
                .setFullNameNormalized(TextNormalizer.genNormalized(draft.getFullName()))
                .setCreatedAt(now)
                .setUpdatedAt(now);
        User saved = userRepository.save(draft);
        // 3. Gan vai tro trong CUNG transaction cua tang goi: tai khoan khong co vai tro la tai
        //    khoan khong lam duoc gi, nen hai write nay khong duoc phep tach roi
        userRoleRepository.save(new UserRole()
                .setUser(saved)
                .setRole(role)
                .setCreatedAt(now));
        log.info("register: saved user | userId={} roleCode={}", saved.getId(), role.getCode());
        return saved;
    }

    @Override
    public RefreshToken issueRefreshToken(User user, Duration ttl) {
        LocalDateTime now = genUtcNow();
        RefreshToken refreshToken = new RefreshToken()
                .setUser(user)
                .setToken(genRefreshTokenValue())
                .setExpiresAt(now.plus(ttl))
                .setIsRevoked(Boolean.FALSE)
                .setCreatedAt(now);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("issueRefreshToken: issued | userId={} expiresAt={}", user.getId(), saved.getExpiresAt());
        return saved;
    }

    @Override
    public RefreshToken findUsableRefreshToken(String token) {
        if (token == null) {
            return null;
        }
        return refreshTokenRepository.findUsableByToken(token, genUtcNow()).orElse(null);
    }

    @Override
    public boolean revokeRefreshToken(String token) {
        boolean revoked = token != null && refreshTokenRepository.revokeByToken(token);
        if (!revoked) {
            log.warn("revokeRefreshToken: no live row matched");
        }
        return revoked;
    }

    @Override
    public boolean revokeRefreshTokenOfUser(String token, Long userId) {
        boolean revoked = token != null && userId != null
                && refreshTokenRepository.revokeByTokenAndUserId(token, userId);
        if (!revoked) {
            log.warn("revokeRefreshTokenOfUser: no live row matched | userId={}", userId);
        }
        return revoked;
    }

    @Override
    public User updateProfile(User user) {
        // Dong dau thoi gian, sinh lai cot phai sinh, roi ghi. Ban va da duoc ap o tang application,
        // va cong email trung da chay XONG truoc khi entity bi cham vao — xem
        // AuthAppServiceImpl.updateProfile.
        //
        // full_name_normalized phai sinh lai o CA duong nay chu khong chi luc dang ky: doi ho ten
        // ma khong doi cot chuan hoa thi bang khach hang van tim ra ten CU va khong bao gio tim ra
        // ten MOI — mot ket qua sai trong y het mot ket qua dung.
        user.setFullNameNormalized(TextNormalizer.genNormalized(user.getFullName()))
                .setUpdatedAt(genUtcNow());
        User saved = userRepository.save(user);
        log.info("updateProfile: saved user | userId={}", saved.getId());
        return saved;
    }

    @Override
    public User changePassword(User user, String rawNewPassword) {
        // Mat khau tho khong bao gio di xuong DB va khong bao gio vao log (§9).
        user.setPasswordHash(passwordEncoder.encode(rawNewPassword))
                .setUpdatedAt(genUtcNow());
        User saved = userRepository.save(user);
        log.info("changePassword: password hash replaced | userId={}", saved.getId());
        return saved;
    }

    @Override
    public int revokeOtherSessions(Long userId, Long keepSessionId) {
        if (userId == null) {
            return 0;
        }
        // keepSessionId = null di thang xuong adapter, noi no duoc chuan hoa thanh gia tri canh gac.
        // KHONG chan null o day: chan o day se bien "token cu khong co sid" thanh "khong thu hoi gi",
        // dung huong hong nguy hiem ma ticket cam.
        int revoked = refreshTokenRepository.revokeAllOfUserExcept(userId, keepSessionId);
        log.info("revokeOtherSessions: revoked | userId={} count={} keptSession={}",
                userId, revoked, keepSessionId);
        return revoked;
    }

    @Override
    public int revokeAllSessions(Long userId) {
        if (userId == null) {
            return 0;
        }
        // keepId = null di thang xuong adapter, noi no duoc chuan hoa thanh gia tri canh gac (-1).
        // Cot id la AUTO_INCREMENT nen khong dong nao mang gia tri am => `rt.id <> -1` dung voi MOI
        // dong => thu hoi TAT CA, ke ca phien dang goi (backlog 0017 §Contract dieu 5).
        int revoked = refreshTokenRepository.revokeAllOfUserExcept(userId, null);
        log.info("revokeAllSessions: revoked every live session | userId={} count={}", userId, revoked);
        return revoked;
    }

    // ========== PASSWORD RESET TOKEN ==========

    @Override
    public String issuePasswordResetToken(User user, Duration ttl) {
        // 1. Sinh chuoi tho — gia tri nay ton tai trong bo nho DUNG mot lan va di thang vao email
        String rawToken = genResetTokenValue();
        LocalDateTime now = genUtcNow();
        // 2. Chi HASH di xuong DB. Khong log rawToken o bat ky muc nao (§9 + javadoc PasswordResetToken)
        PasswordResetToken saved = passwordResetTokenRepository.save(new PasswordResetToken()
                .setUser(user)
                .setTokenHash(genTokenHash(rawToken))
                .setExpiresAt(now.plus(ttl))
                .setIsUsed(Boolean.FALSE)
                .setCreatedAt(now));
        log.info("issuePasswordResetToken: issued | userId={} tokenId={} expiresAt={}",
                user.getId(), saved.getId(), saved.getExpiresAt());
        return rawToken;
    }

    @Override
    public PasswordResetToken findUsablePasswordResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return passwordResetTokenRepository
                .findUsableByTokenHash(genTokenHash(rawToken), genUtcNow())
                .orElse(null);
    }

    @Override
    public boolean consumePasswordResetToken(String rawToken) {
        boolean consumed = rawToken != null && !rawToken.isBlank()
                && passwordResetTokenRepository.markUsed(genTokenHash(rawToken), genUtcNow());
        if (!consumed) {
            // Khong log chuoi token va khong log userId: ba ca that bai gop lam mot o be mat day thi
            // log cung phai giu nguyen tinh chat do (cung ky luat voi handleInvalidCredentials).
            log.warn("consumePasswordResetToken: no usable row matched");
        }
        return consumed;
    }

    // ========== HELPERS ==========

    /**
     * Mốc thời gian chuẩn của aggregate này — giờ UTC, cắt về đúng độ chính xác {@code datetime(6)}
     * của cột. Lý do đầy đủ nằm ở {@code ProductDomainServiceImpl.genUtcNow()}.
     *
     * @return giờ UTC hiện tại
     */
    private LocalDateTime genUtcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Sinh chuỗi refresh token.
     * <p>
     * Base64 <b>URL-safe</b> chứ không phải Base64 chuẩn: chuỗi này đi trong JSON body hôm nay
     * nhưng {@code +} và {@code /} là thứ sẽ hỏng lặng lẽ ngay khi có ai đó đặt nó lên query string.
     *
     * @return chuỗi ngẫu nhiên 43 ký tự
     */
    private String genRefreshTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /**
     * Sinh chuỗi token đặt lại mật khẩu.
     * <p>
     * Base64 <b>URL-safe</b> là bắt buộc ở đây chứ không chỉ là thói quen như với refresh token:
     * chuỗi này sống trong một link email dưới dạng query parameter, và {@code +} trên query string
     * được giải mã thành dấu cách — token sẽ hỏng ở đúng ca thường gặp nhất, khi người dùng bấm vào
     * link.
     *
     * @return chuỗi ngẫu nhiên 43 ký tự
     */
    private String genResetTokenValue() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /**
     * Băm chuỗi token thô thành giá trị của cột {@code token_hash}.
     * <p>
     * <b>Đây là hàm một chiều duy nhất đứng giữa một lần rò đọc DB và quyền chiếm tài khoản</b> —
     * lý do đầy đủ nằm ở javadoc của {@code PasswordResetToken}. Vì sao SHA-256 chứ không phải
     * bcrypt: xem {@link #TOKEN_HASH_ALGORITHM}.
     * <p>
     * {@code MessageDigest} <b>không</b> thread-safe nên phải lấy thể hiện mới mỗi lần gọi; đừng
     * "tối ưu" nó thành một hằng dùng chung như {@code SECURE_RANDOM}.
     *
     * @param rawToken chuỗi token thô, không rỗng
     * @return SHA-256 dạng hex thường, đúng 64 ký tự
     */
    private String genTokenHash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance(TOKEN_HASH_ALGORITHM)
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 la thuat toan BAT BUOC co mat trong moi ban Java SE — nhanh nay khong the xay
            // ra tren mot JVM hop le. Nem IllegalStateException thay vi nuot: mot he thong khong bam
            // duoc token thi khong duoc phep chay tiep va cap ra nhung dong khong tra nguoc duoc.
            log.error("genTokenHash: {} is unavailable on this JVM", TOKEN_HASH_ALGORITHM, e);
            throw new IllegalStateException(TOKEN_HASH_ALGORITHM + " is unavailable on this JVM", e);
        }
    }
}
