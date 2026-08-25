package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh sửa hồ sơ — {@code PUT /api/auth/me}.
 * <p>
 * <b>{@code userId} là tham số riêng, không đến từ body.</b> Nó được đọc từ claim {@code sub} của
 * access token (§C.4.1: "Vi phạm ở đây là rò rỉ dữ liệu, không phải lỗi hiển thị"); chữ ký của
 * {@code AuthControllerMapper.toUpdateProfileCommand} viết như vậy để việc lấy định danh từ body
 * không thể xảy ra do sơ ý — đúng kỷ luật {@code toLogoutCommand} đã đặt ra.
 * <p>
 * <b>Ba trường hồ sơ mang ngữ nghĩa <i>vá từng phần</i>: {@code null} nghĩa là GIỮ NGUYÊN giá trị
 * cũ, không phải "đặt về rỗng".</b> Vắng mặt trong JSON và {@code null} tường minh là <i>một</i> —
 * Jackson khử cả hai về cùng một {@code null}, và phân biệt chúng cần {@code JsonNullable} hay
 * theo dõi sự hiện diện. Bộ máy đó mua về một phân biệt mà domain không dùng được: {@code full_name},
 * {@code email}, {@code phone} đều {@code NOT NULL}, nên "đặt về null" không có đích hợp lệ nào.
 * Chuỗi rỗng thì khác — nó bị chặn ở tầng validate và trả 422, xem {@code UpdateProfileRequest}.
 * <p>
 * <b>{@code avatar} cố ý vắng mặt.</b> Chưa có đường upload nào (backlog 0007), nên nhận một chuỗi
 * path do client tự bịa là mời client trỏ vào bất cứ đâu. {@code id}, {@code role},
 * {@code passwordHash}, {@code createdAt} vắng mặt vì contract §B.4 #2 cấm ghi đè chúng.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileCommand {

    /** Chủ hồ sơ, <b>lấy từ claim {@code sub} của access token</b>. */
    private Long userId;

    /** {@code null} nghĩa là giữ nguyên. */
    private String fullName;

    /** {@code null} nghĩa là giữ nguyên. */
    private String email;

    /** {@code null} nghĩa là giữ nguyên. */
    private String phone;
}
