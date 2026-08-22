package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.ProductImage;
import com.nss.ddd.domain.repository.ProductImageRepository;
import com.nss.ddd.infrastructure.persistence.mapper.ProductImageJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * ADAPTER cho port {@code ProductImageRepository}.
 */
@Repository
@RequiredArgsConstructor
public class ProductImageRepositoryImpl implements ProductImageRepository {

    private final ProductImageJPAMapper productImageJPAMapper;

    @Override
    public List<ProductImage> findByProductId(Long productId) {
        return productImageJPAMapper.findByProductId(productId);
    }

    @Override
    public List<ProductImage> findByProductIdIn(Collection<Long> productIds) {
        return productImageJPAMapper.findByProductIdIn(productIds);
    }

    @Override
    public List<ProductImage> saveAll(List<ProductImage> images) {
        return productImageJPAMapper.saveAll(images);
    }

    @Override
    public int deleteByProductId(Long productId) {
        return productImageJPAMapper.deleteByProductId(productId);
    }
}
