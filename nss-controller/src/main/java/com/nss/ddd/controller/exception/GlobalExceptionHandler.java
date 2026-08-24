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
