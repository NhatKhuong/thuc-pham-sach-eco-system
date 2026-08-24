package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Một dòng giỏ hàng ở ranh giới application — đầu vào của use case đối chiếu giỏ
 * (coding-conventions §7).
 * <p>
 * <b>Bốn trường, giống hệt {@code CartLine} của domain — và sự trùng lặp đó có chủ ý.</b> Cùng lý
 * do mà {@code ProductControllerMapper} viết ra: hai kiểu trùng tên trường hôm nay không có nghĩa
 * là chúng sẽ trùng mãi. Ngày {@code CartItemRequest} nhận thêm một trường mà domain không được
 * biết — hoặc domain cần một khái niệm mà bề mặt HTTP không có — thì ranh giới này là chỗ duy nhất
 * phải sửa, và trình biên dịch sẽ chỉ đúng vào nó.
 * <p>
 * <b>Năm trường còn lại của {@code CartItem} phía client dừng lại ở tầng controller</b>
 * ({@code slug}, {@code image}, {@code unit}, {@code originalPrice}, {@code stock}) — xem javadoc
 * của {@code CartItemRequest} và {@code CartLine}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CartItemCommand {

    /** Khóa tra sản phẩm trong DB. */
    private Long productId;

    /** Tên hiển thị do client gửi; đi thẳng vào issue, không tra lại DB. */
    private String name;

    /** Số lượng khách muốn mua. */
    private Integer quantity;

    /** Giá giỏ hàng đang hiển thị, số nguyên VNĐ — chỉ để so sánh và dội lại, không để tính tiền. */
    private Long price;
}
