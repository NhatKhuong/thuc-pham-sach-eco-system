package com.nss.ddd.controller.exception;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Dựng lại 401 và 403 của Spring Security thành {@code ProblemDetail} tiếng Việt.
 * <p>
 * <b>Đây không phải chi tiết trang trí — thiếu nó là màn lỗi trắng ở frontend.</b> Hai mã trạng
 * thái này được filter chain phát ra <i>trước khi</i> request tới được {@code DispatcherServlet},
 * nên chúng <b>không đi qua {@code @RestControllerAdvice}</b>. Mặc định Spring Security trả 401 với
 * <i>thân rỗng</i> kèm header {@code WWW-Authenticate}; {@code apiError.ts} parse ra chuỗi rỗng và
 * người dùng thấy một trong 24 chỗ hiển thị lỗi trống trơn (ADR 0003 §Consequences).
 * <p>
 * <b>Một class hiện thực cả hai interface</b> vì hai đường chỉ khác nhau ở mã trạng thái và câu
 * chữ; tách đôi chỉ nhân bản đoạn ghi response, và đoạn đó mới là chỗ dễ sai (content-type, bảng
 * mã, thứ tự ghi status trước body).
 * <p>
 * Hình dạng trả về khớp {@link GlobalExceptionHandler}: {@code ProblemDetail} RFC 7807, mã HTTP
 * thật, {@code detail} tiếng Việt (ADR 0001, API_CONTRACT §A.3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityProblemDetailHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String MESSAGE_UNAUTHENTICATED =
            "Bạn cần đăng nhập để thực hiện thao tác này.";

    private static final String MESSAGE_FORBIDDEN =
            "Tài khoản của bạn không có quyền thực hiện thao tác này.";

    private final ObjectMapper objectMapper;

    /**
     * Không có token, token sai chữ ký, hoặc token đã hết hạn.
     *
     * @param request request đang bị từ chối
     * @param response response sẽ được ghi đè bằng ProblemDetail
     * @param authException nguyên nhân từ Spring Security
     * @throws IOException khi không ghi được response
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("commence: unauthenticated request | method={} path={}",
                request.getMethod(), request.getRequestURI());
        writeProblemDetail(request, response, HttpStatus.UNAUTHORIZED, MESSAGE_UNAUTHENTICATED);
    }

    /**
     * Đã xác thực nhưng không đủ quyền.
     *
     * @param request request đang bị từ chối
     * @param response response sẽ được ghi đè bằng ProblemDetail
     * @param accessDeniedException nguyên nhân từ Spring Security
     * @throws IOException khi không ghi được response
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("handle: access denied | method={} path={}",
                request.getMethod(), request.getRequestURI());
        writeProblemDetail(request, response, HttpStatus.FORBIDDEN, MESSAGE_FORBIDDEN);
    }

    /**
     * Ghi một {@code ProblemDetail} ra response.
     * <p>
     * {@code setCharacterEncoding} phải đặt <b>trước</b> khi lấy output stream: thông điệp là tiếng
     * Việt, và nếu client đoán bảng mã sai thì chuỗi hiển thị hỏng dấu — một lỗi chỉ nhìn thấy ở
     * màn hình người dùng chứ không nhìn thấy ở log.
     *
     * @param request request đang bị từ chối, dùng để điền {@code instance}
     * @param response response cần ghi
     * @param status mã trạng thái thật
     * @param detail thông điệp tiếng Việt cho người dùng cuối
     * @throws IOException khi không ghi được response
     */
    private void writeProblemDetail(HttpServletRequest request, HttpServletResponse response,
                                    HttpStatus status, String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
