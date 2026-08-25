package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của một người dùng trên bề mặt dây <b>của khu quản trị</b> — khớp đúng type {@code User}
 * của frontend ({@code src/types/user.ts}, API_CONTRACT §B.12.3).
 * <p>
 * <b>Đây là một DTO MỚI, KHÔNG phải một {@link UserResponse} được nới ra — và sự tồn tại của hai
 * kiểu là chủ ý.</b> Javadoc của {@code UserResponse} khoá nó ở <i>đúng năm trường</i> và nói thẳng
 * "thêm một trường vào đây là thay đổi contract"; {@code UserMapperTest} khoá lại điều đó. Nhưng
 * type {@code User} mà bảng khách hàng đọc thì có <b>sáu</b> trường, kể cả {@code role}. Hai bề mặt
 * khác nhau cho hai namespace được gác bằng hai lớp bảo mật khác nhau:
 * <ul>
 *   <li>{@code UserResponse} phục vụ {@code /auth/**} — <i>người đang đăng nhập tự đọc hồ sơ của
 *       chính mình</i>. Ở đó {@code role} cố ý vắng mặt: vai trò đi trong claim {@code roles} của
 *       access token, không đi trong body, và frontend giải nó bằng {@code getRoleFromToken()}.</li>
 *   <li>Kiểu này phục vụ {@code /admin/customers} — <i>đọc chéo mọi người dùng</i>. Ở đó
 *       {@code role} là một <b>cột của bảng</b>, không phải quyền của người đang gọi.</li>
 * </ul>
 * Nới {@code UserResponse} ra sáu trường sẽ làm vai trò của <i>mọi</i> người dùng rò ra mọi response
 * của {@code /auth/**}, tức đúng điều javadoc entity {@code User} cấm.
 * <p>
 * <b>Không bao giờ kèm {@code password}, kể cả hash</b> (§B.12.3) — {@code UserMapper} liệt kê tay
 * đúng sáu trường bên dưới, và {@code passwordHash} không có mặt.
 * <p>
 * <b>{@link #role} trên dây là chữ thường số ít</b> ({@code customer} / {@code admin}), trong khi
 * cột {@code role.code} trong DB là {@code CUSTOMER} / {@code ADMIN}. Phép dịch nằm ở
 * {@code UserMapper} và chỉ ở đó.
 * <p>
 * <b>Không có {@code createdAt}</b>, và đó là lý do danh sách khách hàng không có {@code sort}: type
 * phía client không mang trường đó nên không tồn tại mốc thời gian nào để xếp theo (§B.12.3).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private Long id;

    private String fullName;

    private String email;

    private String phone;

    /** Đường dẫn ảnh <b>tương đối</b> {@code /images/...}; {@code null} khi chưa có (§A.5). */
    private String avatar;

    /**
     * {@code customer} hoặc {@code admin} — <b>chữ thường số ít</b>, khớp
     * {@code types/user.ts#UserRole}.
     * <p>
     * <b>Là dữ liệu hiển thị, không phải một cột phân quyền.</b> Quyền vào được namespace này đã do
     * filter {@code /api/admin/**} gác; trường này chỉ trả lời "người trong dòng này là ai".
     */
    private String role;
}
