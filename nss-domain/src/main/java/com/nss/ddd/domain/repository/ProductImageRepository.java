package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.ProductImage;

import java.util.Collection;
import java.util.List;

/**
 * PORT của bảng con {@code product_image} — hiện thực của {@code Product.images: string[]} phía client.
 * <p>
 * Không import gì của Spring Data, cùng lý do với {@link ProductRepository}.
 */
public interface ProductImageRepository {

    /**
     * Ảnh của một sản phẩm, đã sắp theo {@code sortOrder} tăng dần.
     *
     * @param productId khóa chính của sản phẩm
     * @return danh sách ảnh, rỗng nếu sản phẩm chưa có ảnh nào
     */
    List<ProductImage> findByProductId(Long productId);

    /**
     * Ảnh của nhiều sản phẩm trong <b>một</b> truy vấn — đường đọc của trang danh sách.
     * <p>
     * Tồn tại để tránh N+1: 12 sản phẩm mỗi trang mà hỏi từng cái thì thành 13 truy vấn.
     *
     * @param productIds tập khóa chính cần lấy ảnh
     * @return danh sách ảnh của tất cả sản phẩm, sắp theo {@code productId} rồi {@code sortOrder}
     */
    List<ProductImage> findByProductIdIn(Collection<Long> productIds);

    /**
     * Ghi một loạt ảnh.
     *
     * @param images các ảnh cần ghi
     * @return các bản ghi sau khi ghi, đã có id
     */
    List<ProductImage> saveAll(List<ProductImage> images);

    /**
     * Xoá cứng toàn bộ ảnh của một sản phẩm — dùng cho {@code PUT}, nơi mảng ảnh mới thay trọn
     * mảng cũ. Ảnh không có khóa ngoại nào trỏ vào nên xoá cứng ở đây là an toàn.
     *
     * @param productId khóa chính của sản phẩm
     * @return số dòng đã xoá
     */
    int deleteByProductId(Long productId);
}
