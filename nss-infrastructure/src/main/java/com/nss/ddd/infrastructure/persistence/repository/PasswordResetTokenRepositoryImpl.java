package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.PasswordResetToken;
import com.nss.ddd.domain.repository.PasswordResetTokenRepository;
import com.nss.ddd.infrastructure.persistence.mapper.PasswordResetTokenJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ADAPTER cho port {@code PasswordResetTokenRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: rows-affected là khái niệm của tầng này, domain chỉ thấy {@code boolean}
 * (coding-conventions §12). Khác {@code RefreshTokenRepositoryImpl.revokeAllOfUserExcept} — ở đó
 * {@code 0} là một thành công hợp lệ nên con số đi thẳng ra ngoài; ở đây {@code 0} là <b>thất
 * bại</b>: token không tồn tại, đã dùng, hoặc đã hết hạn.
 */
@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenJPAMapper passwordResetTokenJPAMapper;

    @Override
    public PasswordResetToken save(PasswordResetToken passwordResetToken) {
        return passwordResetTokenJPAMapper.save(passwordResetToken);
    }

    @Override
    public Optional<PasswordResetToken> findUsableByTokenHash(String tokenHash, LocalDateTime now) {
        return passwordResetTokenJPAMapper.findUsableByTokenHash(tokenHash, now);
    }

    @Override
    public boolean markUsed(String tokenHash, LocalDateTime now) {
        return passwordResetTokenJPAMapper.markUsed(tokenHash, now) > 0;
    }
}
