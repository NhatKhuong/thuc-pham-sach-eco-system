package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ADAPTER cho port {@code UserRepository}.
 * <p>
 * Stereotype là {@code @Repository}, không phải {@code @Service} (coding-conventions §3).
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJPAMapper userJPAMapper;

    @Override
    public Optional<User> findByEmail(String email) {
        return userJPAMapper.findByEmail(email);
    }

    @Override
    public Optional<User> findById(Long id) {
        // findById cua JpaRepository da co san — khong can khai them method nao o UserJPAMapper
        return userJPAMapper.findById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJPAMapper.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        return userJPAMapper.save(user);
    }
}
