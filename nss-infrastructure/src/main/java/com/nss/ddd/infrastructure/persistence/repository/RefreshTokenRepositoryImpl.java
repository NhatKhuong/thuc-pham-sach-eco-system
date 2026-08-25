package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.RefreshToken;
import com.nss.ddd.domain.repository.RefreshTokenRepository;
import com.nss.ddd.infrastructure.persistence.mapper.RefreshTokenJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ADAPTER cho port {@code RefreshTokenRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: rows-affected là khái niệm của tầng này, domain chỉ thấy {@code boolean}
 * (coding-conventions §12).
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {

    /**
     * Giá trị canh gác thay cho {@code sid} vắng mặt.
     * <p>
     * <b>Đây là chỗ một lỗ hổng bảo mật im lặng bị chặn, không phải một phép phòng thủ thừa.</b>
     * Cột {@code id} là {@code AUTO_INCREMENT} nên không dòng nào mang giá trị âm; {@code rt.id <>
     * -1} vì vậy đúng với <i>mọi</i> dòng, và ca "token cấp trước khi claim {@code sid} ra đời"
     * hỏng về <b>phía an toàn</b>: thu hồi tất cả, kể cả phiên đang gọi.
     * <p>
     * Truyền thẳng {@code null} xuống thì {@code rt.id <> NULL} cho ra UNKNOWN — SQL không coi
     * UNKNOWN là true, nên câu UPDATE khớp 0 dòng. Mật khẩu đổi xong, response vẫn 204, build vẫn
     * xanh, và <i>không phiên nào chết</i> — kể cả phiên của kẻ đã chiếm được tài khoản. Đừng "đơn
     * giản hoá" dòng này.
     */
    private static final Long NO_SESSION_SENTINEL = -1L;

    private final RefreshTokenJPAMapper refreshTokenJPAMapper;

    @Override
    public Optional<RefreshToken> findUsableByToken(String token, LocalDateTime now) {
        return refreshTokenJPAMapper.findUsableByToken(token, now);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJPAMapper.save(refreshToken);
    }

    @Override
    public boolean revokeByToken(String token) {
        return refreshTokenJPAMapper.markRevoked(token) > 0;
    }

    @Override
    public boolean revokeByTokenAndUserId(String token, Long userId) {
        return refreshTokenJPAMapper.markRevokedForUser(token, userId) > 0;
    }

    /**
     * <b>Ngoại lệ có chủ ý với quy ước "domain chỉ thấy boolean".</b> Rows-affected đi thẳng ra
     * ngoài ở đúng method này vì {@code 0} không phải thất bại: nó là ca người dùng chỉ có một
     * thiết bị. Lý do đầy đủ nằm ở javadoc của port.
     *
     * @param userId chủ sở hữu
     * @param keepId id phiên được giữ lại; {@code null} thì thu hồi tất cả
     * @return số phiên vừa bị thu hồi
     */
    @Override
    public int revokeAllOfUserExcept(Long userId, Long keepId) {
        return refreshTokenJPAMapper.markRevokedForUserExcept(userId,
                keepId == null ? NO_SESSION_SENTINEL : keepId);
    }
}
