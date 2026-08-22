package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Body của {@code POST /api/products}.
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
public class CreateProductRequest {

    @NotBlank(message = "slug must not be blank")
    @Size(max = 160, message = "slug must not exceed 160 characters")
    @Pattern(regexp = "^[a-z0-9-]+$",
            message = "slug must contain only lowercase letters, digits and hyphens")
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
