package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.TextNormalizer;
import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.repository.UserRoleRepository;
import com.nss.ddd.domain.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực domain service của đường đọc chéo người dùng.
 * <p>
 * Phụ thuộc là hai port của domain — không có tham chiếu nào tới module infrastructure ở
 * compile-time.
 * <p>
 * <b>Không có method ghi nào, và đó là contract</b> — xem javadoc của {@link UserDomainService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDomainServiceImpl implements UserDomainService {

    private final UserRepository userRepository;

    private final UserRoleRepository userRoleRepository;

    @Override
    public PageResult<User> findAdminPage(UserFilter filter) {
        // Dung mot UserFilter MOI thay vi sua cai duoc truyen vao — cung ly do da viet o
        // ProductDomainServiceImpl.findAdminPage: sua tai cho thi mot dong log o tang tren in ra
        // sau loi goi nay se noi sai ve chinh cai request no dang xu ly.
        return userRepository.findAdminPage(UserFilter.of(
                TextNormalizer.genSearchKeyword(filter.getKeyword()),
                filter.getRoleCode(),
                filter.getPage(),
                filter.getLimit()));
    }

    @Override
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public Map<Long, List<String>> findRoleCodesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRoleRepository.findRoleCodesByUserIds(userIds);
    }

    @Override
    public long countCustomers() {
        // Bo loc dung y het GET /admin/customers khong kem tham so: khong tu khoa, vai tro CUSTOMER.
        // `page` / `limit` khong tham gia phep dem nen truyen gia tri trung tinh.
        return userRepository.countAdminUsers(UserFilter.of(null, ROLE_CODE_CUSTOMER, 1, 1));
    }
}
