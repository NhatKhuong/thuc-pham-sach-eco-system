package com.nss.ddd.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Khối {@code shipping} trong body {@code POST /api/orders} — khớp
 * {@code types/order.ts#ShippingInfo} (API_CONTRACT §B.6).
 * <p>
 * <b>Tám trường, và bảy trong số đó bắt buộc.</b> Đây là địa chỉ mà hàng thật sẽ được mang tới, nên
 * một trường rỗng không phải chuyện hiển thị — nó là một đơn không giao được. Chỉ {@code note} là
 * tuỳ chọn ({@code note?} phía client).
 * <p>
 * <b>Chỉ TÊN tỉnh / quận / phường, không có mã.</b> §B.9 nói rõ vì sao: {@code Address} của sổ địa
 * chỉ giữ cả mã lẫn tên vì ô {@code <Select>} chạy theo mã, còn {@code ShippingInfo} của đơn hàng
 * là dữ liệu để in lên chứng từ. Thêm {@code provinceCode} vào đây là kéo cả §B.9 (địa giới hành
 * chính) vào một ticket đã ghi rõ nó là non-goal.
 * <p>
 * <b>Giới hạn {@code @Size} lấy đúng độ dài cột của {@code ShippingInfo}</b>, không phải con số
 * chọn cho đẹp. Thiếu chúng thì một chuỗi quá dài đi qua được tầng validate rồi chết bằng
 * {@code DataIntegrityViolationException} ở tầng dưới — một lỗi 500 cho thứ lẽ ra là 422 kèm tên
 * trường sai.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} khai <b>tường minh</b> điều Spring Boot vốn
 * đã đặt mặc định, cùng lý do đã viết ở {@code CartItemRequest}.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b>; thông điệp validate viết <b>tiếng Anh</b>
 * theo coding-conventions §1 (chuỗi tiếng Việt cho người dùng cuối nằm ở {@code detail} của
 * {@code ProblemDetail}).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingInfoRequest {

    @NotBlank(message = "shipping.fullName must not be blank")
    @Size(max = 128, message = "shipping.fullName must not exceed 128 characters")
    private String fullName;

    @NotBlank(message = "shipping.phone must not be blank")
    @Size(max = 20, message = "shipping.phone must not exceed 20 characters")
    private String phone;

    /**
     * Email nhận xác nhận đơn.
     * <p>
     * <b>{@code @Email} chứ không chỉ {@code @NotBlank}:</b> với khách vãng lai đây là kênh liên
     * lạc <i>duy nhất</i> — không có tài khoản nào để tra lại. Một địa chỉ sai cú pháp lọt xuống DB
     * là một đơn không bao giờ liên hệ lại được, và triệu chứng chỉ lộ ra ở khâu giao hàng.
     */
    @NotBlank(message = "shipping.email must not be blank")
    @Email(message = "shipping.email must be a well-formed email address")
    @Size(max = 160, message = "shipping.email must not exceed 160 characters")
    private String email;

    @NotBlank(message = "shipping.province must not be blank")
    @Size(max = 128, message = "shipping.province must not exceed 128 characters")
    private String province;

    @NotBlank(message = "shipping.district must not be blank")
    @Size(max = 128, message = "shipping.district must not exceed 128 characters")
    private String district;

    @NotBlank(message = "shipping.ward must not be blank")
    @Size(max = 128, message = "shipping.ward must not exceed 128 characters")
    private String ward;

    @NotBlank(message = "shipping.street must not be blank")
    @Size(max = 255, message = "shipping.street must not exceed 255 characters")
    private String street;

    /** Ghi chú giao hàng — trường <b>tuỳ chọn</b> duy nhất của khối này. */
    @Size(max = 500, message = "shipping.note must not exceed 500 characters")
    private String note;
}
