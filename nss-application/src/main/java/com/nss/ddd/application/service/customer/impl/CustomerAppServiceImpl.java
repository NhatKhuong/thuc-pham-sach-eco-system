package com.nss.ddd.application.service.customer.impl;

import com.nss.ddd.application.mapper.UserMapper;
import com.nss.ddd.application.model.response.AdminUserResponse;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.service.customer.CustomerAppService;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực use case khách hàng ở khu quản trị.
 * <p>
 * Tầng này chỉ điều phối: hỏi domain service, rồi lắp kết quả thành kiểu của bề mặt dây.
 * <p>
 * <b>Không {@code @Transactional}:</b> cả hai đường đều chỉ đọc và mỗi đường có đúng hai truy vấn
 * cố định (tài khoản, rồi vai trò của cả lô), nên không có gì để gói lại. coding-conventions §8 mục
 * 5 cấm khai {@code readOnly} khi không viết ra được lý do — ở đây không có lý do nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAppServiceImpl implements CustomerAppService {

    /** §A.4 — mặc định 10 khách mỗi trang, khớp {@code USERS_PER_PAGE} của bảng quản trị. */
    private static final int DEFAULT_LIMIT = 10;

    private final UserDomainService userDomainService;

    @Override
    public PaginatedResponse<AdminUserResponse> findAdminUsers(UserFilter filter) {
        // 1. Keo tham so ve khoang dung duoc — dung mot luat voi hai bang quan tri kia (§A.4)
        int safePage = Math.max(filter.getPage(), 1);
        int safeLimit = filter.getLimit() < 1 ? DEFAULT_LIMIT : filter.getLimit();
        // 2. Dung filter MOI thay vi sua cai duoc truyen vao
        UserFilter safeFilter = UserFilter.of(filter.getKeyword(), filter.getRoleCode(),
                safePage, safeLimit);
        PageResult<User> pageResult = userDomainService.findAdminPage(safeFilter);
        List<User> users = pageResult.getItems();
        // 3. Vai tro cua CA trang lay trong MOT truy van — hoi tung nguoi la bien mot trang 12 dong
        //    thanh 13 luot di vong toi MySQL, va trieu chung khong phai mot loi ma la mot trang
        //    cham dan theo so dong.
        Map<Long, List<String>> roleCodesByUserId = userDomainService.findRoleCodesByUserIds(
                users.stream().map(User::getId).toList());
        List<AdminUserResponse> items = new ArrayList<>(users.size());
        for (User user : users) {
            items.add(UserMapper.toAdminResponse(user,
                    roleCodesByUserId.getOrDefault(user.getId(), Collections.emptyList())));
        }
        log.info("findAdminUsers: success | q={} role={} page={} limit={} total={}",
                safeFilter.getKeyword(), safeFilter.getRoleCode(), safePage, safeLimit,
                pageResult.getTotal());
        return PaginatedResponse.of(items, pageResult.getTotal(), safePage, safeLimit);
    }

    @Override
    public AdminUserResponse findAdminUserById(Long id) {
        User user = userDomainService.findById(id);
        if (user == null) {
            log.warn("findAdminUserById: not found | userId={}", id);
            return null;
        }
        // Dung lai chinh duong doc theo lo cho MOT id: mot ham dung o hai cho van tot hon hai ham
        // tra cung mot thu, va lo mot phan tu thi khong dat them truy van nao.
        Map<Long, List<String>> roleCodesByUserId =
                userDomainService.findRoleCodesByUserIds(List.of(id));
        log.info("findAdminUserById: success | userId={}", id);
        return UserMapper.toAdminResponse(user,
                roleCodesByUserId.getOrDefault(id, Collections.emptyList()));
    }
}
