package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.Review;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quy tắc nghiệp vụ của đánh giá sản phẩm — API_CONTRACT §B.8 và §C.3.
 * <p>
 * <b>Hai luật sống ở đây, và cả hai đều là thứ hỏng trong im lặng nếu đặt nhầm chỗ:</b>
 * <ul>
 *   <li><b>Mỗi tài khoản một đánh giá mỗi sản phẩm</b> (ADR 0008) — thi hành bằng ràng buộc duy
 *       nhất của DB, không bằng một phép đọc chạy trước; xem {@link #create}.</li>
 *   <li><b>{@code product.rating} làm tròn HALF-UP</b> (coding-conventions §15) — xem
 *       {@link #genRating}.</li>
 * </ul>
 */
public interface ReviewDomainService {

    /**
     * Đánh giá của một sản phẩm, mới nhất trước.
     *
     * @param productId khóa chính của sản phẩm
     * @return danh sách đánh giá, rỗng nếu chưa có
     */
    List<Review> findByProductId(Long productId);

    /**
     * Số lượt theo từng mức sao — <b>chỉ những mức CÓ lượt</b>. Bù đủ năm mức là việc của tầng
     * application.
     *
     * @param productId khóa chính của sản phẩm
     * @return các mức sao có lượt, sắp tăng dần
     */
    List<RatingCount> countGroupedByRating(Long productId);

    /**
     * Ghi một đánh giá mới.
     * <p>
     * <b>{@code productId} phải là giá trị lấy từ PATH.</b> Trường {@code productId} trong
     * {@code CreateReviewPayload} bị bỏ qua ở tầng mapper — đúng kỷ luật "chặn cứng, không báo lỗi"
     * đã dùng cho {@code rating} / {@code reviewCount} / {@code sold} ở backlog 0008.
     * <p>
     * <b>Trả {@code false} chứ không ném exception khi trùng</b> — coding-conventions §11 Pattern A:
     * thất bại nghiệp vụ là giá trị trả về. Việc dịch nó thành 409 thuộc về controller.
     *
     * @param productId khóa chính của sản phẩm, lấy từ path
     * @param userId khóa chính của tài khoản, lấy từ claim {@code sub}
     * @param authorName tên hiển thị người dùng tự khai
     * @param rating điểm đánh giá {@code 1..5}
     * @param content nội dung đánh giá
     * @return đánh giá vừa ghi; {@code null} khi tài khoản đã đánh giá sản phẩm này rồi
     */
    Review create(Long productId, Long userId, String authorName, Integer rating, String content);

    /**
     * Tính lại {@code rating} và {@code reviewCount} của một sản phẩm từ bảng {@code review}, rồi
     * ghi xuống (§C.3).
     * <p>
     * <b>Tính lại từ nguồn thay vì cộng dồn.</b> Cộng dồn ({@code count + 1}, {@code sum + rating})
     * thì hai đường ghi đồng thời cùng đọc một con số cũ và cùng ghi đè — đúng cơ chế đọc-rồi-ghi
     * của {@code bugs/0004}, chỉ đổi bảng. Đọc lại {@code COUNT} / {@code SUM} thì kết quả luôn là
     * hàm của trạng thái hiện tại của bảng.
     *
     * @param product sản phẩm cần tính lại, đã nạp từ DB
     * @return chính sản phẩm đó sau khi đã ghi giá trị mới
     */
    Product recalcRatingStats(Product product);

    /**
     * Điểm trung bình theo quy ước <b>HALF-UP, 1 chữ số thập phân</b> — coding-conventions §15.
     * <p>
     * <b>Đây là nửa thứ hai của một quy ước hai nơi.</b> Nửa thứ nhất là
     * {@code environment/mysql/init/02-seed-data.sql}, tính bằng {@code ROUND(AVG(rating),1)} của
     * MySQL — cũng half-up. Hai nơi lệch quy ước thì giá trị <b>nhảy một bước 0.1 vào lúc không ai
     * đang nhìn</b>, và triệu chứng trông y hệt một cái bug trong khi mỗi phép tính đều "đúng" theo
     * quy ước của riêng nó.
     * <p>
     * <b>Sản phẩm 11 "Cam sành hữu cơ" là ca ghim:</b> bốn đánh giá {@code 5 + 4 + 5 + 3 = 17}, tức
     * {@code AVG} đúng bằng {@code 4.2500} — sản phẩm <i>duy nhất</i> trong 42 rơi vào ranh giới
     * {@code .x5}. Half-up cho {@code 4.3}; mặc định của {@code BigDecimal#setScale} là HALF_EVEN và
     * cho {@code 4.2}. Backlog 0006 đã tiên đoán đúng ca này.
     *
     * @param sumRating tổng điểm của mọi đánh giá
     * @param reviewCount số đánh giá
     * @return điểm trung bình, đúng 1 chữ số thập phân; {@code 0.0} khi chưa có đánh giá nào
     */
    BigDecimal genRating(long sumRating, long reviewCount);
}
