package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.EmailConfirmationToken;
import com.nss.ddd.domain.repository.EmailConfirmationTokenRepository;
import com.nss.ddd.infrastructure.persistence.mapper.EmailConfirmationTokenJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ADAPTER cho port {@code EmailConfirmationTokenRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: rows-affected là khái niệm của tầng này, domain chỉ thấy {@code boolean}
 * (coding-conventions §12) — cùng khuôn {@code PasswordResetTokenRepositoryImpl}.
 */
@Repository
@RequiredArgsConstructor
public class EmailConfirmationTokenRepositoryImpl implements EmailConfirmationTokenRepository {

    private final EmailConfirmationTokenJPAMapper emailConfirmationTokenJPAMapper;

    @Override
    public EmailConfirmationToken save(EmailConfirmationToken emailConfirmationToken) {
        return emailConfirmationTokenJPAMapper.save(emailConfirmationToken);
    }

    @Override
    public Optional<EmailConfirmationToken> findUsableByTokenHash(String tokenHash, LocalDateTime now) {
        return emailConfirmationTokenJPAMapper.findUsableByTokenHash(tokenHash, now);
    }

    @Override
    public boolean markUsed(String tokenHash, LocalDateTime now) {
        return emailConfirmationTokenJPAMapper.markUsed(tokenHash, now) > 0;
    }
}
