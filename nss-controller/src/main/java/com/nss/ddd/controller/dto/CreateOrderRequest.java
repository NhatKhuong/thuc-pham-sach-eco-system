package com.nss.ddd.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Body của {@code POST /api/orders} — API_CONTRACT §B.6 {@code CreateOrderPayload}.
 * <p>
 * <b>KHÔNG có trường {@code userId}, và đó là điều khoản quan trọng nhất của file này</b> (§C.2).
 * Chủ đơn lấy từ claim {@code sub} của JWT, do {@code OrderController} truyền vào lúc dựng lệnh.
 * Một trường không tồn tại thì không đọc được; một trường được khai rồi "nhớ đừng dùng" thì chỉ cần
 * một lần sửa vội là ai cũng đặt hàng hộ người khác được.
 * <p>
 * <b>{@code items} dùng lại {@link CartItemRequest} của phase 2, cố ý không dựng DTO riêng.</b>
 * Hợp đồng khai {@code CreateOrderPayload.items: CartItem[]} — cùng kiểu mà
 * {@code POST /cart/validate} nhận — và cả hai đường đều phải bỏ qua {@code stock},
 * {@code originalPrice}, {@code slug}, {@code image}, {@code unit} do client gửi (§C.1). Class kia
 * đã khai đúng bốn trường được phép đi qua và đã viết ra lý do; một bản sao ở đây sẽ là bản sẽ được
 * nới lỏng trước, và trường nguy hiểm nhất — {@code stock} — sẽ mọc lại ở đúng endpoint <i>ghi</i>.
 * <p>
 * <b>{@code items} là {@code @NotNull} chứ KHÔNG {@code @NotEmpty}, và đó là contract.</b>
 * {@code GlobalExceptionHandler} dịch mọi lỗi {@code jakarta.validation} thành <b>422</b>, còn §B.6
 * chốt giỏ trống là <b>400</b>. Một {@code @NotEmpty} ở đây sẽ trả đúng thông điệp nhưng sai mã, và
 * frontend phân biệt hai mã đó. Phép chặn giỏ trống vì vậy nằm ở tầng use case.
 * <p>
 * <b>{@code paymentMethod} cố ý KHÔNG mang {@code @Pattern} liệt kê bốn giá trị hợp lệ.</b> Bảng
 * dịch {@code cod} / {@code bank_transfer} / {@code momo} / {@code vnpay} sang {@code int} nằm đúng
 * một chỗ là {@code OrderMapper} (§Contract 4); một regex ở đây là bản thứ hai của cùng bảng ấy,
 * đặt ở một tầng không có gì buộc nó đổi theo. Giá trị lạ vì vậy ra <b>422</b> từ tầng use case
 * kèm {@code detail} tiếng Việt, chứ không phải 422 kèm map {@code errors}.
 * <p>
 * {@code @Valid} trên hai field là bắt buộc để validation chạy xuống từng phần tử và xuống khối
 * lồng (coding-conventions §7): thiếu nó thì mọi ràng buộc của {@code CartItemRequest} và
 * {@code ShippingInfoRequest} bị bỏ qua trong im lặng.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderRequest {

    @NotNull(message = "items must not be null")
    @Valid
    private List<CartItemRequest> items;

    @NotNull(message = "shipping must not be null")
    @Valid
    private ShippingInfoRequest shipping;

    @NotBlank(message = "paymentMethod must not be blank")
    private String paymentMethod;

    /**
     * Mã giảm giá khách áp; bỏ trống nghĩa là không áp mã.
     * <p>
     * Chỉ khai giới hạn độ dài — bằng đúng độ dài cột {@code customer_order.coupon_code}. Việc mã
     * có tồn tại và có dùng được hay không là câu hỏi <i>nghiệp vụ</i>, và nó được trả lời bằng
     * chính hai vị từ mà {@code POST /coupons/validate} dùng, trong cùng transaction với đơn.
     */
    @Size(max = 32, message = "couponCode must not exceed 32 characters")
    private String couponCode;
}
