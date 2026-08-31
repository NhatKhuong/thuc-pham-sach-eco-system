package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Điều kiện lọc + sắp xếp + phân trang của {@code GET /products} công khai (API_CONTRACT §B.1) —
 * mười hai tham số truy vấn.
 * <p>
 * <b>Cố ý KHÔNG dùng chung kiểu với {@link ProductFilter}</b> dù cả hai đều lọc {@code Product}.
 * {@link ProductFilter} phục vụ {@code GET /admin/products} và mang {@code stockStatus} — một khái
 * niệm quản trị không có mặt ở trang cửa hàng; ngược lại kiểu này mang bảy tiêu chí trang cửa hàng
 * mới có ({@code minPrice}, {@code maxPrice}, {@code minRating}, bốn cờ boolean) mà khu quản trị
 * không cần. Gộp hai kiểu vào một chữ ký là mở sẵn đường cho một bộ lọc quản trị rò sang endpoint
 * công khai (hoặc ngược lại) mà không ai phải quyết định gì — đúng rủi ro mà javadoc
 * {@code ProductRepository#findAdminPage} đã cảnh báo khi tách {@code findPage} khỏi
 * {@code findAdminPage}.
 * <p>
 * <b>{@link #keyword} mang hai nghĩa ở hai phía, giống hệt {@link ProductFilter}:</b> phía vào domain
 * service là chuỗi thô còn dấu; phía ra (vào port) là chuỗi đã bỏ dấu, hạ chữ thường. Bỏ dấu là quy
 * tắc nghiệp vụ nên sống ở domain service, không ở adapter (coding-conventions §18).
 * <p>
 * <b>Bốn cờ dùng {@code Boolean} (nullable), không {@code boolean} nguyên thuỷ — cùng quy ước với
 * {@code Product.isFeatured} ở entity, để giữ cặp getter/setter {@code getIsFeatured()} /
 * {@code setIsFeatured()} thay vì {@code isIsFeatured()} xấu xí mà Lombok sinh cho trường
 * {@code boolean isFeatured}.</b> Về ngữ nghĩa chúng vẫn là "chỉ hiện X", không phải tam trạng:
 * {@code true} kích hoạt bộ lọc tương ứng; {@code null} hoặc {@code false} (tham số vắng mặt hoặc
 * gửi {@code false}) đều nghĩa là không lọc theo tiêu chí đó — không có trạng thái thứ ba "chỉ hiện
 * KHÔNG X".
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PublicProductFilter {

    /** Từ khoá tìm kiếm; {@code null} là không tìm. Khớp {@code name} HOẶC {@code shortDescription}. */
    private String keyword;

    /**
     * Slug danh mục cần lọc; {@code null} là không lọc.
     * <p>
     * Cùng quy ước với {@link ProductFilter#getCategorySlug()}: slug không tồn tại cho ra tập RỖNG
     * (ADR 0007 vế 2), và bộ lọc kéo theo danh mục con một cấp.
     */
    private String categorySlug;

    /** Giá thấp nhất, đã bao gồm — lọc theo {@code effectivePrice}, KHÔNG theo {@code price}. */
    private Long minPrice;

    /** Giá cao nhất, đã bao gồm — lọc theo {@code effectivePrice}, KHÔNG theo {@code price}. */
    private Long maxPrice;

    /** Điểm đánh giá thấp nhất, đã bao gồm; {@code null} là không chặn. */
    private BigDecimal minRating;

    /** {@code true} = chỉ hiện sản phẩm còn hàng ({@code stock > 0}); {@code null}/{@code false} = không lọc. */
    private Boolean inStockOnly;

    /** {@code true} = chỉ hiện sản phẩm đang giảm giá ({@code salePrice IS NOT NULL}); {@code null}/{@code false} = không lọc. */
    private Boolean onSaleOnly;

    /** {@code true} = chỉ hiện sản phẩm nổi bật; {@code null}/{@code false} = không lọc. */
    private Boolean isFeatured;

    /** {@code true} = chỉ hiện sản phẩm bán chạy; {@code null}/{@code false} = không lọc. */
    private Boolean isBestSeller;

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
     * @param minPrice giá thấp nhất, có thể {@code null}
     * @param maxPrice giá cao nhất, có thể {@code null}
     * @param minRating điểm đánh giá thấp nhất, có thể {@code null}
     * @param inStockOnly chỉ hiện còn hàng
     * @param onSaleOnly chỉ hiện đang giảm giá
     * @param isFeatured chỉ hiện nổi bật
     * @param isBestSeller chỉ hiện bán chạy
     * @param sort thứ tự sắp xếp, có thể {@code null}
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc đã dựng xong
     */
    public static PublicProductFilter of(String keyword, String categorySlug, Long minPrice, Long maxPrice,
                                         BigDecimal minRating, Boolean inStockOnly, Boolean onSaleOnly,
                                         Boolean isFeatured, Boolean isBestSeller, ProductSort sort,
                                         int page, int limit) {
        return new PublicProductFilter()
                .setKeyword(keyword)
                .setCategorySlug(categorySlug)
                .setMinPrice(minPrice)
                .setMaxPrice(maxPrice)
                .setMinRating(minRating)
                .setInStockOnly(inStockOnly)
                .setOnSaleOnly(onSaleOnly)
                .setIsFeatured(isFeatured)
                .setIsBestSeller(isBestSeller)
                .setSort(sort)
                .setPage(page)
                .setLimit(limit);
    }
}
