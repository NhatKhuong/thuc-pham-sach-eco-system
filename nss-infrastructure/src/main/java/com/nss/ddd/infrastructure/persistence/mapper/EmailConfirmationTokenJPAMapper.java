package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.EmailConfirmationToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data interface của bảng {@code email_confirmation_token}.
 * <p>
 * <b>{@code JOIN FETCH ect.user} là bắt buộc, không phải tối ưu</b> — cùng lý do đã viết ở
 * {@code PasswordResetTokenJPAMapper}: {@code open-in-view: false} nên session đóng ngay khi
 * repository trả về, và đường xác nhận email cần chính chủ sở hữu để đặt {@code emailVerified = true}.
 * <p>
 * Tham số đều là {@code :named} + {@code @Param}; {@code ?1} bị cấm (§12).
 */
public interface EmailConfirmationTokenJPAMapper extends JpaRepository<EmailConfirmationToken, Long> {

    /**
     * Token xác nhận còn dùng được: chưa dùng và chưa hết hạn.
     *
     * @param tokenHash SHA-256 hex của chuỗi thô — <b>không bao giờ là chuỗi thô</b>
     * @param now mốc so sánh hạn, giờ UTC
     * @return bản ghi kèm chủ sở hữu đã nạp, hoặc rỗng
     */
    @Query("SELECT ect FROM EmailConfirmationToken ect"
            + " JOIN FETCH ect.user"
            + " WHERE ect.tokenHash = :tokenHash AND ect.isUsed = false AND ect.expiresAt > :now")
    Optional<EmailConfirmationToken> findUsableByTokenHash(@Param("tokenHash") String tokenHash,
                                                           @Param("now") LocalDateTime now);

    /**
     * Tiêu token bằng UPDATE có điều kiện.
     *
     * @param tokenHash SHA-256 hex của chuỗi thô
     * @param now mốc so sánh hạn, giờ UTC
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmailConfirmationToken ect SET ect.isUsed = true"
            + " WHERE ect.tokenHash = :tokenHash AND ect.isUsed = false AND ect.expiresAt > :now")
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
