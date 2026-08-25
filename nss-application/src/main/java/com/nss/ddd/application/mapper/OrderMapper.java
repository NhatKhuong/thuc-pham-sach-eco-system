package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.OrderItemResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.application.model.response.ShippingInfoResponse;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ShippingInfo;
import com.nss.ddd.domain.service.OrderDomainService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Converter viết tay giữa các kiểu của tầng application và của domain cho luồng đơn hàng.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7).
 * <p>
 * <b>Đây là "đúng một chỗ" của HAI bảng dịch enum</b> mà backlog 0014 §Contract 4 chốt —
 * {@code status} 0..4 và {@code paymentMethod} 0..3. DB lưu {@code int} (rẻ, có thứ tự, không phụ
 * thuộc chuỗi), dây mang chuỗi thường mà {@code types/order.ts} khai thành union. Nhân đôi bảng
 * dịch là cách chắc chắn nhất để hai bản lệch nhau, và triệu chứng sẽ là một đơn {@code cancelled}
 * hiển thị thành {@code delivered}: không exception nào, chỉ một chứng từ nói sai.
 * <p>
 * <b>Con số của {@code status} không được khai lại ở đây.</b> Năm hằng sống ở
 * {@link OrderDomainService} vì domain là nơi <i>ghi</i> chúng xuống cột lúc tạo đơn; file này chỉ
 * <i>dịch</i>. Chiều phụ thuộc chỉ cho phép một hướng, nên chỗ duy nhất chứa được cả hai người dùng
 * là domain — xem javadoc {@code OrderDomainService.STATUS_PENDING}.
 * <p>
 * <b>{@code paymentMethod} thì ngược lại: cả bốn con số sống ở đây và chỉ ở đây.</b> Domain không
 * bao giờ đọc chúng — nó chỉ lưu lại thứ khách chọn — nên không có người dùng thứ hai để phải kéo
 * hằng xuống dưới. Bảng này là <b>hai chiều</b>, và đó cũng là lý do
 * {@code CreateOrderRequest.paymentMethod} cố ý <i>không</i> mang {@code @Pattern} liệt kê bốn chuỗi
 * hợp lệ: một regex như vậy sẽ là bản thứ hai của đúng bảng này, đặt ở một tầng không có gì buộc nó
 * đổi theo.
 * <p>
 * Các hằng {@code WIRE_*} là {@code public} để test khoá được chính chuỗi đi lên dây thay vì chép
 * lại chúng — một bản chép trong test sẽ đổi theo cùng lúc với bản trong code và không bắt được gì.
 */
public final class OrderMapper {

    /** Chuỗi {@code status} trên dây cho đơn vừa đặt — khớp {@code types/order.ts#OrderStatus}. */
    public static final String WIRE_STATUS_PENDING = "pending";

    /** Chuỗi {@code status} trên dây cho đơn đã xác nhận. */
    public static final String WIRE_STATUS_CONFIRMED = "confirmed";

    /** Chuỗi {@code status} trên dây cho đơn đang giao. */
    public static final String WIRE_STATUS_SHIPPING = "shipping";

    /** Chuỗi {@code status} trên dây cho đơn đã giao. */
    public static final String WIRE_STATUS_DELIVERED = "delivered";

    /** Chuỗi {@code status} trên dây cho đơn đã huỷ. */
    public static final String WIRE_STATUS_CANCELLED = "cancelled";

    /** Chuỗi {@code paymentMethod} trên dây — thanh toán khi nhận hàng. */
    public static final String WIRE_PAYMENT_COD = "cod";

    /** Chuỗi {@code paymentMethod} trên dây — chuyển khoản ngân hàng. */
    public static final String WIRE_PAYMENT_BANK_TRANSFER = "bank_transfer";

    /**
     * Chuỗi {@code paymentMethod} trên dây — ví MoMo (§D #1).
     * <p>
     * <b>Là một giá trị hợp lệ của trường, KHÔNG phải một luồng thanh toán.</b> Backlog 0014 ghi rõ
     * không tích hợp cổng thanh toán nào; đơn chọn {@code momo} vẫn ra đời ở trạng thái
     * {@code pending} y hệt đơn {@code cod}.
     */
    public static final String WIRE_PAYMENT_MOMO = "momo";

    /** Chuỗi {@code paymentMethod} trên dây — VNPay (§D #1); xem ghi chú ở {@link #WIRE_PAYMENT_MOMO}. */
    public static final String WIRE_PAYMENT_VNPAY = "vnpay";

    /** Giá trị {@code payment_method} trong DB cho {@link #WIRE_PAYMENT_COD}. */
    private static final int PAYMENT_COD = 0;

    /** Giá trị {@code payment_method} trong DB cho {@link #WIRE_PAYMENT_BANK_TRANSFER}. */
    private static final int PAYMENT_BANK_TRANSFER = 1;

    /** Giá trị {@code payment_method} trong DB cho {@link #WIRE_PAYMENT_MOMO}. */
    private static final int PAYMENT_MOMO = 2;

