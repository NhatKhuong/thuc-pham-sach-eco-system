package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.ProductImage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data interface của {@code product_image}.
 */
public interface ProductImageJPAMapper extends JpaRepository<ProductImage, Long> {

    /**
     * @param productId khóa chính của sản phẩm
     * @return ảnh của sản phẩm, sắp theo thứ tự hiển thị
     */
    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId ORDER BY i.sortOrder ASC")
    List<ProductImage> findByProductId(@Param("productId") Long productId);

    /**
     * Ảnh của nhiều sản phẩm trong một truy vấn — đường đọc của trang danh sách, tránh N+1.
     * <p>
     * {@code JOIN FETCH i.product} là bắt buộc chứ không phải tối ưu: tầng trên gom ảnh theo
     * {@code image.getProduct().getId()}, mà {@code open-in-view: false} thì proxy lazy đã hết
     * session để tự nạp.
     *
     * @param productIds tập khóa chính
     * @return ảnh của tất cả sản phẩm, sắp theo {@code productId} rồi {@code sortOrder}
     */
    @Query("SELECT i FROM ProductImage i JOIN FETCH i.product p"
            + " WHERE p.id IN :productIds"
            + " ORDER BY p.id ASC, i.sortOrder ASC")
    List<ProductImage> findByProductIdIn(@Param("productIds") Collection<Long> productIds);

    /**
     * @param productId khóa chính của sản phẩm
     * @return số dòng đã xoá
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ProductImage i WHERE i.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
