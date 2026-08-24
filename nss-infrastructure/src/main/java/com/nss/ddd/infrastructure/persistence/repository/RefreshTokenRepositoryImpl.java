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
}
