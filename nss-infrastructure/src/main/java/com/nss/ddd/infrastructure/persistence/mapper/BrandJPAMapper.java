package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Brand;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data interface của {@code brand} — chỉ đủ để phân giải {@code product.brand_id}.
 */
public interface BrandJPAMapper extends JpaRepository<Brand, Long> {
}
