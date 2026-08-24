package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.User;

import java.util.Optional;

/**
 * PORT của aggregate {@code User} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> file này không được import bất cứ thứ gì thuộc
 * {@code org.springframework.data.*} (architecture/01-overview.md §1).
 * <p>
 * <b>Không có đường đọc nào trả {@code passwordHash} ra ngoài domain.</b> Entity mang trường đó vì
 * đối chiếu mật khẩu là việc của {@code AuthDomainService}; tầng application chỉ được nhìn thấy nó
 * qua {@code UserMapper.toResponse}, và mapper đó cố ý không map trường này (§B.4 #1).
 */
public interface UserRepository {

    /**
     * Tra tài khoản theo email — khóa đăng nhập, duy nhất toàn hệ ({@code uk_email}).
     *
     * @param email email đăng nhập
     * @return tài khoản, hoặc rỗng khi email chưa được đăng ký
     */
    Optional<User> findByEmail(String email);

    /**
     * Email đã có ai giữ chưa — cổng kiểm trước khi {@code INSERT} để trả 409 thay vì lỗi ràng buộc
     * {@code uk_email} ở tầng dưới.
     *
     * @param email email cần kiểm
     * @return true nếu email đã được đăng ký
     */
    boolean existsByEmail(String email);

    /**
     * Ghi tài khoản (chèn mới khi {@code id} rỗng, cập nhật khi đã có).
     *
     * @param user tài khoản cần ghi
     * @return bản ghi sau khi ghi, đã có id
     */
    User save(User user);
}
