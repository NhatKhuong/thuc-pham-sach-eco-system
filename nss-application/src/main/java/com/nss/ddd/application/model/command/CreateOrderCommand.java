package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Lệnh tạo đơn hàng ở ranh giới application — API_CONTRACT §B.6 {@code CreateOrderPayload}
 * (coding-conventions §7).
 * <p>
 * <b>{@code items} dùng lại {@link CartItemCommand} của phase 2, cố ý không dựng kiểu song song.</b>
 * Hợp đồng khai {@code CreateOrderPayload.items: CartItem[]} — đúng cùng một kiểu mà
 * {@code POST /cart/validate} nhận — và cả hai đường đều phải bỏ qua {@code stock},
 * {@code originalPrice}, {@code slug}, {@code image}, {@code unit} mà client gửi (§C.1). Dựng một
 * {@code OrderItemCommand} riêng ở đây sẽ là bản thứ hai của cùng một luật lọc trường, và bản thứ
 * hai là bản sẽ được nới lỏng trước.
 * <p>
 * <b>{@code userId} KHÔNG đến từ body.</b> Trường này có mặt ở tầng application nhưng
 * {@code CreateOrderRequest} phía controller <i>không</i> khai nó: controller đọc claim {@code sub}
 * của JWT rồi truyền vào lúc dựng lệnh, đúng khuôn {@code AuthControllerMapper.toLogoutCommand}.
 * Một trường không tồn tại trong DTO thì không có đường nào để client gửi lên (§C.2) — nếu gửi được
 * thì ai cũng đặt hàng hộ người khác được.
 * <p>
 * <b>{@code paymentMethod} là chuỗi thường của dây</b> ({@code cod} / {@code bank_transfer} /
 * {@code momo} / {@code vnpay}), chưa dịch sang {@code int}. Bảng dịch nằm đúng một chỗ là
 * {@code OrderMapper} (§Contract 4), nên tầng này mang nguyên chuỗi cho tới lúc chạm mapper đó.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderCommand {

    /**
     * Chủ đơn, lấy từ claim {@code sub} của JWT; {@code null} là <b>đơn của khách vãng lai</b>
     * (§B.6, §D #2) — một giá trị hợp lệ, không phải một thiếu sót.
     */
    private Long userId;

    /** Các dòng hàng khách muốn mua; danh sách rỗng bị chặn ở tầng use case bằng <b>400</b>. */
    private List<CartItemCommand> items;

    /** Thông tin giao hàng, bản chụp tại thời điểm đặt. */
    private ShippingInfoCommand shipping;

    /** Phương thức thanh toán ở dạng chuỗi của dây; giá trị lạ cho ra 422. */
    private String paymentMethod;

    /** Mã giảm giá khách áp; {@code null} hoặc rỗng nghĩa là không áp mã. */
    private String couponCode;
}
