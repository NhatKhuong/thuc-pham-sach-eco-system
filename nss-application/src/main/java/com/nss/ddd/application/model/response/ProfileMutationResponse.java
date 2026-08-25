package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Kết quả của lệnh sửa hồ sơ ({@code PUT /auth/me}) — thành công thì mang {@code user}, thất bại thì
 * mang {@code code} và {@code message}.
 * <p>
 * <b>Vì sao là giá trị trả về chứ không phải exception:</b> coding-conventions §11 Pattern A nói
 * thất bại nghiệp vụ là giá trị, và §3 đặt mọi kiểu {@code *Exception} ở module <i>controller</i> —
 * mà application nằm <i>dưới</i> controller trong chiều phụ thuộc nên không thể ném chúng.
 * <p>
 * <b>Vì sao là một kiểu RIÊNG chứ không thêm mã vào {@code AuthMutationResponse}:</b> đường dịch của
 * kiểu đó có nhánh {@code default} ném {@code InvalidCredentialsException} → <b>401</b>. Nhét
 * {@code USER_NOT_FOUND} vào chung nghĩa là bất kỳ mã nào quên map về sau đều <i>âm thầm</i> thành
 * 401, và {@code client.ts} phản ứng với 401 bằng cách gọi {@code /auth/refresh} rồi đăng xuất
 * người dùng. Giữ các tập mã <b>rời nhau</b> thì mỗi đường dịch chỉ phải phủ đúng tập của nó, và
 * một mã lọt lưới sẽ nổ thành 500 — ồn ào, tức là sửa được.
 * <p>
 * Đối tượng này <b>không bao giờ đi ra dây</b>: controller lấy {@code user} ra trả trần, hoặc ném
 * exception tương ứng. {@code message} viết <b>tiếng Việt</b> vì nó chính là {@code detail} của
 * {@code ProblemDetail} mà frontend hiển thị thẳng cho người dùng cuối (§A.3).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMutationResponse {

    /**
     * Dòng {@code user} ứng với claim {@code sub} không còn tồn tại.
     * <p>
     * Hôm nay trạng thái này <b>không thể xảy ra</b> — không có đường nào xoá tài khoản. Mã vẫn tồn
     * tại vì một {@code sub} hợp lệ về chữ ký không đồng nghĩa với một bản ghi có thật, và ngày có
     * đường xoá tài khoản thì chỗ này đã đúng sẵn.
     */
    public static final String CODE_USER_NOT_FOUND = "USER_NOT_FOUND";

    /** Email mới đã có tài khoản khác giữ — {@code uk_email} nằm trên toàn bảng. */
    public static final String CODE_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";

    /** Hồ sơ sau khi ghi, đúng 5 trường; {@code null} khi thất bại. */
    private UserResponse user;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; {@code null} khi thành công. */
    private String code;

    /** Thông điệp tiếng Việt cho người dùng cuối; {@code null} khi thành công. */
    private String message;

    /**
     * @param user hồ sơ đã ghi
     * @return kết quả thành công
     */
    public static ProfileMutationResponse success(UserResponse user) {
        return new ProfileMutationResponse().setUser(user);
    }

    /**
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    public static ProfileMutationResponse failed(String code, String message) {
        return new ProfileMutationResponse()
                .setCode(code)
                .setMessage(message);
    }
}
