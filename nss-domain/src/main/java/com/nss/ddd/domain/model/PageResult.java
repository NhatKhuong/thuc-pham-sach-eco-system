package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Kết quả phân trang <b>của domain</b> — một lát dữ liệu kèm tổng số dòng khớp điều kiện.
 * <p>
 * Tồn tại vì một lý do duy nhất: {@code nss-domain} không được biết {@code Page} /
 * {@code Pageable} của Spring Data (architecture/01-overview.md §1 — domain không phụ thuộc
 * module nào và không mang khái niệm hạ tầng). Port khai báo {@code findPage} trả kiểu này;
 * adapter ở infrastructure mới là nơi dịch sang {@code Pageable} và {@code Page}.
 * <p>
 * Cố ý <b>không</b> mang {@code page} / {@code limit} / {@code totalPages}: đó là hình dạng của
 * bề mặt dây (API_CONTRACT §A.4), thuộc về {@code PaginatedResponse} ở tầng application.
 *
 * @param <T> kiểu phần tử của trang
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** Các phần tử của trang hiện tại, theo đúng thứ tự adapter trả về. */
    private List<T> items;

    /** Tổng số dòng khớp điều kiện lọc, không phải số phần tử của trang. */
    private long total;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param items các phần tử của trang
     * @param total tổng số dòng khớp điều kiện
     * @return trang đã dựng xong
     */
    public static <T> PageResult<T> of(List<T> items, long total) {
        return new PageResult<T>()
                .setItems(items)
                .setTotal(total);
    }
}
