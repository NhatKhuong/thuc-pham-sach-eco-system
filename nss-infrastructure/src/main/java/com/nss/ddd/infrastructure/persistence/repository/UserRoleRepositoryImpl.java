package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.Role;
import com.nss.ddd.domain.model.entity.UserRole;
import com.nss.ddd.domain.repository.UserRoleRepository;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleCode;
import com.nss.ddd.infrastructure.persistence.mapper.UserRoleJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ADAPTER cho port {@code UserRoleRepository}.
 */
@Repository
@RequiredArgsConstructor
public class UserRoleRepositoryImpl implements UserRoleRepository {

    private final UserRoleJPAMapper userRoleJPAMapper;

    @Override
    public List<String> findRoleCodesByUserId(Long userId) {
        return userRoleJPAMapper.findRoleCodesByUserId(userId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Phép chặn danh sách rỗng nằm ở đây</b>, cùng khuôn với
     * {@code OrderRepositoryImpl.findItemsByOrderIds} và cùng lý do: {@code IN :userIds} với
     * collection rỗng dịch ra {@code in ()}, mà MySQL từ chối cú pháp đó. Chặn tại adapter chứ
     * không ở domain vì đây là ràng buộc của <i>SQL</i>, không phải một quy tắc nghiệp vụ.
     * <p>
     * <b>{@code LinkedHashMap} chứ không {@code Collectors.groupingBy}</b>: thứ tự chèn giữ đúng
     * thứ tự {@code ORDER BY} của câu truy vấn, nên hai lần gọi cho ra hai map duyệt giống nhau.
     */
    @Override
    public Map<Long, List<String>> findRoleCodesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<String>> codesByUserId = new LinkedHashMap<>();
        for (UserRoleCode row : userRoleJPAMapper.findRoleCodesByUserIds(userIds)) {
            codesByUserId.computeIfAbsent(row.userId(), key -> new ArrayList<>())
                    .add(row.roleCode());
        }
        return codesByUserId;
    }

    @Override
    public Optional<Role> findRoleByCode(String code) {
        return userRoleJPAMapper.findRoleByCode(code);
    }

    @Override
    public UserRole save(UserRole userRole) {
        return userRoleJPAMapper.save(userRole);
    }
}
