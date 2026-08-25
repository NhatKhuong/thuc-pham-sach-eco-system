package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của {@code POST /orders} — thành công thì mang {@code order}, thất bại thì mang
 * {@code code} và {@code message}.
 * <p>
 * Cùng khuôn với {@link ProductMutationResponse} và {@link CouponValidationResponse}, cùng lý do:
 * coding-conventions §11 Pattern A nói thất bại nghiệp vụ là <b>giá trị trả về</b>, và §3 đặt mọi
 * kiểu {@code *Exception} ở module <i>controller</i> — mà application nằm <i>dưới</i> controller
 * trong chiều phụ thuộc nên không thể ném chúng. Controller là nơi dịch {@code code} thành mã HTTP
 * thật.
 * <p>
 * Đối tượng này <b>không bao giờ đi ra dây</b>: controller lấy {@code order} ra trả trần, hoặc ném
 * exception tương ứng. {@code message} viết <b>tiếng Việt</b> vì nó chính là {@code detail} của
 * {@code ProblemDetail} mà frontend hiển thị thẳng cho người dùng cuối (§A.3).
 * <p>
 * <b>Ba mã lỗi, ba mã HTTP — và bộ ba đó lấy thẳng từ cột Lỗi của §B.6</b>
 * ({@code 400 giỏ trống, 409 hết hàng, 422 mã giảm giá sai}):
 * <ul>
 *   <li>{@link #CODE_EMPTY_ORDER} → <b>400</b>: không có dòng hàng nào để đặt.</li>
 *   <li>{@link #CODE_OUT_OF_STOCK} → <b>409</b>: một dòng không còn đủ hàng, hoặc sản phẩm đã bị
 *       gỡ khỏi cửa hàng.</li>
 *   <li>{@link #CODE_INVALID_ORDER_DATA} → <b>422</b>: dữ liệu đúng cú pháp nhưng sai ngữ nghĩa
 *       nghiệp vụ — hiện là phương thức thanh toán lạ, hoặc token trỏ tới một tài khoản không còn
 *       tồn tại.</li>
 * </ul>
 * Ca <b>mã giảm giá không dùng được</b> cố ý <i>không</i> có mã riêng ở đây: nó dùng lại
 * {@link CouponValidationResponse#CODE_COUPON_NOT_APPLICABLE} và do đó dùng lại đúng exception mà
 * {@code POST /coupons/validate} đã ném, tức đúng một mã HTTP và đúng một khuôn thông điệp cho cùng
 * một sự việc. Dựng mã lỗi thứ hai cho cùng một luật là bước đầu tiên để hai bên trả lời khác nhau.
 * <p>
 * <b>Vì sao "mã không tồn tại" ở đây là 422 chứ không phải 404</b> như ở
 * {@code POST /coupons/validate}: mã HTTP nói về <i>tài nguyên của request</i>, và tài nguyên của
 * {@code POST /orders} là đơn hàng, không phải mã giảm giá. Trả 404 sẽ nói với frontend rằng đường
 * dẫn đặt hàng không tồn tại. Cột Lỗi của §B.6 cũng chỉ liệt kê 400 / 409 / 422 cho endpoint này.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderMutationResponse {

    /** Giỏ không có dòng hàng nào — controller dịch thành <b>400</b>. */
    public static final String CODE_EMPTY_ORDER = "EMPTY_ORDER";

    /**
     * Một dòng hàng không mua được — controller dịch thành <b>409</b>.
     * <p>
     * Gộp ba tình huống vào một mã là có chủ ý và khớp với quy ước đã dùng ở
     * {@code POST /cart/validate}: không đủ tồn kho, sản phẩm không tồn tại, và sản phẩm đã bị xoá
     * mềm đều dẫn tới cùng một hành động của người dùng — bỏ món đó khỏi giỏ. Sự khác nhau giữa
     * chúng nằm trong {@code message}, chỗ người dùng thật sự đọc.
     */
    public static final String CODE_OUT_OF_STOCK = "OUT_OF_STOCK";

    /** Dữ liệu đơn không qua được quy tắc nghiệp vụ — controller dịch thành <b>422</b>. */
    public static final String CODE_INVALID_ORDER_DATA = "INVALID_ORDER_DATA";

    /**
     * Không có đơn nào mang mã đó — controller dịch thành <b>404</b> (backlog 0019).
     *
     * <p>Ra đời cùng {@code PATCH /admin/orders/{code}/status}: đó là lệnh ghi đầu tiên khoá theo
     * một mã do client cung cấp, nên nó là chỗ đầu tiên "không tìm thấy" trở thành một kết quả
     * <i>của lệnh ghi</i> chứ không phải một {@code null} trả về từ đường đọc.
     *
     * <p><b>404 chứ không 422</b>, khác ba mã trên: ở đây tài nguyên của request — chính cái đơn
     * trong đường dẫn — không tồn tại. Ba mã kia nói về <i>nội dung</i> của một request trỏ đúng
     * tài nguyên. §B.12.2 cũng liệt kê đúng 404 cho ca này.
     */
    public static final String CODE_ORDER_NOT_FOUND = "ORDER_NOT_FOUND";

    /** Đơn hàng sau khi ghi; {@code null} khi thất bại. */
    private OrderResponse order;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param order đơn hàng đã ghi
     * @return kết quả thành công
     */
    public static OrderMutationResponse success(OrderResponse order) {
        return new OrderMutationResponse().setOrder(order);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static OrderMutationResponse failed(String code, String message) {
        return new OrderMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
