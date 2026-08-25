package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.infrastructure.persistence.mapper.UserJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ADAPTER cho port {@code UserRepository}.
 * <p>
 * Stereotype là {@code @Repository}, không phải {@code @Service} (coding-conventions §3).
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    /** Ký tự escape của mệnh đề {@code LIKE}; phải khớp {@code ESCAPE} khai trong JPQL. */
    private static final char LIKE_ESCAPE = '!';

    private final UserJPAMapper userJPAMapper;

    @Override
    public Optional<User> findByEmail(String email) {
        return userJPAMapper.findByEmail(email);
    }

    @Override
    public Optional<User> findById(Long id) {
        // findById cua JpaRepository da co san — khong can khai them method nao o UserJPAMapper
        return userJPAMapper.findById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJPAMapper.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        return userJPAMapper.save(user);
    }

    // ========== KHU QUAN TRI (§B.12.3, §B.12.4) ==========

    @Override
    public PageResult<User> findAdminPage(UserFilter filter) {
        // API_CONTRACT §A.4: `page` tren duong day danh so tu 1, Spring Data danh so tu 0.
        // Khong truyen Sort: thu tu (id ASC) da nam trong chuoi JPQL vi client khong chon duoc.
        Page<User> result = userJPAMapper.findAdminPage(
                genLikePattern(filter.getKeyword()),
                filter.getRoleCode(),
                PageRequest.of(filter.getPage() - 1, filter.getLimit()));
        return PageResult.of(result.getContent(), result.getTotalElements());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Đi qua <b>đúng</b> mẫu {@code LIKE} và <b>đúng</b> mệnh đề lọc mà
     * {@link #findAdminPage(UserFilter)} dùng — đó là thứ giữ {@code customerCount} bằng
     * {@code total} của {@code GET /admin/customers} theo cấu tạo.
     */
    @Override
    public long countAdminUsers(UserFilter filter) {
        return userJPAMapper.countAdminUsers(
                genLikePattern(filter.getKeyword()),
                filter.getRoleCode());
    }

    /**
     * Dựng mẫu {@code LIKE} chứa (contains) từ một từ khoá đã bỏ dấu.
     * <p>
     * Cùng khuôn — và cùng lý do được phép là bản riêng của adapter — với
     * {@code ProductRepositoryImpl.genLikePattern} và {@code OrderRepositoryImpl.genLikePattern}:
     * thứ coding-conventions §18 cấm chép là <i>phép bỏ dấu</i>, thứ đã được gom về
     * {@code TextNormalizer}. Việc bọc {@code %} và escape thì nói về cú pháp {@code LIKE} của câu
     * truy vấn ngay cạnh nó.
     *
     * @param keyword từ khoá đã bỏ dấu; {@code null} hoặc rỗng nghĩa là không tìm
     * @return mẫu dạng {@code %tu-khoa%}, hoặc {@code null} khi không tìm
     */
    private static String genLikePattern(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);
        escaped.append('%');
        for (char character : keyword.toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.append('%').toString();
    }
}
