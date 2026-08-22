package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Category;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data interface của {@code category} — chỉ đủ để phân giải {@code product.category_id}.
 */
public interface CategoryJPAMapper extends JpaRepository<Category, Long> {
}
