package com.nss;

import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;
import com.nss.ddd.infrastructure.persistence.repository.RefreshTokenRepositoryImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm việc chuẩn hoá {@code sid} vắng mặt ở adapter — không Spring context, không database.
 * <p>
 * <b>Đây là lưới chặn cho một lỗ hổng bảo mật IM LẶNG.</b> {@code id <> NULL} trong SQL cho ra
 * UNKNOWN, và SQL không coi UNKNOWN là true — nghĩa là câu UPDATE khớp <b>0 dòng</b>. Nếu
 * {@code null} được truyền thẳng xuống thì một lần đổi mật khẩu sẽ <i>không thu hồi phiên nào</i>,
 * kể cả phiên của kẻ đã chiếm được tài khoản, trong khi build xanh, test tính năng xanh, và response
 * vẫn đúng {@code 204}. Không có mã lỗi nào để mà bắt.
 * <p>
 * Vì vậy phép kiểm ở đây <b>không</b> hỏi "có gọi xuống JPA mapper không" — nó bắt lấy chính đối số
 * đã đi xuống và khẳng định đó là một giá trị <i>thật</i>, không phải {@code null}.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private RefreshTokenJPAMapper refreshTokenJPAMapper;

    /**
     * @return adapter sẵn sàng dùng
     */
    private RefreshTokenRepositoryImpl genRepository() {
        return new RefreshTokenRepositoryImpl(refreshTokenJPAMapper);
    }

    @Test
    @DisplayName("sid vang mat KHONG duoc di xuong SQL duoi dang null — id <> NULL thu hoi 0 dong")
    void missingSessionIdIsNormalisedToASentinel() {
        when(refreshTokenJPAMapper.markRevokedForUserExcept(anyLong(), anyLong())).thenReturn(3);

        int revoked = genRepository().revokeAllOfUserExcept(USER_ID, null);

        ArgumentCaptor<Long> keepId = ArgumentCaptor.forClass(Long.class);
        verify(refreshTokenJPAMapper).markRevokedForUserExcept(any(), keepId.capture());
        assertNotNull(keepId.getValue(),
                "null di thang xuong cau UPDATE se thu hoi 0 dong ma khong bao loi");
        assertTrue(keepId.getValue() < 0,
                "gia tri canh gac phai am de khong khop id AUTO_INCREMENT nao");
        assertEquals(3, revoked);
    }

    @Test
    @DisplayName("sid co that thi di xuong nguyen ven — phien hien tai duoc giu lai")
    void presentSessionIdIsPassedThroughUnchanged() {
        when(refreshTokenJPAMapper.markRevokedForUserExcept(USER_ID, 42L)).thenReturn(2);

        int revoked = genRepository().revokeAllOfUserExcept(USER_ID, 42L);

        verify(refreshTokenJPAMapper).markRevokedForUserExcept(USER_ID, 42L);
        assertEquals(2, revoked);
    }

    /**
     * <b>{@code 0} là một thành công hợp lệ ở đúng method này</b>, khác hẳn hai phép thu hồi kia nơi
     * {@code 0} nghĩa là thua cuộc đua. Người dùng chỉ đăng nhập trên một thiết bị thì không có
     * phiên nào khác để đá — ép kiểu trả về về {@code boolean} sẽ biến ca thường gặp nhất thành ca
     * trông như lỗi.
     */
    @Test
    @DisplayName("Thu hoi 0 dong van la ket qua hop le, khong bi doi thanh that bai")
    void zeroRevokedRowsIsReturnedAsIs() {
        when(refreshTokenJPAMapper.markRevokedForUserExcept(USER_ID, 42L)).thenReturn(0);

        assertEquals(0, genRepository().revokeAllOfUserExcept(USER_ID, 42L));
    }
}
