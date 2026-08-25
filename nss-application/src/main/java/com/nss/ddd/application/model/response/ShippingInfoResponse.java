package com.nss.ddd.application.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Thông tin giao hàng trong {@code Order.shipping} trên bề mặt dây — khớp
 * {@code types/order.ts#ShippingInfo} (API_CONTRACT §B.6, §B.9).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 * <p>
 * <b>Chỉ TÊN tỉnh / quận / phường, không có mã.</b> §B.9 nói thẳng vì sao: {@code Address} của sổ
 * địa chỉ giữ cả mã lẫn tên vì ô {@code <Select>} chạy theo mã, còn {@code ShippingInfo} của đơn
 * hàng là dữ liệu để <i>in lên chứng từ</i> — thêm mã vào đây là thêm một trường không ai đọc và
 * một cơ hội để hai bên nói khác nhau khi tên đơn vị hành chính thay đổi.
 * <p>
 * <b>{@code note} vắng mặt khỏi JSON khi không có</b>, cưỡng chế bằng {@code @JsonInclude(NON_NULL)}
 * đặt trên <i>đúng trường đó</i> chứ không ở cấp class — cùng kỷ luật và cùng lý do với
 * {@code CartIssueResponse}: đặt ở cấp class thì bảy trường bắt buộc cũng thừa hưởng luật ấy và sẽ
 * <i>âm thầm biến mất</i> nếu một ngày nào đó chúng rỗng, biến một lỗi thành một response trông
 * hợp lệ.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ShippingInfoResponse {

    /** Họ tên người nhận — <b>luôn có mặt</b>. */
    private String fullName;

    /** Số điện thoại người nhận — <b>luôn có mặt</b>. */
    private String phone;

    /** Email nhận xác nhận đơn — <b>luôn có mặt</b>. */
    private String email;

    /** Tên tỉnh / thành — <b>luôn có mặt</b>. */
    private String province;

    /** Tên quận / huyện — <b>luôn có mặt</b>. */
    private String district;

    /** Tên phường / xã — <b>luôn có mặt</b>. */
    private String ward;

    /** Số nhà và tên đường — <b>luôn có mặt</b>. */
    private String street;

    /** Ghi chú giao hàng của khách — <b>vắng mặt khỏi JSON</b> khi khách không ghi gì. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String note;
}
