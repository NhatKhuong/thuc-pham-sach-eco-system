package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payload của {@code GET /products/{id}/reviews/summary} — khớp
 * {@code types/product.ts#ReviewSummary} (API_CONTRACT §B.8).
 * <p>
 * <b>Không có bảng nào đứng sau kiểu này, và đó là chủ ý</b> (backlog 0004 liệt nó vào bốn field
 * "cố ý không có"): nó là kết quả {@code GROUP BY rating} trên bảng {@code review}.
 * <p>
 * <b>{@link #distribution} là object khoá CHUỖI {@code "1"}…{@code "5"}, không phải mảng 5 phần tử
 * và không phải khoá số.</b> Trả về một mảng thì biểu đồ phân bố của frontend <b>rỗng mà không nổ
 * lỗi nào</b>. Kiểu {@code Map<String, Long>} khai ra đúng điều đó ngay ở chữ ký.
 * <p>
 * <b>Cả năm khoá LUÔN có mặt, kể cả mức sao 0 lượt.</b> {@code GROUP BY} trần bỏ hẳn mức không ai
 * chọn khỏi kết quả; bù đủ là việc của {@code ReviewMapper}. Cùng họ với bẫy zero-fill đã đo ở
 * backlog 0019 (29/30 ô ngày = 0).
 * <p>
 * <b>{@link #average} làm tròn HALF-UP một chữ số thập phân — cùng quy ước với
 * {@code product.rating}</b> (coding-conventions §15). Hai chỗ này hiện cạnh nhau trên cùng một
 * màn hình: điểm sao trên thẻ sản phẩm đọc {@code product.rating}, còn biểu đồ ngay bên dưới đọc
 * trường này. Để chúng làm tròn khác nhau là để một trang hiện {@code 4.2} ở nửa trên và
 * {@code 4.3} ở nửa dưới — không lỗi nào nổ ra, và không chỗ nào nói ra là vì sao.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponse {

    /** Điểm trung bình, HALF-UP 1 chữ số thập phân; {@code 0.0} khi chưa có đánh giá nào. */
    private BigDecimal average;

    /** Tổng số đánh giá. */
    private long total;

    /** Số lượt theo mức sao — khoá chuỗi {@code "1"}…{@code "5"}, <b>luôn đủ năm khoá</b>. */
    private Map<String, Long> distribution;
}
