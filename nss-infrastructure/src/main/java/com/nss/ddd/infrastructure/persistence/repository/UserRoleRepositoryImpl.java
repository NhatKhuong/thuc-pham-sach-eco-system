package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.UserRole;
import com.nss.ddd.domain.repository.UserRoleRepository;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code UserRoleRepository}.
 */
@Repository
@RequiredArgsConstructor
public class UserRoleRepositoryImpl implements UserRoleRepository {

    private final UserRoleJPAMapper userRoleJPAMapper;

    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        return userRoleJPAMapper.findRoleCodesByUserId(userId);
    }

    @Override
    public Optional<Role> findRoleByCode(String code) {
        return userRoleJPAMapper.findRoleByCode(code);
    }

    @Override
    public UserRole save(UserRole userRole) {
        return userRoleJPAMapper.save(userRole);
    }
}
