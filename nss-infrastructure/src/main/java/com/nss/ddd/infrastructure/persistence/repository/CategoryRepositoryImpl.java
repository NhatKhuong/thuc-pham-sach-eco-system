package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.infrastructure.persistence.mapper.CategoryJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code CategoryRepository}.
 */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJPAMapper categoryJPAMapper;

    @Override
    public Optional<Category> findById(Long id) {
        return categoryJPAMapper.findById(id);
    }

    @Override
    public List<Category> findAll() {
        return categoryJPAMapper.findAllByOrderByNameAsc();
    }

    @Override
    public List<Category> findRootCategories() {
        return categoryJPAMapper.findByParentIsNullOrderByNameAsc();
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return categoryJPAMapper.findBySlug(slug);
    }
}
