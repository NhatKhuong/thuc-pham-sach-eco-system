package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.EmailConfirmationToken;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PORT của {@code EmailConfirmationToken} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> không import {@code org.springframework.data.*}.
 * <p>
 * <b>Mọi tham số nhận vào ở đây là HASH, không bao giờ là chuỗi thô</b> — đúng khuôn
 * {@code PasswordResetTokenRepository}. Việc băm nằm ở {@code AuthDomainServiceImpl}, phía trên port
 * này, nên adapter không bao giờ nhìn thấy bí mật.
 */
public interface EmailConfirmationTokenRepository {

    /**
     * Ghi một dòng token xác nhận mới.
     *
     * @param emailConfirmationToken bản ghi cần ghi, cột {@code tokenHash} đã băm sẵn
     * @return bản ghi sau khi ghi, đã có id
     */
    EmailConfirmationToken save(EmailConfirmationToken emailConfirmationToken);

    /**
     * Tra một token xác nhận <b>còn dùng được</b>: chưa dùng và chưa hết hạn.
     * <p>
     * Hai điều kiện nằm trong <i>câu truy vấn</i> chứ không kiểm ở tầng trên — đúng nếp
     * {@code PasswordResetTokenRepository.findUsableByTokenHash}. Chủ token được nạp <b>tường
     * minh</b> cùng câu truy vấn: {@code open-in-view: false} nên quan hệ LAZY không đọc được sau
     * khi session đóng, mà đường xác nhận email <i>bắt buộc</i> cần chính chủ sở hữu để đặt
     * {@code emailVerified = true}.
     *
     * @param tokenHash SHA-256 hex của chuỗi client gửi lên
     * @param now mốc so sánh hạn, <b>giờ UTC</b>
     * @return bản ghi kèm chủ sở hữu, hoặc rỗng khi không tồn tại / đã dùng / đã hết hạn —
     *         <b>ba ca gộp làm một</b>, vì client không được biết ca nào
     */
    Optional<EmailConfirmationToken> findUsableByTokenHash(String tokenHash, LocalDateTime now);

    /**
     * Tiêu token: đặt {@code is_used = true} bằng UPDATE có điều kiện.
     * <p>
     * Điều kiện {@code is_used = false AND expires_at > :now} khiến lần gọi thứ hai trả 0 dòng, nên
     * hai request đồng thời cầm cùng một chuỗi thì đúng một cái thắng — đúng cơ chế
     * {@code PasswordResetTokenRepository.markUsed}.
     *
     * @param tokenHash SHA-256 hex của chuỗi client gửi lên
     * @param now mốc so sánh hạn, <b>giờ UTC</b>
     * @return true nếu có đúng một dòng vừa chuyển sang trạng thái đã dùng
     */
    boolean markUsed(String tokenHash, LocalDateTime now);
}
