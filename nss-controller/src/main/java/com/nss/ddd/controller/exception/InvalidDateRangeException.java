package com.nss.ddd.controller.exception;

/**
 * Khoảng thời gian yêu cầu nằm ngoài dải hợp lệ — {@code GlobalExceptionHandler} dịch thành
 * <b>400</b> (API_CONTRACT §B.12.4).
 * <p>
 * <b>400 chứ không 422, và đó là chữ của hợp đồng:</b> cột Lỗi của §B.12.4 khai đúng
 * {@code 400, 401, 403}. Nó cũng đọc đúng nghĩa — {@code days} là một <i>tham số truy vấn</i> sai,
 * không phải một quy tắc nghiệp vụ bị vi phạm; và nó không có ô nhập nào để chỉ vào, nên không có
 * map {@code errors} đi kèm (cùng quy ước với {@code EmptyOrderException}).
 * <p>
 * <b>Vì sao ném lỗi thay vì âm thầm kẹp giá trị về biên</b> — §B.12.4 nói thẳng: "đừng âm thầm kẹp
 * giá trị, một khoảng khác thứ người dùng yêu cầu là một câu trả lời sai im lặng". Kẹp
 * {@code days=9999} về 365 sẽ trả về một biểu đồ trông hoàn toàn hợp lý cho một câu hỏi khác hẳn
 * câu được hỏi.
 * <p>
 * Message truyền vào phải là <b>tiếng Việt cho người dùng cuối</b> — nó đi thẳng vào {@code detail}
 * của {@code ProblemDetail} và frontend hiển thị nguyên văn (§A.3).
 */
public class InvalidDateRangeException extends RuntimeException {

    /**
     * @param message thông điệp tiếng Việt cho người dùng cuối
     */
    public InvalidDateRangeException(String message) {
        super(message);
    }
}
