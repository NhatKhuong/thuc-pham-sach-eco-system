package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data interface của bảng {@code refresh_token}.
 * <p>
 * <b>{@code JOIN FETCH rt.user} là bắt buộc, không phải tối ưu.</b> {@code open-in-view: false} nên
 * session đóng ngay khi repository trả về; đường {@code refresh} cần chính chủ sở hữu để đúc cặp
 * token mới. Để LAZY thì hoặc {@code LazyInitializationException}, hoặc thêm một truy vấn nữa —
 * ticket 0010 điểm 5 nói rõ phải nạp tường minh.
 * <p>
 * Tham số đều là {@code :named} + {@code @Param}; {@code ?1} bị cấm (§12).
 */
public interface RefreshTokenJPAMapper extends JpaRepository<RefreshToken, Long> {

    /**
     * Refresh token còn dùng được: chưa thu hồi và chưa hết hạn.
     * <p>
     * Cả hai điều kiện nằm trong truy vấn để không đường gọi nào bỏ sót được một trong hai.
     *
     * @param token chuỗi token
     * @param now mốc so sánh hạn, giờ UTC
     * @return bản ghi kèm chủ sở hữu đã nạp, hoặc rỗng
     */
    @Query("SELECT rt FROM RefreshToken rt"
            + " JOIN FETCH rt.user"
            + " WHERE rt.token = :token AND rt.isRevoked = false AND rt.expiresAt > :now")
    Optional<RefreshToken> findUsableByToken(@Param("token") String token, @Param("now") LocalDateTime now);

    /**
     * Thu hồi bằng UPDATE có điều kiện — {@code AND rt.isRevoked = false} khiến lần gọi thứ hai trả
     * 0 dòng, nhờ đó một refresh token đã dùng không xoay vòng được lần nữa.
     *
     * @param token chuỗi token
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true"
            + " WHERE rt.token = :token AND rt.isRevoked = false")
    int markRevoked(@Param("token") String token);

    /**
     * Thu hồi token <b>của đúng một người dùng</b> — đường của {@code logout}, {@code userId} lấy
     * từ JWT chứ không từ body (§C.2).
     *
     * @param token chuỗi token
     * @param userId chủ sở hữu
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true"
            + " WHERE rt.token = :token AND rt.user.id = :userId AND rt.isRevoked = false")
    int markRevokedForUser(@Param("token") String token, @Param("userId") Long userId);

    /**
     * Thu hồi mọi dòng còn sống của một người dùng <b>trừ một dòng</b> — đường của
     * {@code PUT /auth/password}.
     * <p>
     * {@code AND rt.isRevoked = false} giữ đúng nếp của hai câu trên: không đếm lại những dòng đã
     * thu hồi từ trước, nên con số trả về là số phiên <i>thật sự</i> vừa bị đá ra.
     * <p>
     * <b>{@code keepId} phải là một giá trị thật, không bao giờ {@code null}.</b> {@code rt.id <>
     * :keepId} với {@code keepId = NULL} cho ra UNKNOWN trên <i>mọi</i> dòng, tức câu lệnh thu hồi
     * 0 dòng và im lặng. Việc chuẩn hoá nằm ở {@code RefreshTokenRepositoryImpl}; đừng gọi thẳng
     * method này với một giá trị có thể rỗng.
     *
     * @param userId chủ sở hữu
     * @param keepId id dòng được giữ lại — phiên đang gọi
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true"
            + " WHERE rt.user.id = :userId AND rt.isRevoked = false AND rt.id <> :keepId")
    int markRevokedForUserExcept(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
