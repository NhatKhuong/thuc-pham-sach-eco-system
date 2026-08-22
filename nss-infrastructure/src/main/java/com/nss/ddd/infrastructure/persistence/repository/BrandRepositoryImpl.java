package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.Brand;
import com.nss.ddd.domain.repository.BrandRepository;
import com.nss.ddd.infrastructure.persistence.mapper.BrandJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ADAPTER cho port {@code BrandRepository}.
 */
@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJPAMapper brandJPAMapper;

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJPAMapper.findById(id);
    }
}
