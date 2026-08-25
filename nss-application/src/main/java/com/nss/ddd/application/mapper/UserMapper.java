package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.command.UpdateProfileCommand;
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

    /**
     * Áp một bản vá <b>từng phần</b> lên entity đang được transaction quản lý.
     * <p>
     * <b>Đọc kỹ sự đối lập với {@code ProductMapper.applyUpdate} trước khi chép mẫu.</b> Cái kia là
     * <i>thay thế toàn phần</i>: nó gán <b>mọi</b> trường từ command, kể cả khi giá trị là
     * {@code null}, vì {@code PUT /products/{id}} thay trọn bản ghi. Method này làm điều ngược lại —
     * nó <b>bỏ qua</b> mọi trường {@code null}. Chép nhầm chiều: một người sửa mỗi {@code fullName}
     * sẽ mất trắng {@code email} và {@code phone}, và vì cả hai cột đều {@code NOT NULL} thì lỗi nổ
     * ở tầng JDBC lúc commit chứ không ở chỗ gây ra nó.
     * <p>
     * <b>Ba trường được vá là toàn bộ những gì được phép ghi.</b> {@code id}, {@code passwordHash},
     * {@code avatar}, {@code createdAt} và mọi thứ liên quan tới vai trò không có mặt ở đây và
     * không có mặt trong {@code UpdateProfileCommand} — hai lớp chặn cho cùng một luật §B.4 #2:
     * sửa hồ sơ không được phép tự nâng quyền. Client gửi thừa những trường đó thì Jackson bỏ qua
     * trong im lặng, đúng như contract yêu cầu.
     * <p>
     * <b>Method này sửa entity tại chỗ, nên gọi nó là một hành động KHÔNG hoàn tác được trong
     * transaction.</b> Entity đọc trong {@code @Transactional} là entity được quản lý: sửa xong rồi
     * trả về một giá trị thất bại thì transaction <i>vẫn commit</i>. Mọi cổng thất bại phải chạy
     * <b>trước</b> lời gọi này — xem {@code AuthAppServiceImpl.updateProfile}.
     *
     * @param target entity đọc từ DB, đang được quản lý
     * @param command bản vá; trường {@code null} nghĩa là giữ nguyên giá trị cũ
     * @return chính {@code target} đã được vá, hoặc {@code null} khi tham số rỗng
     */
    public static User applyPatch(User target, UpdateProfileCommand command) {
        if (target == null || command == null) {
            return null;
        }
        if (command.getFullName() != null) {
            target.setFullName(command.getFullName());
        }
        if (command.getEmail() != null) {
            target.setEmail(command.getEmail());
        }
        if (command.getPhone() != null) {
            target.setPhone(command.getPhone());
        }
        return target;
    }
}
