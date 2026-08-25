package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * Vai trò của <b>nhiều</b> tài khoản trong một lượt — chống N+1 cho
     * {@code GET /admin/customers} (§B.12.3).
     * <p>
     * <b>Trả về dòng nối {@code (userId, roleCode)} chứ không trả entity {@code UserRole}</b>: phía
     * gọi chỉ cần hai giá trị vô hướng đó, và một entity kéo theo hai quan hệ LAZY mà
     * {@code open-in-view: false} không cho đọc ngoài session. Cùng lý do khiến
     * {@link #findRoleCodesByUserId(Long)} trả thẳng {@code List<String>}.
     * <p>
     * <b>Trả {@code List<UserRoleCode>} chứ không {@code Object[]}</b> — coding-conventions §7 chỉ
     * cho phép map theo vị trí ở native query; đây là JPQL nên nó dựng thẳng kiểu kết quả bằng
     * constructor expression.
     * <p>
     * <b>Phía gọi phải chặn danh sách rỗng trước khi tới đây.</b> {@code IN :userIds} với một
     * collection rỗng dịch ra {@code in ()}, và MySQL từ chối cú pháp đó — cùng cái bẫy đã ghi ở
     * {@code OrderItemJPAMapper.findByOrderIdIn}. Chỗ chặn nằm ở {@code UserRoleRepositoryImpl}.
     * <p>
     * {@code ORDER BY} để danh sách vai trò của mỗi tài khoản ổn định giữa hai lần gọi — cùng kỷ
     * luật với {@link #findRoleCodesByUserId(Long)}.
     *
     * @param userIds khóa chính của các tài khoản, <b>không rỗng</b>
     * @return các cặp {@code (userId, roleCode)}; tài khoản chưa có vai trò nào thì không có dòng
     */
    @Query("SELECT new com.nss.ddd.infrastructure.persistence.mapper.UserRoleCode(ur.user.id, ur.role.code)"
            + " FROM UserRole ur"
            + " WHERE ur.user.id IN :userIds"
            + " ORDER BY ur.user.id ASC, ur.role.code ASC")
    List<UserRoleCode> findRoleCodesByUserIds(@Param("userIds") Collection<Long> userIds);
}
