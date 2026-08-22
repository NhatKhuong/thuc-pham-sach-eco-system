package com.nss.ddd.controller.exception;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lưới cuối cho ngoại lệ ngoài dự kiến (coding-conventions §11 Pattern B).
 * <p>
 * <b>Tách khỏi {@link GlobalExceptionHandler} vì thứ tự advice, không phải vì gọn.</b> Spring chọn
 * advice theo {@code @Order} rồi mới chọn method theo kiểu exception, nên một
 * {@code @ExceptionHandler(Exception.class)} nằm trong advice xếp trước sẽ nuốt <i>mọi thứ</i> —
 * kể cả 405, 415, và 404-không-có-handler mà {@code ProblemDetailsExceptionHandler} của Spring
 * vốn đã trả đúng — rồi biến chúng thành 500.
 * <p>
 * Xếp {@code LOWEST_PRECEDENCE} nên nó chỉ nhận những gì không advice nào khác nhận.
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class UnexpectedExceptionHandler {

    private static final String MESSAGE_SERVER_ERROR = "Hệ thống đang gặp sự cố, vui lòng thử lại sau.";

    /**
     * Exception là <b>tham số cuối và không có placeholder tương ứng</b> (§9) — nhờ vậy stack trace
     * vào log chứ không vào response.
     *
     * @param e lỗi ngoài dự kiến
     * @return 500 kèm {@code detail} tiếng Việt chung
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("handleUnexpected: unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, MESSAGE_SERVER_ERROR);
    }
}
