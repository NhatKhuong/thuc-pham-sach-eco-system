package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.CreateReviewCommand;
import com.nss.ddd.controller.dto.CreateReviewRequest;

/**
 * Converter ở ranh giới HTTP: {@code CreateReviewRequest} sang {@code CreateReviewCommand}
 * (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * <p>
 * <b>Đây là chỗ "path thắng body" thật sự được thi hành, và nó là MỘT DÒNG dễ mất.</b>
 * {@link #toCommand} nhận {@code productId} như một <i>tham số riêng</i> — lấy từ
 * {@code @PathVariable} — và <b>không bao giờ</b> đọc {@code request.getProductId()}. Nếu ai đó
 * "dọn cho gọn" bằng {@code BeanUtils.copyProperties} hay bằng cách đọc trường của body, thì
 * {@code POST /api/products/7/reviews} kèm {@code {"productId": 9}} sẽ ghi đánh giá vào sản phẩm 9
 * và vẫn trả 201 — không exception, không log, không cách nào nhận ra từ phía client. Owner chốt
 * 2026-08-26: path là nguồn chân lý, giá trị trong body <b>bị bỏ qua trong im lặng</b>, không báo
 * lỗi (backlog 0027 bẫy #7).
 * <p>
 * <b>{@code userId} cũng đến từ ngoài body</b> — claim {@code sub} của access token (ADR 0008,
 * §C.4.1). {@code CreateReviewRequest} không có trường nào tên {@code userId} và sẽ không bao giờ
 * có.
 */
public final class ReviewControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private ReviewControllerMapper() {
    }

    /**
     * Dựng lệnh tạo đánh giá từ body cộng hai giá trị <b>không đến từ body</b>.
     *
     * @param request body đã qua validate
     * @param productId khóa chính của sản phẩm, lấy từ <b>path</b> — trường cùng tên trong
     *                  {@code request} bị bỏ qua
     * @param userId khóa chính của tài khoản, lấy từ claim {@code sub}
     * @return lệnh tạo, hoặc {@code null} khi {@code request} rỗng
     */
    public static CreateReviewCommand toCommand(CreateReviewRequest request, Long productId, Long userId) {
        if (request == null) {
            return null;
        }
        return new CreateReviewCommand()
                .setProductId(productId)
                .setUserId(userId)
                .setAuthorName(request.getAuthorName())
                .setRating(request.getRating())
                .setContent(request.getContent());
    }
}
