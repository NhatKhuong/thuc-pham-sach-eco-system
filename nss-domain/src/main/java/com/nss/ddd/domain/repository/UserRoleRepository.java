package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * PORT của bảng nối {@code user_role} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> không import {@code org.springframework.data.*}.
 * <p>
 * <b>{@link #findRoleByCode(String)} nằm ở đây thay vì trong một {@code RoleRepository} riêng</b>,
 * cùng lý do {@code CategoryRepository} chỉ có đúng một method: aggregate {@code Role} là việc của
 * ticket quản trị vai trò, còn ở đây chỉ cần đủ để {@code register} phân giải mã {@code CUSTOMER}
 * thành entity trước khi tạo dòng nối. Mở một port mới cho một method sẽ phải gỡ về sau.
 */
public interface UserRoleRepository {

    /**
     * Mã vai trò của một người dùng — nguồn của claim {@code roles} trong access token.
     * <p>
     * Trả thẳng {@code List<String>} chứ không trả entity: đây là truy vấn tường minh thay cho việc
     * đi qua quan hệ LAZY, mà {@code open-in-view: false} không cho phép (ADR 0003).
     *
     * @param userId khóa chính của người dùng
     * @return danh sách mã vai trò UPPER_SNAKE; rỗng khi người dùng chưa được gán vai trò nào
     */
    List<String> findRoleCodesByUserId(Long userId);

    /**
     * @param code mã vai trò UPPER_SNAKE, ví dụ {@code CUSTOMER}
     * @return vai trò, hoặc rỗng khi mã không tồn tại trong bảng {@code role}
     */
    Optional<Role> findRoleByCode(String code);

    /**
     * Gán một vai trò cho một người dùng.
     *
     * @param userRole dòng nối cần ghi
     * @return bản ghi sau khi ghi, đã có id
     */
    UserRole save(UserRole userRole);
}
