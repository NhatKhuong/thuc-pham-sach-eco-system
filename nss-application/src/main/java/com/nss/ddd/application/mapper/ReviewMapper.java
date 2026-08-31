package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.response.ReviewResponse;
import com.nss.ddd.application.model.response.ReviewSummaryResponse;
import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Review;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converter viết tay giữa {@code Review} và các kiểu của tầng application.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7).
 * <p>
 * <b>Đây là chỗ chặn {@code user_id} rò ra response.</b> {@link #toResponse} liệt kê tay đúng sáu
 * trường của {@code types/product.ts#Review}; bảng có bảy cột từ ADR 0008 trở đi và cột thứ bảy
 * không nằm trong danh sách. Cùng cách chặn mà {@code ProductMapper} dùng cho {@code isActive}
 * (backlog 0008) — một danh sách viết ra bằng tay không thể "vô tình" mọc thêm một trường khi
 * schema đổi, còn {@code BeanUtils.copyProperties} hay MapStruct thì có.
 * <p>
 * <b>Không có phép làm tròn nào trong file này.</b> {@code average} đi vào
 * {@link #toSummaryResponse} như một tham số đã tính sẵn, chứ không được tính lại ở đây: quy ước
 * làm tròn của điểm đánh giá có đúng <b>một</b> bản, ở
 * {@code ReviewDomainService#genRating} (coding-conventions §15). Chép nó ra bản thứ hai là cách
 * chắc chắn để hai bên lệch nhau vào đúng lúc chỉ một bên được sửa — cùng lý lẽ §18 đã dùng cho
 * phép bỏ dấu.
 */
public final class ReviewMapper {

    /** Mức sao thấp nhất của {@code ReviewSummary.distribution}. */
    private static final int RATING_MIN = 1;

    /** Mức sao cao nhất của {@code ReviewSummary.distribution}. */
    private static final int RATING_MAX = 5;

    /**
     * Class tiện ích, không có thể hiện.
     */
    private ReviewMapper() {
    }

    /**
     * Dựng payload cho bề mặt dây — <b>đúng sáu trường</b>.
     * <p>
     * <b>{@code review.getProduct()} phải đã được nạp</b> ({@code JOIN FETCH} ở
     * {@code ReviewJPAMapper}): {@code open-in-view: false} nên session đã đóng khi tới đây.
     * <p>
     * <b>Không có dòng nào đọc {@code review.getUser()}, và đừng thêm.</b> Xem javadoc cấp class.
     *
     * @param review entity, đã nạp sẵn {@code product}
     * @return payload, hoặc {@code null} khi {@code review} rỗng
     */
    public static ReviewResponse toResponse(Review review) {
        if (review == null) {
            return null;
        }
        return new ReviewResponse()
                .setId(review.getId())
                .setProductId(review.getProduct() == null ? null : review.getProduct().getId())
                .setAuthorName(review.getAuthorName())
                .setRating(review.getRating())
                .setContent(review.getContent())
                .setCreatedAt(toIsoUtc(review.getCreatedAt()));
    }

    /**
     * @param reviews entity, đã nạp sẵn {@code product}
     * @return danh sách payload; danh sách rỗng khi đầu vào rỗng
     */
    public static List<ReviewResponse> toResponses(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReviewResponse> items = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            items.add(toResponse(review));
        }
        return items;
    }

    /**
     * Dựng {@code ReviewSummary} từ kết quả {@code GROUP BY rating}.
     * <p>
     * <b>Zero-fill đủ năm mức sao là toàn bộ điểm của method này.</b> {@code GROUP BY} chỉ trả về
     * những mức <i>có</i> lượt, nên một sản phẩm không ai chấm 1 sao sẽ thiếu hẳn khoá {@code "1"}.
     * Frontend đọc {@code distribution['1']} để vẽ một thanh của biểu đồ; khoá vắng mặt cho ra
     * {@code undefined}, và thanh đó <b>không vẽ ra gì mà cũng không nổ lỗi nào</b>. Cùng bẫy đã đo
     * ở backlog 0019, nơi 29/30 ô ngày đều là 0.
     * <p>
     * <b>Dựng khung đủ năm khoá TRƯỚC rồi mới đổ số liệu vào</b> — không bao giờ đi từ map của kết
     * quả {@code GROUP BY} rồi bù thêm, vì thứ thiếu chính là thứ cần bù.
     * <p>
     * <b>{@code LinkedHashMap} chứ không phải {@code HashMap}:</b> thứ tự {@code "1"}…{@code "5"}
     * là thứ tự đọc ra của JSON, và một biểu đồ có các cột đảo lung tung giữa hai lần gọi là thứ
     * không ai debug được vì nó không sai ở đâu cả.
     *
     * @param counts các mức sao <b>có lượt</b>; có thể rỗng
     * @param total tổng số đánh giá
     * @param average điểm trung bình đã làm tròn theo {@code ReviewDomainService#genRating}
     * @return summary với đủ năm khoá {@code "1"}…{@code "5"}
     */
    public static ReviewSummaryResponse toSummaryResponse(List<RatingCount> counts, long total,
                                                          BigDecimal average) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (int rating = RATING_MIN; rating <= RATING_MAX; rating++) {
            distribution.put(String.valueOf(rating), 0L);
        }
        if (counts != null) {
            for (RatingCount count : counts) {
                distribution.put(String.valueOf(count.getRating()), count.getCount());
            }
        }
        return new ReviewSummaryResponse()
                .setAverage(average)
                .setTotal(total)
                .setDistribution(distribution);
    }

    /**
     * Cột lưu giờ UTC nên đóng dấu hậu tố {@code Z} vào chuỗi trả ra (§A.5) — thiếu nó, trình duyệt
     * đọc chuỗi như giờ địa phương và lệch 7 tiếng ở VN mà không có gì báo lỗi.
     *
     * @param value thời điểm lưu trong DB, hiểu là giờ UTC
     * @return chuỗi ISO 8601 dạng {@code 2026-08-12T00:00:00Z}, hoặc {@code null} khi đầu vào rỗng
     */
    private static String toIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC));
    }
}
