package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Ý định "viết một đánh giá" đã làm sạch cho tầng application — API_CONTRACT §B.8.
 * <p>
 * <b>Hai trường ở đây KHÔNG đến từ body, và đó là toàn bộ điểm của kiểu này:</b>
 * <ul>
 *   <li>{@link #productId} lấy từ <b>path</b>. {@code CreateReviewPayload} của frontend <i>cũng</i>
 *       mang một trường {@code productId}, và hợp đồng không nói cái nào thắng — Owner chốt
 *       2026-08-26: <b>path thắng, body bị bỏ qua trong im lặng, không báo lỗi</b>. Nhận theo body
 *       thì {@code POST /api/products/7/reviews} kèm {@code {"productId": 9}} sẽ ghi đánh giá vào
 *       sản phẩm 9 và vẫn trả 201 — dựng nhầm im lặng đúng nghĩa. Kỷ luật "chặn cứng, không báo
 *       lỗi" lấy từ {@code rating} / {@code reviewCount} / {@code sold} ở backlog 0008.</li>
 *   <li>{@link #userId} lấy từ claim <b>{@code sub}</b> của access token (ADR 0008), không bao giờ
 *       từ query / path / body — §C.4.1. Nhận nó từ client là để ai cũng đánh giá hộ người khác
 *       được.</li>
 * </ul>
 * <b>{@code authorName} thì ngược lại — vẫn là chuỗi người dùng tự khai.</b> Nó là <i>tên hiển
 * thị</i>, không phải danh tính; danh tính nằm ở {@link #userId}. Nhờ vậy
 * {@code CreateReviewPayload} của frontend không phải đổi một trường nào.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewCommand {

    /** Khóa chính của sản phẩm — <b>lấy từ path</b>, không phải từ body. */
    private Long productId;

    /** Khóa chính của tài khoản — <b>lấy từ claim {@code sub}</b>, không phải từ body. */
    private Long userId;

    /** Tên hiển thị người đánh giá tự khai. */
    private String authorName;

    /** Điểm đánh giá, số nguyên {@code 1..5}. */
    private Integer rating;

    /** Nội dung đánh giá, tối thiểu 10 ký tự. */
    private String content;
}
