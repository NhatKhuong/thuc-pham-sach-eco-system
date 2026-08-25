package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Điều kiện lọc + phân trang của {@code GET /admin/customers} (API_CONTRACT §B.12.3).
 * <p>
 * Cùng khuôn với {@link ProductFilter} và {@link OrderFilter}.
 * <p>
 * <b>Không có trường {@code sort}, và sự vắng mặt đó là contract</b> (§B.12.3): thứ tự cố định là
 * {@code id} <b>tăng dần</b> — không phải "mới nhất trước", vì {@code types/user.ts#User} không có
 * {@code createdAt} nên không tồn tại mốc thời gian nào để xếp theo.
 * <p>
 * <b>{@link #keyword} mang hai nghĩa ở hai phía của domain service</b> — xem {@code ProductFilter}.
 * Nó được so khớp với ba trường: {@code fullNameNormalized} (đã bỏ dấu), {@code email} (hạ chữ
 * thường) và {@code phone} (khớp cả đoạn giữa).
 * <p>
 * <b>{@link #roleCode} KHÔNG BAO GIỜ {@code null} khi tới được domain</b>, khác hẳn hai
 * {@code *Filter} kia: §B.12.3 chốt "{@code role} bỏ trống ⇒ {@code customer}", nên phép rơi về
 * mặc định xảy ra ở tầng biên và xuống tới đây thì luôn có giá trị. Một {@code null} lọt xuống sẽ
 * biến bộ lọc thành "mọi vai trò" và bảng khách hàng lặng lẽ mọc lại tài khoản quản trị.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserFilter {

    /** Từ khoá tìm kiếm; {@code null} là không tìm. Xem javadoc cấp class về hai nghĩa của nó. */
    private String keyword;

    /**
     * Mã vai trò cần lọc — {@code UPPER_SNAKE} như trong cột {@code role.code}, ví dụ
     * {@code CUSTOMER}. Xem javadoc cấp class: giá trị này không được {@code null}.
     */
    private String roleCode;

    /** Trang cần lấy, <b>đánh số từ 1</b> (§A.4). Phép trừ 1 nằm ở adapter. */
    private int page;

    /** Số phần tử tối đa mỗi trang. */
    private int limit;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param keyword từ khoá tìm kiếm, có thể {@code null}
     * @param roleCode mã vai trò UPPER_SNAKE, <b>không được {@code null}</b>
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc đã dựng xong
     */
    public static UserFilter of(String keyword, String roleCode, int page, int limit) {
        return new UserFilter()
                .setKeyword(keyword)
                .setRoleCode(roleCode)
                .setPage(page)
                .setLimit(limit);
    }
}
