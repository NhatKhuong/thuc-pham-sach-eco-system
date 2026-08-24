package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data interface của bảng {@code user} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * Hai method dưới đây suy được từ tên nên dùng <b>derived query</b> (coding-conventions §12): không
 * có gì để viết bằng JPQL mà rõ hơn.
 * <p>
 * Cố ý <b>không</b> có đường đọc nào kèm {@code JOIN FETCH} vai trò: claim {@code roles} lấy bằng
 * truy vấn riêng ở {@code UserRoleJPAMapper}, nên {@code User} đọc lên không mang theo
 * {@code Role} — đúng ràng buộc "vai trò không rò ra response" trong javadoc của {@code User}.
 */
public interface UserJPAMapper extends JpaRepository<User, Long> {

    /**
     * @param email email đăng nhập
     * @return tài khoản, hoặc rỗng
     */
    Optional<User> findByEmail(String email);

    /**
     * @param email email cần kiểm
     * @return true nếu email đã có tài khoản giữ
     */
    boolean existsByEmail(String email);
}
