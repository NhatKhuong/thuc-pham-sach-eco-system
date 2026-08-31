package com.nss.ddd.application.service.review;

import com.nss.ddd.application.model.command.CreateReviewCommand;
import com.nss.ddd.application.model.response.ReviewMutationResponse;
import com.nss.ddd.application.model.response.ReviewResponse;
import com.nss.ddd.application.model.response.ReviewSummaryResponse;

import java.util.List;

/**
 * Use case đánh giá sản phẩm — API_CONTRACT §B.8 và §C.3.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code ReviewDomainService}, tầng này
 * chỉ hỏi domain rồi lắp kết quả thành kiểu của bề mặt dây.
 * <p>
 * <b>Cả ba đường đều khoá theo {@code productId} SỐ, không theo {@code slug}</b> — khác §B.1
 * ({@code GET /products/{slug}}). Hai cách đánh khoá cạnh nhau trên cùng một tài nguyên là có chủ
 * ý ở hợp đồng, và ca hỏng của nó thì <i>ồn ào</i> (404), không im lặng.
 * <p>
 * <b>Cả ba đường trả {@code null} khi sản phẩm không tồn tại HOẶC đã bị xoá mềm</b>, và controller
 * dịch thành 404. Trả mảng rỗng thay vào đó sẽ khiến frontend hiện "chưa có đánh giá nào" cho một
 * sản phẩm <i>không tồn tại</i>.
 */
public interface ReviewAppService {

    /**
     * Đánh giá của một sản phẩm, mới nhất trước.
     * <p>
     * <b>Mảng trần, KHÔNG phân trang</b> — §B.8 khai {@code Review[]} chứ không phải
     * {@code Paginated<Review>}; bọc envelope §A.4 vào đây là contract change.
     *
     * @param productId khóa chính của sản phẩm
     * @return danh sách đánh giá (có thể rỗng), hoặc {@code null} khi sản phẩm không tồn tại / đã
     *         bị xoá mềm
     */
    List<ReviewResponse> findReviews(Long productId);

    /**
     * Tổng hợp điểm đánh giá của một sản phẩm — {@code average}, {@code total},
     * {@code distribution} đủ năm mức sao.
     *
     * @param productId khóa chính của sản phẩm
     * @return summary, hoặc {@code null} khi sản phẩm không tồn tại / đã bị xoá mềm
     */
    ReviewSummaryResponse findSummary(Long productId);

    /**
     * Ghi một đánh giá mới rồi <b>tính lại {@code rating} / {@code reviewCount} của sản phẩm</b>
     * (§C.3), cả hai trong một transaction.
     *
     * @param command lệnh tạo; {@code productId} đã lấy từ path và {@code userId} từ claim
     *                {@code sub}
     * @return kết quả mang đánh giá đã ghi, hoặc mã lỗi nghiệp vụ kèm thông điệp tiếng Việt
     */
    ReviewMutationResponse createReview(CreateReviewCommand command);
}
