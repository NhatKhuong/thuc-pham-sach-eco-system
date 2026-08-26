package com.nss.ddd.controller.config;

import java.util.Locale;

/**
 * Ba nhóm trần thông lượng của biên vào {@code /api/**} (backlog 0021, ADR 0005).
 * <p>
 * <b>Đây là trần theo NHÓM ENDPOINT, không phải theo người gọi.</b> {@code RateLimiter} của
 * Resilience4j là một bể permit gắn với <i>một tên instance</i>, không phải một map theo IP — nên
 * lớp này chặn <b>quá tải</b> (tổng lưu lượng vượt sức tiến trình), không chặn <b>lạm dụng</b>
 * (một người gọi dò tài khoản). Lớp chống lạm dụng theo hai khoá IP + email vẫn nằm ở
 * {@link ForgotPasswordRateLimiter} và <i>không</i> bị thay thế: hai lớp giải hai bài toán khác
 * nhau, và cả hai đều ở lại.
 * <p>
 * <b>Một đường dẫn thuộc đúng MỘT tier.</b> Khi hai luật cùng khớp thì {@link #AUTH} thắng — xem
 * {@link #resolve(String, String)}.
 */
public enum ApiRateLimitTier {

    /** {@code /api/auth/**}, mọi verb — nhóm chặt nhất: đây là chỗ đăng nhập và đổi mật khẩu. */
    AUTH("auth"),

    /** {@code POST} / {@code PUT} / {@code PATCH} / {@code DELETE} dưới {@code /api/**}, ngoài auth. */
    WRITE("write"),

    /** Phần còn lại dưới {@code /api/**} — chủ yếu là đường đọc. */
    READ("read");

    /** Tiền tố của nhóm auth. Mọi mapping auth hiện có đều nằm dưới đường dẫn này. */
    private static final String PATH_PREFIX_AUTH = "/api/auth/";

    /** Đường dẫn auth không có phần đuôi — không có mapping nào, nhưng vẫn phải rơi vào đúng tier. */
    private static final String PATH_AUTH = "/api/auth";

    private final String instanceName;

    ApiRateLimitTier(String instanceName) {
        this.instanceName = instanceName;
    }

    /**
     * Chọn tier cho một request.
     * <p>
     * <b>Thứ tự kiểm là load-bearing:</b> {@code POST /api/auth/login} khớp cả luật auth lẫn luật
     * write, và luật auth phải thắng. Đảo hai nhánh này thì mọi endpoint auth ghi âm thầm chạy dưới
     * trần của {@code write} — cao gấp ba lần — mà không có test nào đỏ.
     * <p>
     * Verb không nhận ra (kể cả {@code OPTIONS} của CORS preflight) rơi vào {@link #READ}, tier
     * rộng nhất: một trần <i>chặt</i> đặt nhầm chỗ là một sự cố từ chối dịch vụ do chính mình gây ra.
     *
     * @param path   đường dẫn đã bỏ context path, luôn bắt đầu bằng {@code /api} vì interceptor chỉ
     *               được đăng ký cho {@code /api/**}
     * @param method verb HTTP, không phân biệt hoa thường
     * @return tier duy nhất áp cho request này
     */
    public static ApiRateLimitTier resolve(String path, String method) {
        if (path != null && (path.startsWith(PATH_PREFIX_AUTH) || path.equals(PATH_AUTH))) {
            return AUTH;
        }
        return isWriteMethod(method) ? WRITE : READ;
    }

    /**
     * @param method verb HTTP
     * @return true nếu verb này thay đổi trạng thái phía server
     */
    private static boolean isWriteMethod(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    /**
     * @return tên instance của {@code RateLimiter}, cũng là tên dùng trong log và trong khoá cấu hình
     */
    public String getInstanceName() {
        return instanceName;
    }
}
