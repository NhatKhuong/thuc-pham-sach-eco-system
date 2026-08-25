package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Một dòng giỏ hàng như domain nhìn thấy nó — đầu vào của {@code POST /api/cart/validate}.
 * <p>
 * <b>Bốn trường, và con số đó là một quyết định chứ không phải hiện trạng.</b> Type {@code CartItem}
 * của frontend mang thêm {@code slug}, {@code image}, {@code unit}, {@code originalPrice} và
 * {@code stock}; <b>không trường nào trong số đó qua được ranh giới này</b>. Chúng là bản chụp lúc
 * khách bấm "thêm vào giỏ", để giỏ hàng trong localStorage hiển thị đúng khi sản phẩm đổi giá —
 * không phải dữ kiện để backend suy luận (API_CONTRACT §C.1).
 * <p>
 * Hai trường dễ bị dùng sai nhất, viết rõ ở đây vì cả hai đều hỏng trong im lặng:
 * <ul>
 *   <li><b>{@code name} lấy từ client, KHÔNG tra lại DB.</b> Nghe ngược với §C.1 nhưng đúng: một
 *       sản phẩm đã bị gỡ khỏi hệ thống thì DB không còn tên nào để tra, mà cảnh báo
 *       {@code out_of_stock} vẫn phải gọi tên món hàng ra thì người dùng mới biết bỏ cái nào khỏi
 *       giỏ. Đây là <i>chuỗi hiển thị</i>, không phải con số tiền.</li>
 *   <li><b>{@code price} CHỈ dùng để so sánh và dội lại.</b> Nó là giá mà giỏ hàng đang hiển thị,
 *       dùng để phát hiện lệch giá và để điền {@code cartPrice} của issue. §C.1 cấm tin số tiền
 *       client gửi <i>khi tính tiền</i>; đường này không tính một đồng nào — việc tính tiền thuộc
 *       {@code POST /orders}, nơi giá phải tra lại từ cột {@code effective_price}.</li>
 * </ul>
 * Tồn kho thì ngược lại hoàn toàn: {@code stock} client gửi <b>không có mặt ở đây</b> vì §C.1 nói
 * thẳng là phải kiểm lại tồn kho thật. Thêm nó vào class này là mở đúng cái cửa đó.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CartLine {

    /** Khóa tra sản phẩm trong DB — thứ duy nhất trong dòng này được tin để tra cứu. */
    private Long productId;

    /** Tên hiển thị do client gửi; dùng nguyên văn trong issue, không tra lại DB. */
    private String name;

    /** Số lượng khách muốn mua. */
    private Integer quantity;

    /** Giá mà giỏ hàng của khách đang hiển thị, số nguyên VNĐ (§A.5) — chỉ để so sánh và dội lại. */
    private Long price;
}
