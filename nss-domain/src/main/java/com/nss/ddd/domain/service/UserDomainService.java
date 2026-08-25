package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.model.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Domain service của <b>đường đọc chéo người dùng</b> — {@code GET /admin/customers} (§B.12.3) và
 * {@code customerCount} của {@code GET /admin/stats/overview} (§B.12.4).
 * <p>
 * <b>Tách khỏi {@link AuthDomainService} vì hai bên trả lời hai câu hỏi khác nhau, và ranh giới đó
 * chính là ranh giới bảo mật của hệ.</b> {@code AuthDomainService} phục vụ namespace
 * {@code /auth/**} — <i>người đang đăng nhập tự đọc và tự sửa hồ sơ của chính mình</i>; file này
 * phục vụ namespace {@code /admin/**} — <i>đọc chéo mọi người dùng</i>. §C.4 tách hai phạm vi đó
 * ra làm hai, và để chung một service là mời một lời gọi liệt kê chéo mọc nhánh vào đường
 * {@code /auth}.
 * <p>
 * <b>CHỈ ĐỌC, và sự vắng mặt của mọi method ghi ở đây là contract chứ không phải chỗ còn thiếu.</b>
 * §B.12.3 cấm tường minh: không sửa hồ sơ, không xoá, không khoá tài khoản, và <b>không đổi vai
 * trò</b> — vai trò chỉ được gán ở phía server (ADR 0002). Một method
 * {@code changeRole(userId, roleCode)} thêm vào đây là mở đúng cái cửa ADR đó đóng lại; cần thì
 * phải là một quyết định của Owner, không phải một dòng thêm vào interface này.
 * <p>
 * <b>Service này KHÔNG mở transaction</b> — cùng kỷ luật với {@link OrderDomainService}.
 */
public interface UserDomainService {

    /**
     * Mã vai trò <b>khách hàng</b> trong cột {@code role.code}.
     * <p>
     * <b>Khai ở domain vì đây là chỗ duy nhất cả hai người dùng của nó cùng nhìn thấy được.</b>
     * Trước backlog 0019, chuỗi {@code "CUSTOMER"} sống ở {@code AuthAppServiceImpl} (lúc đăng ký)
     * — và ticket này cần đúng chuỗi ấy ở một chỗ thứ hai (bộ lọc mặc định của
     * {@code GET /admin/customers} và tập mà {@code customerCount} đếm). Chiều phụ thuộc chỉ cho
     * phép một hướng: application thấy domain, domain không thấy application; nên chỗ chứa được cả
     * hai người dùng là domain. Cùng lý lẽ đã dùng cho {@code OrderDomainService.STATUS_PENDING}.
     * <p>
     * <b>Đây là "khách hàng" theo nghĩa của cả §B.12.3 lẫn §B.12.4</b> — hai chỗ duy nhất trong
     * tài liệu đếm người dùng, và chúng phải đếm cùng một tập.
     */
    String ROLE_CODE_CUSTOMER = "CUSTOMER";

    /**
     * Mã vai trò <b>quản trị</b> trong cột {@code role.code}.
     * <p>
     * Là một <i>giá trị hợp lệ của bộ lọc</i> {@code role}, không phải một hàng rào (§B.12.3):
     * {@code role=admin} vẫn trả về tài khoản quản trị. Quyền vào được namespace này đã do filter
     * {@code /api/admin/**} gác.
     * <p>
     * <b>Bản trong {@code SecurityConfig.ROLE_ADMIN} cố ý KHÔNG được gộp vào đây.</b> Nó nói về
     * một thứ khác: authority của Spring Security suy ra từ claim {@code roles} của token, và nó
     * phải nằm cạnh dòng luật dùng nó để một người đọc {@code SecurityConfig} thấy đủ hàng rào
     * trong một màn hình. Gộp lại sẽ khiến module controller phụ thuộc vào domain <i>chỉ vì một
     * chuỗi</i>, và làm dòng luật quan trọng nhất của hệ phải đọc ở hai file.
     */
    String ROLE_CODE_ADMIN = "ADMIN";

    /**
     * Một trang tài khoản có lọc — đường đọc của {@code GET /admin/customers} (§B.12.3).
     * <p>
     * <b>Đây là nơi từ khoá {@code q} được bỏ dấu</b>, không phải ở adapter (coding-conventions
     * §18). Từ khoá được so khớp với ba trường: họ tên đã bỏ dấu, email đã hạ chữ thường, và số
     * điện thoại (khớp cả đoạn giữa).
     *
     * @param filter điều kiện lọc; {@code keyword} là chuỗi thô client gửi, {@code roleCode} đã rơi
     *               về mặc định
     * @return trang tài khoản kèm tổng số dòng khớp điều kiện
     */
    PageResult<User> findAdminPage(UserFilter filter);

    /**
     * @param id khoá chính của tài khoản
     * @return tài khoản, hoặc {@code null} khi id rỗng hoặc không khớp dòng nào
     */
    User findById(Long id);

    /**
     * Mã vai trò của <b>nhiều</b> tài khoản trong một lượt — chống N+1 cho bảng khách hàng.
     * <p>
     * <b>Trang 12 tài khoản phải là một số truy vấn HẰNG SỐ, không phải 12+.</b>
     * {@code AuthDomainService.findRoleCodes} là một-user-một-lần và vẫn đúng cho luồng đăng nhập
     * (đúng một người dùng); dùng lại nó trong một vòng lặp là biến một bảng thành 12 lượt đi vòng
     * tới MySQL mà không có gì báo lỗi — chỉ có một trang chậm dần theo số dòng.
     *
     * @param userIds khoá chính của các tài khoản; rỗng cho ra map rỗng
     * @return mã vai trò theo id; tài khoản chưa có vai trò nào thì vắng mặt khỏi map
     */
    Map<Long, List<String>> findRoleCodesByUserIds(Collection<Long> userIds);

    /**
     * Số tài khoản <b>khách hàng</b> — {@code customerCount} của §B.12.4.
     * <p>
     * <b>Phải bằng {@code total} của {@code GET /admin/customers} khi không kèm tham số nào.</b>
     * Hợp đồng nói thẳng rằng lệch nhau thì bảng ghi 11 dòng còn ô chỉ số ghi 12, và <b>không lỗi
     * nào nổ ra</b>. Cách giữ chúng đúng theo cấu tạo: cùng {@link #ROLE_CODE_CUSTOMER}, cùng một
     * mệnh đề lọc dưới hạ tầng ({@code UserJPAMapper.ADMIN_FILTER}).
     * <p>
     * <b>Là ảnh chụp hiện tại, không phụ thuộc {@code days}</b> (§B.12.4): {@code User} không có
     * {@code createdAt} nên không tồn tại chiều thời gian nào để cắt.
     *
     * @return số tài khoản mang vai trò {@link #ROLE_CODE_CUSTOMER}
     */
    long countCustomers();
}
