package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PORT của {@code PasswordResetToken} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> không import {@code org.springframework.data.*}.
 * <p>
 * <b>Mọi tham số nhận vào ở đây là HASH, không bao giờ là chuỗi thô.</b> Việc băm nằm ở
 * {@code AuthDomainServiceImpl}, phía trên port này, nên adapter không bao giờ nhìn thấy bí mật —
 * và một câu truy vấn viết nhầm cũng không thể vô tình so sánh chuỗi thô với cột đã băm. Tên tham
 * số viết thẳng là {@code tokenHash} để chỗ gọi sai đọc ra ngay.
 */
public interface PasswordResetTokenRepository {

    /**
     * Ghi một dòng token đặt lại mới.
     *
     * @param passwordResetToken bản ghi cần ghi, cột {@code tokenHash} đã băm sẵn
     * @return bản ghi sau khi ghi, đã có id
     */
    PasswordResetToken save(PasswordResetToken passwordResetToken);

    /**
     * Tra một token đặt lại <b>còn dùng được</b>: chưa dùng và chưa hết hạn.
     * <p>
     * Hai điều kiện nằm trong <i>câu truy vấn</i> chứ không kiểm ở tầng trên, đúng nếp
     * {@code RefreshTokenRepository.findUsableByToken}: nhờ đó "hết hạn" và "đã dùng" không thể bị
     * bỏ sót ở một đường gọi nào đó.
     * <p>
     * Chủ token được nạp <b>tường minh</b> cùng câu truy vấn: {@code open-in-view: false} nên quan
     * hệ LAZY không đọc được sau khi session đóng, mà đường đặt lại mật khẩu <i>bắt buộc</i> cần
     * chính chủ sở hữu để ghi hash mới và thu hồi phiên.
     *
     * @param tokenHash SHA-256 hex của chuỗi client gửi lên
     * @param now mốc so sánh hạn, <b>giờ UTC</b>
     * @return bản ghi kèm chủ sở hữu, hoặc rỗng khi không tồn tại / đã dùng / đã hết hạn —
     *         <b>ba ca gộp làm một</b>, vì client không được biết ca nào
     */
    Optional<PasswordResetToken> findUsableByTokenHash(String tokenHash, LocalDateTime now);

    /**
     * Tiêu token: đặt {@code is_used = true} bằng UPDATE có điều kiện.
     * <p>
     * <b>Đây là chỗ "dùng đúng một lần" được cưỡng chế, không phải ở một câu {@code if} nào phía
     * trên.</b> Điều kiện {@code is_used = false AND expires_at > :now} khiến lần gọi thứ hai trả 0
     * dòng, nên hai request đồng thời cầm cùng một chuỗi thì đúng một cái thắng — cùng cơ chế
     * {@code RefreshTokenRepository.revokeByToken} đang dùng cho xoay vòng refresh token.
     * <p>
     * Kiểm "còn dùng được" rồi mới UPDATE mà <i>không</i> lặp lại điều kiện trong chính câu UPDATE
     * là một cuộc đua đọc-rồi-ghi: hai request cùng đọc thấy dòng còn sống, cả hai cùng ghi, và một
     * token đổi được <b>hai</b> mật khẩu.
     *
     * @param tokenHash SHA-256 hex của chuỗi client gửi lên
     * @param now mốc so sánh hạn, <b>giờ UTC</b>
     * @return true nếu có đúng một dòng vừa chuyển sang trạng thái đã dùng
     */
    boolean markUsed(String tokenHash, LocalDateTime now);
}
