package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của một đánh giá trên bề mặt dây — khớp {@code types/product.ts#Review}
 * (API_CONTRACT §B.8).
 * <p>
 * <b>ĐÚNG SÁU trường, và con số đó là contract.</b> Bảng {@code review} có bảy cột từ ADR 0008 trở
 * đi; cột thứ bảy là {@code user_id} và nó <b>không</b> có mặt ở đây. Cùng họ với {@code isActive}
 * của {@code ProductResponse}: chỗ chặn là danh sách trường viết tay trong {@code ReviewMapper},
 * không phải một quy ước ai đó phải nhớ.
 * <p>
 * <b>{@code authorName} là tên hiển thị, không phải danh tính.</b> Nó do người dùng tự khai và có
 * thể khác tên trên tài khoản — ADR 0008 cố ý không kiểm điều đó.
 * <p>
 * {@code createdAt} là chuỗi ISO-8601 <b>có hậu tố {@code Z}</b> (§A.5), không phải
 * {@code LocalDateTime}: cột lưu giờ UTC, thiếu hậu tố thì trình duyệt đọc như giờ địa phương và
 * lệch 7 tiếng ở VN mà không có gì báo lỗi.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    /** Khóa chính của đánh giá. */
    private Long id;

    /** Khóa chính của sản phẩm được đánh giá. */
    private Long productId;

    /** Tên hiển thị người đánh giá tự khai. */
    private String authorName;

    /** Điểm đánh giá, số nguyên {@code 1..5}. */
    private Integer rating;

    /** Nội dung đánh giá. */
    private String content;

    /** Thời điểm tạo, chuỗi ISO-8601 có hậu tố {@code Z}. */
    private String createdAt;
}
