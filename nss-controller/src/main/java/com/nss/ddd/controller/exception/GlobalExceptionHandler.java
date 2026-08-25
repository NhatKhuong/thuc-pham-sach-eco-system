package com.nss.ddd.controller.exception;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global handler — bắt buộc ở dự án mới (coding-conventions §11 Pattern B). Dự án tham chiếu không
 * có handler nào, nên mọi lỗi ngoài dự kiến trả ra một body khác hình dạng và frontend vỡ khi parse.
 * <p>
 * Trả <b>{@code ProblemDetail}</b> (RFC 7807) với <b>mã HTTP thật</b> — ADR 0001 và API_CONTRACT
 * §A.3. Đây là chỗ bề mặt dây lệch với architecture §7 ({@code ResultMessage} + HTTP 200); mọi quy
 * ước khác của {@code coding-conventions} vẫn giữ nguyên.
 * <p>
 * <b>{@code detail} luôn viết tiếng Việt.</b> Frontend hiển thị thẳng chuỗi đó ở 24 chỗ, nên tiếng
 * Anh hay stack trace ở đây là thứ người dùng cuối đọc được nguyên văn.
 * <p>
 * <p>
 * <b>{@code @Order(HIGHEST_PRECEDENCE)} là bắt buộc, không phải trang trí.</b> Bật
 * {@code spring.mvc.problemdetails.enabled} khiến Spring tự đăng ký
 * {@code ProblemDetailsExceptionHandler} ở order 0, và nó cũng nhận
 * {@code MethodArgumentNotValidException}. Không xếp trước nó thì lỗi validate trả
 * <i>400 "Invalid request content."</i> thay vì 422 kèm map {@code errors} — đúng hình dạng
 * ProblemDetail nên rất dễ tưởng là đã chạy đúng.
 * <p>
 * Lưới cuối cho ngoại lệ ngoài dự kiến nằm ở {@link UnexpectedExceptionHandler}, cố ý tách ra một
 * class riêng xếp <i>sau cùng</i>: gộp {@code @ExceptionHandler(Exception.class)} vào đây thì advice
 * này giành luôn cả 405 / 415 / 404-không-có-handler của Spring và biến chúng thành 500.
 * Spring tự điền {@code type}, {@code title} và {@code instance}; {@code errors} là phần mở rộng
 * ngoài chuẩn của §A.3, map {@code tên trường -> thông điệp}.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MESSAGE_VALIDATION_FAILED =
            "Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra lại các trường được đánh dấu.";

    private static final String MESSAGE_BAD_REQUEST =
            "Yêu cầu không hợp lệ, vui lòng thử lại.";

    /**
     * @param e lỗi không tìm thấy sản phẩm
     * @return 404 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException e) {
        log.warn("handleProductNotFound: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * @param e lỗi trùng slug
     * @return 409 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(DuplicateSlugException.class)
    public ProblemDetail handleDuplicateSlug(DuplicateSlugException e) {
        log.warn("handleDuplicateSlug: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Sai thông tin đăng nhập, hoặc refresh token không còn dùng được.
     * <p>
     * <b>Cố ý không log định danh nào của người dùng.</b> Chuỗi {@code detail} trả về giống hệt
     * nhau cho mọi ca thất bại, và log phải giữ nguyên tính chất đó — một dòng log phân biệt được
     * "email không tồn tại" với "sai mật khẩu" là cùng một rò rỉ, chỉ đổi nơi đọc. Ca đăng nhập đã
     * ghi {@code email} ở tầng application, chỗ có ngữ cảnh để ghi đúng mức {@code warn}.
     *
     * @param e lỗi xác thực
     * @return 401 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        log.warn("handleInvalidCredentials: authentication rejected");
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * @param e lỗi trùng email
     * @return 409 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException e) {
        log.warn("handleDuplicateEmail: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Không tìm thấy tài khoản ứng với claim {@code sub}.
     *
     * @param e lỗi không tìm thấy người dùng
     * @return 404 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException e) {
        log.warn("handleUserNotFound: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Sai mật khẩu hiện tại khi đổi mật khẩu.
     * <p>
     * <b>422, không phải 401</b> — 401 là tín hiệu "access token hỏng" mà {@code client.ts} phản
     * ứng bằng cách gọi {@code /auth/refresh} rồi đăng xuất; xem javadoc của
     * {@link InvalidCurrentPasswordException}.
     * <p>
     * <b>Cố ý KHÔNG đặt khoá {@code errors}</b>: sự vắng mặt của nó là thứ phân biệt 422 này với
     * 422 của validate. Và <b>cố ý không log định danh nào</b>, đúng kỷ luật của
     * {@link #handleInvalidCredentials}: một dòng log gắn userId vào mỗi lần đoán sai mật khẩu biến
     * file log thành bản ghi các nỗ lực dò mật khẩu theo từng tài khoản.
     *
     * @param e lỗi sai mật khẩu hiện tại
     * @return 422 kèm {@code detail} tiếng Việt, <b>không</b> có khoá {@code errors}
     */
    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ProblemDetail handleInvalidCurrentPassword(InvalidCurrentPasswordException e) {
        log.warn("handleInvalidCurrentPassword: current password rejected");
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * Token đặt lại mật khẩu không dùng được — <b>một handler cho cả ba ca</b> (không tồn tại, đã
     * dùng, đã hết hạn).
     * <p>
     * <b>422, không phải 401</b> — người gọi đang <i>không đăng nhập</i>, nên 401 vô nghĩa ở đây;
     * lý do đầy đủ nằm ở javadoc của {@link InvalidResetTokenException}.
     * <p>
     * <b>Cố ý KHÔNG đặt khoá {@code errors}</b>: sự vắng mặt của nó là thứ phân biệt 422 này với
     * 422 của validate — cùng quy ước đã chốt ở {@link #handleInvalidCurrentPassword}. Và <b>cố ý
     * không log gì ngoài một câu cố định</b>: ba ca gộp làm một ở bề mặt dây thì log cũng phải giữ
     * nguyên tính chất đó, còn chuỗi token thì tuyệt đối không được ghi ra — DB chỉ giữ hash, nên
     * một dòng log sẽ là nơi duy nhất chuỗi thô còn tồn tại.
     *
     * @param e lỗi token đặt lại không hợp lệ
     * @return 422 kèm {@code detail} tiếng Việt, <b>không</b> có khoá {@code errors}
     */
    @ExceptionHandler(InvalidResetTokenException.class)
    public ProblemDetail handleInvalidResetToken(InvalidResetTokenException e) {
        log.warn("handleInvalidResetToken: reset token rejected");
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * Vượt ngưỡng tần suất — hiện chỉ {@code POST /auth/forgot-password} (backlog 0017 điều 8).
     * <p>
     * <b>429, không phải 403.</b> 403 đọc như một trạng thái vĩnh viễn mà người dùng không sửa
     * được; 429 nói đúng sự thật — quá nhanh, thử lại sau — và là mã duy nhất frontend dịch được
     * thành một câu hướng dẫn có ích.
     *
     * @param e lỗi vượt ngưỡng tần suất
     * @return 429 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException e) {
        log.warn("handleTooManyRequests: forgot-password rate limit rejected a call");
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    /**
     * @param e lỗi vi phạm quy tắc nghiệp vụ
     * @return 422 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(InvalidProductDataException.class)
    public ProblemDetail handleInvalidProductData(InvalidProductDataException e) {
        log.warn("handleInvalidProductData: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * @param e không tìm thấy mã giảm giá
     * @return 404 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(CouponNotFoundException.class)
    public ProblemDetail handleCouponNotFound(CouponNotFoundException e) {
        log.warn("handleCouponNotFound: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Mã giảm giá có thật nhưng không dùng được cho đơn này.
     * <p>
     * <b>422, không phải 400 và không phải 404.</b> Request đúng cú pháp và mã <i>có tồn tại</i>;
     * thứ sai là ngữ nghĩa nghiệp vụ. Trả 404 ở đây sẽ nói với người dùng rằng mã họ đang cầm không
     * có thật, còn 400 thì đọc như "client gửi sai" và làm frontend rơi về thông điệp dự phòng
     * chung chung thay vì lý do thật (§A.3).
     *
     * @param e mã giảm giá không dùng được
     * @return 422 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(CouponNotApplicableException.class)
    public ProblemDetail handleCouponNotApplicable(CouponNotApplicableException e) {
        log.warn("handleCouponNotApplicable: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * @param e không tìm thấy đơn hàng
     * @return 404 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException e) {
        log.warn("handleOrderNotFound: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * Đặt hàng với giỏ trống.
     * <p>
     * <b>400, không phải 422</b> — API_CONTRACT §B.6 khai đúng chữ "400 giỏ trống". Đây là ca duy
     * nhất trong dự án trả 400 cho một lỗi <i>nội dung</i> body: mọi lỗi theo từng trường đều là
     * 422 kèm map {@code errors}, còn giỏ trống thì không có ô nhập nào để chỉ vào.
     *
     * @param e giỏ hàng không có dòng nào
     * @return 400 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(EmptyOrderException.class)
    public ProblemDetail handleEmptyOrder(EmptyOrderException e) {
        log.warn("handleEmptyOrder: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Một dòng hàng không mua được lúc đặt đơn.
     * <p>
     * <b>409, không phải 422.</b> Request hợp lệ cả về cú pháp lẫn nghiệp vụ tại thời điểm khách
     * bấm nút; thứ đã đổi là trạng thái của tài nguyên. Đơn hàng tương ứng đã được rollback trọn
     * vẹn trước khi exception này tới đây.
     *
     * @param e dòng hàng hết hàng, hoặc sản phẩm đã bị gỡ khỏi cửa hàng
     * @return 409 kèm {@code detail} tiếng Việt có nêu tên món hàng
     */
    @ExceptionHandler(OutOfStockException.class)
    public ProblemDetail handleOutOfStock(OutOfStockException e) {
        log.warn("handleOutOfStock: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * @param e dữ liệu đơn hàng vi phạm quy tắc nghiệp vụ
     * @return 422 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(InvalidOrderDataException.class)
    public ProblemDetail handleInvalidOrderData(InvalidOrderDataException e) {
        log.warn("handleInvalidOrderData: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /**
     * Tham số {@code days} của {@code GET /admin/stats/overview} nằm ngoài dải hợp lệ.
     * <p>
     * <b>400, và không kèm khoá {@code errors}</b> — §B.12.4 khai đúng 400, và sự vắng mặt của
     * {@code errors} là thứ phân biệt nó với 422 của validate theo trường (cùng quy ước đã chốt ở
     * {@link #handleInvalidCurrentPassword}).
     *
     * @param e khoảng thời gian ngoài dải
     * @return 400 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException e) {
        log.warn("handleInvalidDateRange: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Lỗi của {@code jakarta.validation} trên {@code @Valid @RequestBody}.
     * <p>
     * <b>422, không phải 400.</b> Mặc định của Spring cho lỗi bind là 400; contract §A.3 và ticket
     * chốt 422 cho lỗi validate theo trường, nên handler này cố ý ghi đè hành vi mặc định.
     * {@code putIfAbsent} giữ lại lỗi <i>đầu tiên</i> của mỗi trường — một ô nhập chỉ hiển thị được
     * một dòng.
     *
     * @param e lỗi validate
     * @return 422 kèm {@code detail} tiếng Việt và map {@code errors}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("handleValidation: invalid request | fields={}", errors.keySet());
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, MESSAGE_VALIDATION_FAILED);
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    /**
     * @param e tham số không hợp lệ
     * @return 400 kèm {@code detail} tiếng Việt
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        log.warn("handleIllegalArgument: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, MESSAGE_BAD_REQUEST);
    }
}
