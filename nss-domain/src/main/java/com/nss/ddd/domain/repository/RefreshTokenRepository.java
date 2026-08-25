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

    /**
     * Thu hồi <b>mọi</b> refresh token còn sống của một người dùng, <b>trừ đúng một dòng</b> —
     * đường của {@code PUT /auth/password}: đổi mật khẩu phải đá các thiết bị khác ra mà không đá
     * chính phiên đang gọi.
     * <p>
     * <b>Trả {@code int} chứ không phải {@code boolean}, và đó là khác biệt về ngữ nghĩa chứ không
     * phải về kiểu.</b> Ở hai phép thu hồi phía trên, {@code 0 dòng} nghĩa là <i>thua cuộc đua</i>
     * — dòng đã bị thu hồi trước đó, và tầng trên phải phản ứng. Ở đây {@code 0 dòng} là một
     * <b>thành công hợp lệ</b>: người dùng chỉ đăng nhập trên đúng một thiết bị nên không có phiên
     * nào khác để đá. Ép nó về {@code boolean} sẽ biến ca thường gặp nhất thành ca trông như lỗi.
     * Con số trả về dùng để ghi log, không để quyết định mã HTTP.
     * <p>
     * <b>{@code keepId} không được là {@code null} khi câu lệnh chạy.</b> {@code id <> NULL} trong
     * SQL cho ra UNKNOWN, tức <i>không dòng nào</i> khớp — mật khẩu đổi xong mà không phiên nào
     * chết, và không có gì báo lỗi. Việc chuẩn hoá {@code null} thành một giá trị canh gác không
     * khớp id nào là trách nhiệm của adapter; xem {@code RefreshTokenRepositoryImpl}.
     *
     * @param userId chủ sở hữu, lấy từ claim {@code sub} của JWT (§C.2)
     * @param keepId id dòng {@code refresh_token} của phiên đang gọi — lấy từ claim {@code sid};
     *               {@code null} nghĩa là token được cấp trước khi claim này ra đời, và ca đó phải
     *               hỏng về <b>phía an toàn</b>: thu hồi tất cả, kể cả phiên hiện tại
     * @return số dòng vừa chuyển sang trạng thái đã thu hồi; {@code 0} là kết quả hợp lệ
     */
    int revokeAllOfUserExcept(Long userId, Long keepId);
}
