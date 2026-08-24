package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PORT của {@code RefreshToken} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> không import {@code org.springframework.data.*}.
 * <p>
 * Bảng này tồn tại vì §B.4 #3: {@code logout} phải <b>thu hồi</b> được refresh token. Một JWT thuần
 * stateless không thu hồi được, nên trạng thái phải nằm ở phía server (ADR 0003).
 */
public interface RefreshTokenRepository {

    /**
     * Tra một refresh token <b>còn dùng được</b>: chưa bị thu hồi và chưa hết hạn.
     * <p>
     * Hai điều kiện nằm trong <i>câu truy vấn</i> chứ không kiểm ở tầng trên, để "hết hạn" và
     * "bị thu hồi" không thể vô tình bị bỏ sót ở một đường gọi nào đó.
     * <p>
     * Chủ token được nạp <b>tường minh</b> cùng câu truy vấn: {@code open-in-view: false} nên quan
     * hệ LAZY không đọc được sau khi session đóng.
     *
     * @param token chuỗi refresh token client gửi lên
     * @param now mốc so sánh hạn, <b>giờ UTC</b>
     * @return bản ghi kèm chủ sở hữu, hoặc rỗng khi không tồn tại / đã thu hồi / đã hết hạn
     */
    Optional<RefreshToken> findUsableByToken(String token, LocalDateTime now);

    /**
     * Ghi refresh token mới.
     *
     * @param refreshToken bản ghi cần ghi
     * @return bản ghi sau khi ghi, đã có id
     */
    RefreshToken save(RefreshToken refreshToken);

    /**
     * Thu hồi: đặt {@code is_revoked = true} bằng UPDATE có điều kiện.
     * <p>
     * Điều kiện {@code is_revoked = false} khiến lần gọi thứ hai trả 0 dòng — nhờ đó tầng trên phân
     * biệt được "vừa thu hồi xong" với "không có gì để thu hồi", và một refresh token đã dùng không
     * xoay vòng được lần thứ hai.
     *
     * @param token chuỗi refresh token
     * @return true nếu có đúng một dòng chuyển sang trạng thái đã thu hồi
     */
    boolean revokeByToken(String token);

    /**
     * Thu hồi refresh token <b>của đúng người dùng đang đăng nhập</b> — đường của {@code logout}.
     * <p>
     * {@code userId} lấy từ JWT chứ không từ body (§C.2). Không có điều kiện này thì một người bất
     * kỳ có thể đăng xuất phiên của người khác chỉ bằng cách đoán chuỗi token.
     *
     * @param token chuỗi refresh token
     * @param userId chủ sở hữu, lấy từ JWT
     * @return true nếu có đúng một dòng chuyển sang trạng thái đã thu hồi
     */
    boolean revokeByTokenAndUserId(String token, Long userId);
}
