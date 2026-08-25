package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Một đơn hàng trên bề mặt dây — type {@code Order} của frontend (API_CONTRACT §B.6).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001. Cùng một hình dạng được trả bởi cả ba
 * endpoint {@code POST /orders}, {@code GET /orders/me} và {@code GET /orders/{code}}: một đơn là
 * một đơn, và ba hình dạng cho cùng một thứ sẽ buộc frontend viết ba nhánh hiển thị.
 * <p>
 * <b>Mọi con số tiền ở đây do backend tự tính</b>, không lấy từ payload của client (§C.1). Thứ tự
 * tính là contract và cố định (§Contract 1): {@code subtotal} từ giá tra lại trong DB,
 * {@code discount} trên {@code subtotal} đó, {@code shippingFee} trên hiệu
 * {@code subtotal - discount} — <b>không</b> trên {@code subtotal} trần — rồi {@code total}.
 * <p>
 * <b>{@code status} và {@code paymentMethod} là chuỗi thường, DB lưu {@code int}</b> (§Contract 4).
 * Bảng dịch nằm đúng một chỗ: {@code OrderMapper}.
 * <p>
 * <b>{@code userId} là {@code null} tường minh cho đơn của khách vãng lai</b> (§D #2), không phải
 * một trường vắng mặt: {@code Order.userId: number | null} phía frontend là kiểu bắt buộc, và §A.5
 * chốt "giá trị không có" là {@code null} chứ không phải chuỗi rỗng hay khoá bị bỏ đi. Cùng lý do
 * cho {@code couponCode} khi đơn không áp mã — nên class này <b>không</b> mang
 * {@code @JsonInclude(NON_NULL)} ở cấp nào.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    /** Khoá chính của đơn. */
    private Long id;

    /**
     * Mã đơn hiển thị cho khách, dạng {@code NSS-20260817-0001} (§Contract 6).
     * <p>
     * Đây là khoá tra cứu của {@code GET /orders/{code}} và là thứ duy nhất nhân viên với khách
     * cùng đọc được qua điện thoại; {@code id} không bao giờ rời khỏi cơ sở dữ liệu.
     */
    private String code;

    /** Chủ đơn; <b>{@code null} là đơn của khách vãng lai</b> (§B.6, §D #2). */
    private Long userId;

    /** Các dòng hàng, giữ đúng thứ tự khách xếp trong giỏ; <b>không</b> mang trường {@code stock} (§Contract 7). */
    private List<OrderItemResponse> items;

    /** Thông tin giao hàng, bản chụp tại thời điểm đặt. */
    private ShippingInfoResponse shipping;

    /** {@code cod} / {@code bank_transfer} / {@code momo} / {@code vnpay} — chuỗi thường (§Contract 4). */
    private String paymentMethod;

    /**
     * {@code pending} / {@code confirmed} / {@code shipping} / {@code delivered} / {@code cancelled}.
     * <p>
     * Đơn vừa tạo luôn ở {@code pending}; việc chuyển trạng thái thuộc
     * {@code PATCH /admin/orders/{code}/status} của backlog 0013, không thuộc ticket này.
     */
    private String status;

    /** Tổng tiền hàng trước giảm giá, số nguyên VNĐ. */
    private Long subtotal;

    /** Số tiền được giảm, số nguyên VNĐ; {@code 0} khi không áp mã. */
    private Long discount;

    /** Phí vận chuyển, số nguyên VNĐ — tính trên hiệu {@code subtotal - discount} (§Contract 1). */
    private Long shippingFee;

    /** Tổng phải trả, số nguyên VNĐ — con số cuối cùng frontend hiển thị lại. */
    private Long total;

    /** Mã giảm giá đã áp, bản chụp; <b>{@code null} tường minh</b> khi đơn không áp mã. */
    private String couponCode;

    /**
     * Chuỗi ISO 8601 <b>kèm hậu tố {@code Z}</b>, ví dụ {@code 2026-08-17T10:30:00Z} (§A.5).
     * <p>
     * Là {@code String} chứ không {@code LocalDateTime}, cùng lý do đã viết ở
     * {@code ProductResponse}: cột lưu giờ UTC nhưng {@code LocalDateTime} không mang múi giờ, nên
     * Jackson sẽ tuần tự hoá thành chuỗi không có offset và {@code new Date(...)} phía trình duyệt
     * đọc nó như <i>giờ địa phương</i> — lệch đúng 7 tiếng ở VN mà không có gì báo lỗi.
     */
    private String createdAt;
}
