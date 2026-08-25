package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code PUT /api/auth/me} — <b>bản vá từng phần</b>, không phải bản thay thế.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b> — {@code javax.validation} bị cấm
 * (coding-conventions §7, §17). Thông điệp validation viết <b>tiếng Anh</b> theo §1; chuỗi tiếng
 * Việt mà người dùng cuối đọc là {@code detail} của {@code ProblemDetail}, do
 * {@code GlobalExceptionHandler} dựng.
 * <p>
 * <b>Ba trạng thái của một trường phải cho ra ba kết quả phân biệt được</b>, và đó chính là bằng
 * chứng của ngữ nghĩa partial:
 * <ul>
 *   <li><i>vắng mặt</i> trong JSON → {@code null} → 200, giữ nguyên giá trị cũ</li>
 *   <li>{@code null} tường minh → cũng {@code null} → 200, giữ nguyên giá trị cũ</li>
 *   <li>chuỗi rỗng hoặc toàn khoảng trắng → <b>422</b> kèm map {@code errors}</li>
 * </ul>
 * Hai ca đầu gộp làm một là <i>chủ ý</i>: Jackson khử cả khoá vắng mặt lẫn {@code null} tường minh
 * về cùng một {@code null} trong Java, và phân biệt chúng cần {@code JsonNullable} hoặc theo dõi sự
 * hiện diện — một bộ máy mua về một phân biệt mà domain không dùng được, vì cả ba cột đều
 * {@code NOT NULL} nên "đặt về null" không có đích hợp lệ nào.
 * <p>
 * <b>Vì sao KHÔNG dùng {@code @NotBlank} ở đây.</b> {@code @NotBlank} từ chối {@code null}, mà
 * {@code null} tại đây nghĩa là "giữ nguyên" — dùng nó sẽ biến mọi trường thành bắt buộc và giết
 * chết ngữ nghĩa partial. Thứ cần là một ràng buộc <b>bỏ qua {@code null}</b> nhưng vẫn bắt được
 * chuỗi rỗng: {@code @Pattern} làm đúng điều đó (đặc tả Bean Validation nói {@code null} luôn hợp lệ
 * với {@code @Pattern}).
 * <p>
 * <b>Cờ {@code DOTALL} không phải trang trí.</b> {@code @Pattern} khớp <i>toàn chuỗi</i>
 * ({@code Matcher.matches()}), và mặc định {@code .} không khớp ký tự xuống dòng. Thiếu cờ này thì
 * {@code "Nguyễn\nAn"} không khớp được mẫu và người dùng nhận thông điệp "must not be blank" trên
 * một giá trị <i>không hề rỗng</i> — một thông điệp sai, trên một 422 đúng.
 * <p>
 * <b>{@code @Email} một mình là chưa đủ.</b> Nó chấp nhận chuỗi rỗng như một giá trị hợp lệ, nên
 * không có ràng buộc chống-rỗng đi kèm thì {@code {"email": ""}} lọt qua validate rồi đâm thẳng vào
 * cột {@code NOT NULL} — 500 thay vì 422.
 * <p>
 * <b>Danh sách trường ở đây chính là cổng chặn.</b> {@code id}, {@code role}, {@code avatar},
 * {@code passwordHash}, {@code createdAt} cố ý không có mặt: Spring Boot tắt
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, nên client gửi chúng lên thì Jackson <b>bỏ qua trong im
 * lặng</b> — đúng như §B.4 #2 yêu cầu ("client gửi lên thì bỏ qua, không báo lỗi"), và nghĩa là
 * không ai tự nâng quyền cho mình bằng một trường thừa trong body.
 * <p>
 * <b>{@code avatar} vắng mặt còn vì một lý do riêng:</b> backlog 0007 chưa mở đường upload nào, nên
 * nhận một chuỗi path do client tự khai là mời client trỏ vào bất cứ đâu.
 * <p>
 * Giới hạn độ dài khớp cột trong DB ({@code full_name} 128, {@code email} 160, {@code phone} 20).
 */
@Data
public class UpdateProfileRequest {

    /**
     * Mẫu "có ít nhất một ký tự không phải khoảng trắng" — thứ thay thế {@code @NotBlank} cho một
     * trường tuỳ chọn. {@code null} vẫn hợp lệ theo đặc tả {@code @Pattern}.
     */
    private static final String PATTERN_NOT_BLANK = ".*\\S.*";

    @Pattern(regexp = PATTERN_NOT_BLANK, flags = Pattern.Flag.DOTALL,
            message = "fullName must not be blank when provided")
    @Size(max = 128, message = "fullName must not exceed 128 characters")
    private String fullName;

    @Pattern(regexp = PATTERN_NOT_BLANK, flags = Pattern.Flag.DOTALL,
            message = "email must not be blank when provided")
    @Email(message = "email must be a well-formed email address")
    @Size(max = 160, message = "email must not exceed 160 characters")
    private String email;

    @Pattern(regexp = PATTERN_NOT_BLANK, flags = Pattern.Flag.DOTALL,
            message = "phone must not be blank when provided")
    @Size(max = 20, message = "phone must not exceed 20 characters")
    private String phone;
}
