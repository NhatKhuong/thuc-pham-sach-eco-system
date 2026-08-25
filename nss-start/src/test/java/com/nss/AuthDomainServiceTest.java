package com.nss;

import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.model.entity.UserRole;
import com.nss.ddd.domain.repository.PasswordResetTokenRepository;
import com.nss.ddd.domain.repository.RefreshTokenRepository;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.repository.UserRoleRepository;
import com.nss.ddd.domain.service.impl.AuthDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm quy tắc nghiệp vụ của {@code AuthDomainServiceImpl} bằng port giả — không Spring context,
 * không database.
 * <p>
 * <b>{@code PasswordEncoder} ở đây là bản thật, không phải mock</b>, và đó là điểm chính của file
 * này: thứ cần chứng minh là bcrypt trong code khớp với những hash đã <i>commit</i> ở ticket 0006.
 * Một mock sẽ trả {@code true} cho mọi thứ và test vẫn xanh trong khi tài khoản seed không đăng
 * nhập được — đúng loại lỗi mà lane test mặc định phải bắt trước khi ai đó phải dựng server lên.
 */
@ExtendWith(MockitoExtension.class)
class AuthDomainServiceTest {

    /** Hash bcrypt của mật khẩu {@code 123456}, chép nguyên văn từ {@code 02-seed-data.sql}. */
    private static final String SEEDED_DEMO_HASH =
            "$2a$10$.dr31WdMiDT/t/i5.2U.8uaILF5ttzbLjzwhUmqSL74cQQMfM48Sy";

    /** Hash bcrypt của mật khẩu {@code admin123}, chép nguyên văn từ {@code 02-seed-data.sql}. */
    private static final String SEEDED_ADMIN_HASH =
            "$2a$10$2v0zKpUNHhcns/FatbcjZu2WjTHSffOIsIBo4yKzKUsy6iX7lxyYy";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Dựng service với {@code BCryptPasswordEncoder} thật.
     *
     * @return service sẵn sàng dùng
     */
    private AuthDomainServiceImpl genService() {
        return new AuthDomainServiceImpl(userRepository, userRoleRepository, refreshTokenRepository,
                passwordResetTokenRepository, new BCryptPasswordEncoder());
    }

    @Test
    @DisplayName("Encoder khop hash bcrypt da seed o ticket 0006 — ca hai tai khoan")
    void encoderMatchesSeededHashes() {
        AuthDomainServiceImpl service = genService();

        assertTrue(service.hasMatchingPassword("123456", SEEDED_DEMO_HASH),
                "demo@nongsansach.vn / 123456 phai dang nhap duoc bang hash da seed");
        assertTrue(service.hasMatchingPassword("admin123", SEEDED_ADMIN_HASH),
                "admin@nongsansach.vn / admin123 phai dang nhap duoc bang hash da seed");
    }

    @Test
    @DisplayName("Sai mat khau va tham so null deu tra false, khong nem exception")
    void wrongPasswordAndNullsReturnFalse() {
        AuthDomainServiceImpl service = genService();

        assertFalse(service.hasMatchingPassword("sai-mat-khau", SEEDED_DEMO_HASH));
        assertFalse(service.hasMatchingPassword(null, SEEDED_DEMO_HASH));
        assertFalse(service.hasMatchingPassword("123456", null));
    }

