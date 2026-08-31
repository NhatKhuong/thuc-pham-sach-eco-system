package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của {@code POST /products/{id}/reviews} — thành công thì mang {@code review}, thất bại
 * thì mang {@code code} và {@code message}.
 * <p>
 * Cùng khuôn với {@link OrderMutationResponse} và {@link ProductMutationResponse}, cùng lý do:
 * coding-conventions §11 Pattern A nói thất bại nghiệp vụ là <b>giá trị trả về</b>, và §3 đặt mọi
 * kiểu {@code *Exception} ở module <i>controller</i> — mà application nằm <i>dưới</i> controller
 * trong chiều phụ thuộc nên không thể ném chúng. Controller là nơi dịch {@code code} thành mã HTTP.
 * <p>
 * Đối tượng này <b>không bao giờ đi ra dây</b>: controller lấy {@code review} ra trả trần, hoặc ném
 * exception tương ứng. {@code message} viết <b>tiếng Việt</b> vì nó chính là {@code detail} của
 * {@code ProblemDetail} mà frontend hiển thị thẳng cho người dùng cuối (§A.3).
 * <p>
 * <b>Ba mã lỗi, ba mã HTTP:</b>
 * <ul>
 *   <li>{@link #CODE_PRODUCT_NOT_FOUND} → <b>404</b>: sản phẩm không tồn tại hoặc đã bị xoá mềm.</li>
 *   <li>{@link #CODE_DUPLICATE_REVIEW} → <b>409</b>: tài khoản này đã đánh giá sản phẩm này rồi
 *       (ADR 0008). <b>Xung đột trạng thái, không phải lỗi ô nhập</b> — nên response
 *       <b>không</b> mang map {@code errors}, và sự vắng mặt của khoá đó chính là thứ phân biệt nó
 *       với 422 của validate. Cùng quy ước đã chốt ở {@code DuplicateSlugException} (backlog 0018)
 *       và ở {@code InvalidCurrentPasswordException} (backlog 0016).</li>
 *   <li>{@link #CODE_INVALID_REVIEW_DATA} → <b>422</b>: dữ liệu đúng cú pháp nhưng sai ngữ nghĩa
 *       nghiệp vụ — hiện là token trỏ tới một tài khoản không còn tồn tại. Cùng lựa chọn mã với
 *       {@link OrderMutationResponse#CODE_INVALID_ORDER_DATA} ở đúng ca đó.</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReviewMutationResponse {

    /** Sản phẩm không tồn tại hoặc đã bị xoá mềm — controller dịch thành <b>404</b>. */
    public static final String CODE_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";

    /**
     * Tài khoản đã đánh giá sản phẩm này rồi — controller dịch thành <b>409</b>.
     * <p>
     * Mã này chỉ ra đời từ <b>0 dòng bị ảnh hưởng</b> của {@code INSERT IGNORE}, tức từ chính ràng
     * buộc {@code uk_review_product_user}. Không có đường nào khác dựng ra nó, và đó là chủ ý: một
     * phép {@code SELECT} chạy trước rồi kết luận "đã có" là đọc-rồi-ghi, đúng cơ chế của
     * {@code bugs/0004}.
     */
    public static final String CODE_DUPLICATE_REVIEW = "DUPLICATE_REVIEW";

    /** Dữ liệu đánh giá vi phạm quy tắc nghiệp vụ — controller dịch thành <b>422</b>. */
    public static final String CODE_INVALID_REVIEW_DATA = "INVALID_REVIEW_DATA";

    /** Đánh giá sau khi ghi; {@code null} khi thất bại. */
    private ReviewResponse review;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param review đánh giá đã ghi
     * @return kết quả thành công
     */
    public static ReviewMutationResponse success(ReviewResponse review) {
        return new ReviewMutationResponse().setReview(review);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static ReviewMutationResponse failed(String code, String message) {
        return new ReviewMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
