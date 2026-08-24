package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.model.entity.UserRole;
import com.nss.ddd.domain.repository.RefreshTokenRepository;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.repository.UserRoleRepository;
import com.nss.ddd.domain.service.AuthDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    private final RefreshTokenRepository refreshTokenRepository;

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
        // 2. Bam mat khau — mat khau tho khong bao gio di xuong DB
        draft.setPasswordHash(passwordEncoder.encode(rawPassword))
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
}
