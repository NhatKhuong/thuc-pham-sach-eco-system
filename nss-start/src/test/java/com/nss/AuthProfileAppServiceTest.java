package com.nss;

import com.nss.ddd.application.model.command.ChangePasswordCommand;
import com.nss.ddd.application.model.command.UpdateProfileCommand;
import com.nss.ddd.application.model.response.PasswordMutationResponse;
import com.nss.ddd.application.model.response.ProfileMutationResponse;
import com.nss.ddd.application.service.auth.impl.AuthAppServiceImpl;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.AuthDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm <b>thứ tự</b> của hai use case hồ sơ — không Spring context, không database.
 * <p>
 * <b>File này tồn tại vì loại lỗi nó bắt không hiện ra ở mã HTTP.</b> Entity đọc trong một
 * {@code @Transactional} là entity <i>được quản lý</i>: sửa nó rồi {@code return failed(...)} thì
 * transaction <b>vẫn commit</b> — trả về một giá trị không phải là ném exception, nên không có
 * rollback. Kết quả là một response nói 409 trong khi database đã đổi. Một test chỉ khẳng định
 * "mã trả về là DUPLICATE_EMAIL" sẽ xanh trong đúng tình huống đó.
 * <p>
 * Vì vậy mỗi ca thất bại ở đây kiểm <b>hai</b> điều: mã lỗi đúng, <i>và</i> entity chưa hề bị chạm
 * vào — bằng cách khẳng định lệnh ghi của domain service <b>không được gọi</b>, và bằng cách đọc
 * lại chính các trường của entity.
 */
@ExtendWith(MockitoExtension.class)
class AuthProfileAppServiceTest {

    private static final Long USER_ID = 7L;

    private static final String OLD_EMAIL = "demo@nongsansach.vn";

    private static final String OLD_FULL_NAME = "Nguyen Van Demo";

    private static final String OLD_PHONE = "0900000000";

    private static final String OLD_HASH = "$2a$10$hash-cu";

    @Mock
    private AuthDomainService authDomainService;

    @Mock
    private MailAppService mailAppService;

    @Mock
    private JwtEncoder jwtEncoder;

