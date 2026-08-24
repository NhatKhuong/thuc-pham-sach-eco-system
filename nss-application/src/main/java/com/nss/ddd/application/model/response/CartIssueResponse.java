package com.nss.ddd.application.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Một phần tử của {@code CartIssue[]} trên bề mặt dây — khớp <b>đúng</b> type {@code CartIssue} của
 * frontend (API_CONTRACT §B.6).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 * <p>
 * <b>Ba hình dạng, không phải một hình dạng có ba biến thể:</b>
 * <pre>
 * { "productId": 10, "name": "...", "type": "out_of_stock" }
 * { "productId": 32, "name": "...", "type": "insufficient_stock", "availableStock": 24 }
 * { "productId": 5,  "name": "...", "type": "price_changed", "currentPrice": 39000, "cartPrice": 45000 }
 * </pre>
 * Trường không áp dụng <b>vắng mặt khỏi JSON</b>, không phải bằng 0 và không phải {@code null}.
 * Một {@code "availableStock": 0} gắn trên issue {@code price_changed} là một con số sai — và con
 * số sai thì frontend vẫn hiển thị được, nó chỉ hiển thị sai ("còn 0 sản phẩm" cho một món đang còn
 * hàng). Cưỡng chế bằng {@code @JsonInclude(NON_NULL)}, và <b>chỗ dựng ra chúng</b> là ba static
 * factory của {@code CartIssue} bên domain.
 * <p>
 * <b>{@code @JsonInclude} đặt trên từng trường tuỳ chọn, cố ý KHÔNG đặt ở cấp class.</b> Đặt ở cấp
 * class thì {@code productId}, {@code name}, {@code type} cũng thừa hưởng luật đó — và ba trường
 * bắt buộc ấy sẽ <i>âm thầm biến mất</i> nếu một ngày nào đó chúng rỗng, biến một lỗi thành một
 * response trông hợp lệ. Để nguyên như hiện tại thì một trường bắt buộc rỗng hiện ra thành
 * {@code "name": null} — sai một cách nhìn thấy được.
 * <p>
 * <b>Đây cũng là lý do đường này KHÔNG trả entity {@code Product}.</b> Trả thẳng sản phẩm thì
 * {@code is_active}, {@code sold}, {@code nameNormalized} và cả bảng giá gốc đi ra ngoài trên một
 * endpoint công khai, chỉ để nói "món này hết hàng".
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CartIssueResponse {

    /** Sản phẩm gặp vấn đề — <b>luôn có mặt</b>; id do client gửi, kể cả khi id đó không có trong DB. */
    private Long productId;

    /**
     * Tên hiển thị — <b>luôn có mặt</b>, lấy từ chính dòng client gửi lên chứ không tra lại DB.
     * <p>
     * Nghe ngược với §C.1 nhưng đúng: sản phẩm đã bị gỡ khỏi hệ thống thì không còn tên nào trong
     * DB để tra, mà cảnh báo {@code out_of_stock} vẫn phải gọi tên món hàng ra thì người dùng mới
     * biết bỏ cái nào khỏi giỏ. Đây là chuỗi hiển thị, không phải con số tiền.
     */
    private String name;

    /**
     * {@code "out_of_stock"} / {@code "insufficient_stock"} / {@code "price_changed"} — chuỗi
     * thường, <b>luôn có mặt</b>.
     * <p>
     * Bảng dịch từ enum của domain nằm đúng một chỗ: {@code CartMapper}.
     */
    private String type;

    /**
     * Tồn kho <b>thật, đọc từ DB</b> (§C.1) — chỉ có mặt khi {@code type = "insufficient_stock"}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer availableStock;

    /**
     * Giá hiện tại, lấy từ cột sinh {@code effective_price} — chỉ có mặt khi
     * {@code type = "price_changed"}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long currentPrice;

    /**
     * Giá giỏ hàng của khách đang hiển thị, dội lại nguyên văn từ request — chỉ có mặt khi
     * {@code type = "price_changed"}.
     * <p>
     * Dội lại số client gửi <b>không</b> vi phạm §C.1: mục đó cấm tin số tiền client gửi <i>khi
     * tính tiền</i>, còn ở đây nó là vế "từ X" của câu "giá đã đổi từ X thành Y". Không có đồng nào
     * được tính trên đường này.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long cartPrice;
}
