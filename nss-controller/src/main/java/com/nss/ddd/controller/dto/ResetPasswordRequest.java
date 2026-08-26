package com.nss.ddd.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/auth/reset-password}.
 * <p>
 * <b>CẢ ENDPOINT LẪN HAI TÊN TRƯỜNG Ở ĐÂY ĐỀU LÀ SUY DIỄN CỦA BACKEND, không đọc ra từ nguồn.</b>
 * API_CONTRACT §B.4 khai <i>đúng một</i> endpoint ({@code forgotPassword}) cho một luồng
 * <i>hai</i> bước; nửa còn lại không tồn tại ở bất kỳ đâu trong tài liệu. Backlog 0017 chọn tên
 * endpoint, tên hai trường và mã lỗi; đã route lên backlog 0015 cùng năm chỗ lệch khác cùng loại.
 * Nếu tài liệu nguồn phía frontend chọn tên khác thì đây là chỗ phải sửa, và đó là <b>một thay đổi
 * contract</b>, không phải một lần đổi tên biến.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b>; thông điệp viết <b>tiếng Anh</b> theo §1.
 */
@Data
public class ResetPasswordRequest {

    /**
     * Chuỗi token lấy từ link trong email.
     * <p>
     * <b>Cố ý chỉ có {@code @NotBlank}, không có {@code min} và không có ràng buộc định dạng.</b>
     * Một luật độ dài ở đây sẽ nói cho người gửi chuỗi bịa biết token thật dài bao nhiêu, và tách
     * "sai định dạng" (422 kèm {@code errors}) khỏi "không dùng được" (422 không kèm
     * {@code errors}) — tức tạo lại đúng cái phân biệt mà §Contract điều 2 yêu cầu gộp lại. Mọi
     * chuỗi không rỗng đều đi tới cùng một cổng và nhận cùng một câu trả lời.
     * <p>
     * Trần 512 chỉ để một body khổng lồ không đi xuống tới tầng băm; nó không mang thông tin gì về
     * độ dài thật (43 ký tự).
     */
    @NotBlank(message = "Thiếu mã đặt lại mật khẩu, vui lòng mở lại liên kết trong email.")
    @Size(max = 512, message = "Mã đặt lại mật khẩu không hợp lệ, vui lòng mở lại liên kết trong email.")
    private String token;

    /**
     * Mật khẩu mới.
     * <p>
     * <b>Cùng ràng buộc với {@code register} và {@code changePassword}</b> ({@code @Size(min = 6,
     * max = 72)}) — cùng một luật độ dài cho cùng một thứ (backlog 0017 §Contract điều 6). Trần 72
     * là giới hạn của thuật toán chứ không phải con số tuỳ ý: bcrypt chỉ băm 72 byte đầu và
     * <i>im lặng</i> bỏ phần còn lại; lý do đầy đủ nằm ở {@code RegisterRequest}.
     */
    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = 6, max = 72, message = "Mật khẩu mới phải từ 6 đến 72 ký tự.")
    private String newPassword;
}
