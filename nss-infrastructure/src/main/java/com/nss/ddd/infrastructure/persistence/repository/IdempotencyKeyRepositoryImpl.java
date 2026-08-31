package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.repository.IdempotencyKeyRepository;
import com.nss.ddd.infrastructure.persistence.mapper.IdempotencyKeyJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * ADAPTER cho port {@link IdempotencyKeyRepository}.
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryImpl implements IdempotencyKeyRepository {

    private final IdempotencyKeyJPAMapper idempotencyKeyJPAMapper;

    @Override
    public boolean tryInsert(Long eventId, LocalDateTime processedAt) {
        return idempotencyKeyJPAMapper.tryInsert(eventId, processedAt) > 0;
    }
}