    /**
     * Dựng app service. {@code JwtEncoder} và {@code MailAppService} đều là mock và <b>không được
     * dùng</b> ở hai use case này — sửa hồ sơ và đổi mật khẩu không cấp phiên mới và không gửi mail.
     *
     * @return service sẵn sàng dùng
     */
    private AuthAppServiceImpl genService() {
        return new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofMinutes(15), Duration.ofHours(24));
    }

    /**
     * @return bản ghi người dùng như vừa đọc từ DB
     */
    private User genExistingUser() {
        return new User()
                .setId(USER_ID)
                .setFullName(OLD_FULL_NAME)
                .setEmail(OLD_EMAIL)
                .setPhone(OLD_PHONE)
                .setPasswordHash(OLD_HASH);
    }

    // ========== PUT /auth/me ==========

    @Test
    @DisplayName("updateProfile: khong tim thay user thi tra USER_NOT_FOUND va khong ghi gi")
    void updateProfileReturnsNotFoundWithoutWriting() {
        when(authDomainService.findById(USER_ID)).thenReturn(null);

        ProfileMutationResponse result = genService().updateProfile(
                new UpdateProfileCommand().setUserId(USER_ID).setFullName("Ten moi"));

        assertNull(result.getUser());
        assertEquals(ProfileMutationResponse.CODE_USER_NOT_FOUND, result.getCode());
        verify(authDomainService, never()).updateProfile(any());
    }

    /**
     * <b>Ca quan trọng nhất của file này.</b> Một 409 kèm dòng đã đổi là chính cái bug mà thứ tự
     * "đọc → kiểm hết cổng → rồi mới sửa" sinh ra để chặn.
     */
    @Test
    @DisplayName("updateProfile: email trung thi tra 409 VA entity khong bi sua mot truong nao")
    void updateProfileRejectsDuplicateEmailWithoutTouchingEntity() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.hasEmailTaken("admin@nongsansach.vn")).thenReturn(true);

        ProfileMutationResponse result = genService().updateProfile(new UpdateProfileCommand()
                .setUserId(USER_ID)
                .setFullName("Ten moi khong duoc phep ghi")
                .setEmail("admin@nongsansach.vn"));

        assertEquals(ProfileMutationResponse.CODE_DUPLICATE_EMAIL, result.getCode());
        // Lenh ghi khong duoc chay...
        verify(authDomainService, never()).updateProfile(any());
        // ...va entity van nguyen ven. Neu ban va duoc ap TRUOC cong nay thi hai dong duoi do.
        assertEquals(OLD_EMAIL, existing.getEmail());
        assertEquals(OLD_FULL_NAME, existing.getFullName());
    }

    /**
     * Bảng dùng {@code utf8mb4_unicode_ci} nên MySQL so sánh chuỗi <b>không phân biệt hoa thường</b>,
     * còn Java thì có. Dùng {@code equals} ở cổng "email có thật sự đổi không" thì người đổi
     * {@code demo@x.vn} thành {@code Demo@x.vn} bị coi là có đổi, {@code existsByEmail} tìm thấy
     * <i>chính dòng của họ</i>, và họ nhận 409 báo rằng địa chỉ của chính mình đã bị chiếm.
     */
    @Test
    @DisplayName("updateProfile: doi email chi khac hoa/thuong thi KHONG kiem trung, van ghi")
    void updateProfileTreatsCaseOnlyEmailChangeAsUnchanged() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.updateProfile(any())).thenReturn(existing);

        ProfileMutationResponse result = genService().updateProfile(new UpdateProfileCommand()
                .setUserId(USER_ID)
                .setEmail("DEMO@nongsansach.vn"));

        // Cong trung email khong duoc cham toi — day la khac biet giua equalsIgnoreCase va equals
        verify(authDomainService, never()).hasEmailTaken(anyString());
        assertNull(result.getCode(), "doi hoa/thuong khong duoc tra 409");
        assertEquals("DEMO@nongsansach.vn", existing.getEmail(), "chu hoa moi van phai duoc ghi");
    }

    @Test
    @DisplayName("updateProfile: giu nguyen email thi khong goi hasEmailTaken")
    void updateProfileSkipsDuplicateCheckWhenEmailAbsent() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.updateProfile(any())).thenReturn(existing);

        genService().updateProfile(new UpdateProfileCommand()
                .setUserId(USER_ID)
                .setFullName("Ten moi"));

        verify(authDomainService, never()).hasEmailTaken(anyString());
    }

    @Test
    @DisplayName("updateProfile: truong null giu nguyen gia tri cu, truong co gia tri thi ghi de")
    void updateProfileAppliesPartialPatch() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.updateProfile(any())).thenReturn(existing);

        ProfileMutationResponse result = genService().updateProfile(new UpdateProfileCommand()
                .setUserId(USER_ID)
                .setFullName(null)
                .setPhone("0909999999"));

        assertNull(result.getCode());
        assertEquals(OLD_FULL_NAME, existing.getFullName(), "null nghia la giu nguyen");
        assertEquals(OLD_EMAIL, existing.getEmail(), "vang mat nghia la giu nguyen");
        assertEquals("0909999999", existing.getPhone());
        // Response mang dung 5 truong cua type User phia client
        assertEquals(USER_ID, result.getUser().getId());
        assertEquals(OLD_FULL_NAME, result.getUser().getFullName());
    }

    // ========== PUT /auth/password ==========

    @Test
    @DisplayName("changePassword: khong tim thay user thi tra USER_NOT_FOUND va khong ghi gi")
    void changePasswordReturnsNotFoundWithoutWriting() {
        when(authDomainService.findById(USER_ID)).thenReturn(null);

        PasswordMutationResponse result = genService().changePassword(new ChangePasswordCommand()
                .setUserId(USER_ID)
                .setCurrentPassword("123456")
                .setNewPassword("matkhaumoi"));

        assertFalse(result.isSuccess());
        assertEquals(PasswordMutationResponse.CODE_USER_NOT_FOUND, result.getCode());
        verify(authDomainService, never()).changePassword(any(), anyString());
    }

    /**
     * Cùng luật thứ tự như {@code updateProfile}: đối chiếu mật khẩu cũ <b>trước</b>, đặt hash mới
     * <b>sau</b>. Đảo lại thì một lần gõ nhầm vẫn ghi được hash mới xuống DB kèm theo một response
     * 422 — người dùng bị đổi mật khẩu mà không biết.
     */
    @Test
    @DisplayName("changePassword: sai mat khau cu thi KHONG dat hash moi va KHONG thu hoi phien nao")
    void changePasswordRejectsWrongCurrentPasswordWithoutWriting() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.hasMatchingPassword("sai-mat-khau", OLD_HASH)).thenReturn(false);

        PasswordMutationResponse result = genService().changePassword(new ChangePasswordCommand()
                .setUserId(USER_ID)
                .setSessionId(42L)
                .setCurrentPassword("sai-mat-khau")
                .setNewPassword("matkhaumoi"));

        assertFalse(result.isSuccess());
        assertEquals(PasswordMutationResponse.CODE_INVALID_CURRENT_PASSWORD, result.getCode());
        verify(authDomainService, never()).changePassword(any(), anyString());
        verify(authDomainService, never()).revokeOtherSessions(any(), any());
        assertEquals(OLD_HASH, existing.getPasswordHash());
    }

    @Test
    @DisplayName("changePassword: dung thi dat hash moi RO I MOI thu hoi cac phien khac, giu sid")
    void changePasswordWritesThenRevokesOtherSessions() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.hasMatchingPassword("123456", OLD_HASH)).thenReturn(true);
        when(authDomainService.changePassword(existing, "matkhaumoi")).thenReturn(existing);
        when(authDomainService.revokeOtherSessions(USER_ID, 42L)).thenReturn(2);

        PasswordMutationResponse result = genService().changePassword(new ChangePasswordCommand()
                .setUserId(USER_ID)
                .setSessionId(42L)
                .setCurrentPassword("123456")
                .setNewPassword("matkhaumoi"));

        assertTrue(result.isSuccess());
        assertNull(result.getCode());
        InOrder order = inOrder(authDomainService);
        order.verify(authDomainService).hasMatchingPassword("123456", OLD_HASH);
        order.verify(authDomainService).changePassword(existing, "matkhaumoi");
        order.verify(authDomainService).revokeOtherSessions(USER_ID, 42L);
    }

    /**
     * Token cấp <b>trước</b> khi claim {@code sid} ra đời không mang id phiên. Giá trị {@code null}
     * phải <i>đi xuyên qua</i> tầng này chứ không bị chặn lại: chặn ở đây sẽ biến ca "không biết
     * phiên nào" thành "không thu hồi gì", đúng hướng hỏng nguy hiểm mà ticket cấm. Việc chuẩn hoá
     * thành giá trị canh gác là của adapter — xem {@code RefreshTokenRepositoryImplTest}.
     */
    @Test
    @DisplayName("changePassword: sid vang mat van goi thu hoi, truyen null xuong nguyen ven")
    void changePasswordStillRevokesWhenSessionIdMissing() {
        User existing = genExistingUser();
        when(authDomainService.findById(USER_ID)).thenReturn(existing);
        when(authDomainService.hasMatchingPassword("123456", OLD_HASH)).thenReturn(true);
        when(authDomainService.changePassword(existing, "matkhaumoi")).thenReturn(existing);
        when(authDomainService.revokeOtherSessions(eq(USER_ID), eq(null))).thenReturn(3);

        PasswordMutationResponse result = genService().changePassword(new ChangePasswordCommand()
                .setUserId(USER_ID)
                .setSessionId(null)
                .setCurrentPassword("123456")
                .setNewPassword("matkhaumoi"));

        assertTrue(result.isSuccess());
        verify(authDomainService).revokeOtherSessions(USER_ID, null);
    }
}
