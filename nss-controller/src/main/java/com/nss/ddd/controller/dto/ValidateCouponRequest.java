package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/coupons/validate} — API_CONTRACT §B.7 khai {@code { code, subtotal }}.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b> — {@code javax.validation} bị cấm
 * (coding-conventions §7, §17). Thông điệp validation viết <b>tiếng Anh</b> theo §1; chuỗi tiếng
 * Việt mà người dùng cuối đọc là {@code detail} của {@code ProblemDetail}.
 * <p>
 * <b>Cố ý KHÔNG có {@code @Pattern} trên {@code code}.</b> Một mã sai định dạng phải trả
 * <b>404 "mã không tồn tại"</b> — câu trả lời đúng và dễ hiểu — chứ không phải 422 kèm một lỗi
 * trường nói về biểu thức chính quy. Ràng buộc duy nhất là độ dài, khớp {@code varchar(32)} của
 * cột: dài hơn thì chắc chắn không khớp dòng nào, và chặn sớm thì không phải đẩy chuỗi dài tuỳ ý
 * xuống truy vấn.
 * <p>
 * {@code subtotal} là {@code Long} vì tiền là <b>số nguyên VNĐ</b> (§A.5), và
 * {@code @PositiveOrZero} chứ không {@code @Positive}: giỏ rỗng có {@code subtotal = 0}, đó là một
 * giá trị hợp lệ để hỏi (câu trả lời sẽ là 422 chưa đạt ngưỡng, không phải 400 sai định dạng).
 */
@Data
public class ValidateCouponRequest {

    @NotBlank(message = "code must not be blank")
    @Size(max = 32, message = "code must not exceed 32 characters")
    private String code;

    @NotNull(message = "subtotal must not be null")
    @PositiveOrZero(message = "subtotal must not be negative")
    private Long subtotal;
}
