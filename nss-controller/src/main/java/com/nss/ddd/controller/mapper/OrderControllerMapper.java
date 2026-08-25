package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.controller.dto.CreateOrderRequest;
import com.nss.ddd.controller.dto.ShippingInfoRequest;

/**
 * Converter ở ranh giới HTTP: {@code CreateOrderRequest} của controller sang
 * {@code CreateOrderCommand} của application (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * <p>
 * <b>{@code userId} là tham số riêng, KHÔNG đọc từ {@code request}</b> — nó đến từ claim {@code sub}
 * của JWT, đúng khuôn {@code AuthControllerMapper.toLogoutCommand}. Chữ ký của
 * {@link #toCommand(CreateOrderRequest, Long)} là chỗ điều đó nhìn thấy được: hai nguồn dữ liệu
 * khác nhau vào cùng một lệnh, và cái thứ hai không bao giờ đi qua body.
 * <p>
 * <b>Không {@code BeanUtils.copyProperties}</b>, và ở đây lý do cấm nó sắc hơn thường lệ — cùng lý
 * do đã viết ở {@code CartControllerMapper}: một bản chép ngầm theo tên trường sẽ tự động mang theo
 * bất kỳ trường nào ai đó thêm vào DTO về sau, kể cả một {@code userId} lỡ tay khai. Danh sách viết
 * tay bên dưới là chỗ duy nhất phải sửa để một trường mới đi được qua ranh giới này.
 * <p>
 * <b>Phần {@code items} uỷ thác cho {@code CartControllerMapper}</b> thay vì chép lại: hai endpoint
 * nhận cùng một kiểu {@code CartItem[]} và phải lọc trường theo cùng một luật (§C.1), nên chúng
 * dùng chung đúng một phép chuyển đổi.
 */
public final class OrderControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private OrderControllerMapper() {
    }

    /**
     * @param request body đã qua validate
     * @param userId chủ đơn lấy từ claim {@code sub}; {@code null} là đơn khách vãng lai
     * @return lệnh tạo đơn, hoặc {@code null} khi {@code request} rỗng
     */
    public static CreateOrderCommand toCommand(CreateOrderRequest request, Long userId) {
        if (request == null) {
            return null;
        }
        return new CreateOrderCommand()
                .setUserId(userId)
                .setItems(CartControllerMapper.toCommands(request.getItems()))
                .setShipping(toShippingCommand(request.getShipping()))
                .setPaymentMethod(request.getPaymentMethod())
                .setCouponCode(request.getCouponCode());
    }

    /**
     * @param request khối {@code shipping} trong body
     * @return lệnh tương ứng, hoặc {@code null} khi {@code request} rỗng
     */
    public static ShippingInfoCommand toShippingCommand(ShippingInfoRequest request) {
        if (request == null) {
            return null;
        }
        return new ShippingInfoCommand()
                .setFullName(request.getFullName())
                .setPhone(request.getPhone())
                .setEmail(request.getEmail())
                .setProvince(request.getProvince())
                .setDistrict(request.getDistrict())
                .setWard(request.getWard())
                .setStreet(request.getStreet())
                .setNote(request.getNote());
    }
}
