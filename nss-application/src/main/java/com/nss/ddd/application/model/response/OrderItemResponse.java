package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Một dòng hàng trong {@code Order.items} trên bề mặt dây — API_CONTRACT §B.6.
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 * <p>
 * <b>TÁM trường, trong khi type {@code CartItem} của frontend có chín — trường thiếu là
 * {@code stock}, và nó thiếu có chủ ý</b> (backlog 0014 §Contract 7). Tồn kho tại thời điểm đặt là
 * con số vô nghĩa trên một chứng từ, và bảng {@code order_item} cố ý không có cột đó. Ba cách làm
 * sai, đều đã cân nhắc và đều bị loại:
 * <ul>
 *   <li><b>Trả {@code "stock": 0}</b> — một con số sai vẫn là một con số sai, và frontend sẽ hiển
 *       thị nó ra thành "còn 0 sản phẩm" cho một món vẫn đang bán.</li>
 *   <li><b>Tra tồn kho hiện tại của sản phẩm</b> — đơn là bản chụp; con số ấy đổi mỗi lần có người
 *       khác mua, nên cùng một đơn cũ sẽ đọc khác nhau ở hai lần mở.</li>
 *   <li><b>Trả {@code "stock": null}</b> — vẫn là khai rằng trường này thuộc về chứng từ.</li>
 * </ul>
 * PM đã đối chiếu phía frontend: trong 7 file render {@code order.items}, <b>không file nào đọc
 * {@code .stock}</b>; chỗ duy nhất đọc là {@code CartItemRow.tsx}, thuộc giỏ hàng chứ không thuộc
 * đơn. Việc tách kiểu {@code OrderItem} khỏi {@code CartItem} phải sửa ở <b>nguồn frontend</b>.
 * <p>
 * <b>{@code price} và {@code originalPrice} đều là bản chụp lúc đặt, không phải giá hôm nay.</b>
 * {@code price} là giá thực tế đã bán (cột {@code effective_price} tại thời điểm đó),
 * {@code originalPrice} là giá gốc để hiển thị gạch ngang. Sửa chúng về sau là làm lệch chính số
 * tiền khách đã trả (§B.12.2).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    /**
     * Sản phẩm đã mua.
     * <p>
     * Cột {@code product_id} <b>không có khoá ngoại</b> (xem javadoc {@code OrderItem}), nên id này
     * có thể trỏ tới một sản phẩm đã bị gỡ khỏi cửa hàng — đúng như mong muốn: đơn cũ phải đọc được
     * cả khi sản phẩm không còn.
     */
    private Long productId;

    /** Slug bản chụp — đủ để dựng link về trang sản phẩm, kể cả khi slug đó nay đã đổi. */
    private String slug;

    /** Tên sản phẩm, bản chụp tại thời điểm đặt. */
    private String name;

    /** Ảnh bản chụp, đường dẫn <b>tương đối</b> {@code /images/...} (§A.5); {@code null} khi không có ảnh. */
    private String image;

    /** Đơn vị tính, bản chụp tại thời điểm đặt. */
    private String unit;

    /** Giá thực tế đã bán một đơn vị, số nguyên VNĐ — backend tra lại từ DB, không lấy từ client (§C.1). */
    private Long price;

    /** Giá gốc một đơn vị để hiển thị gạch ngang, số nguyên VNĐ. */
    private Long originalPrice;

    /** Số lượng đã đặt. */
    private Integer quantity;
}
