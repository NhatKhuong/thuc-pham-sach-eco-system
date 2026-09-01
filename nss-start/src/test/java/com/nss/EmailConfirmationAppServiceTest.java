package com.nss;

import com.nss.ddd.application.model.command.LoginCommand;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.command.ResendConfirmationCommand;
import com.nss.ddd.application.model.response.AuthMutationResponse;
import com.nss.ddd.application.model.response.RegisterMutationResponse;
import com.nss.ddd.application.service.auth.impl.AuthAppServiceImpl;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.domain.model.entity.EmailConfirmationToken;
import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.AuthDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm bốn luồng của backlog 0037 ở tầng application — mock giả, không Spring context, không DB.
 * <p>
 * <b>File này bắt đúng loại lỗi mà một request thật ở nơi nó chạy KHÔNG bắt được</b>, cùng lý do đã
 * viết ở {@code PasswordResetAppServiceTest}:
 * <ul>
 *   <li>{@code register} chỉ trả một câu xác nhận — bề mặt dây không phân biệt được "đã phát token
 *       xác nhận và gửi mail" với "không làm gì cả". Ở đây phân biệt được, bằng khẳng định trên mock.</li>
 *   <li>{@code login} có một <b>thứ tự chống dò</b> load-bearing: mật khẩu sai phải luôn trả
 *       {@code INVALID_CREDENTIALS}, kể cả khi tài khoản chưa xác nhận email — nếu không, một kẻ dò
 *       mật khẩu sẽ đọc ra được trạng thái xác nhận của một tài khoản qua nhánh lỗi khác.</li>
 *   <li>{@code confirmEmail} có cùng luật thứ tự đã khoá ở {@code resetPassword}: cổng tiêu token
 *       phải đóng lại <i>trước</i> khi {@code User} bị sửa.</li>
 *   <li>{@code resendConfirmation} trả 204 cho mọi ca (anti-enumeration), nên chỉ mock mới phân biệt
 *       được "đã phát token mới" với "không làm gì cả".</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmailConfirmationAppServiceTest {

    private static final Long USER_ID = 7L;

    private static final String EMAIL = "moi@nongsansach.vn";

    private static final String PASSWORD = "matkhauho";

    private static final String RAW_TOKEN = "mot-chuoi-token-tho";

    private static final Duration EMAIL_CONFIRMATION_TTL = Duration.ofHours(24);

    @Mock
    private AuthDomainService authDomainService;

    @Mock
    private MailAppService mailAppService;

    @Mock
    private JwtEncoder jwtEncoder;

    /**
     * Dựng app service. {@code JwtEncoder} là mock và <b>không được dùng</b> ở các ca chạm nhánh
     * {@code register} / gate {@code login} / {@code confirmEmail} / {@code resendConfirmation} —
     * không nhánh nào trong số đó cấp phiên mới.
     *
     * @return service sẵn sàng dùng
     */
    private AuthAppServiceImpl genService() {
        return new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofMinutes(15), EMAIL_CONFIRMATION_TTL);
    }

    /**
     * @return người dùng vừa đăng ký, chưa xác nhận email
     */
    private User genUnverifiedUser() {
        return new User().setId(USER_ID).setEmail(EMAIL).setEmailVerified(Boolean.FALSE);
    }

    /**
     * @return dòng token xác nhận email còn dùng được, kèm chủ sở hữu đã nạp sẵn
     */
    private EmailConfirmationToken genUsableToken() {
        return new EmailConfirmationToken().setId(1L).setUser(genUnverifiedUser()).setIsUsed(false);
    }

    private Role genCustomerRole() {
        return new Role().setId(2L).setCode("CUSTOMER").setName("Khách hàng");
    }

    // ========== POST /api/auth/register ==========

    /**
     * <b>Không còn auto-login (backlog 0037 §Contract điều 1).</b> Đăng ký thành công không được cấp
     * phiên nào — {@code issueRefreshToken} không được gọi, và {@code jwtEncoder} không được đụng
     * tới. Thay vào đó phải phát đúng một token xác nhận email và gửi mail tới đúng địa chỉ vừa đăng
     * ký.
     */
    @Test
    @DisplayName("Dang ky thanh cong: KHONG cap phien, phat token xac nhan va gui mail dung dia chi")
    void registerSuccessIssuesConfirmationInsteadOfSession() {
        User saved = genUnverifiedUser();
        when(authDomainService.hasEmailTaken(EMAIL)).thenReturn(false);
        when(authDomainService.findRoleByCode("CUSTOMER")).thenReturn(genCustomerRole());
        when(authDomainService.register(any(User.class), eq(PASSWORD), any(Role.class))).thenReturn(saved);
        when(authDomainService.issueEmailConfirmationToken(saved, EMAIL_CONFIRMATION_TTL))
                .thenReturn(RAW_TOKEN);

        RegisterMutationResponse result = genService().register(new RegisterCommand()
                .setFullName("Người Mới").setEmail(EMAIL).setPhone("0900000000").setPassword(PASSWORD));

        assertNotNull(result.getRegister(), "Vang mat truong register chinh la tin hieu that bai");
        assertNotNull(result.getRegister().getMessage());
        verify(authDomainService, never()).issueRefreshToken(any(), any());
        verify(authDomainService).issueEmailConfirmationToken(saved, EMAIL_CONFIRMATION_TTL);
        verify(mailAppService).sendEmailConfirmationMail(EMAIL, RAW_TOKEN);
    }

    /**
     * <b>Response không còn chứa token/refreshToken</b> — {@code RegisterResponse} chỉ mang một câu
     * xác nhận, đúng contract đã pin. Test này khoá lại hình dạng đó ở mức kiểu: không có accessor
     * nào khác để đọc token ra, nên bằng chứng là {@code RegisterResponse} chỉ có field
     * {@code message}, và ca trên đã xác nhận {@code jwtEncoder} — thứ duy nhất đúc access token —
     * không hề được gọi.
     */
    @Test
    @DisplayName("Dang ky thanh cong: khong dung toi jwtEncoder — response khong mang token nao")
    void registerSuccessNeverTouchesJwtEncoder() {
        User saved = genUnverifiedUser();
        when(authDomainService.hasEmailTaken(EMAIL)).thenReturn(false);
        when(authDomainService.findRoleByCode("CUSTOMER")).thenReturn(genCustomerRole());
        when(authDomainService.register(any(User.class), eq(PASSWORD), any(Role.class))).thenReturn(saved);
        when(authDomainService.issueEmailConfirmationToken(saved, EMAIL_CONFIRMATION_TTL))
                .thenReturn(RAW_TOKEN);

        genService().register(new RegisterCommand()
                .setFullName("Người Mới").setEmail(EMAIL).setPhone("0900000000").setPassword(PASSWORD));

        verifyNoInteractions(jwtEncoder);
    }

    /**
     * Email trùng phải dừng lại <b>trước</b> khi chạm tới token xác nhận — cùng luật "đóng cổng thất
     * bại trước khi làm gì khác" đã lặp lại xuyên suốt class này.
     */
    @Test
    @DisplayName("Email trung: CODE_DUPLICATE_EMAIL, khong dung toi token xac nhan hay mail")
    void registerDuplicateEmailNeverIssuesConfirmation() {
        when(authDomainService.hasEmailTaken(EMAIL)).thenReturn(true);

        RegisterMutationResponse result = genService().register(new RegisterCommand()
                .setFullName("Người Mới").setEmail(EMAIL).setPhone("0900000000").setPassword(PASSWORD));

        assertNull(result.getRegister());
        assertEquals(RegisterMutationResponse.CODE_DUPLICATE_EMAIL, result.getCode());
        verify(authDomainService, never()).issueEmailConfirmationToken(any(), any());
        verify(mailAppService, never()).sendEmailConfirmationMail(anyString(), anyString());
        verify(authDomainService, never()).register(any(), anyString(), any());
    }

    // ========== POST /api/auth/login — gate xac nhan email ==========

    /**
     * <b>Thông tin đăng nhập ĐÚNG nhưng tài khoản chưa xác nhận email</b> — phải chặn ở gate riêng,
     * không cấp phiên và không đọc vai trò (đọc vai trò là bước cuối cùng trước khi cấp phiên).
     */
    @Test
    @DisplayName("Mat khau dung nhung chua verify: CODE_EMAIL_NOT_VERIFIED, khong cap phien")
    void loginBlocksUnverifiedAccountWithCorrectPassword() {
        User user = genUnverifiedUser().setPasswordHash("hash-that");
        when(authDomainService.findByEmail(EMAIL)).thenReturn(user);
        when(authDomainService.hasMatchingPassword(PASSWORD, "hash-that")).thenReturn(true);

        AuthMutationResponse result = genService().login(new LoginCommand().setEmail(EMAIL).setPassword(PASSWORD));

        assertNull(result.getAuth());
        assertEquals(AuthMutationResponse.CODE_EMAIL_NOT_VERIFIED, result.getCode());
        verify(authDomainService, never()).findRoleCodes(anyLong());
        verify(authDomainService, never()).issueRefreshToken(any(), any());
    }

    /**
     * <b>Mật khẩu SAI phải luôn trả INVALID_CREDENTIALS, kể cả khi tài khoản chưa verify.</b> Đây là
     * ca chống dò quan trọng nhất của gate này: gate {@code emailVerified} chạy SAU bước đối chiếu
     * mật khẩu, nên một kẻ gõ sai mật khẩu vào một tài khoản chưa xác nhận không được phép đọc ra
     * trạng thái xác nhận của tài khoản đó qua sự khác biệt giữa hai mã lỗi.
     */
    @Test
    @DisplayName("Mat khau SAI tren tai khoan chua verify: van la INVALID_CREDENTIALS, khong lo verify status")
    void loginWithWrongPasswordNeverLeaksVerificationStatus() {
        User user = genUnverifiedUser().setPasswordHash("hash-that");
        when(authDomainService.findByEmail(EMAIL)).thenReturn(user);
        when(authDomainService.hasMatchingPassword("sai-mat-khau", "hash-that")).thenReturn(false);

        AuthMutationResponse result = genService().login(
                new LoginCommand().setEmail(EMAIL).setPassword("sai-mat-khau"));

        assertNull(result.getAuth());
        assertEquals(AuthMutationResponse.CODE_INVALID_CREDENTIALS, result.getCode());
        verify(authDomainService, never()).findRoleCodes(anyLong());
    }

    /**
     * Tài khoản đã xác nhận vẫn phải đăng nhập được bình thường — gate không được chặn nhầm quần thể
     * còn lại.
     */
    @Test
    @DisplayName("Tai khoan da verify: dang nhap binh thuong, khong bi gate chan")
    void loginAllowsVerifiedAccount() {
        User user = new User().setId(USER_ID).setEmail(EMAIL).setEmailVerified(Boolean.TRUE)
                .setPasswordHash("hash-that");
        RefreshToken refreshToken = new RefreshToken().setId(1L).setUser(user).setToken("refresh-tho");
        Jwt jwt = mock(Jwt.class);
        when(authDomainService.findByEmail(EMAIL)).thenReturn(user);
        when(authDomainService.hasMatchingPassword(PASSWORD, "hash-that")).thenReturn(true);
        when(authDomainService.findRoleCodes(USER_ID)).thenReturn(List.of("CUSTOMER"));
        when(authDomainService.issueRefreshToken(eq(user), any())).thenReturn(refreshToken);
        when(jwtEncoder.encode(any())).thenReturn(jwt);
        when(jwt.getTokenValue()).thenReturn("access-token-tho");

        AuthMutationResponse result = genService().login(new LoginCommand().setEmail(EMAIL).setPassword(PASSWORD));

        assertNotNull(result.getAuth());
        verify(authDomainService).issueRefreshToken(eq(user), any());
    }

    // ========== GET /api/auth/confirm-email ==========

    /**
     * <b>THỨ TỰ là nội dung của ca này</b>, cùng luật đã khoá ở {@code resetPassword}: cổng tiêu
     * token phải đóng lại trước khi {@code User} bị sửa.
     */
    @Test
    @DisplayName("Token hop le: tieu token TRUOC, roi confirmEmail(user), tra true")
    void confirmEmailSuccessConsumesTokenBeforeUpdatingUser() {
        EmailConfirmationToken usable = genUsableToken();
        when(authDomainService.findUsableEmailConfirmationToken(RAW_TOKEN)).thenReturn(usable);
        when(authDomainService.consumeEmailConfirmationToken(RAW_TOKEN)).thenReturn(true);
        when(authDomainService.confirmEmail(usable.getUser())).thenReturn(usable.getUser());

        boolean result = genService().confirmEmail(RAW_TOKEN);

        assertTrue(result);
        InOrder order = inOrder(authDomainService);
        order.verify(authDomainService).consumeEmailConfirmationToken(RAW_TOKEN);
        order.verify(authDomainService).confirmEmail(usable.getUser());
    }

    /**
     * <b>Ba ca thất bại gộp làm một</b> (không tồn tại / đã dùng / đã hết hạn) —
     * {@code findUsableEmailConfirmationToken} trả {@code null} cho cả ba, nên chỉ cần một test.
     */
    @Test
    @DisplayName("Token khong tra duoc (khong ton tai/da dung/het han): tra false, khong cham User")
    void confirmEmailUnknownTokenNeverTouchesUser() {
        when(authDomainService.findUsableEmailConfirmationToken(anyString())).thenReturn(null);

        boolean result = genService().confirmEmail("bia-ra");

        assertFalse(result);
        verify(authDomainService, never()).confirmEmail(any());
        verify(authDomainService, never()).consumeEmailConfirmationToken(anyString());
    }

    /**
     * <b>Thua cuộc đua tiêu token cũng KHÔNG được chạm vào {@code User}.</b> Hai request đồng thời
     * cầm cùng một chuỗi: cả hai đọc thấy dòng còn sống ở bước 1, UPDATE có điều kiện chỉ cho một cái
     * thắng — cái thua phải dừng lại đúng ở đây, không được gọi {@code confirmEmail}.
     */
    @Test
    @DisplayName("Thua cuoc dua tieu token: tra false, khong cham User")
    void confirmEmailLostRaceNeverTouchesUser() {
        EmailConfirmationToken usable = genUsableToken();
        when(authDomainService.findUsableEmailConfirmationToken(RAW_TOKEN)).thenReturn(usable);
        when(authDomainService.consumeEmailConfirmationToken(RAW_TOKEN)).thenReturn(false);

        boolean result = genService().confirmEmail(RAW_TOKEN);

        assertFalse(result);
        verify(authDomainService, never()).confirmEmail(any());
    }

    // ========== POST /api/auth/resend-confirmation ==========

    /**
     * Đúng khuôn {@code unknownEmailLeavesNoTrace} của {@code PasswordResetAppServiceTest}: một email
     * không ứng với tài khoản nào phải rời khỏi hệ thống mà không để lại dấu vết nào.
     */
    @Test
    @DisplayName("Email khong ton tai: KHONG phat token, KHONG gui mail, khong nem gi")
    void resendConfirmationUnknownEmailLeavesNoTrace() {
        when(authDomainService.findByEmail(anyString())).thenReturn(null);

        genService().resendConfirmation(new ResendConfirmationCommand().setEmail("khong-ton-tai@nongsansach.vn"));

        verify(authDomainService, never()).issueEmailConfirmationToken(any(), any());
        verify(mailAppService, never()).sendEmailConfirmationMail(anyString(), anyString());
    }

    /**
     * <b>Tài khoản đã xác nhận rồi: resend không được phát một link vô nghĩa.</b> Phát token mới cho
     * một tài khoản đã kích hoạt chỉ tổ phình bảng token bằng những dòng không bao giờ được dùng.
     */
    @Test
    @DisplayName("Tai khoan da verify: KHONG phat token moi, KHONG gui mail")
    void resendConfirmationAlreadyVerifiedAccountLeavesNoTrace() {
        User verified = new User().setId(USER_ID).setEmail(EMAIL).setEmailVerified(Boolean.TRUE);
        when(authDomainService.findByEmail(EMAIL)).thenReturn(verified);

        genService().resendConfirmation(new ResendConfirmationCommand().setEmail(EMAIL));

        verify(authDomainService, never()).issueEmailConfirmationToken(any(), any());
        verify(mailAppService, never()).sendEmailConfirmationMail(anyString(), anyString());
    }

    @Test
    @DisplayName("Tai khoan hop le chua verify: phat token moi va gui mail")
    void resendConfirmationValidUnverifiedAccountIssuesNewToken() {
        User unverified = genUnverifiedUser();
        when(authDomainService.findByEmail(EMAIL)).thenReturn(unverified);
        when(authDomainService.issueEmailConfirmationToken(unverified, EMAIL_CONFIRMATION_TTL))
                .thenReturn(RAW_TOKEN);

        genService().resendConfirmation(new ResendConfirmationCommand().setEmail(EMAIL));

        verify(authDomainService).issueEmailConfirmationToken(unverified, EMAIL_CONFIRMATION_TTL);
        verify(mailAppService).sendEmailConfirmationMail(EMAIL, RAW_TOKEN);
    }

    /**
     * <b>Ngoại lệ KHÔNG được thoát ra khỏi ranh giới {@code @Async}</b> — đúng khuôn
     * {@code failureNeverEscapesTheAsyncBoundary} của {@code PasswordResetAppServiceTest}. Method
     * chạy trên luồng khác và người dùng đã nhận 204 từ lâu, nên một exception ném ra ở đây chỉ tới
     * bộ xử lý mặc định của executor.
     */
    @Test
    @DisplayName("Loi o tang duoi KHONG thoat ra khoi ranh gioi @Async")
    void resendConfirmationFailureNeverEscapesTheAsyncBoundary() {
        User unverified = genUnverifiedUser();
        when(authDomainService.findByEmail(EMAIL)).thenReturn(unverified);
        when(authDomainService.issueEmailConfirmationToken(any(), any()))
                .thenThrow(new IllegalStateException("database is down"));

        genService().resendConfirmation(new ResendConfirmationCommand().setEmail(EMAIL));

        verify(mailAppService, never()).sendEmailConfirmationMail(anyString(), anyString());
    }
}
