package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Số lượt đánh giá ở <b>một mức sao</b> — một dòng kết quả của {@code GROUP BY rating} trên bảng
 * {@code review} ({@code ReviewSummary.distribution}, API_CONTRACT §B.8).
 * <p>
 * <b>Kiểu này do domain sở hữu</b>, cùng lý do đã viết ở {@link StatusCount} và {@link DailyRevenue}:
 * truy vấn tổng hợp trả về một hình dạng không phải entity nào, và hình dạng đó không được phép là
 * {@code Object[]} đi xuyên các tầng.
 * <p>
 * <b>Chỉ chứa những mức sao CÓ lượt.</b> {@code GROUP BY} trần bỏ hẳn mức không ai chọn khỏi kết
 * quả — bù đủ năm mức {@code '1'}…{@code '5'} (kể cả giá trị {@code 0}) là việc của tầng
 * application, không của SQL. Cùng bẫy zero-fill đã đo ở backlog 0019: trả thiếu khoá thì biểu đồ
 * phân bố của frontend <b>rỗng mà không nổ lỗi nào</b>.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class RatingCount {

    /** Mức sao — số nguyên {@code 1..5}, đúng giá trị cột {@code rating}. */
    private int rating;

    /** Số lượt đánh giá ở mức sao đó. */
    private long count;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param rating mức sao {@code 1..5}
     * @param count số lượt
     * @return dòng kết quả đã dựng xong
     */
    public static RatingCount of(int rating, long count) {
        return new RatingCount()
                .setRating(rating)
                .setCount(count);
    }
}
