package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code review} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * <b>Không đường đọc nào ở đây chạm tới {@code r.user}</b>, và đó là một phần của việc chặn rò
 * {@code user_id} ra response: quan hệ để LAZY và không có {@code JOIN FETCH} nào kéo nó lên, nên
 * tầng trên <i>không có gì</i> để vô tình map ra ngoài. Chỗ chặn thứ hai — danh sách trường viết
 * tay — nằm ở {@code ReviewMapper}.
 */
public interface ReviewJPAMapper extends JpaRepository<Review, Long> {

    /**
     * Đánh giá của một sản phẩm, <b>mới nhất trước</b>.
     * <p>
     * <b>{@code JOIN FETCH r.product} là bắt buộc chứ không phải tối ưu</b>, đúng lý do đã ghi ở
     * {@code ProductImageJPAMapper#findByProductIdIn}: {@code open-in-view: false} nên session đóng
     * ngay khi repository trả về, còn {@code ReviewMapper} thì đọc {@code review.getProduct()
     * .getId()} để dựng trường {@code productId}. Đây là quan hệ {@code @ManyToOne} tới đúng
     * <i>một</i> sản phẩm nên fetch join không nhân bản dòng nào.
     * <p>
     * <b>{@code r.id DESC} là khoá phụ, không phải trang trí.</b> Bốn mươi tám đánh giá đã seed
     * mang mốc thời gian {@code 00:00:00} theo ngày, nên trùng {@code created_at} là chuyện thường
     * chứ không phải ca hiếm; không có khoá phụ thì MySQL được phép trả hai thứ tự khác nhau ở hai
     * lần chạy, và không có gì báo lỗi.
     *
     * @param productId khóa chính của sản phẩm
     * @return đánh giá của sản phẩm, mới nhất trước
     */
    @Query("SELECT r FROM Review r JOIN FETCH r.product"
            + " WHERE r.product.id = :productId"
            + " ORDER BY r.createdAt DESC, r.id DESC")
    List<Review> findByProductId(@Param("productId") Long productId);

    /**
     * {@code GROUP BY rating} — nguồn của {@code ReviewSummary.distribution} (§B.8).
     * <p>
     * Dùng <b>constructor expression</b> thay vì {@code Object[]}: coding-conventions §12 chỉ cho
     * phép map {@code Object[]} theo vị trí ở native query. Cùng khuôn với
     * {@code OrderJPAMapper#countGroupedByStatus}.
     * <p>
     * <b>Mức sao 0 lượt KHÔNG có mặt trong kết quả</b> — {@code GROUP BY} không sinh ra dòng cho
     * thứ không tồn tại. Bù đủ năm mức là việc của tầng application.
     *
     * @param productId khóa chính của sản phẩm
     * @return các mức sao có lượt, sắp tăng dần
     */
    @Query("SELECT new com.nss.ddd.domain.model.RatingCount(r.rating, COUNT(r))"
            + " FROM Review r"
            + " WHERE r.product.id = :productId"
            + " GROUP BY r.rating"
            + " ORDER BY r.rating ASC")
    List<RatingCount> countGroupedByRating(@Param("productId") Long productId);

    /**
     * @param productId khóa chính của sản phẩm
     * @return số đánh giá của sản phẩm
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId")
    long countByProductId(@Param("productId") Long productId);

    /**
     * Tổng điểm đánh giá — tử số của {@code product.rating} (§C.3).
     * <p>
     * <b>{@code COALESCE(..., 0)} là bắt buộc:</b> {@code SUM} trên tập rỗng trả {@code NULL}, và
     * một {@code NULL} unbox sang {@code long} là {@code NullPointerException} chứ không phải số 0
     * — sản phẩm chưa có đánh giá nào là ca thường gặp nhất (24/42 sản phẩm trong seed).
     *
     * @param productId khóa chính của sản phẩm
     * @return tổng điểm, {@code 0} khi chưa có đánh giá nào
     */
    @Query("SELECT COALESCE(SUM(r.rating), 0) FROM Review r WHERE r.product.id = :productId")
    long sumRatingByProductId(@Param("productId") Long productId);

