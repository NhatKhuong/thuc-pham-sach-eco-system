package com.nss.ddd.domain.model;

/**
 * Năm giá trị của tham số {@code sort} ở {@code GET /admin/products} (API_CONTRACT §B.12.1).
 * <p>
 * <b>§B.12.1 nêu tên tham số nhưng KHÔNG liệt kê giá trị nào.</b> Năm hằng dưới đây được <i>đo</i>
 * từ source frontend — kiểu {@code ProductSort} ở {@code src/types/product.ts:63} và hàm
 * {@code applySort} ở {@code src/api/adminProducts.api.ts:86-107} — chứ không suy từ tài liệu. Thêm
 * hoặc bớt một giá trị ở đây là một thay đổi contract với màn {@code /quan-tri/san-pham}.
 * <p>
 * <b>Enum này cố ý KHÔNG mang tên cột hay chiều sắp xếp.</b> Việc dịch sang {@code Sort} của Spring
 * Data thuộc adapter ({@code ProductRepositoryImpl}) — {@code nss-domain} không được biết Spring
 * Data tồn tại (architecture/01-overview.md §1).
 */
public enum ProductSort {

    /** Mới nhất trước — {@code createdAt} giảm dần. */
    NEWEST,

    /** Giá tăng dần — theo <b>giá sau giảm</b> ({@code effective_price}), không phải {@code price}. */
    PRICE_ASC,

    /** Giá giảm dần — theo <b>giá sau giảm</b> ({@code effective_price}), không phải {@code price}. */
    PRICE_DESC,

    /** Bán chạy — {@code sold} giảm dần. */
    BEST_SELLING,

    /** Điểm đánh giá — {@code rating} giảm dần. */
    RATING;

    /**
     * Giá trị dùng khi client không gửi {@code sort}, hoặc gửi một giá trị không nhận ra.
     * <p>
     * Khớp hành vi đo được ở frontend theo <b>hai</b> đường độc lập, và cả hai đều chọn
     * {@code newest}: {@code applySort} gộp {@code case 'newest'} với {@code default} vào chung một
     * thân, còn {@code parseSort} của {@code AdminProductsPage} ép mọi giá trị lạ trên URL về
     * {@code 'newest'}. Vì vậy một {@code sort} sai chính tả phải cho ra thứ tự mặc định, <b>không
     * phải</b> một lỗi 400 — trả lỗi ở đây là làm hỏng một màn hình mà frontend coi là chạy đúng.
     */
    public static final ProductSort DEFAULT = NEWEST;
}
