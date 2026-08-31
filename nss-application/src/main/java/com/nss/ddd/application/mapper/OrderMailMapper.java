package com.nss.ddd.application.mapper;

import com.nss.ddd.domain.service.OrderDomainService;

/**
 * Converter viết tay cho phần <b>hiển thị</b> của email trạng thái đơn hàng (backlog 0032, Quyết
 * định Owner 2). Class stateless, method {@code public static}, không phải Spring bean, luôn
 * null-guard (coding-conventions §7) — tách khỏi {@code MailAppServiceImpl} để test được không cần
 * Spring context lẫn Thymeleaf.
 * <p>
 * <b>KHÔNG khai lại nhãn tiếng Việt của trạng thái ở đây</b> — dùng thẳng {@link OrderMapper#toStatusLabel}.
 * Bảng dịch trạng thái chỉ có đúng một chỗ (xem javadoc cấp class của {@code OrderMapper}); một bảng
 * nhãn thứ hai ở đây sẽ lệch với bảng kia đúng vào lúc chỉ một bên được sửa.
 */
public final class OrderMailMapper {

    /** Màu badge cho {@code pending} — vàng hổ phách, "đang chờ". */
    private static final String COLOR_PENDING = "#B45309";

    /** Màu badge cho {@code confirmed} — xanh dương, "đã xác nhận". */
    private static final String COLOR_CONFIRMED = "#1D4ED8";

    /** Màu badge cho {@code shipping} — tím, "đang trên đường". */
    private static final String COLOR_SHIPPING = "#7C3AED";

    /** Màu badge cho {@code delivered} — xanh lá, trạng thái thành công cuối cùng. */
    private static final String COLOR_DELIVERED = "#15803D";

    /** Màu badge cho {@code cancelled} — đỏ, trạng thái thất bại cuối cùng. */
    private static final String COLOR_CANCELLED = "#B91C1C";

    /** Màu trung tính — chỉ dùng khi {@code status} nằm ngoài dải hợp lệ, không nên xảy ra. */
    private static final String COLOR_UNKNOWN = "#6B7280";

    private OrderMailMapper() {
    }

    /**
     * Mã màu hex cho badge trạng thái trong email — một màu riêng cho từng trạng thái trong năm
     * trạng thái, để người nhận phân biệt được ngay bằng mắt mà không cần đọc chữ.
     *
     * @param status con số trong cột {@code status}
     * @return mã hex bắt đầu bằng {@code #}; {@link #COLOR_UNKNOWN} khi giá trị lạ
     */
    public static String genStatusColor(Integer status) {
        if (status == null) {
            return COLOR_UNKNOWN;
        }
        return switch (status) {
            case OrderDomainService.STATUS_PENDING -> COLOR_PENDING;
            case OrderDomainService.STATUS_CONFIRMED -> COLOR_CONFIRMED;
            case OrderDomainService.STATUS_SHIPPING -> COLOR_SHIPPING;
            case OrderDomainService.STATUS_DELIVERED -> COLOR_DELIVERED;
            case OrderDomainService.STATUS_CANCELLED -> COLOR_CANCELLED;
            default -> COLOR_UNKNOWN;
        };
    }

    /**
     * Tiêu đề email — nêu thẳng mã đơn và trạng thái mới để người nhận đọc được nội dung chính ngay
     * ở hộp thư đến, không cần mở email.
     *
     * @param orderCode mã đơn hiển thị cho khách
     * @param status trạng thái mới, con số trong cột {@code status}
     * @return tiêu đề tiếng Việt
     */
    public static String genSubject(String orderCode, Integer status) {
        return "Cập nhật đơn hàng " + orderCode + " — " + OrderMapper.toStatusLabel(status);
    }
}
