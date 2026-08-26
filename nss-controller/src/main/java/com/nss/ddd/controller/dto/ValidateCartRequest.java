package com.nss.ddd.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Body của {@code POST /api/cart/validate} — API_CONTRACT §B.6 khai {@code { items: CartItem[] }}.
 * <p>
 * <b>{@code @NotNull} chứ KHÔNG {@code @NotEmpty}, và đó là contract.</b> Giỏ rỗng
 * ({@code "items": []}) phải trả <b>200</b> kèm mảng rỗng: câu hỏi "giỏ này có vấn đề gì không" vẫn
 * là một câu hỏi hợp lệ khi giỏ chưa có gì, và câu trả lời đúng là "không có vấn đề nào". Việc
 * <i>chặn</i> giỏ rỗng bằng 400 thuộc {@code POST /orders} — ở đó nó mới có nghĩa, vì không ai đặt
 * một đơn hàng trống.
 * <p>
 * {@code @Valid} trên field là bắt buộc để validation chạy xuống từng phần tử (coding-conventions
 * §7): thiếu nó thì mọi ràng buộc của {@code CartItemRequest} bị bỏ qua trong im lặng, và một dòng
 * thiếu {@code productId} sẽ đi thẳng tới domain rồi sinh ra một issue mang {@code productId: null}.
 */
@Data
public class ValidateCartRequest {

    @NotNull(message = "Vui lòng chọn sản phẩm để kiểm tra giỏ hàng.")
    @Valid
    private List<CartItemRequest> items;
}
