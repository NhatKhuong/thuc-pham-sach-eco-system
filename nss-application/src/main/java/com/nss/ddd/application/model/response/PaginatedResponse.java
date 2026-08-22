package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Dạng phân trang của bề mặt dây (API_CONTRACT §A.4) — <b>không</b> phải {@code Page<T>} mặc định
 * của Spring Data.
 * <p>
 * Bốn tên trường lệch với Spring ({@code items}/{@code content}, {@code total}/{@code totalElements},
 * {@code page}/{@code number}, {@code limit}/{@code size}) và một khác biệt về ngữ nghĩa:
 * <b>{@code page} đánh số từ 1</b>, vì nó đi thẳng lên URL người dùng nhìn thấy ({@code ?page=2}).
 *
 * @param <T> kiểu phần tử
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {

    private List<T> items;

    /** Tổng số bản ghi khớp điều kiện, không phải số phần tử của trang. */
    private long total;

    /** Trang hiện tại, <b>đánh số từ 1</b>. */
    private int page;

    /** Số phần tử tối đa mỗi trang. */
    private int limit;

    /** Tổng số trang; 0 khi không có bản ghi nào. */
    private int totalPages;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     * <p>
     * {@code totalPages} làm tròn <b>lên</b>: 42 bản ghi chia 12 một trang ra 4 trang, trang cuối
     * chỉ có 6 phần tử. Tính bằng số nguyên chứ không qua {@code Math.ceil} trên {@code double}
     * để không phải bàn về sai số dấu phẩy động.
     *
     * @param items các phần tử của trang
     * @param total tổng số bản ghi khớp điều kiện
     * @param page trang hiện tại, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return trang đã dựng xong
     */
    public static <T> PaginatedResponse<T> of(List<T> items, long total, int page, int limit) {
        int totalPages = limit <= 0 ? 0 : (int) ((total + limit - 1) / limit);
        return new PaginatedResponse<T>()
                .setItems(items)
                .setTotal(total)
                .setPage(page)
                .setLimit(limit)
                .setTotalPages(totalPages);
    }
}
