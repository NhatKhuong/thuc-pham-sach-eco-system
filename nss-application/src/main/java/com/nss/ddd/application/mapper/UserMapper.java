package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.response.UserResponse;
import com.nss.ddd.domain.model.entity.User;

/**
 * Converter viết tay giữa {@code User} và các kiểu của tầng application.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7).
 * <p>
 * <b>Đây là cổng chặn rò rỉ, không chỉ là chỗ chép field.</b> {@link #toResponse(User)} liệt kê
 * bằng tay đúng năm trường của {@code types/user.ts#User}; {@code passwordHash} không có mặt và
 * không được phép có mặt (§B.4 #1). Đó cũng là lý do không dùng
 * {@code BeanUtils.copyProperties} ở đây: một bản chép ngầm sẽ mang cả hash sang response ngay lần
 * đầu tiên ai đó thêm một field vào DTO, và không có gì báo lỗi.
 */
public final class UserMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private UserMapper() {
    }

    /**
     * @param user tài khoản đã đọc từ DB
     * @return payload năm trường, hoặc {@code null} khi {@code user} rỗng
     */
    public static UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse()
                .setId(user.getId())
                .setFullName(user.getFullName())
                .setEmail(user.getEmail())
                .setPhone(user.getPhone())
                .setAvatar(user.getAvatar());
    }

    /**
     * Dựng bản nháp entity từ lệnh đăng ký.
     * <p>
     * Cố ý <b>không</b> đụng tới {@code passwordHash}, {@code createdAt} / {@code updatedAt},
     * {@code avatar} — mật khẩu do {@code AuthDomainService} băm, mốc thời gian do nó đóng dấu, và
     * ảnh đại diện chưa có ở bước đăng ký.
     *
     * @param command lệnh đăng ký
     * @return bản nháp entity, hoặc {@code null} khi {@code command} rỗng
     */
    public static User toEntity(RegisterCommand command) {
        if (command == null) {
            return null;
        }
        return new User()
                .setFullName(command.getFullName())
                .setEmail(command.getEmail())
                .setPhone(command.getPhone());
    }
}
