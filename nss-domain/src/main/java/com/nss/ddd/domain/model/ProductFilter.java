package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Điều kiện lọc + sắp xếp + phân trang của {@code GET /admin/products} (API_CONTRACT §B.12.1).
 * <p>
 * Gom sáu tham số truy vấn thành <b>một</b> kiểu vì chúng luôn đi cùng nhau qua bốn tầng
 * (controller {@literal ->} application {@literal ->} domain {@literal ->} port). Sáu tham số rời
 * trên một chữ ký method là sáu chỗ để hoán vị nhầm hai chuỗi cạnh nhau mà trình biên dịch không
 * phản đối.
 * <p>
 * <b>{@link #keyword} mang hai nghĩa ở hai phía của domain service, và đó là chủ ý:</b>
 * <ul>
 *   <li>Phía <i>vào</i> domain service: chuỗi thô client gửi ở tham số {@code q}, còn nguyên dấu.</li>
 *   <li>Phía <i>ra</i> domain service — tức cái mà port nhận: chuỗi đã <b>bỏ dấu và hạ chữ
 *       thường</b> để so được với cột {@code name_normalized}. Bỏ dấu là một quy tắc nghiệp vụ nên
 *       nó sống ở domain service, không ở adapter.</li>
 * </ul>
 * <b>Adapter tuyệt đối không chuẩn hoá lại.</b> Chuẩn hoá hai lần là hai bản sao của cùng một quy
 * tắc, và chúng sẽ lệch nhau vào đúng lúc chỉ một bên được sửa — đúng dạng lỗi mà
 * {@code coding-conventions} §15 mô tả cho quy ước làm tròn.
 * <p>
 * Trường rỗng ({@code null}) nghĩa là <b>không lọc theo tiêu chí đó</b>, không phải "lọc theo giá
 * trị rỗng".
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilter {

    /** Từ khoá tìm kiếm; {@code null} là không tìm. Xem javadoc cấp class về hai nghĩa của nó. */
    private String keyword;

    /**
     * Slug danh mục cần lọc; {@code null} là không lọc.
     * <p>
     * <b>Slug không tồn tại cho ra tập RỖNG, không phải "bỏ qua bộ lọc".</b> Khớp hành vi đo được ở
     * {@code adminProducts.api.ts:35-42}: {@code resolveCategoryIds} trả mảng rỗng khi không khớp
     * danh mục nào, và phép lọc theo mảng rỗng loại hết mọi sản phẩm.
     * <p>
     * Bộ lọc kéo theo <b>danh mục con một cấp</b>: lọc "Rau củ" phải ra cả rau ăn lá.
     */
    private String categorySlug;

    /** Trạng thái tồn kho cần lọc; {@code null} là không lọc. */
    private StockStatus stockStatus;

    /** Thứ tự sắp xếp; {@code null} được hiểu là {@link ProductSort#DEFAULT}. */
    private ProductSort sort;

    /** Trang cần lấy, <b>đánh số từ 1</b> (§A.4). Phép trừ 1 nằm ở adapter. */
    private int page;

    /** Số phần tử tối đa mỗi trang. */
    private int limit;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param keyword từ khoá tìm kiếm, có thể {@code null}
     * @param categorySlug slug danh mục, có thể {@code null}
     * @param stockStatus trạng thái tồn kho, có thể {@code null}
     * @param sort thứ tự sắp xếp, có thể {@code null}
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc đã dựng xong
     */
    public static ProductFilter of(String keyword, String categorySlug, StockStatus stockStatus,
                                   ProductSort sort, int page, int limit) {
        return new ProductFilter()
                .setKeyword(keyword)
                .setCategorySlug(categorySlug)
                .setStockStatus(stockStatus)
                .setSort(sort)
                .setPage(page)
                .setLimit(limit);
    }
}
