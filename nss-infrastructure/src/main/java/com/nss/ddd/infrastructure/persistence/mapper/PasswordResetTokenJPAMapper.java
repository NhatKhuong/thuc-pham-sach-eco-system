package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.PasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data interface của bảng {@code password_reset_token}.
 * <p>
 * <b>{@code JOIN FETCH prt.user} là bắt buộc, không phải tối ưu.</b> {@code open-in-view: false}
 * nên session đóng ngay khi repository trả về; đường {@code reset-password} cần chính chủ sở hữu để
 * ghi hash mật khẩu mới và thu hồi phiên. Để LAZY thì hoặc {@code LazyInitializationException},
 * hoặc thêm một truy vấn nữa — cùng lý do đã viết ở {@code RefreshTokenJPAMapper}.
 * <p>
 * Tham số đều là {@code :named} + {@code @Param}; {@code ?1} bị cấm (§12).
 */
public interface PasswordResetTokenJPAMapper extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Token đặt lại còn dùng được: chưa dùng và chưa hết hạn.
     * <p>
     * Cả hai điều kiện nằm trong truy vấn để không đường gọi nào bỏ sót được một trong hai.
     *
     * @param tokenHash SHA-256 hex của chuỗi thô — <b>không bao giờ là chuỗi thô</b>
     * @param now mốc so sánh hạn, giờ UTC
     * @return bản ghi kèm chủ sở hữu đã nạp, hoặc rỗng
     */
    @Query("SELECT prt FROM PasswordResetToken prt"
            + " JOIN FETCH prt.user"
            + " WHERE prt.tokenHash = :tokenHash AND prt.isUsed = false AND prt.expiresAt > :now")
    Optional<PasswordResetToken> findUsableByTokenHash(@Param("tokenHash") String tokenHash,
                                                       @Param("now") LocalDateTime now);

    /**
     * Tiêu token bằng UPDATE có điều kiện.
     * <p>
     * <b>Ba điều kiện, và cả ba đều load-bearing:</b> {@code tokenHash} chọn đúng dòng,
     * {@code isUsed = false} khiến lần gọi thứ hai trả 0 dòng, {@code expiresAt > :now} chặn một
     * dòng vừa hết hạn giữa lúc đọc và lúc ghi. Bỏ một trong hai điều kiện sau và kiểm chúng ở tầng
     * trên là biến phép "dùng một lần" thành một cuộc đua đọc-rồi-ghi mà cả hai request cùng thắng.
     *
     * @param tokenHash SHA-256 hex của chuỗi thô
     * @param now mốc so sánh hạn, giờ UTC
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken prt SET prt.isUsed = true"
            + " WHERE prt.tokenHash = :tokenHash AND prt.isUsed = false AND prt.expiresAt > :now")
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