    @Test
    @DisplayName("register bam mat khau, khong bao gio luu chuoi tho")
    void registerHashesPassword() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        service.register(new User().setEmail("moi@nongsansach.vn"), "mat-khau-tho", genCustomerRole());
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertNotEquals("mat-khau-tho", saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2a$10$"),
                "Hash phai dung dinh dang bcrypt strength 10 nhu du lieu seed: " + saved.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("mat-khau-tho", saved.getPasswordHash()));
    }

    @Test
    @DisplayName("register dat createdAt theo gio UTC chu khong phai gio may")
    void registerStampsUtcNotLocalTime() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();
        LocalDateTime beforeUtc = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        User saved = service.register(new User().setEmail("moi@nongsansach.vn"), "abc123",
                genCustomerRole());

        LocalDateTime afterUtc = LocalDateTime.now(ZoneOffset.UTC);
        assertFalse(saved.getCreatedAt().isBefore(beforeUtc), "createdAt som hon moc UTC truoc khi goi");
        assertFalse(saved.getCreatedAt().isAfter(afterUtc), "createdAt muon hon moc UTC sau khi goi");
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Test
    @DisplayName("register gan dung vai tro duoc truyen vao, trong cung luot goi")
    void registerAssignsGivenRole() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();
        Role customer = genCustomerRole();

        service.register(new User().setEmail("moi@nongsansach.vn"), "abc123", customer);

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertEquals("CUSTOMER", captor.getValue().getRole().getCode());
    }

    @Test
    @DisplayName("issueRefreshToken sinh chuoi ngau nhien khac nhau moi lan va dat is_revoked = false")
    void issueRefreshTokenGeneratesFreshValue() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();
        User user = new User().setId(1L);

        RefreshToken first = service.issueRefreshToken(user, Duration.ofDays(14));
        RefreshToken second = service.issueRefreshToken(user, Duration.ofDays(14));

        assertNotEquals(first.getToken(), second.getToken(), "Hai refresh token khong duoc trung nhau");
        assertFalse(first.getIsRevoked());
        assertTrue(first.getExpiresAt().isAfter(first.getCreatedAt()));
        assertTrue(first.getToken().length() <= 512, "Chuoi token phai vua cot varchar(512)");
    }

    @Test
    @DisplayName("expiresAt = createdAt + TTL, tinh theo gio UTC")
    void issueRefreshTokenAppliesTtl() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();

        RefreshToken issued = service.issueRefreshToken(new User().setId(1L), Duration.ofDays(14));

        assertEquals(issued.getCreatedAt().plusDays(14), issued.getExpiresAt());
    }

    // ========== HO SO VA DOI MAT KHAU (backlog 0016) ==========

    @Test
    @DisplayName("updateProfile dong dau updatedAt theo gio UTC va khong cham truong nao khac")
    void updateProfileStampsUpdatedAtInUtc() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        User user = new User()
                .setId(1L)
                .setFullName("Tên đã sửa")
                .setEmail("demo@nongsansach.vn")
                .setPhone("0900000000")
                .setPasswordHash(SEEDED_DEMO_HASH)
                .setCreatedAt(before.minusDays(3));

        User saved = service.updateProfile(user);

        assertNotNull(saved.getUpdatedAt());
        assertFalse(saved.getUpdatedAt().isBefore(before), "updatedAt phai la gio UTC hien tai");
        // Cac truong khac giu nguyen — domain service chi dong dau va ghi
        assertEquals("Tên đã sửa", saved.getFullName());
        assertEquals(SEEDED_DEMO_HASH, saved.getPasswordHash());
        assertEquals(before.minusDays(3), saved.getCreatedAt());
    }

    /**
     * Mật khẩu thô <b>không bao giờ</b> đi xuống DB. Encoder ở file này là bản thật, nên ca này
     * chứng minh cả hai điều cùng lúc: hash được thay, và hash mới đúng là bcrypt của chuỗi mới.
     */
    @Test
    @DisplayName("changePassword bam mat khau moi, khong bao gio luu chuoi tho")
    void changePasswordHashesTheNewPassword() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthDomainServiceImpl service = genService();
        User user = new User().setId(1L).setPasswordHash(SEEDED_DEMO_HASH);

        User saved = service.changePassword(user, "mat-khau-moi");

        assertNotEquals(SEEDED_DEMO_HASH, saved.getPasswordHash(), "Hash cu phai bi thay");
        assertNotEquals("mat-khau-moi", saved.getPasswordHash(), "Khong duoc luu chuoi tho");
        assertTrue(saved.getPasswordHash().startsWith("$2a$10$"), "Van phai la bcrypt strength 10");
        assertEquals(60, saved.getPasswordHash().length());
        assertTrue(service.hasMatchingPassword("mat-khau-moi", saved.getPasswordHash()));
        assertFalse(service.hasMatchingPassword("123456", saved.getPasswordHash()));
        assertNotNull(saved.getUpdatedAt(), "Doi mat khau cung phai dong dau updatedAt");
    }

    /**
     * <b>{@code null} phải đi xuyên qua tầng này chứ không bị chặn lại.</b> Chặn ở đây sẽ biến ca
     * "token cũ không có {@code sid}" thành "không thu hồi gì" — đúng hướng hỏng nguy hiểm mà ticket
     * cấm. Việc chuẩn hoá thành giá trị canh gác là của adapter.
     */
    @Test
    @DisplayName("revokeOtherSessions truyen sid xuong nguyen ven, ke ca khi no la null")
    void revokeOtherSessionsPassesSessionIdThrough() {
        AuthDomainServiceImpl service = genService();
        when(refreshTokenRepository.revokeAllOfUserExcept(1L, null)).thenReturn(3);
        when(refreshTokenRepository.revokeAllOfUserExcept(1L, 42L)).thenReturn(2);

        assertEquals(3, service.revokeOtherSessions(1L, null));
        assertEquals(2, service.revokeOtherSessions(1L, 42L));
        verify(refreshTokenRepository).revokeAllOfUserExcept(1L, null);
        verify(refreshTokenRepository).revokeAllOfUserExcept(1L, 42L);
    }

    @Test
    @DisplayName("findById va revokeOtherSessions chiu duoc userId null ma khong nem exception")
    void nullUserIdIsHandled() {
        AuthDomainServiceImpl service = genService();

        assertNull(service.findById(null));
        assertEquals(0, service.revokeOtherSessions(null, 42L));
    }

    /**
     * @return vai trò {@code CUSTOMER} như đã seed ở ticket 0006
     */
    private Role genCustomerRole() {
        return new Role().setId(2L).setCode("CUSTOMER").setName("Khách hàng");
    }
}
