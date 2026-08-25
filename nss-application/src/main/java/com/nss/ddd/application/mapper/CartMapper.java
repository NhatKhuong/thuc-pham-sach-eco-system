package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.response.CartIssueResponse;
import com.nss.ddd.domain.model.CartIssue;
import com.nss.ddd.domain.model.CartIssueType;
import com.nss.ddd.domain.model.CartLine;

import java.util.Collections;
import java.util.List;

/**
 * Converter viết tay giữa các kiểu của tầng application và của domain cho luồng giỏ hàng.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7).
 * <p>
 * <b>Đây là "đúng một chỗ" của bảng dịch {@code type}</b> — cùng vai trò mà {@code CouponMapper}
 * giữ cho {@code percent} / {@code fixed}. Domain nói bằng enum {@code CartIssueType}; dây mang
 * chuỗi thường mà {@code orders.api.ts} {@code switch} lên. Nhân đôi bảng dịch — một bản ở đây, một
 * bản ở chỗ nào đó của phase 3 — là cách chắc chắn nhất để hai bản lệch nhau, và triệu chứng sẽ là
 * một cảnh báo hết hàng được frontend đọc như cảnh báo đổi giá: <i>không chặn thanh toán</i>, trong
 * khi §B.6 nói nó phải chặn.
 * <p>
 * Ba hằng {@code WIRE_TYPE_*} là {@code public} để test khoá được chính chuỗi đi lên dây thay vì
 * chép lại chúng — một bản chép trong test sẽ đổi theo cùng lúc với bản trong code và không bắt
 * được gì.
 */
public final class CartMapper {

    /** Chuỗi {@code type} trên dây cho "không mua được" — khớp {@code src/types/cart.ts}. */
    public static final String WIRE_TYPE_OUT_OF_STOCK = "out_of_stock";

    /** Chuỗi {@code type} trên dây cho "không đủ hàng" — khớp {@code src/types/cart.ts}. */
    public static final String WIRE_TYPE_INSUFFICIENT_STOCK = "insufficient_stock";

    /** Chuỗi {@code type} trên dây cho "giá đã đổi" — khớp {@code src/types/cart.ts}. */
    public static final String WIRE_TYPE_PRICE_CHANGED = "price_changed";

    /**
     * Class tiện ích, không có thể hiện.
     */
    private CartMapper() {
    }

    /**
     * Dựng đầu vào của domain từ lệnh của tầng application.
     *
     * @param command một dòng giỏ hàng
     * @return dòng giỏ hàng của domain, hoặc {@code null} khi {@code command} rỗng
     */
    public static CartLine toLine(CartItemCommand command) {
        if (command == null) {
            return null;
        }
        return new CartLine()
                .setProductId(command.getProductId())
                .setName(command.getName())
                .setQuantity(command.getQuantity())
                .setPrice(command.getPrice());
    }

    /**
     * <b>Thứ tự được giữ nguyên</b> — thứ tự issue trả về bám theo thứ tự dòng client gửi, nên phép
     * chuyển đổi này không được phép sắp xếp lại gì.
     *
     * @param commands các dòng giỏ hàng
     * @return các dòng của domain; danh sách rỗng khi đầu vào rỗng
     */
    public static List<CartLine> toLines(List<CartItemCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return Collections.emptyList();
        }
        return commands.stream()
                .map(CartMapper::toLine)
                .toList();
    }

    /**
     * Dựng payload cho bề mặt dây.
     * <p>
     * Chỉ trường tương ứng với loại issue được điền — ba trường tuỳ chọn còn lại giữ {@code null}
     * và do đó vắng mặt khỏi JSON. Việc này <b>không</b> cần một cái {@code switch}: chính
     * {@code CartIssue} đã ra đời với đúng tập trường của loại nó (ba static factory bên domain),
     * nên chép thẳng qua là đủ. Một {@code switch} ở đây sẽ là bản sao thứ hai của cùng một luật.
     *
     * @param issue vấn đề do domain phát hiện
     * @return payload, hoặc {@code null} khi {@code issue} rỗng
     */
    public static CartIssueResponse toResponse(CartIssue issue) {
        if (issue == null) {
            return null;
        }
        return new CartIssueResponse()
                .setProductId(issue.getProductId())
                .setName(issue.getName())
                .setType(toWireType(issue.getType()))
                .setAvailableStock(issue.getAvailableStock())
                .setCurrentPrice(issue.getCurrentPrice())
                .setCartPrice(issue.getCartPrice());
    }

    /**
     * @param issues các vấn đề, thứ tự giữ nguyên như domain trả về
     * @return danh sách payload; <b>danh sách rỗng</b> khi giỏ hợp lệ — không bao giờ {@code null}
     */
    public static List<CartIssueResponse> toResponses(List<CartIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return Collections.emptyList();
        }
        return issues.stream()
                .map(CartMapper::toResponse)
                .toList();
    }

    // ========== HELPERS ==========

    /**
     * Bảng dịch enum của domain sang chuỗi thường của dây.
     * <p>
     * <b>Giá trị lạ trả {@code null} chứ không đoán</b> — cùng lý do đã viết ở {@code CouponMapper}:
     * một loại issue mới ra đời mà quên khai ở đây thì rơi về một trong ba chuỗi cũ sẽ khiến
     * frontend xử lý sai loại và <i>vẫn hiển thị một cái gì đó</i>; {@code null} thì hỏng ngay và
     * hỏng ở chỗ đọc được.
     *
     * @param type loại vấn đề của domain
     * @return chuỗi của dây, hoặc {@code null} khi giá trị không nằm trong bảng dịch
     */
    private static String toWireType(CartIssueType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case OUT_OF_STOCK -> WIRE_TYPE_OUT_OF_STOCK;
            case INSUFFICIENT_STOCK -> WIRE_TYPE_INSUFFICIENT_STOCK;
            case PRICE_CHANGED -> WIRE_TYPE_PRICE_CHANGED;
        };
    }
}