    /**
     * {@code INSERT IGNORE} — cổng chống trùng atomic của {@code uk_review_product_user}.
     * <p>
     * <b>Ràng buộc của DB là thứ quyết định, không phải một phép {@code SELECT} chạy trước.</b>
     * Đọc-rồi-ghi để lại cửa sổ giữa hai bước: hai request đồng thời cùng tài khoản cùng sản phẩm
     * cùng đọc thấy "chưa có" rồi cùng ghi, và bên thua chết bằng lỗi ràng buộc — một 500 cho thứ
     * lẽ ra là 409. Đó đúng là cơ chế của {@code bugs/0004}. Ở đây engine tự quyết trong một câu
     * lệnh, nên bên thua nhận về <b>0 dòng ảnh hưởng</b> và tầng trên biến nó thành 409.
     * <p>
     * <b>Vì sao {@code INSERT IGNORE} chứ không phải {@code INSERT ... ON DUPLICATE KEY UPDATE}</b>
     * — cả hai đều atomic, nhưng chỉ cái thứ nhất đếm được: với {@code CLIENT_FOUND_ROWS} (mặc
     * định của Connector/J qua {@code useAffectedRows=false}), một {@code ON DUPLICATE KEY UPDATE}
     * không đổi gì vẫn báo <b>1</b> dòng ảnh hưởng, y hệt lúc chèn thành công. Phép phân biệt
     * 0-hay-1 sẽ <i>luôn</i> nói "chèn được" và luật mỗi-tài-khoản-một-đánh-giá biến mất trong im
     * lặng. {@code INSERT IGNORE} không chịu ảnh hưởng của cờ đó: {@code 1} là chèn, {@code 0} là
     * bỏ qua.
     * <p>
     * <b>Cái giá của {@code INSERT IGNORE}, ghi ra để không ai bất ngờ:</b> nó hạ <i>mọi</i> lỗi
     * toàn vẹn xuống thành cảnh báo, không riêng lỗi trùng khoá — một chuỗi quá dài sẽ bị
     * <b>cắt bớt trong im lặng</b> thay vì nổ ra. Vì vậy hai vế chặn phải nằm <i>trước</i> câu lệnh
     * này và không được gỡ: {@code @Size} theo đúng độ dài cột ở {@code CreateReviewRequest}, và
     * phép tra sản phẩm / tài khoản ở tầng application — thiếu vế sau thì một khoá ngoại hỏng sẽ bị
     * báo cáo nhầm thành "bạn đã đánh giá rồi".
     * <p>
     * Native vì {@code INSERT IGNORE} là cú pháp riêng của MySQL — coding-conventions §12 gọi đích
     * danh đúng trường hợp này. Tham số {@code :named}, không có gì được nối chuỗi vào SQL.
     *
     * @param productId khóa chính của sản phẩm, lấy từ path
     * @param userId khóa chính của tài khoản, lấy từ claim {@code sub}
     * @param authorName tên hiển thị người dùng tự khai
     * @param rating điểm đánh giá {@code 1..5}
     * @param content nội dung đánh giá
     * @param createdAt thời điểm tạo, giờ UTC
     * @return số dòng bị ảnh hưởng — {@code 1} là đã ghi; {@code 0} là tài khoản đã đánh giá sản
     *         phẩm này rồi
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO review"
            + " (product_id, user_id, author_name, rating, content, created_at)"
            + " VALUES (:productId, :userId, :authorName, :rating, :content, :createdAt)",
            nativeQuery = true)
    int insertIgnore(@Param("productId") Long productId,
                     @Param("userId") Long userId,
                     @Param("authorName") String authorName,
                     @Param("rating") Integer rating,
                     @Param("content") String content,
                     @Param("createdAt") LocalDateTime createdAt);

    /**
     * Đọc lại đánh giá của một tài khoản trên một sản phẩm.
     * <p>
     * {@code uk_review_product_user} bảo đảm nhiều nhất một dòng thoả, nên {@code Optional} ở đây
     * là một khẳng định của schema chứ không phải một hy vọng.
     *
     * @param productId khóa chính của sản phẩm
     * @param userId khóa chính của tài khoản
     * @return đánh giá, hoặc rỗng
     */
    @Query("SELECT r FROM Review r JOIN FETCH r.product"
            + " WHERE r.product.id = :productId AND r.user.id = :userId")
    Optional<Review> findByProductIdAndUserId(@Param("productId") Long productId,
                                              @Param("userId") Long userId);
}