    /** Giá trị {@code payment_method} trong DB cho {@link #WIRE_PAYMENT_VNPAY}. */
    private static final int PAYMENT_VNPAY = 3;

    /**
     * Class tiện ích, không có thể hiện.
     */
    private OrderMapper() {
    }

    // ========== LENH -> ENTITY ==========

    /**
     * Dựng một dòng hàng từ <b>dữ liệu thật trong DB</b>, không từ con số client gửi.
     * <p>
     * <b>Đây là chỗ §C.1 được cưỡng chế, và danh sách bên dưới là bằng chứng đọc được:</b> mọi
     * trường trừ {@code quantity} đều lấy từ {@code product}. {@code price} lấy từ cột sinh
     * {@code effective_price} — <b>không</b> tính lại {@code salePrice ?? price} ở Java, vì cùng một
     * con số tính ở hai nơi theo hai quy ước là đúng thứ coding-conventions §15 cấm. Trường
     * {@code price} mà {@code CartItemCommand} mang theo <i>không có đường nào</i> đi vào method
     * này: nó không nằm trong danh sách tham số.
     * <p>
     * {@code quantity} là thứ duy nhất khách được quyết, và nó đã đi qua {@code @Positive} ở tầng
     * DTO rồi qua conditional UPDATE tồn kho trước khi tới đây.
     *
     * @param product sản phẩm còn hiệu lực, tra lại từ DB
     * @param quantity số lượng khách đặt
     * @param image ảnh đầu tiên của sản phẩm, đường dẫn tương đối; {@code null} khi sản phẩm chưa
     *              có ảnh nào
     * @return dòng hàng chưa gắn {@code order}, hoặc {@code null} khi {@code product} rỗng
     */
    public static OrderItem toItem(Product product, Integer quantity, String image) {
        if (product == null) {
            return null;
        }
        return new OrderItem()
                .setProductId(product.getId())
                .setSlug(product.getSlug())
                .setName(product.getName())
                .setImage(image)
                .setUnit(product.getUnit())
                .setPrice(product.getEffectivePrice())
                .setOriginalPrice(product.getPrice())
                .setQuantity(quantity);
    }

    /**
     * @param command thông tin giao hàng từ lệnh
     * @return giá trị nhúng của entity, hoặc {@code null} khi {@code command} rỗng
     */
    public static ShippingInfo toShippingInfo(ShippingInfoCommand command) {
        if (command == null) {
            return null;
        }
        return new ShippingInfo()
                .setFullName(command.getFullName())
                .setPhone(command.getPhone())
                .setEmail(command.getEmail())
                .setProvince(command.getProvince())
                .setDistrict(command.getDistrict())
                .setWard(command.getWard())
                .setStreet(command.getStreet())
                .setNote(command.getNote());
    }

    /**
     * Dịch chuỗi {@code paymentMethod} của dây sang con số lưu trong DB.
     * <p>
     * <b>Chuỗi lạ trả {@code null} chứ không đoán</b> — cùng lý do đã viết ở {@code CouponMapper} và
     * {@code CartMapper}: rơi về {@code cod} khi không nhận ra sẽ ghi xuống chứng từ một phương
     * thức thanh toán mà khách không hề chọn, và không có gì ném lỗi. {@code null} thì tầng use
     * case biến nó thành 422 với thông điệp đọc được.
     *
     * @param wireValue chuỗi client gửi
     * @return con số tương ứng, hoặc {@code null} khi chuỗi không nằm trong bảng dịch
     */
    public static Integer toPaymentMethodCode(String wireValue) {
        if (wireValue == null) {
            return null;
        }
        return switch (wireValue.trim()) {
            case WIRE_PAYMENT_COD -> PAYMENT_COD;
            case WIRE_PAYMENT_BANK_TRANSFER -> PAYMENT_BANK_TRANSFER;
            case WIRE_PAYMENT_MOMO -> PAYMENT_MOMO;
            case WIRE_PAYMENT_VNPAY -> PAYMENT_VNPAY;
            default -> null;
        };
    }

    // ========== ENTITY -> DAY ==========

    /**
     * Dựng payload cho bề mặt dây.
     *
     * @param order đơn hàng đã ghi
     * @param items dòng hàng của chính đơn này, đã sắp theo thứ tự chèn; có thể rỗng
     * @return payload, hoặc {@code null} khi {@code order} rỗng
     */
    public static OrderResponse toResponse(Order order, List<OrderItem> items) {
        if (order == null) {
            return null;
        }
        return new OrderResponse()
                .setId(order.getId())
                .setCode(order.getCode())
                // `null` tuong minh cho don khach vang lai (§D #2) — khong phai mot truong vang mat
                .setUserId(order.getUser() == null ? null : order.getUser().getId())
                .setItems(toItemResponses(items))
                .setShipping(toShippingResponse(order.getShipping()))
                .setPaymentMethod(toWirePaymentMethod(order.getPaymentMethod()))
                .setStatus(toWireStatus(order.getStatus()))
                .setSubtotal(order.getSubtotal())
                .setDiscount(order.getDiscount())
                .setShippingFee(order.getShippingFee())
                .setTotal(order.getTotal())
                .setCouponCode(order.getCouponCode())
                .setCreatedAt(toIsoUtc(order.getCreatedAt()));
    }

