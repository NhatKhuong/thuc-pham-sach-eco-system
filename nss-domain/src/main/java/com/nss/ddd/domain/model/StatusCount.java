package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Số đơn theo <b>một trạng thái</b> — một dòng kết quả của truy vấn tổng hợp
 * ({@code GET /admin/stats/overview}, API_CONTRACT §B.12.4).
 * <p>
 * <b>Kiểu này do domain sở hữu</b>, cùng lý do đã viết ở {@link DailyRevenue} và {@link PageResult}.
 * <p>
 * <b>{@link #status} là con số {@code 0..4} của cột {@code status}, không phải chuỗi trên dây.</b>
 * Bảng dịch sang {@code pending} / {@code confirmed} / … nằm ở {@code OrderMapper} của tầng
 * application và chỉ ở đó.
 * <p>
 * <b>Đơn đã huỷ VẪN được đếm ở đây</b> (§B.12.4) — nó đã xảy ra. Thứ đơn huỷ không được vào là
 * {@code revenue}; xem {@link DailyRevenue}.
 * <p>
 * <b>Chỉ chứa những trạng thái CÓ đơn.</b> Việc bù đủ cả năm trạng thái (kể cả {@code count: 0})
 * là của tầng application, không của SQL.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class StatusCount {

    /** Con số của cột {@code status} — {@code 0..4}. */
    private int status;

    /** Số đơn ở trạng thái đó trong khoảng thời gian đang xét. */
    private long count;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param status con số của cột {@code status}
     * @param count số đơn
     * @return dòng kết quả đã dựng xong
     */
    public static StatusCount of(int status, long count) {
        return new StatusCount()
                .setStatus(status)
                .setCount(count);
    }
}
