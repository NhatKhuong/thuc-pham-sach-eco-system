package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code PUT /api/auth/password}.
 * <p>
 * <b>Tên hai trường là một SUY DIỄN của backlog 0016, không phải thứ đọc ra từ nguồn.</b> Kiểu
 * {@code ChangePasswordPayload} được {@code API_CONTRACT} §B.4 nêu tên rồi dừng — bản mirror không
 * định nghĩa nó ở bất kỳ đâu. Đã ghi thành lệch #5 của backlog 0015; nếu tài liệu nguồn phía
 * frontend dùng tên khác thì đây là chỗ phải sửa, và đó là một thay đổi contract.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b>; thông điệp viết <b>tiếng Anh</b> theo §1.
 * <p>
 * <b>Hai trường chịu hai ràng buộc KHÁC nhau, và sự bất đối xứng đó là chủ ý:</b>
 * <ul>
 *   <li>{@code newPassword} chịu <i>cùng</i> ràng buộc như {@code register}
 *       ({@code @Size(min = 6, max = 72)}) — cùng một luật độ dài cho cùng một thứ. Trần 72 là giới
 *       hạn của thuật toán chứ không phải con số tuỳ ý: bcrypt chỉ băm 72 byte đầu và <i>im lặng</i>
 *       bỏ phần còn lại; lý do đầy đủ nằm ở {@code RegisterRequest}.</li>
 *   <li>{@code currentPassword} <b>cố ý không có {@code min}</b>. Đặt {@code min} ở đó biến một lần
 *       gõ sai mật khẩu cũ thành một lỗi <i>theo trường</i> — và qua đó nói cho người gõ biết mật
 *       khẩu thật dài ít nhất bao nhiêu ký tự. Trần 72 vẫn giữ để một chuỗi dài vô hạn không đi
 *       xuống tới bcrypt.</li>
 * </ul>
 * <b>{@code userId} không có mặt và không được phép có mặt</b> — định danh chỉ đến từ claim
 * {@code sub} của access token (§C.4.1).
 * <p>
 * <b>{@code newPassword} trùng {@code currentPassword} thì CHO PHÉP.</b> Hợp đồng im lặng về ca
 * này; thêm một ca thất bại không được khai là bắt frontend hiển thị một lỗi nó không có câu chữ
 * nào để hiển thị.
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    @Size(max = 72, message = "Mật khẩu hiện tại không được vượt quá 72 ký tự.")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = 6, max = 72, message = "Mật khẩu mới phải từ 6 đến 72 ký tự.")
    private String newPassword;
}
