package com.nss.ddd.application.service.customer;

import com.nss.ddd.application.model.response.AdminUserResponse;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.domain.model.UserFilter;

/**
 * Use case <b>khách hàng ở khu quản trị</b> — API_CONTRACT §B.12.3, hai endpoint của backlog 0019
 * phase 2.
 * <p>
 * <b>Tách khỏi {@code AuthAppService} vì hai bên phục vụ hai namespace được gác bằng hai lớp bảo
 * mật khác nhau</b> — đúng lý do §B.12.3 nêu cho việc frontend tách {@code adminUsers.api.ts} khỏi
 * {@code auth.api.ts}: {@code /auth/**} là namespace của <i>chính người đang đăng nhập</i>,
 * {@code /admin/**} là namespace <i>đọc chéo mọi người dùng</i>. Để chung một service là mời một
 * lời gọi liệt kê chéo mọc nhánh vào đường {@code /auth}.
 * <p>
 * <b>CHỈ ĐỌC, và sự vắng mặt của mọi use case ghi ở đây là contract</b> (§B.12.3): không sửa hồ sơ,
 * không xoá, không khoá tài khoản, không đổi vai trò. Riêng vai trò không phải "chưa làm" mà là
 * <b>không được làm ở đây</b> — một {@code PATCH /admin/customers/{id}/role} là mở đúng cái cửa
 * ADR 0002 đóng lại, và cần thì phải là một quyết định của Owner.
 * <p>
 * <b>Cũng KHÔNG có use case "đơn hàng của một khách"</b> (§B.12.3): màn hồ sơ gọi lại
 * {@code GET /admin/orders?userId={id}}. Thêm một đường thứ hai làm đúng việc đường thứ nhất đã làm
 * là thêm một hàng rào quyền phải nhớ gác lại lần nữa.
 */
public interface CustomerAppService {

    /**
     * Danh sách khách hàng — {@code GET /admin/customers}.
     * <p>
     * <b>{@code roleCode} của {@code filter} đã rơi về mặc định trước khi tới đây</b> (§B.12.3:
     * {@code role} bỏ trống ⇒ {@code customer}), nên method này không bao giờ thấy {@code null} và
     * không bao giờ trả về "mọi vai trò".
     *
     * @param filter điều kiện lọc; {@code keyword} là chuỗi thô client gửi
     * @return trang khách hàng theo §A.4
     */
    PaginatedResponse<AdminUserResponse> findAdminUsers(UserFilter filter);

    /**
     * Một khách hàng theo <b>id</b> — {@code GET /admin/customers/{id}}.
     * <p>
     * <b>Khoá theo {@code id} chứ không theo email</b> (§B.12.3): email là thứ khách tự sửa được ở
     * {@code /tai-khoan}, mà link hồ sơ đã lưu không được hỏng sau lần Lưu đầu tiên.
     * <p>
     * <b>Không lọc theo vai trò.</b> Tham số {@code role} là bộ lọc của <i>danh sách</i>; một id có
     * thật thì tra ra được, kể cả khi đó là tài khoản quản trị — nếu không thì một dòng
     * {@code role=admin} vừa hiện trong bảng sẽ trả 404 khi bấm vào.
     *
     * @param id khoá chính của tài khoản
     * @return khách hàng, hoặc {@code null} khi id không khớp dòng nào
     */
    AdminUserResponse findAdminUserById(Long id);
}
