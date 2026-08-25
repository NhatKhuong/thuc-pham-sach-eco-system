package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Doanh thu của <b>một ngày cửa hàng</b> — một dòng kết quả của truy vấn tổng hợp đầu tiên của dự
 * án ({@code GET /admin/stats/overview}, API_CONTRACT §B.12.4).
 * <p>
 * <b>Kiểu này do domain sở hữu, cùng lý do đã viết ở {@link PageResult}:</b> {@code nss-domain}
 * không được import {@code org.springframework.data.*} (architecture §1), nên một projection của
 * Spring Data không đi lên tới đây được. Port khai báo trả kiểu này; adapter ở infrastructure mới
 * là nơi đọc {@code Object[]} của native query và dịch sang đây.
 * <p>
 * <b>{@link #date} là ngày theo múi giờ CỬA HÀNG, không phải UTC</b> (§B.12.4). Cột
 * {@code created_at} lưu giờ UTC; phép quy đổi nằm trong chính câu truy vấn gom nhóm, vì "đơn này
 * thuộc ngày nào" và "đơn này có trong khoảng không" phải do <i>một</i> phép tính quyết định —
 * hai phép tính thì có đơn cộng vào tổng mà không xuất hiện trên biểu đồ.
 * <p>
 * <b>Chỉ chứa những ngày CÓ đơn.</b> Việc zero-fill cho đủ {@code days} phần tử là của tầng
 * application (§B.12.4), không của SQL: khung ngày là hình dạng của bề mặt dây, còn truy vấn thì
 * chỉ biết những dòng có thật.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenue {

    /** Ngày theo múi giờ cửa hàng. */
    private LocalDate date;

    /** Tổng {@code total} của các đơn <b>không</b> ở trạng thái {@code cancelled} trong ngày đó, số nguyên VNĐ. */
    private long revenue;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param date ngày theo múi giờ cửa hàng
     * @param revenue doanh thu của ngày đó
     * @return dòng kết quả đã dựng xong
     */
    public static DailyRevenue of(LocalDate date, long revenue) {
        return new DailyRevenue()
                .setDate(date)
                .setRevenue(revenue);
    }
}
