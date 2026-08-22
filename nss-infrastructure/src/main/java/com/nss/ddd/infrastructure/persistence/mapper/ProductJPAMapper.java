package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data interface của {@code product} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * Mọi đường đọc đều {@code LEFT JOIN FETCH} {@code category} và {@code brand}. Lý do phải viết ra:
 * {@code open-in-view: false} nên session đóng ngay khi repository trả về, và {@code ProductResponse}
 * cần {@code categoryId} / {@code brandId}. Để lazy thì việc đọc id đi qua proxy — hoặc ném
 * {@code LazyInitializationException}, hoặc bắn thêm một truy vấn cho mỗi sản phẩm. Quan hệ
 * {@code @ManyToOne} nên fetch join không nhân bản dòng và không phá phân trang.
 */
public interface ProductJPAMapper extends JpaRepository<Product, Long> {

    /**
     * Một trang sản phẩm còn hiệu lực.
     * <p>
     * {@code ORDER BY p.id} nằm trong câu truy vấn chứ không nằm ở {@code Pageable}: thứ tự phải
     * <b>ổn định</b> thì phân trang mới có nghĩa — không có ORDER BY, MySQL được phép trả cùng một
     * dòng ở hai trang khác nhau và bỏ sót dòng khác, mà không có gì báo lỗi.
     * <p>
     * {@code countQuery} khai tường minh vì Spring Data không suy được câu đếm từ truy vấn có
     * {@code JOIN FETCH}.
     *
     * @param pageable trang cần lấy, <b>đã đánh số từ 0</b> — adapter là nơi trừ 1
     * @return trang sản phẩm kèm tổng số dòng
     */
    @Query(value = "SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.isActive = true"
            + " ORDER BY p.id ASC",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    Page<Product> findActivePage(Pageable pageable);

    /**
     * @param slug slug cần tra
     * @return sản phẩm còn hiệu lực, hoặc rỗng
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.slug = :slug AND p.isActive = true")
    Optional<Product> findActiveBySlug(@Param("slug") String slug);

    /**
     * @param id khóa chính
     * @return sản phẩm còn hiệu lực, hoặc rỗng
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findActiveById(@Param("id") Long id);

    /**
     * Đếm cả bản ghi đã xoá mềm — {@code uk_slug} nằm trên toàn bảng, không quan tâm {@code is_active}.
     *
     * @param slug slug cần kiểm
     * @return true nếu đã có dòng nào giữ slug này
     */
    boolean existsBySlug(String slug);

    /**
     * Xoá mềm bằng UPDATE có điều kiện — {@code AND p.isActive = true} khiến lần gọi thứ hai trả 0
     * dòng, nhờ đó tầng trên phân biệt được "đã xoá xong" với "không có gì để xoá".
     *
     * @param id khóa chính
     * @param deletedAt thời điểm xoá, giờ UTC
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.isActive = false, p.updatedAt = :deletedAt"
            + " WHERE p.id = :id AND p.isActive = true")
    int markInactive(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}
