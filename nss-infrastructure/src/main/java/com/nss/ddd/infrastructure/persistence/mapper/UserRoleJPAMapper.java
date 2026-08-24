package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của bảng nối {@code user_role}.
 * <p>
 * <b>{@link #findRoleByCode(String)} truy vấn entity {@code Role} từ interface này</b> thay vì mở
 * thêm một {@code RoleJPAMapper}: JPQL không bị giới hạn ở entity gốc của repository, và aggregate
 * {@code Role} chưa có ticket nào sở hữu. Cùng lý do {@code CategoryJPAMapper} chỉ có phần tối
 * thiểu để phân giải quan hệ.
 * <p>
 * {@link #findRoleCodesByUserId(Long)} trả thẳng {@code List<String>} — đây là truy vấn tường minh
 * thay cho việc đi qua quan hệ LAZY, thứ mà {@code open-in-view: false} không cho phép.
 */
public interface UserRoleJPAMapper extends JpaRepository<UserRole, Long> {

    /**
     * @param userId khóa chính của người dùng
     * @return mã vai trò UPPER_SNAKE, sắp xếp ổn định để claim {@code roles} không đổi thứ tự giữa
     *         hai lần đăng nhập
     */
    @Query("SELECT ur.role.code FROM UserRole ur"
            + " WHERE ur.user.id = :userId"
            + " ORDER BY ur.role.code ASC")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * @param code mã vai trò UPPER_SNAKE
     * @return vai trò, hoặc rỗng
     */
    @Query("SELECT r FROM Role r WHERE r.code = :code")
    Optional<Role> findRoleByCode(@Param("code") String code);
}
