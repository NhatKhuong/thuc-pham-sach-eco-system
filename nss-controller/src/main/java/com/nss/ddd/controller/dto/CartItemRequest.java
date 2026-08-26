package com.nss.ddd.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Một phần tử của {@code items} trong body {@code POST /api/cart/validate} — API_CONTRACT §B.6 khai
 * {@code { items: CartItem[] }}.
 * <p>
 * <b>Bốn trường, trong khi type {@code CartItem} của frontend có chín.</b> Năm trường không được
 * khai ở đây — {@code slug}, {@code image}, {@code unit}, {@code originalPrice} và
 * {@code stock} — là bản chụp lúc khách bấm "thêm vào giỏ", để giỏ hàng trong localStorage hiển thị
 * đúng khi sản phẩm đổi giá. Backend bỏ qua hoàn toàn (§C.1).
 * <p>
 * <b>Việc KHÔNG khai chúng là phép cưỡng chế, không phải sự lười.</b> Một trường không tồn tại thì
 * không đọc được; một trường được khai rồi "nhớ đừng dùng" thì chỉ cần một lần sửa vội là thành lỗ
 * hổng. Nguy hiểm nhất là {@code stock}: §C.1 nói thẳng "kiểm lại tồn kho thật, <i>không tin</i>
 * {@code stock} trong {@code CartItem}", nên thêm nó vào class này là mở đúng cái cửa đó — và một
 * client tự chế gửi {@code stock: 9999} sẽ mua được thứ trong kho không có.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} khai <b>tường minh</b> điều Spring Boot vốn
 * đã đặt mặc định. Frontend gửi cả chín trường; nếu một cấu hình nào đó về sau bật
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} thì mọi request giỏ hàng hợp lệ sẽ bị từ chối, và triệu chứng
 * ("khách không thanh toán được") không hề trỏ về một dòng cấu hình Jackson.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b> — {@code javax.validation} bị cấm
 * (coding-conventions §7, §17). Thông điệp validation viết <b>tiếng Anh</b> theo §1.
 * <p>
 * <b>Vì sao {@code name} là {@code @NotBlank} dù nó chỉ là chuỗi hiển thị:</b> nó là <i>khoá duy
 * nhất</i> mà người dùng dùng để nhận ra món hàng có vấn đề trong danh sách issue, và với một sản
 * phẩm đã bị gỡ khỏi hệ thống thì DB không còn tên nào để tra thay. Một issue không tên nói với
 * người dùng rằng "có gì đó trong giỏ bị hết hàng" mà không nói cái nào.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItemRequest {

    @NotNull(message = "Thiếu mã sản phẩm của dòng hàng này.")
    @Positive(message = "Mã sản phẩm của dòng hàng này không hợp lệ.")
    private Long productId;

    @NotBlank(message = "Vui lòng nhập tên sản phẩm.")
    @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự.")
    private String name;

    @NotNull(message = "Vui lòng nhập số lượng.")
    @Positive(message = "Số lượng phải lớn hơn 0.")
    private Integer quantity;

    /**
     * Giá giỏ hàng đang hiển thị, số nguyên VNĐ (§A.5).
     * <p>
     * {@code @PositiveOrZero} chứ không {@code @Positive}: một sản phẩm giá 0 là điều kỳ quặc chứ
     * không phải điều bất hợp lệ, và câu trả lời đúng cho nó là một issue {@code price_changed},
     * không phải một lỗi 422.
     */
    @NotNull(message = "Thiếu đơn giá của sản phẩm.")
    @PositiveOrZero(message = "Đơn giá không được là số âm.")
    private Long price;
}
