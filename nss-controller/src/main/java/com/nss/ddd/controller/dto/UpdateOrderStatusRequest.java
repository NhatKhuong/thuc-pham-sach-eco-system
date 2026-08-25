package com.nss.ddd.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body của {@code PATCH /api/admin/orders/{code}/status} — API_CONTRACT §B.12.2.
 * <p>
 * <b>Một trường, và đúng một trường.</b> §B.12.2 khai body là {@code { status: OrderStatus }}: đơn
 * đã đặt là chứng từ, nên endpoint này <i>chỉ</i> đổi được trạng thái — không items, không tiền,
 * không địa chỉ. Thêm một trường vào đây là mở đúng cái cửa §B.12.2 đóng lại ("không cho sửa
 * items/tiền của đơn"); client gửi thừa trường thì Jackson bỏ qua trong im lặng, đúng như §C.3.
 * <p>
 * <b>Cố ý KHÔNG có {@code @Pattern} liệt kê năm chuỗi hợp lệ</b> — cùng lý do đã viết ở
 * {@code CreateOrderRequest.paymentMethod}: một regex như vậy sẽ là bản thứ hai của bảng dịch trạng
 * thái, đặt ở một tầng không có gì buộc nó đổi theo. Bảng thật nằm ở {@code OrderMapper}, và một
 * chuỗi lạ dừng ở tầng use case với <b>422</b> — đúng mã mà §B.12.2 khai, chỉ khác thông điệp: 422
 * của validate kèm map {@code errors}, còn 422 của use case thì không.
 * <p>
 * <b>{@code @NotBlank} thì vẫn cần</b>: một body {@code {}} hay {@code {"status": ""}} là lỗi
 * <i>theo trường</i>, và nó phải chỉ được vào đúng ô nhập — đó là việc của tầng validate, không
 * phải của bảng dịch.
 */
@Data
public class UpdateOrderStatusRequest {

    /**
     * Trạng thái muốn chuyển sang — {@code pending} / {@code confirmed} / {@code shipping} /
     * {@code delivered} / {@code cancelled}.
     */
    @NotBlank(message = "Status is required")
    @Schema(description = "Trạng thái muốn chuyển sang, chữ thường.", example = "confirmed",
            allowableValues = {"pending", "confirmed", "shipping", "delivered", "cancelled"})
    private String status;
}
