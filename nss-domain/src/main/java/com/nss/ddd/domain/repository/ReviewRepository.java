package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Review;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PORT của bảng {@code review} — API_CONTRACT §B.8.
 * <p>
 * Không import gì của Spring Data, cùng lý do với {@link ProductRepository}: domain khai cái nó
 * cần, hạ tầng lo cách lấy.
 * <p>
 * <b>Không có method {@code save(Review)} nào, và đó là chủ ý.</b> Đường ghi duy nhất là
 * {@link #insertIfAbsent} — xem javadoc của nó. Một {@code save} trần cạnh đó là cánh cửa để ai đó
 * quay lại lối đọc-rồi-ghi mà ADR 0008 và {@code bugs/0004} đã loại.
 */
public interface ReviewRepository {

    /**
     * Đánh giá của một sản phẩm, <b>mới nhất trước</b>.
     * <p>
     * Trả mảng trần, <b>không phân trang</b> — §B.8 khai {@code Review[]} chứ không phải
     * {@code Paginated<Review>}, và đổi hình dạng đó là contract change.
     *
     * @param productId khóa chính của sản phẩm
     * @return danh sách đánh giá, rỗng nếu sản phẩm chưa có đánh giá nào
     */
    List<Review> findByProductId(Long productId);

    /**
     * Số lượt theo từng mức sao — nguồn của {@code ReviewSummary.distribution}.
     * <p>
     * <b>Chỉ trả về những mức sao CÓ lượt.</b> Bù đủ năm mức là việc của tầng application; xem
     * {@link RatingCount}.
     *
     * @param productId khóa chính của sản phẩm
     * @return các mức sao có lượt, sắp tăng dần theo mức sao
     */
    List<RatingCount> countGroupedByRating(Long productId);

    /**
     * Tổng số đánh giá của một sản phẩm — nguồn của {@code product.review_count} (§C.3).
     *
     * @param productId khóa chính của sản phẩm
     * @return số đánh giá, {@code 0} nếu chưa có
     */
    long countByProductId(Long productId);

    /**
     * Tổng điểm đánh giá của một sản phẩm — tử số của {@code product.rating} (§C.3).
     * <p>
     * Trả <b>tổng</b> chứ không trả trung bình: phép chia và quy ước làm tròn thuộc về domain
     * service, không thuộc về SQL. Để SQL tự chia là để hai nơi (seed và runtime) làm tròn theo hai
     * quy ước khác nhau — đúng thứ coding-conventions §15 pin lại để tránh.
     *
     * @param productId khóa chính của sản phẩm
     * @return tổng điểm, {@code 0} nếu chưa có đánh giá nào
     */
    long sumRatingByProductId(Long productId);

    /**
     * Ghi một đánh giá, <b>để ràng buộc duy nhất của DB quyết định có trùng hay không</b>.
     * <p>
     * <b>Đây là chỗ luật "mỗi tài khoản một đánh giá mỗi sản phẩm" được thi hành, và nó KHÔNG được
     * dựng bằng {@code SELECT} rồi {@code INSERT}.</b> Đọc-rồi-ghi để lại một cửa sổ giữa hai bước:
     * hai request đồng thời cùng tài khoản cùng sản phẩm sẽ cùng đọc thấy "chưa có", cùng ghi, và
     * bên thua chết bằng lỗi ràng buộc — một 500 cho thứ lẽ ra là 409. Đó đúng là cơ chế đã sinh ra
     * {@code bugs/0004}.
     *
     * @param productId khóa chính của sản phẩm, <b>lấy từ path</b> chứ không từ body
     * @param userId khóa chính của tài khoản, lấy từ claim {@code sub}; không bao giờ {@code null}
     *               trên đường ghi
     * @param authorName tên hiển thị người dùng tự khai
     * @param rating điểm đánh giá {@code 1..5}
     * @param content nội dung, tối thiểu 10 ký tự
     * @param createdAt thời điểm tạo, giờ UTC
     * @return {@code true} khi ghi được; {@code false} khi tài khoản đã đánh giá sản phẩm này rồi
     */
    boolean insertIfAbsent(Long productId, Long userId, String authorName, Integer rating,
                           String content, LocalDateTime createdAt);

    /**
     * Đọc lại đánh giá của một tài khoản trên một sản phẩm — dùng ngay sau
     * {@link #insertIfAbsent} để lấy {@code id} và trả về payload đầy đủ.
     * <p>
     * Đây là một phép <b>đọc sau một lần ghi đã chắc chắn thành công</b>, không phải phép kiểm
     * trùng: {@code uk_review_product_user} bảo đảm nó trả về đúng dòng vừa ghi.
     *
     * @param productId khóa chính của sản phẩm
     * @param userId khóa chính của tài khoản
     * @return đánh giá, hoặc rỗng
     */
    Optional<Review> findByProductIdAndUserId(Long productId, Long userId);
}
