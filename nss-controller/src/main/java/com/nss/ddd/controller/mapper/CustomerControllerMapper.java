package com.nss.ddd.controller.mapper;

import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.service.UserDomainService;

import java.util.Locale;

/**
 * Converter ở ranh giới HTTP cho {@code GET /api/admin/customers} — tham số truy vấn thành
 * {@link UserFilter} của domain (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * <p>
 * <b>Đây là chỗ luật "{@code role} bỏ trống ⇒ {@code customer}" được cưỡng chế</b> (§B.12.3), và nó
 * phải nằm ở <i>một</i> chỗ: lớp mock của frontend giữ luật đó trong {@code DEFAULT_ROLE}, và khi
 * frontend chuyển sang gọi backend thật thì hằng đó <b>biến mất khỏi phía họ</b> — javadoc của
 * {@code getAdminUsers} nói thẳng "backend phải mặc định {@code customer} khi {@code role} bỏ
 * trống. Thay thân hàm mà quên điều đó thì bảng lặng lẽ mọc lại tài khoản quản trị."
 */
public final class CustomerControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private CustomerControllerMapper() {
    }

    /**
     * Gom bốn tham số truy vấn thành điều kiện lọc của domain.
     *
     * @param q từ khoá tìm kiếm; rỗng là không tìm
     * @param role vai trò dạng chuỗi trên dây ({@code customer} / {@code admin}); rỗng là
     *             {@code customer}
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc của domain, không bao giờ {@code null}
     */
    public static UserFilter toFilter(String q, String role, int page, int limit) {
        return UserFilter.of(
                toNullIfBlank(q),
                toRoleCode(role),
                page,
                limit);
    }

    /**
     * Chuỗi {@code role} trên dây thành mã vai trò của DB.
     * <p>
     * <b>Rỗng ⇒ {@link UserDomainService#ROLE_CODE_CUSTOMER}</b> (§B.12.3). Đây là <i>mặc định</i>,
     * không phải hàng rào: truyền {@code role=admin} vẫn trả về tài khoản quản trị, và đó là chỗ để
     * xem tập khác khi cần. Quyền vào được namespace này đã do filter {@code /api/admin/**} gác.
     * <p>
     * <b>Phép dịch là một phép đổi hoa/thường, không phải một bảng ánh xạ phải bảo trì</b> — cùng
     * quy ước với {@code ProductControllerMapper}: dây dùng {@code lower_snake} của TypeScript, DB
     * dùng {@code UPPER_SNAKE}.
     * <p>
     * <b>Một vai trò lạ ({@code role=xyz}) cho ra TẬP RỖNG, không phải "bỏ lọc".</b> Nó được đổi
     * hoa lên thành {@code XYZ}, không khớp dòng nào trong bảng {@code role}, nên mệnh đề
     * {@code EXISTS} loại hết — <i>chính xác</i> hành vi của {@code applyFilters} phía frontend
     * ({@code adminUsers.api.ts:74-76}), vốn so bằng {@code user.role === query.role}. Rơi về mặc
     * định {@code customer} ở đây sẽ trả lời một câu hỏi khác câu được hỏi; bỏ lọc thì còn tệ hơn —
     * nó trả về cả tài khoản quản trị cho một request không hề xin chúng.
     * <p>
     * Không cần danh sách trắng: giá trị đi vào truy vấn qua tham số {@code :roleCode} đã bind, nên
     * một chuỗi lạ chỉ là một chuỗi không khớp gì.
     *
     * @param role chuỗi trên dây
     * @return mã vai trò UPPER_SNAKE, không bao giờ {@code null}
     */
    public static String toRoleCode(String role) {
        String normalized = toNullIfBlank(role);
        if (normalized == null) {
            return UserDomainService.ROLE_CODE_CUSTOMER;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * Gộp {@code null} và chuỗi toàn khoảng trắng thành một tín hiệu duy nhất — cùng khuôn với
     * {@code ProductControllerMapper}.
     *
     * @param value chuỗi thô
     * @return chuỗi đã {@code trim}, hoặc {@code null} khi rỗng
     */
    private static String toNullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
