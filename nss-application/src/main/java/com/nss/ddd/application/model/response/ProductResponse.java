package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload của một sản phẩm trên bề mặt dây — khớp <b>đúng</b> type {@code Product} của frontend
 * (API_CONTRACT §B.1, {@code src/types/product.ts}).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 * <p>
 * <b>Ba cột của bảng {@code product} cố ý không có mặt ở đây</b>, và việc thêm chúng vào là một
 * thay đổi contract chứ không phải tiện tay:
 * <ul>
 *   <li>{@code isActive} — cờ xoá mềm, chuyện nội bộ của backend; type {@code Product} phía client
 *       không có trường này.</li>
 *   <li>{@code nameNormalized} — dữ liệu phụ trợ cho tìm kiếm bỏ dấu.</li>
 *   <li>{@code effectivePrice} — client tự tính {@code salePrice ?? price} khi hiển thị.</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;

    private String slug;

    private String name;

    /** Giá gốc, số nguyên VNĐ (§A.5). */
    private Long price;

    /** Giá khuyến mãi; {@code null} nghĩa là không giảm giá — §A.5 cấm dùng 0 hay chuỗi rỗng. */
    private Long salePrice;

    /** Đường dẫn ảnh <b>tương đối</b> dạng {@code /images/...}, đã sắp theo thứ tự hiển thị (§A.5). */
    private List<String> images;

    private Long categoryId;

    /** {@code null} khi sản phẩm không gắn thương hiệu nào. */
    private Long brandId;

    /** Điểm đánh giá trung bình, thang 0.0–5.0. */
    private BigDecimal rating;

    private Integer reviewCount;

    private Integer stock;

    private Integer sold;

    private String unit;

    private String origin;

    private String shortDescription;

    private String description;

    private Boolean isFeatured;

    private Boolean isBestSeller;

    /**
     * Chuỗi ISO 8601 <b>kèm hậu tố {@code Z}</b>, ví dụ {@code 2026-07-02T00:00:00Z} (§A.5).
     * <p>
     * Là {@code String} chứ không phải {@code LocalDateTime} vì lý do rất cụ thể: cột lưu giờ UTC
     * nhưng {@code LocalDateTime} không mang thông tin múi giờ, nên Jackson sẽ tuần tự hoá thành
     * {@code "2026-07-02T00:00:00"} — và {@code new Date(...)} phía trình duyệt đọc chuỗi không có
     * offset như <i>giờ địa phương</i>, lệch đúng 7 tiếng ở VN mà không có gì báo lỗi.
     */
    private String createdAt;
}
