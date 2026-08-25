package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Body của {@code PUT /api/products/{id}}.
 * <p>
 * Cùng tập trường với {@code CreateProductRequest} — {@code PUT} thay trọn bản ghi, kể cả mảng ảnh.
 * Sản phẩm được xác định bằng {@code id} trên đường dẫn nên body không mang {@code id}.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b> — {@code javax.validation} bị cấm
 * (coding-conventions §7, §17). Thông điệp validation viết <b>tiếng Anh</b> theo §1; chuỗi tiếng
 * Việt mà người dùng cuối đọc là {@code detail} của {@code ProblemDetail}, do
 * {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Danh sách trường ở đây chính là cổng chặn.</b> {@code id}, {@code effectivePrice},
 * {@code rating}, {@code reviewCount}, {@code sold}, {@code nameNormalized}, {@code isActive},
 * {@code createdAt} / {@code updatedAt} cố ý không có mặt: Spring Boot tắt
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, nên client gửi chúng lên thì Jackson bỏ qua trong im lặng —
 * đúng như contract yêu cầu ("client gửi lên thì bỏ qua, không báo lỗi").
 * <p>
 * Quy tắc liên trường {@code salePrice < price} <b>không</b> nằm ở đây mà ở
 * {@code ProductDomainService}: nó là bất biến nghiệp vụ, và đặt ở đó thì lỗi trả về mang được
 * {@code detail} tiếng Việt cụ thể thay vì một khoá trường do annotation suy ra.
 */
@Data
public class UpdateProductRequest {

    /**
     * Slug do admin gõ; <b>bỏ trống nghĩa là "tự sinh từ {@code name}"</b> (§B.12.1).
     * <p>
     * <b>Cố ý KHÔNG còn {@code @NotBlank} và {@code @Pattern("^[a-z0-9-]+$")}.</b> Hai ràng buộc
     * đó từ chối đúng thứ frontend gửi lên: ô slug của màn quản trị cho gõ tự do, và
     * {@code adminProducts.api.ts:117} slugify cả slug client gửi chứ không chỉ khi bỏ trống. Giữ
     * chúng lại thì "Cà Rốt Hữu Cơ" gõ vào ô slug trả 422 thay vì thành {@code ca-rot-huu-co}, và
     * bỏ trống ô slug — ca dùng phổ biến nhất — cũng trả 422.
     * <p>
     * <b>Nới ở đây KHÔNG phải nới luật:</b> hình dạng slug vẫn được cưỡng chế, chỉ là ở chỗ khác và
     * bằng cách khác. {@code ProductDomainService#genSlug} chuẩn hoá về đúng tập ký tự cũ, và trả
     * lỗi nghiệp vụ khi không còn ký tự hợp lệ nào. Khác biệt là nó <i>sửa</i> đầu vào hợp lý thay
     * vì <i>từ chối</i> nó.
     * <p>
     * {@code @Size} thì giữ: nó chặn một chuỗi dài hơn cột {@code varchar(160)} <b>trước</b> khi
     * chuỗi ấy đi tới tầng dữ liệu.
     */
    @Size(max = 160, message = "slug must not exceed 160 characters")
    private String slug;

    @NotBlank(message = "name must not be blank")
    @Size(max = 255, message = "name must not exceed 255 characters")
    private String name;

    @Size(max = 500, message = "shortDescription must not exceed 500 characters")
    private String shortDescription;

    private String description;

    @NotNull(message = "price must not be null")
    @Positive(message = "price must be greater than 0")
    private Long price;

    /** Rỗng nghĩa là không giảm giá — §A.5 cấm dùng 0 hay chuỗi rỗng thay cho {@code null}. */
    @Positive(message = "salePrice must be greater than 0")
    private Long salePrice;

    @NotBlank(message = "unit must not be blank")
    @Size(max = 32, message = "unit must not exceed 32 characters")
    private String unit;

    @Size(max = 128, message = "origin must not exceed 128 characters")
    private String origin;

    @NotNull(message = "stock must not be null")
    @PositiveOrZero(message = "stock must not be negative")
    private Integer stock;

    private Boolean isFeatured;

    private Boolean isBestSeller;

    @NotNull(message = "categoryId must not be null")
    private Long categoryId;

    /** Rỗng khi sản phẩm không gắn thương hiệu nào. */
    private Long brandId;

    /**
     * Đường dẫn ảnh <b>tương đối</b> dạng {@code /images/...} (§A.5); thứ tự trong mảng là thứ tự
     * hiển thị trong gallery.
     * <p>
     * Ticket này không nhận multipart và không ghi file — backend chỉ lưu chuỗi.
     */
    private List<@Size(max = 255, message = "image path must not exceed 255 characters") String> images;
}
