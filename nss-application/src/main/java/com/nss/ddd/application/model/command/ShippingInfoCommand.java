package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Thông tin giao hàng ở ranh giới application — một phần của {@link CreateOrderCommand}
 * (coding-conventions §7).
 * <p>
 * <b>Chỉ lưu TÊN tỉnh / quận / phường, không lưu mã.</b> Đây là dữ liệu để in lên đơn, không phải
 * để đổ ngược vào ô {@code <Select>} — API_CONTRACT §B.9 nói rõ {@code ShippingInfo} khác
 * {@code Address} đúng ở điểm này, và {@code getWards} vì vậy trả mảng chuỗi tên chứ không trả
 * object {@code {code, name}}. Sổ địa chỉ (§B.5) là ticket khác.
 * <p>
 * <b>Không có trường {@code userId}.</b> Chủ đơn nằm ở {@link CreateOrderCommand#getUserId()} và
 * chỉ được điền từ claim {@code sub} của JWT (§C.2). Nhét nó vào đây là mở đúng cái cửa cho phép
 * đặt hàng hộ người khác.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ShippingInfoCommand {

    /** Họ tên người nhận hàng. */
    private String fullName;

    /** Số điện thoại người nhận. */
    private String phone;

    /** Email nhận xác nhận đơn — kênh liên lạc duy nhất với khách vãng lai. */
    private String email;

    /** Tên tỉnh / thành giao hàng. */
    private String province;

    /** Tên quận / huyện giao hàng. */
    private String district;

    /** Tên phường / xã giao hàng. */
    private String ward;

    /** Số nhà và tên đường. */
    private String street;

    /** Ghi chú giao hàng của khách; {@code null} khi không có. */
    private String note;
}
