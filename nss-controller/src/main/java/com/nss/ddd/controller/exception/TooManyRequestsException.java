package com.nss.ddd.controller.exception;

/**
 * Vượt ngưỡng tần suất cho phép — {@code GlobalExceptionHandler} dịch thành <b>429</b>.
 * <p>
 * Sinh ra cho {@code POST /api/auth/forgot-password} (backlog 0017 §Contract điều 8): một endpoint
 * công khai nhận email vừa là công cụ dò tài khoản gián tiếp, vừa là cách bắt hệ thống <i>gửi mail
 * hộ</i> — mỗi lời gọi thành công là một email thật rời khỏi hệ thống, nên không giới hạn nghĩa là
 * biến server thành máy phát tán thư rác nhắm vào một địa chỉ do người gọi chọn.
 * <p>
 * <b>429 chứ không phải 403.</b> 403 nói "bạn không có quyền", tức một trạng thái vĩnh viễn mà
 * người dùng không làm gì được. 429 nói "quá nhanh, thử lại sau" — đúng sự thật, và là mã duy nhất
 * frontend có thể dịch thành một câu hướng dẫn có ích.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> (§A.3) và <b>không nêu ngưỡng
 * cụ thể</b>: con số đó chỉ giúp người muốn lách nó.
 */
public class TooManyRequestsException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public TooManyRequestsException(String message) {
        super(message);
    }
}
