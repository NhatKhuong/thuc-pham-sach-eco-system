package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của một lệnh xác thực — thành công thì mang {@code auth}, thất bại thì mang {@code code}
 * và {@code message}.
 * <p>
 * <b>Vì sao là giá trị trả về chứ không phải exception:</b> coding-conventions §11 Pattern A nói
 * thất bại nghiệp vụ là giá trị, và §3 đặt mọi kiểu {@code *Exception} ở module <i>controller</i> —
 * mà application nằm <i>dưới</i> controller trong chiều phụ thuộc nên không thể ném chúng.
 * Controller là nơi dịch {@code code} thành mã HTTP thật (403 / 401) theo ADR 0001.
 * <p>
 * Đối tượng này <b>không bao giờ đi ra dây</b>: controller lấy {@code auth} ra trả trần, hoặc ném
 * exception tương ứng. {@code message} viết <b>tiếng Việt</b> vì nó chính là {@code detail} của
 * {@code ProblemDetail} mà frontend hiển thị thẳng cho người dùng cuối (§A.3).
 * <p>
 * <b>{@code register} KHÔNG còn dùng kiểu này từ backlog 0037</b> — nó cấp {@code RegisterResponse}
 * chứ không còn {@code AuthResponse}, nên nó có kiểu kết quả riêng ({@code RegisterMutationResponse}).
 * Kiểu này giờ chỉ phục vụ {@code login} và {@code refresh}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthMutationResponse {

    /**
     * Sai thông tin đăng nhập.
     * <p>
     * <b>Dùng chung cho cả "email không tồn tại" và "sai mật khẩu"</b>, và {@code message} kèm theo
     * cũng phải là một chuỗi duy nhất: phân biệt hai ca biến endpoint đăng nhập thành công cụ dò
     * xem địa chỉ nào đã đăng ký — đúng lý do khiến §B.4 #4 bắt {@code forgot-password} luôn trả
     * 204.
     */
    public static final String CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    /** Refresh token không tồn tại, đã bị thu hồi, hoặc đã hết hạn — ba ca gộp làm một. */
    public static final String CODE_INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    /**
     * Email hợp lệ và mật khẩu đúng, nhưng tài khoản <b>chưa xác nhận email</b> (backlog 0037).
     * <p>
     * <b>Mã riêng, không gộp vào {@link #CODE_INVALID_CREDENTIALS}</b> — khác "sai thông tin đăng
     * nhập", ở đây thông tin đăng nhập đúng hoàn toàn; điều còn thiếu là một bước xác nhận, và người
     * dùng cần biết chính xác việc phải làm tiếp (kiểm tra email / bấm resend) chứ không phải "thử
     * lại mật khẩu". Gộp hai ca sẽ khiến người dùng đoán sai nguyên nhân.
     */
    public static final String CODE_EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";

    /** Phiên vừa cấp; {@code null} khi thất bại. */
    private AuthResponse auth;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param auth phiên vừa cấp
     * @return kết quả thành công
     */
    public static AuthMutationResponse success(AuthResponse auth) {
        return new AuthMutationResponse().setAuth(auth);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static AuthMutationResponse failed(String code, String message) {
        return new AuthMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