    /**
     * @param item dòng hàng
     * @return payload, hoặc {@code null} khi {@code item} rỗng
     */
    public static OrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) {
            return null;
        }
        // Danh sach viet tay chinh la cho chan `stock` khong bao gio moc lai vao day (§Contract 7):
        // OrderItem khong co truong do, va mot BeanUtils.copyProperties se tu dong mang theo bat ky
        // truong nao ai do them vao sau nay.
        return new OrderItemResponse()
                .setProductId(item.getProductId())
                .setSlug(item.getSlug())
                .setName(item.getName())
                .setImage(item.getImage())
                .setUnit(item.getUnit())
                .setPrice(item.getPrice())
                .setOriginalPrice(item.getOriginalPrice())
                .setQuantity(item.getQuantity());
    }

    /**
     * <b>Thứ tự được giữ nguyên</b> — dòng hàng hiển thị theo đúng thứ tự khách xếp trong giỏ.
     *
     * @param items các dòng hàng
     * @return danh sách payload; danh sách rỗng khi đầu vào rỗng — không bao giờ {@code null}
     */
    public static List<OrderItemResponse> toItemResponses(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(OrderMapper::toItemResponse)
                .toList();
    }

    /**
     * @param shipping thông tin giao hàng nhúng trong đơn
     * @return payload, hoặc {@code null} khi {@code shipping} rỗng
     */
    public static ShippingInfoResponse toShippingResponse(ShippingInfo shipping) {
        if (shipping == null) {
            return null;
        }
        return new ShippingInfoResponse()
                .setFullName(shipping.getFullName())
                .setPhone(shipping.getPhone())
                .setEmail(shipping.getEmail())
                .setProvince(shipping.getProvince())
                .setDistrict(shipping.getDistrict())
                .setWard(shipping.getWard())
                .setStreet(shipping.getStreet())
                .setNote(shipping.getNote());
    }

    // ========== HELPERS ==========

    /**
     * Bảng dịch {@code status} từ con số của DB sang chuỗi của dây (§Contract 4).
     * <p>
     * <b>Giá trị lạ trả {@code null} chứ không đoán.</b> Một trạng thái thứ sáu ra đời mà quên khai
     * ở đây thì rơi về một trong năm chuỗi cũ sẽ khiến frontend hiển thị <i>một cái gì đó</i> — một
     * đơn đã huỷ trông như đang giao; {@code null} thì hỏng ngay và hỏng ở chỗ đọc được.
     *
     * @param status con số trong cột {@code status}
     * @return chuỗi của dây, hoặc {@code null} khi giá trị không nằm trong bảng dịch
     */
    private static String toWireStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case OrderDomainService.STATUS_PENDING -> WIRE_STATUS_PENDING;
            case OrderDomainService.STATUS_CONFIRMED -> WIRE_STATUS_CONFIRMED;
            case OrderDomainService.STATUS_SHIPPING -> WIRE_STATUS_SHIPPING;
            case OrderDomainService.STATUS_DELIVERED -> WIRE_STATUS_DELIVERED;
            case OrderDomainService.STATUS_CANCELLED -> WIRE_STATUS_CANCELLED;
            default -> null;
        };
    }

    /**
     * Bảng dịch {@code paymentMethod} từ con số của DB sang chuỗi của dây — chiều ngược của
     * {@link #toPaymentMethodCode}.
     *
     * @param paymentMethod con số trong cột {@code payment_method}
     * @return chuỗi của dây, hoặc {@code null} khi giá trị không nằm trong bảng dịch
     */
    private static String toWirePaymentMethod(Integer paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        return switch (paymentMethod) {
            case PAYMENT_COD -> WIRE_PAYMENT_COD;
            case PAYMENT_BANK_TRANSFER -> WIRE_PAYMENT_BANK_TRANSFER;
            case PAYMENT_MOMO -> WIRE_PAYMENT_MOMO;
            case PAYMENT_VNPAY -> WIRE_PAYMENT_VNPAY;
            default -> null;
        };
    }

    /**
     * Cột lưu giờ UTC nên đóng dấu hậu tố {@code Z} vào chuỗi trả ra (§A.5) — thiếu nó, trình duyệt
     * đọc chuỗi như giờ địa phương và lệch 7 tiếng ở VN mà không có gì báo lỗi.
     *
     * @param value thời điểm lưu trong DB, hiểu là giờ UTC
     * @return chuỗi ISO 8601 dạng {@code 2026-08-17T10:30:00Z}, hoặc {@code null} khi đầu vào rỗng
     */
    private static String toIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC));
    }
}
