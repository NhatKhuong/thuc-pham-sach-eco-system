package com.nss;

import com.nss.ddd.domain.model.entity.PasswordResetToken;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.EmailConfirmationTokenRepository;
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

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm quy tắc token đặt lại mật khẩu ở tầng domain — port giả, không Spring context, không database.
 * <p>
 * <b>Ca có giá trị nhất trong file này là {@link #issuedTokenIsNeverStoredInPlaintext()}</b>, và nó
 * tồn tại vì thứ nó bảo vệ hỏng <i>im lặng</i>: nếu một ngày ai đó "đơn giản hoá"
 * {@code issuePasswordResetToken} thành lưu chuỗi thô, mọi endpoint vẫn chạy đúng, mọi test tính
 * năng vẫn xanh, email vẫn tới nơi — chỉ có điều một lần đọc được bảng là chiếm được mọi tài khoản
 * đang có yêu cầu mở. Không có ca nào khác trong dự án bắt được thay đổi đó.
 * <p>
 * Test khẳng định trên <b>giá trị đi xuống port</b> chứ không trên giá trị trả về: cột
 * {@code token_hash} là thứ thật sự nằm trong DB, và nó là thứ duy nhất đáng kiểm ở đây.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetTokenDomainServiceTest {

    private static final Long USER_ID = 7L;

    private static final Duration TTL = Duration.ofMinutes(15);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailConfirmationTokenRepository emailConfirmationTokenRepository;

    /**
     * @return service với {@code BCryptPasswordEncoder} thật — cùng lý do đã viết ở
     *         {@code AuthDomainServiceTest}
     */
    private AuthDomainServiceImpl genService() {
        return new AuthDomainServiceImpl(userRepository, userRoleRepository, refreshTokenRepository,
                passwordResetTokenRepository, emailConfirmationTokenRepository, new BCryptPasswordEncoder());
    }

    /**
     * @return người dùng như vừa đọc từ DB
     */
    private User genUser() {
        return new User().setId(USER_ID).setEmail("demo@nongsansach.vn");
    }

    /**
     * Bản tính SHA-256 <b>độc lập với code sản phẩm</b>.
     * <p>
     * Cố ý không gọi lại helper của {@code AuthDomainServiceImpl}: một test dùng chính hàm đang kiểm
     * để dựng giá trị kỳ vọng sẽ xanh kể cả khi hàm đó sai.
     *
     * @param raw chuỗi thô
     * @return SHA-256 dạng hex thường
     */
    private String genExpectedHash(String raw) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ========== PHAT TOKEN ==========

    /**
     * <b>Chuỗi thô KHÔNG BAO GIỜ đi xuống DB — đây là bất biến trung tâm của bảng
     * {@code password_reset_token}</b> (backlog 0017 §Contract điều 4), và là chỗ nó cố ý đi khác
     * tiền lệ {@code refresh_token}.
     */
    @Test
    @DisplayName("Token phat ra: DB nhan HASH, chuoi tho chi nam o gia tri tra ve")
    void issuedTokenIsNeverStoredInPlaintext() throws Exception {
        when(passwordResetTokenRepository.save(any()))
                .thenAnswer(invocation -> ((PasswordResetToken) invocation.getArgument(0)).setId(1L));

        String rawToken = genService().issuePasswordResetToken(genUser(), TTL);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(saved.capture());
        PasswordResetToken row = saved.getValue();

        assertNotNull(rawToken, "Phai tra ve chuoi tho de dat vao link email");
        assertNotEquals(rawToken, row.getTokenHash(),
                "Cot token_hash KHONG duoc mang chinh chuoi tho — do la ca lo hong nay sinh ra de chan");
        assertEquals(genExpectedHash(rawToken), row.getTokenHash(),
                "Cot token_hash phai la SHA-256 hex cua chuoi tho");
        assertEquals(64, row.getTokenHash().length(), "SHA-256 hex phai dai dung 64 ky tu");
        assertFalse(row.getIsUsed(), "Dong moi phat phai chua duoc dung");
    }

    @Test
    @DisplayName("Hai lan phat ra hai chuoi khac nhau — token khong duoc phep doan truoc")
    void everyIssuedTokenIsUnique() {
        when(passwordResetTokenRepository.save(any()))
                .thenAnswer(invocation -> ((PasswordResetToken) invocation.getArgument(0)).setId(1L));
        AuthDomainServiceImpl service = genService();

        assertNotEquals(service.issuePasswordResetToken(genUser(), TTL),
                service.issuePasswordResetToken(genUser(), TTL));
    }

    /**
     * <b>Hạn phải tính theo giờ UTC</b>, cùng kỷ luật {@code genUtcNow()} của cả aggregate: máy dev ở
     * {@code Asia/Saigon} lệch UTC+7, và một mốc lấy từ đồng hồ máy sẽ khiến token sống thêm 7 tiếng
     * so với ý định — <i>không có gì báo lỗi</i>.
     */
    @Test
    @DisplayName("expires_at tinh theo gio UTC, khong theo dong ho may")
    void expiryIsComputedInUtc() {
        when(passwordResetTokenRepository.save(any()))
                .thenAnswer(invocation -> ((PasswordResetToken) invocation.getArgument(0)).setId(1L));
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        genService().issuePasswordResetToken(genUser(), TTL);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(saved.capture());
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
        PasswordResetToken row = saved.getValue();

        assertFalse(row.getCreatedAt().isBefore(before.minusSeconds(1)),
                "created_at phai la gio UTC hien tai, khong phai gio may");
        assertFalse(row.getCreatedAt().isAfter(after.plusSeconds(1)),
                "created_at phai la gio UTC hien tai, khong phai gio may");
        assertEquals(row.getCreatedAt().plus(TTL), row.getExpiresAt(),
                "expires_at phai la created_at cong dung TTL");
    }

    // ========== TRA TOKEN ==========

    /**
     * <b>Port nhận HASH, không nhận chuỗi thô.</b> Nếu tầng domain quên băm thì cột đã băm sẽ không
     * bao giờ khớp, và triệu chứng là "mọi token đều không hợp lệ" — một endpoint chết hoàn toàn mà
     * vẫn trả đúng hình dạng lỗi, nên rất dễ đọc nhầm thành "token hết hạn".
     */
    @Test
    @DisplayName("Tra token: gia tri di xuong port la HASH, khong phai chuoi tho")
    void lookupHashesBeforeQuerying() throws Exception {
        String rawToken = "mot-chuoi-token-tho";
        when(passwordResetTokenRepository.findUsableByTokenHash(anyString(), any()))
                .thenReturn(Optional.empty());

        genService().findUsablePasswordResetToken(rawToken);

        verify(passwordResetTokenRepository)
                .findUsableByTokenHash(eq(genExpectedHash(rawToken)), any());
    }

    @Test
    @DisplayName("Token rong hoac null: khong cham vao DB, tra null")
    void blankTokenNeverReachesDatabase() {
        AuthDomainServiceImpl service = genService();

        assertNull(service.findUsablePasswordResetToken(null));
        assertNull(service.findUsablePasswordResetToken("   "));
        verify(passwordResetTokenRepository, never()).findUsableByTokenHash(anyString(), any());
    }

    @Test
    @DisplayName("Khong tim thay dong nao thi tra null, khong nem exception")
    void missingRowReturnsNull() {
        when(passwordResetTokenRepository.findUsableByTokenHash(anyString(), any()))
                .thenReturn(Optional.empty());

        assertNull(genService().findUsablePasswordResetToken("khong-ton-tai"));
    }

    // ========== TIEU TOKEN ==========

    /**
     * Phép tiêu token cũng phải băm trước khi xuống port — cùng lý do với đường tra.
     */
    @Test
    @DisplayName("Tieu token: gia tri di xuong port la HASH, va ket qua la rows-affected > 0")
    void consumeHashesAndReportsOutcome() throws Exception {
        String rawToken = "mot-chuoi-token-tho";
        when(passwordResetTokenRepository.markUsed(anyString(), any())).thenReturn(true);

        assertTrue(genService().consumePasswordResetToken(rawToken));

        verify(passwordResetTokenRepository).markUsed(eq(genExpectedHash(rawToken)), any());
    }

    /**
     * <b>Lần gọi thứ hai phải thất bại, và đó là cái chốt "dùng đúng một lần".</b> Ở đây port trả
     * {@code false} vì UPDATE có điều kiện khớp 0 dòng; tầng domain không được phép "sửa" kết quả đó
     * thành thành công.
     */
    @Test
    @DisplayName("Port bao 0 dong thi tieu token that bai — lan dung thu hai khong duoc phep thang")
    void consumeFailsWhenNoRowFlipped() {
        when(passwordResetTokenRepository.markUsed(anyString(), any())).thenReturn(false);

        assertFalse(genService().consumePasswordResetToken("da-dung-roi"));
    }

    @Test
    @DisplayName("Tieu mot token rong: khong cham vao DB, tra false")
    void consumeBlankTokenNeverReachesDatabase() {
        AuthDomainServiceImpl service = genService();

        assertFalse(service.consumePasswordResetToken(null));
        assertFalse(service.consumePasswordResetToken("  "));
        verify(passwordResetTokenRepository, never()).markUsed(anyString(), any());
    }

    // ========== THU HOI TOAN BO PHIEN ==========

    /**
     * <b>{@code revokeAllSessions} phải truyền {@code keepId = null} xuống port</b> — đó là giá trị
     * mà {@code RefreshTokenRepositoryImpl} chuẩn hoá thành canh gác {@code -1}, tức "không trừ dòng
     * nào". Truyền một id thật vào đây sẽ chừa lại đúng một phiên còn sống sau khi đặt lại mật khẩu,
     * và nếu tài khoản đã bị chiếm thì đó có thể là phiên của kẻ chiếm (backlog 0017 điều 5).
     */
    @Test
    @DisplayName("revokeAllSessions truyen keepId=null — KHONG chua dong nao")
    void revokeAllSessionsKeepsNothing() {
        when(refreshTokenRepository.revokeAllOfUserExcept(USER_ID, null)).thenReturn(3);

        assertEquals(3, genService().revokeAllSessions(USER_ID));

        verify(refreshTokenRepository).revokeAllOfUserExcept(USER_ID, null);
    }

    @Test
    @DisplayName("revokeAllSessions voi userId null: khong cham vao DB")
    void revokeAllSessionsIgnoresNullUser() {
        assertEquals(0, genService().revokeAllSessions(null));

        verify(refreshTokenRepository, never()).revokeAllOfUserExcept(any(), any());
    }
}
