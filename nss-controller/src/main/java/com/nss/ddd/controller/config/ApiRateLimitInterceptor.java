package com.nss.ddd.controller.config;

import com.nss.ddd.controller.exception.TooManyRequestsException;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Trần thông lượng của biên vào {@code /api/**} — ba tier {@code auth} / {@code write} / {@code read}
 * (backlog 0021 Phase 1, ADR 0005). Đăng ký ở {@link WebMvcConfig}.
 *
 * <h2>Vì sao là HandlerInterceptor chứ KHÔNG phải Filter</h2>
 * <b>Đây là ràng buộc về hình dạng lỗi, không phải sở thích.</b> Một {@code Filter} từ chối request
 * thì việc từ chối đó xảy ra <i>trước</i> {@code DispatcherServlet}, nên nó không bao giờ đi qua
 * {@code @RestControllerAdvice} và buộc phải tự serialize JSON — ra một thân lỗi <b>khác hình
 * dạng</b> với mọi lỗi khác của hệ thống, đúng loại "body parse được nhưng sai" mà frontend không
 * phát hiện ra. {@code preHandle} chạy <i>bên trong</i> khối try của {@code doDispatch}, nên
 * exception ném từ đây đi qua đúng chuỗi {@code HandlerExceptionResolver} và ra
 * {@link com.nss.ddd.controller.exception.GlobalExceptionHandler} như mọi lỗi khác.
 *
 * <h2>{@code timeoutDuration} PHẢI là 0, và đó là toàn bộ giá trị của lớp này</h2>
 * Mặc định của Resilience4j là <b>5 giây</b>, nghĩa là hết permit thì caller bị <i>park chờ</i> chứ
 * không bị từ chối: không ai nhận 429, mọi người cùng chậm — một lớp throttle biến thành bộ khuếch
 * đại độ trễ. Hỏng kiểu đó <b>không làm test nào đỏ</b> vì mọi request vẫn trả 200. Vì vậy
 * {@link #TIMEOUT_DURATION} được <b>khai cứng bằng {@code Duration.ZERO}</b> và cố ý <b>không</b>
 * nằm sau biến môi trường: một con số vận hành chỉnh được là một con số chỉnh nhầm được, và ca
 * chỉnh nhầm ở đây không có triệu chứng nào nhìn thấy.
 *
 * <h2>Giới hạn đã biết</h2>
 * Bể permit sống <b>trong bộ nhớ của một tiến trình</b>. Chạy N instance thì trần thật gấp N lần —
 * cùng một giới hạn mà {@link ForgotPasswordRateLimiter} đã ghi cho bộ đếm của nó. Khi triển khai
 * nhiều instance, câu trả lời là một bể dùng chung (Redis), không phải chỉnh con số ở
 * {@code application.yml}.
 */
@Slf4j
@Component
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    /**
     * Thông điệp 429 của lớp global — người dùng cuối đọc (§A.3).
     * <p>
     * <b>Cố ý không nêu ngưỡng</b>, cùng lý do với {@code AuthController}: con số đó chỉ giúp người
     * muốn lách nó canh cho vừa đủ.
     */
    private static final String MESSAGE_TOO_MANY_REQUESTS =
            "Hệ thống đang nhận quá nhiều yêu cầu, vui lòng thử lại sau giây lát.";

    /**
     * <b>Từ chối ngay, không xếp hàng.</b> Xem javadoc cấp class — đây là dòng làm lớp này có tác
     * dụng thật thay vì thành một bộ khuếch đại độ trễ.
     */
    private static final Duration TIMEOUT_DURATION = Duration.ZERO;

    private final Map<ApiRateLimitTier, RateLimiter> limiters = new EnumMap<>(ApiRateLimitTier.class);

    /**
     * @param authLimitForPeriod      số permit mỗi chu kỳ cho {@code /api/auth/**}
     * @param authRefreshPeriod       độ dài chu kỳ của tier auth, dạng ISO-8601 ({@code PT1S})
     * @param writeLimitForPeriod     số permit mỗi chu kỳ cho các verb ghi
     * @param writeRefreshPeriod      độ dài chu kỳ của tier write
     * @param readLimitForPeriod      số permit mỗi chu kỳ cho phần còn lại
     * @param readRefreshPeriod       độ dài chu kỳ của tier read
     * @throws IllegalStateException khi cấu hình không dùng được — fail lúc khởi động, đúng tiền lệ
     *                               {@code JwtConfig} và {@link ForgotPasswordRateLimiter}
     */
    public ApiRateLimitInterceptor(
            @Value("${nss.rate-limit.auth.limit-for-period}") int authLimitForPeriod,
            @Value("${nss.rate-limit.auth.limit-refresh-period}") Duration authRefreshPeriod,
            @Value("${nss.rate-limit.write.limit-for-period}") int writeLimitForPeriod,
            @Value("${nss.rate-limit.write.limit-refresh-period}") Duration writeRefreshPeriod,
            @Value("${nss.rate-limit.read.limit-for-period}") int readLimitForPeriod,
            @Value("${nss.rate-limit.read.limit-refresh-period}") Duration readRefreshPeriod) {
        limiters.put(ApiRateLimitTier.AUTH,
                genLimiter(ApiRateLimitTier.AUTH, authLimitForPeriod, authRefreshPeriod));
        limiters.put(ApiRateLimitTier.WRITE,
                genLimiter(ApiRateLimitTier.WRITE, writeLimitForPeriod, writeRefreshPeriod));
        limiters.put(ApiRateLimitTier.READ,
                genLimiter(ApiRateLimitTier.READ, readLimitForPeriod, readRefreshPeriod));
        // Moi tier phai co mot limiter. Mot tier thieu limiter se khien resolve() tra ve mot khoa
        // khong co trong map va request di qua khong gioi han — dung kieu hong IM LANG ma ca lop nay
        // sinh ra de chan.
        for (ApiRateLimitTier tier : ApiRateLimitTier.values()) {
            if (!limiters.containsKey(tier)) {
                throw new IllegalStateException("no rate limiter registered for tier: " + tier);
            }
        }
        log.info("ApiRateLimitInterceptor: wired | auth={}/{} write={}/{} read={}/{} timeout={}",
                authLimitForPeriod, authRefreshPeriod, writeLimitForPeriod, writeRefreshPeriod,
                readLimitForPeriod, readRefreshPeriod, TIMEOUT_DURATION);
    }

    /**
     * Cổng trần thông lượng, chạy trước mọi handler dưới {@code /api/**}.
     *
     * @param request  request đang tới
     * @param response response, không bị chạm tới ở đây — thân lỗi do advice dựng
     * @param handler  handler đã khớp
     * @return true khi còn permit
     * @throws TooManyRequestsException khi hết permit; advice dịch thành 429 kèm
     *                                  {@code application/problem+json}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = genLookupPath(request);
        ApiRateLimitTier tier = ApiRateLimitTier.resolve(path, request.getMethod());
        if (limiters.get(tier).acquirePermission()) {
            return true;
        }
        // Dong log nay PHAI phan biet duoc voi dong cua ForgotPasswordRateLimiter: hai lop cung tra
        // 429 nhung chan hai thu khac nhau (qua tai vs lam dung), va khong phan biet duoc trong log
        // thi lan sau khong ai biet cai nao dang can.
        log.warn("preHandle: api rate limit rejected | tier={} method={} path={}",
                tier.getInstanceName(), request.getMethod(), path);
        throw new TooManyRequestsException(MESSAGE_TOO_MANY_REQUESTS);
    }

    /**
     * @param request request đang tới
     * @return đường dẫn đã bỏ context path, dạng {@code /api/...}
     */
    private String genLookupPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * @param tier           nhóm endpoint
     * @param limitForPeriod số permit mỗi chu kỳ
     * @param refreshPeriod  độ dài chu kỳ
     * @return limiter đã cấu hình, từ chối ngay khi hết permit
     * @throws IllegalStateException khi ngưỡng hoặc chu kỳ không dùng được
     */
    private RateLimiter genLimiter(ApiRateLimitTier tier, int limitForPeriod, Duration refreshPeriod) {
        // Nguong <= 0 se chan MOI request ke ca request dau tien — endpoint chet hoan toan nhung van
        // tra dung hinh dang loi, nen khong ai nhin ra. Chu ky <= 0 thi Resilience4j nem
        // IllegalArgumentException voi mot cau khong nhac ten khoa cau hinh nao. Ca hai deu la hong
        // IM LANG hoac kho lan nguoc, nen fail o day voi ten khoa day du (tien le
        // ForgotPasswordRateLimiter).
        if (limitForPeriod <= 0) {
            throw new IllegalStateException("nss.rate-limit." + tier.getInstanceName()
                    + ".limit-for-period must be positive; got: " + limitForPeriod);
        }
        if (refreshPeriod == null || refreshPeriod.isZero() || refreshPeriod.isNegative()) {
            throw new IllegalStateException("nss.rate-limit." + tier.getInstanceName()
                    + ".limit-refresh-period must be a positive ISO-8601 duration; got: " + refreshPeriod);
        }
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(refreshPeriod)
                .timeoutDuration(TIMEOUT_DURATION)
                .build();
        return RateLimiter.of(tier.getInstanceName(), config);
    }
}
