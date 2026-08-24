package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.controller.dto.CartItemRequest;

import java.util.Collections;
import java.util.List;

/**
 * Converter ở ranh giới HTTP: {@code CartItemRequest} của controller sang {@code CartItemCommand}
 * của application (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * Không {@code BeanUtils.copyProperties} — và ở đây lý do cấm nó sắc hơn thường lệ: một bản chép
 * ngầm theo tên trường sẽ <b>tự động mang theo bất kỳ trường nào ai đó thêm vào
 * {@code CartItemRequest} về sau</b>, kể cả {@code stock} mà §C.1 cấm tin. Danh sách viết tay bên
 * dưới là chỗ duy nhất phải sửa để một trường mới đi được qua ranh giới này, và người sửa nó sẽ
 * nhìn thấy dòng javadoc này.
 * <p>
 * <b>Thứ tự phần tử được giữ nguyên</b> — thứ tự issue trả về bám theo thứ tự dòng client gửi.
 */
public final class CartControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private CartControllerMapper() {
    }

    /**
     * @param request một dòng giỏ hàng trong body
     * @return lệnh tương ứng, hoặc {@code null} khi {@code request} rỗng
     */
    public static CartItemCommand toCommand(CartItemRequest request) {
        if (request == null) {
            return null;
        }
        return new CartItemCommand()
                .setProductId(request.getProductId())
                .setName(request.getName())
                .setQuantity(request.getQuantity())
                .setPrice(request.getPrice());
    }

    /**
     * @param requests các dòng giỏ hàng trong body
     * @return các lệnh tương ứng, giữ nguyên thứ tự; danh sách rỗng khi đầu vào rỗng
     */
    public static List<CartItemCommand> toCommands(List<CartItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        return requests.stream()
                .map(CartControllerMapper::toCommand)
                .toList();
    }
}
