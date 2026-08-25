package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Một vấn đề phát hiện được trên một dòng giỏ hàng — phần tử của {@code CartIssue[]} (§B.6).
 * <p>
 * <b>Ba trường tuỳ chọn, và mỗi trường chỉ thuộc về đúng một loại:</b>
 * <ul>
 *   <li>{@code availableStock} — <b>chỉ</b> {@link CartIssueType#INSUFFICIENT_STOCK};</li>
 *   <li>{@code currentPrice} và {@code cartPrice} — <b>chỉ</b> {@link CartIssueType#PRICE_CHANGED};</li>
 *   <li>{@link CartIssueType#OUT_OF_STOCK} không mang trường tuỳ chọn nào.</li>
 * </ul>
 * Trường không áp dụng phải <b>vắng mặt</b>, không phải bằng 0. Một
 * {@code "availableStock": 0} gắn trên issue {@code price_changed} là một con số sai, và con số sai
 * thì frontend vẫn hiển thị được — nó chỉ hiển thị sai. Chỗ cưỡng chế "vắng mặt" trên JSON là
 * {@code CartIssueResponse} ở tầng application; chỗ cưỡng chế "không bao giờ được điền" là ba
 * static factory bên dưới.
 * <p>
 * <b>Dựng issue bằng static factory, không bao giờ {@code new} rồi set tay</b>
 * (coding-conventions §7). Lombok {@code @Data} vẫn sinh setter — chúng tồn tại cho Jackson và cho
 * kiểm thử, không phải để gọi ở call site nghiệp vụ. Ba factory dưới đây là cách duy nhất đảm bảo
 * một issue ra đời với đúng tập trường của loại nó: đọc call site là biết ngay loại nào đang được
 * dựng, và không có đường nào để lỡ tay điền một trường thuộc loại khác.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CartIssue {

    /** Sản phẩm gặp vấn đề — id do client gửi, kể cả khi id đó không có trong DB. */
    private Long productId;

    /** Tên hiển thị do client gửi (xem javadoc {@link CartLine#getName()}). */
    private String name;

    /** Loại vấn đề; bảng dịch sang chuỗi của dây nằm ở {@code CartMapper}. */
    private CartIssueType type;

    /** Tồn kho thật đọc từ DB — chỉ có mặt ở {@link CartIssueType#INSUFFICIENT_STOCK}. */
    private Integer availableStock;

    /** Giá hiện tại, lấy từ cột sinh {@code effective_price} — chỉ ở {@link CartIssueType#PRICE_CHANGED}. */
    private Long currentPrice;

    /** Giá mà giỏ hàng đang hiển thị — chỉ ở {@link CartIssueType#PRICE_CHANGED}. */
    private Long cartPrice;

    /**
     * Không tìm thấy sản phẩm, hoặc {@code stock <= 0}, hoặc {@code is_active = 0}.
     * <p>
     * Ba nguyên nhân khác nhau, <b>một</b> câu trả lời: với người đang cầm giỏ hàng, một sản phẩm
     * đã bị gỡ khỏi hệ thống và một sản phẩm hết hàng là cùng một tình huống — bỏ nó ra khỏi giỏ.
     * Tách thành ba loại issue sẽ bắt frontend xử lý ba nhánh cho cùng một hành động, và tiết lộ
     * rằng một id nào đó từng tồn tại.
     *
     * @param productId id sản phẩm do client gửi
     * @param name tên hiển thị do client gửi
     * @return issue không mang trường tuỳ chọn nào
     */
    public static CartIssue outOfStock(Long productId, String name) {
        return new CartIssue()
                .setProductId(productId)
                .setName(name)
                .setType(CartIssueType.OUT_OF_STOCK);
    }

    /**
     * Còn hàng nhưng ít hơn số lượng khách muốn.
     *
     * @param productId id sản phẩm do client gửi
     * @param name tên hiển thị do client gửi
     * @param availableStock tồn kho <b>thật, đọc từ DB</b> — không bao giờ là con số client gửi (§C.1)
     * @return issue mang {@code availableStock}
     */
    public static CartIssue insufficientStock(Long productId, String name, Integer availableStock) {
        return new CartIssue()
                .setProductId(productId)
                .setName(name)
                .setType(CartIssueType.INSUFFICIENT_STOCK)
                .setAvailableStock(availableStock);
    }

    /**
     * Giá đã đổi kể từ lúc khách thêm sản phẩm vào giỏ.
     *
     * @param productId id sản phẩm do client gửi
     * @param name tên hiển thị do client gửi
     * @param currentPrice giá hiện tại, từ cột sinh {@code effective_price}
     * @param cartPrice giá client gửi lên — dội lại nguyên văn để frontend nói được "từ X thành Y"
     * @return issue mang cả hai giá
     */
    public static CartIssue priceChanged(Long productId, String name, Long currentPrice, Long cartPrice) {
        return new CartIssue()
                .setProductId(productId)
                .setName(name)
                .setType(CartIssueType.PRICE_CHANGED)
                .setCurrentPrice(currentPrice)
                .setCartPrice(cartPrice);
    }
}
