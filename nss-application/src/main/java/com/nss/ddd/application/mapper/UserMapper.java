package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.command.UpdateProfileCommand;
import com.nss.ddd.application.model.response.AdminUserResponse;
import com.nss.ddd.application.model.response.UserResponse;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.UserDomainService;

import java.util.List;

/**
 * Converter viết tay giữa {@code User} và các kiểu của tầng application.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7).
 * <p>
 * <b>Đây là cổng chặn rò rỉ, không chỉ là chỗ chép field.</b> {@link #toResponse(User)} liệt kê
 * bằng tay đúng năm trường của {@code UserResponse}; {@code passwordHash} không có mặt và
 * không được phép có mặt (§B.4 #1). Đó cũng là lý do không dùng
 * {@code BeanUtils.copyProperties} ở đây: một bản chép ngầm sẽ mang cả hash sang response ngay lần
 * đầu tiên ai đó thêm một field vào DTO, và không có gì báo lỗi. Cùng kỷ luật đó áp cho
 * {@link #toAdminResponse(User, List)} — và từ backlog 0019 nó còn phải chặn thêm một trường mới:
 * {@code fullNameNormalized}.
 * <p>
 * <b>HAI đường dựng payload người dùng, và chúng KHÔNG được gộp.</b> {@link #toResponse(User)} phục
 * vụ {@code /auth/**} (năm trường, không có {@code role}); {@link #toAdminResponse(User, List)}
 * phục vụ {@code /admin/customers} (sáu trường, có {@code role}). Lý do đầy đủ nằm ở javadoc của
 * {@link AdminUserResponse}. Gộp lại là để vai trò của mọi người dùng rò ra mọi response của
 * {@code /auth/**}.
 */
public final class UserMapper {

    /**
     * Giá trị {@code role} trên dây cho vai trò {@code CUSTOMER} — <b>chữ thường số ít</b>, khớp
     * {@code types/user.ts#UserRole}.
     * <p>
     * {@code public} để test khoá được chính chuỗi đi lên dây thay vì chép lại nó — cùng lý do
     * khiến {@code OrderMapper.WIRE_*} là {@code public}.
     */
    public static final String WIRE_ROLE_CUSTOMER = "customer";

    /** Giá trị {@code role} trên dây cho vai trò {@code ADMIN} — xem {@link #WIRE_ROLE_CUSTOMER}. */
    public static final String WIRE_ROLE_ADMIN = "admin";

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
     * Payload <b>sáu trường</b> của khu quản trị (§B.12.3).
     * <p>
     * <b>Không đụng tới {@link #toResponse(User)}</b> — xem javadoc cấp class và javadoc của
     * {@link AdminUserResponse} về việc vì sao đây phải là một DTO riêng.
     * <p>
     * <b>Danh sách viết tay là chỗ chặn HAI trường, không phải một.</b> {@code passwordHash} thì đã
     * bị cấm từ §B.4 #1; backlog 0019 thêm {@code fullNameNormalized} — một cột <i>phái sinh</i>
     * chỉ tồn tại để tìm kiếm bỏ dấu, và để nó lọt ra dây là công bố một chi tiết cài đặt mà client
     * sẽ bắt đầu phụ thuộc vào.
     *
     * @param user tài khoản đã đọc từ DB
     * @param roleCodes mã vai trò của chính tài khoản đó, đọc theo lô; có thể rỗng
     * @return payload sáu trường, hoặc {@code null} khi {@code user} rỗng
     */
    public static AdminUserResponse toAdminResponse(User user, List<String> roleCodes) {
        if (user == null) {
            return null;
        }
        return new AdminUserResponse()
                .setId(user.getId())
                .setFullName(user.getFullName())
                .setEmail(user.getEmail())
                .setPhone(user.getPhone())
                .setAvatar(user.getAvatar())
                .setRole(toWireRole(roleCodes));
    }

    /**
     * Bảng dịch vai trò từ mã trong DB sang chuỗi trên dây.
     * <p>
     * <b>{@code ADMIN} thắng khi một tài khoản mang cả hai vai trò.</b> Cột {@code role} của bảng
     * khách hàng là một giá trị đơn ({@code types/user.ts#UserRole} là union hai chuỗi), còn
     * {@code user_role} là quan hệ nhiều-nhiều — nên phải có một luật ưu tiên, và luật đó phải
     * nghiêng về vai trò <i>mạnh hơn</i>: hiển thị "khách hàng" cho một tài khoản có quyền quản trị
     * là nói sai về đúng thứ người đọc bảng cần biết.
     * <p>
     * <b>Không nhận ra vai trò nào thì trả {@code null}, không đoán</b> — cùng khuôn với
     * {@code OrderMapper.toWireStatus}. Một vai trò thứ ba ra đời mà quên khai ở đây thì
     * {@code null} hỏng ngay và hỏng ở chỗ đọc được; rơi về {@code customer} sẽ khiến bảng hiển thị
     * một sự thật sai.
     *
     * @param roleCodes mã vai trò UPPER_SNAKE; có thể rỗng
     * @return {@code admin} / {@code customer}, hoặc {@code null} khi không nhận ra vai trò nào
     */
    private static String toWireRole(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return null;
        }
        if (roleCodes.contains(UserDomainService.ROLE_CODE_ADMIN)) {
            return WIRE_ROLE_ADMIN;
        }
        if (roleCodes.contains(UserDomainService.ROLE_CODE_CUSTOMER)) {
            return WIRE_ROLE_CUSTOMER;
        }
        return null;
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
