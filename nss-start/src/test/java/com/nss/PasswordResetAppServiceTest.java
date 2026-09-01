package com.nss;

import com.nss.ddd.application.model.command.ForgotPasswordCommand;
import com.nss.ddd.application.model.command.ResetPasswordCommand;
import com.nss.ddd.application.model.response.PasswordResetMutationResponse;
import com.nss.ddd.application.service.auth.impl.AuthAppServiceImpl;
import com.nss.ddd.application.service.mail.MailAppService;
import com.nss.ddd.domain.model.entity.PasswordResetToken;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm hai use case đặt lại mật khẩu ở tầng application — mock giả, không Spring context, không DB.
 * <p>
 * <b>File này bắt đúng loại lỗi mà bằng chứng chạy thật KHÔNG bắt được</b>, và đó là lý do nó tồn
 * tại bên cạnh ma trận request ở mục Verification của ticket:
 * <ul>
 *   <li>{@code forgot-password} trả 204 cho <i>mọi</i> ca, nên một lời gọi thật không phân biệt
 *       được "đã phát token và gửi mail" với "không làm gì cả". Ở đây thì phân biệt được, bằng cách
 *       khẳng định trên các mock.</li>
 *   <li>{@code reset-password} có một <b>thứ tự</b> load-bearing: cổng tiêu token phải đóng lại
 *       <i>trước</i> khi hash mật khẩu bị ghi đè. Đảo lại thì một token đã tiêu vẫn đổi được mật
 *       khẩu, và response vẫn 422 — database nói một đằng, bề mặt dây nói một nẻo. Một test chỉ
 *       khẳng định mã trả về sẽ xanh trong đúng tình huống đó.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetAppServiceTest {

    private static final Long USER_ID = 7L;

    private static final String EMAIL = "demo@nongsansach.vn";

    private static final String RAW_TOKEN = "mot-chuoi-token-tho";

    private static final String NEW_PASSWORD = "matkhaumoi";

    private static final Duration RESET_TTL = Duration.ofMinutes(15);

    @Mock
    private AuthDomainService authDomainService;

    @Mock
    private MailAppService mailAppService;

    @Mock
    private JwtEncoder jwtEncoder;

    /**
     * Dựng app service. {@code JwtEncoder} là mock và <b>không được dùng</b> ở hai use case này —
     * đặt lại mật khẩu không cấp phiên mới, nó chỉ huỷ phiên cũ.
     *
     * @return service sẵn sàng dùng
     */
    private AuthAppServiceImpl genService() {
        return new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                Duration.ofMinutes(30), Duration.ofDays(14), RESET_TTL, Duration.ofHours(24));
    }

    /**
     * @return người dùng như vừa đọc từ DB
     */
    private User genUser() {
        return new User().setId(USER_ID).setEmail(EMAIL);
    }

    /**
     * @return dòng token còn dùng được, kèm chủ sở hữu đã nạp sẵn
     */
    private PasswordResetToken genUsableToken() {
        return new PasswordResetToken().setId(1L).setUser(genUser()).setIsUsed(false);
    }

    // ========== KHOI DONG: FAIL-FAST ==========

    /**
     * <b>TTL không dương phải làm ứng dụng chết lúc khởi động</b>, đúng tiền lệ {@code JwtConfig} với
     * {@code jwt-secret} ngắn hơn 32 byte.
     * <p>
     * Ca này đáng có vì hướng hỏng của nó đặc biệt kín: mọi token sinh ra sẽ hết hạn ngay tại thời
     * điểm sinh, nên người dùng nhận email, bấm link, và nhận 422 — trong khi endpoint, build và
     * test tính năng đều xanh. Không có gì trong hệ thống nói rằng nguyên nhân là một dòng cấu hình.
     */
    @Test
    @DisplayName("TTL token dat lai <= 0: fail NGAY luc dung bean, khong doi den lan gui dau tien")
    void nonPositiveResetTtlFailsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                        Duration.ofMinutes(30), Duration.ofDays(14), Duration.ZERO, Duration.ofHours(24)));
        assertThrows(IllegalStateException.class,
                () -> new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                        Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofMinutes(-1), Duration.ofHours(24)));
    }

    /**
     * <b>Backlog 0037.</b> Cùng kỷ luật với TTL token đặt lại mật khẩu, áp cho TTL token xác nhận
     * email — cả hai đều fail lúc khởi động thay vì phát ra những token chết ngay lúc sinh.
     */
    @Test
    @DisplayName("TTL token xac nhan email <= 0: fail NGAY luc dung bean")
    void nonPositiveEmailConfirmationTtlFailsAtStartup() {
        assertThrows(IllegalStateException.class,
                () -> new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                        Duration.ofMinutes(30), Duration.ofDays(14), RESET_TTL, Duration.ZERO));
        assertThrows(IllegalStateException.class,
                () -> new AuthAppServiceImpl(authDomainService, mailAppService, jwtEncoder,
                        Duration.ofMinutes(30), Duration.ofDays(14), RESET_TTL, Duration.ofHours(-1)));
    }

    // ========== POST /auth/forgot-password ==========

    @Test
    @DisplayName("Email co that: phat token voi dung TTL cau hinh, roi gui mail toi dia chi cua tai khoan")
    void knownEmailIssuesTokenAndSendsMail() {
        User user = genUser();
        when(authDomainService.findByEmail(EMAIL)).thenReturn(user);
        when(authDomainService.issuePasswordResetToken(user, RESET_TTL)).thenReturn(RAW_TOKEN);

        genService().forgotPassword(new ForgotPasswordCommand().setEmail(EMAIL));

        verify(authDomainService).issuePasswordResetToken(user, RESET_TTL);
        // Gui toi email CUA BAN GHI, khong phai chuoi client gui len: hai gia tri chi khac nhau ve
        // hoa thuong hom nay, nhung lay tu ban ghi la thu giu dung tinh chat do khi cot email doi.
        verify(mailAppService).sendPasswordResetMail(EMAIL, RAW_TOKEN);
    }

    /**
     * <b>Ca quan trọng nhất của endpoint này.</b> Một email không ứng với tài khoản nào phải rời
     * khỏi hệ thống mà <i>không để lại dấu vết nào</i>: không dòng token, không email gửi đi.
     * <p>
     * Bề mặt dây không phân biệt được hai nhánh (cả hai đều 204), nên chỉ có ca này nói được rằng
     * nhánh thứ hai thật sự không làm gì. Nếu một ngày nó bắt đầu ghi một dòng token cho email lạ,
     * bảng sẽ phình theo mỗi lần dò và <b>không có endpoint nào tố cáo điều đó</b>.
     */
    @Test
    @DisplayName("Email khong ton tai: KHONG phat token, KHONG gui mail — va khong nem gi")
    void unknownEmailLeavesNoTrace() {
        when(authDomainService.findByEmail(anyString())).thenReturn(null);

        genService().forgotPassword(
                new ForgotPasswordCommand().setEmail("khong-ton-tai@nongsansach.vn"));

        verify(authDomainService, never()).issuePasswordResetToken(any(), any());
        verify(mailAppService, never()).sendPasswordResetMail(anyString(), anyString());
    }

    /**
     * <b>Ngoại lệ KHÔNG được thoát ra khỏi ranh giới {@code @Async}.</b>
     * <p>
     * Method chạy trên luồng khác và người dùng <i>đã</i> nhận 204 từ lâu, nên một exception ném ra
     * chỉ tới bộ xử lý mặc định của executor. Ca này khoá lại việc nó được bắt và ghi log tại chỗ —
     * nếu không, một sự cố database trên đường quên mật khẩu sẽ hoàn toàn vô hình.
     */
    @Test
    @DisplayName("Loi o tang duoi KHONG thoat ra khoi ranh gioi @Async")
    void failureNeverEscapesTheAsyncBoundary() {
        when(authDomainService.findByEmail(EMAIL)).thenReturn(genUser());
        when(authDomainService.issuePasswordResetToken(any(), any()))
                .thenThrow(new IllegalStateException("database is down"));

        genService().forgotPassword(new ForgotPasswordCommand().setEmail(EMAIL));

        verify(mailAppService, never()).sendPasswordResetMail(anyString(), anyString());
    }

    // ========== POST /auth/reset-password ==========

    /**
     * <b>THỨ TỰ là nội dung của ca này, không phải mã trả về.</b> Cổng tiêu token phải đóng lại
     * trước khi hash mật khẩu bị ghi đè; đảo hai dòng đó thì một token đã tiêu vẫn đổi được mật khẩu.
     */
    @Test
    @DisplayName("Thanh cong: tieu token TRUOC, doi mat khau SAU, roi thu hoi toan bo phien")
    void successConsumesTokenBeforeWritingPassword() {
        PasswordResetToken usable = genUsableToken();
        when(authDomainService.findUsablePasswordResetToken(RAW_TOKEN)).thenReturn(usable);
        when(authDomainService.consumePasswordResetToken(RAW_TOKEN)).thenReturn(true);
        when(authDomainService.revokeAllSessions(USER_ID)).thenReturn(2);

        PasswordResetMutationResponse result = genService().resetPassword(
                new ResetPasswordCommand().setToken(RAW_TOKEN).setNewPassword(NEW_PASSWORD));

        assertTrue(result.isSuccess());
        InOrder order = inOrder(authDomainService);
        order.verify(authDomainService).consumePasswordResetToken(RAW_TOKEN);
        order.verify(authDomainService).changePassword(usable.getUser(), NEW_PASSWORD);
        order.verify(authDomainService).revokeAllSessions(USER_ID);
    }

    /**
     * <b>Thu hồi TẤT CẢ, không chừa dòng nào</b> — khác {@code changePassword} của backlog 0016,
     * nơi phiên đang gọi được giữ lại. Ở đây người dùng đang không đăng nhập, nên giả định phải là
     * tài khoản đã bị chiếm.
     */
    @Test
    @DisplayName("Thanh cong goi revokeAllSessions, KHONG goi revokeOtherSessions")
    void successRevokesEverySession() {
        when(authDomainService.findUsablePasswordResetToken(RAW_TOKEN)).thenReturn(genUsableToken());
        when(authDomainService.consumePasswordResetToken(RAW_TOKEN)).thenReturn(true);

        genService().resetPassword(
                new ResetPasswordCommand().setToken(RAW_TOKEN).setNewPassword(NEW_PASSWORD));

        verify(authDomainService).revokeAllSessions(USER_ID);
        verify(authDomainService, never()).revokeOtherSessions(any(), any());
    }

    /**
     * <b>Token không tra được: KHÔNG được chạm vào mật khẩu.</b> Cùng luật thứ tự đã khoá ở
     * {@code AuthProfileAppServiceTest} — trả về một giá trị thất bại không phải là ném exception,
     * nên transaction <i>vẫn commit</i> và một lệnh ghi lỡ tay ở nhánh này sẽ đổi mật khẩu thật.
     */
    @Test
    @DisplayName("Token khong tra duoc: 422 INVALID_RESET_TOKEN va KHONG cham vao mat khau")
    void unknownTokenNeverTouchesPassword() {
        when(authDomainService.findUsablePasswordResetToken(anyString())).thenReturn(null);

        PasswordResetMutationResponse result = genService().resetPassword(
                new ResetPasswordCommand().setToken("bia-ra").setNewPassword(NEW_PASSWORD));

        assertFalse(result.isSuccess());
        assertEquals(PasswordResetMutationResponse.CODE_INVALID_RESET_TOKEN, result.getCode());
        verify(authDomainService, never()).changePassword(any(), anyString());
        verify(authDomainService, never()).revokeAllSessions(any());
    }

    /**
     * <b>Thua cuộc đua tiêu token cũng KHÔNG được chạm vào mật khẩu.</b> Đây là ca hai request đồng
     * thời cầm cùng một chuỗi: cả hai đọc thấy dòng còn sống ở bước 1, nhưng UPDATE có điều kiện chỉ
     * cho một cái thắng. Cái thua phải dừng lại đúng ở đây.
     */
    @Test
    @DisplayName("Thua cuoc dua tieu token: 422 va KHONG cham vao mat khau")
    void lostRaceNeverTouchesPassword() {
        when(authDomainService.findUsablePasswordResetToken(RAW_TOKEN)).thenReturn(genUsableToken());
        when(authDomainService.consumePasswordResetToken(RAW_TOKEN)).thenReturn(false);

        PasswordResetMutationResponse result = genService().resetPassword(
                new ResetPasswordCommand().setToken(RAW_TOKEN).setNewPassword(NEW_PASSWORD));

        assertFalse(result.isSuccess());
        assertEquals(PasswordResetMutationResponse.CODE_INVALID_RESET_TOKEN, result.getCode());
        verify(authDomainService, never()).changePassword(any(), anyString());
        verify(authDomainService, never()).revokeAllSessions(any());
    }

    /**
     * <b>Hai ca thất bại phải mang cùng một {@code message}, không chỉ cùng mã.</b> Chuỗi đó đi
     * thẳng ra màn hình người dùng (§A.3), nên hai câu chữ khác nhau là cùng một rò rỉ, chỉ đổi nơi
     * đọc: người cầm một chuỗi bịa sẽ đọc ra được rằng chuỗi của mình khác loại với một chuỗi đã tiêu.
     */
    @Test
    @DisplayName("Ba ca that bai tra ve CUNG mot message, khong chi cung ma")
    void everyFailureShareTheSameMessage() {
        when(authDomainService.findUsablePasswordResetToken("khong-ton-tai")).thenReturn(null);
        when(authDomainService.findUsablePasswordResetToken(RAW_TOKEN)).thenReturn(genUsableToken());
        when(authDomainService.consumePasswordResetToken(RAW_TOKEN)).thenReturn(false);
        AuthAppServiceImpl service = genService();

        String missingMessage = service.resetPassword(new ResetPasswordCommand()
                .setToken("khong-ton-tai").setNewPassword(NEW_PASSWORD)).getMessage();
        String consumedMessage = service.resetPassword(new ResetPasswordCommand()
                .setToken(RAW_TOKEN).setNewPassword(NEW_PASSWORD)).getMessage();

        assertEquals(missingMessage, consumedMessage,
                "Token khong ton tai va token da tieu phai doc ra y het nhau tu phia client");
    }

    /**
     * Chủ tài khoản đến từ <b>dòng token</b>, không từ bất cứ thứ gì client gửi lên — đó là toàn bộ
     * lý do bảng {@code password_reset_token} mang khoá ngoại tới {@code user}.
     */
    @Test
    @DisplayName("Chu tai khoan lay tu dong token, khong tu body")
    void ownerComesFromTokenRow() {
        User owner = new User().setId(99L).setEmail("nguoi-khac@nongsansach.vn");
        when(authDomainService.findUsablePasswordResetToken(RAW_TOKEN))
                .thenReturn(new PasswordResetToken().setId(1L).setUser(owner).setIsUsed(false));
        when(authDomainService.consumePasswordResetToken(RAW_TOKEN)).thenReturn(true);

        genService().resetPassword(
                new ResetPasswordCommand().setToken(RAW_TOKEN).setNewPassword(NEW_PASSWORD));

        verify(authDomainService).changePassword(eq(owner), eq(NEW_PASSWORD));
        verify(authDomainService).revokeAllSessions(99L);
    }
}
