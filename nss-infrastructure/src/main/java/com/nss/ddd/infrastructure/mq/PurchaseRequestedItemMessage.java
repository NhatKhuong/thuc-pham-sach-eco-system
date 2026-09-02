package com.nss.ddd.infrastructure.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Một dòng giỏ hàng trong payload {@link PurchaseRequestedMessage} (backlog 0039 Phase 4).
 * <p>
 * Bốn trường, khớp {@code CartItemCommand} — payload chỉ là bản chụp JSON của lệnh gốc để consumer
 * dựng lại được {@code CreateOrderCommand} mà không cần đọc thêm gì từ DB tại bước giải mã.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestedItemMessage {

    /** Khóa tra sản phẩm trong DB. */
    private Long productId;

    /** Tên hiển thị do client gửi. */
    private String name;

    /** Số lượng khách muốn mua. */
    private Integer quantity;

    /** Giá giỏ hàng đang hiển thị lúc submit — chỉ để dội lại, không dùng tính tiền (§Contract C.1). */
    private Long price;
}
