package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của một lệnh <b>ghi</b> sản phẩm ({@code POST} / {@code PUT}) — thành công thì mang
 * {@code product}, thất bại thì mang {@code code} và {@code message}.
 * <p>
 * <b>Vì sao là giá trị trả về chứ không phải exception:</b> coding-conventions §11 Pattern A nói
 * thất bại nghiệp vụ là giá trị, và §3 đặt mọi kiểu {@code *Exception} ở module <i>controller</i> —
 * mà application nằm <i>dưới</i> controller trong chiều phụ thuộc nên không thể ném chúng.
 * Controller là nơi dịch {@code code} thành mã HTTP thật (409 / 422 / 404) theo ADR 0001.
 * <p>
 * Đối tượng này <b>không bao giờ đi ra dây</b>: controller lấy {@code product} ra trả trần, hoặc
 * ném exception tương ứng. {@code message} viết <b>tiếng Việt</b> vì nó chính là {@code detail}
 * của {@code ProblemDetail} mà frontend hiển thị thẳng cho người dùng cuối (§A.3).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProductMutationResponse {

    /** Không tìm thấy sản phẩm — hoặc id không tồn tại, hoặc đã bị xoá mềm. */
    public static final String CODE_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";

    /** Slug đã có sản phẩm khác giữ. */
    public static final String CODE_DUPLICATE_SLUG = "DUPLICATE_SLUG";

    /** Dữ liệu không qua được quy tắc nghiệp vụ (cặp giá, danh mục / thương hiệu không tồn tại). */
    public static final String CODE_INVALID_PRODUCT_DATA = "INVALID_PRODUCT_DATA";

    /** Sản phẩm sau khi ghi; {@code null} khi thất bại. */
    private ProductResponse product;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param product sản phẩm đã ghi
     * @return kết quả thành công
     */
    public static ProductMutationResponse success(ProductResponse product) {
        return new ProductMutationResponse().setProduct(product);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static ProductMutationResponse failed(String code, String message) {
        return new ProductMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
