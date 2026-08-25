package com.nss;

import com.nss.ddd.application.mapper.UserMapper;
import com.nss.ddd.application.model.response.AdminUserResponse;
import com.nss.ddd.controller.mapper.CustomerControllerMapper;
import com.nss.ddd.domain.model.TextNormalizer;
import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.UserDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm bề mặt dây của {@code GET /admin/customers} (§B.12.3): DTO sáu trường, phép dịch vai trò, và
 * luật "{@code role} bỏ trống ⇒ {@code customer}".
 * <p>
 * <b>Ba thứ được khoá ở đây, cả ba đều hỏng trong im lặng nếu sai:</b>
 * <ul>
 *   <li><b>{@code AdminUserResponse} có đúng sáu trường, và {@code passwordHash} /
 *       {@code fullNameNormalized} không nằm trong đó.</b> Danh sách trường được đọc bằng phản
 *       chiếu chứ không liệt kê tay — cùng cách {@code UserMapperTest} khoá {@code UserResponse} ở
 *       năm trường. Một trường thêm vào DTO mà không ai để ý sẽ đỏ ngay ở đây.</li>
 *   <li><b>{@code role} bỏ trống ⇒ {@code CUSTOMER}.</b> Lớp mock của frontend giữ luật này trong
 *       {@code DEFAULT_ROLE}, và hằng đó <i>biến mất</i> khi frontend chuyển sang gọi backend
 *       thật — nên nếu backend cũng quên thì bảng khách hàng lặng lẽ mọc lại tài khoản quản trị,
 *       và không có gì báo lỗi.</li>
 *   <li><b>Vai trò lạ cho ra tập rỗng</b>, không phải "bỏ lọc" và cũng không phải "rơi về
 *       customer" — cả hai cách kia đều trả lời một câu khác câu được hỏi.</li>
 * </ul>
 */
class AdminCustomerMapperTest {

    /** Sáu trường của {@code types/user.ts#User} — bản khoá cứng để so với DTO. */
    private static final Set<String> EXPECTED_FIELDS =
            Set.of("id", "fullName", "email", "phone", "avatar", "role");

    // ========== DTO SAU TRUONG ==========

    /**
     * {@code AdminUserResponse} có <b>đúng sáu trường</b>, không hơn không kém.
     * <p>
     * Đọc bằng phản chiếu chứ không so từng getter: một trường thêm vào sẽ lọt qua mọi phép so
     * từng-getter, còn ca này thì đỏ.
     */
    @Test
    @DisplayName("AdminUserResponse co DUNG sau truong cua types/user.ts#User")
    void adminUserResponseHasExactlySixFields() {
        Set<String> actual = Arrays.stream(AdminUserResponse.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertEquals(EXPECTED_FIELDS, actual,
                "Sau truong nay la contract voi frontend; them mot truong la doi contract");
    }

    /**
     * <b>Hai trường tuyệt đối không được rò ra dây</b>, kèm control dương.
     * <p>
     * {@code passwordHash} bị §B.12.3 cấm; {@code fullNameNormalized} là cột <i>phái sinh</i> chỉ
     * tồn tại để tìm kiếm bỏ dấu, và để nó lọt ra là công bố một chi tiết cài đặt mà client sẽ bắt
     * đầu phụ thuộc vào.
     * <p>
     * Hai khẳng định {@code assertFalse} chỉ có nghĩa sau khẳng định {@code assertTrue} ngay trên
     * chúng — nó chứng minh phép đo <i>nhìn thấy được</i> danh sách trường.
     */
    @Test
    @DisplayName("passwordHash va fullNameNormalized KHONG co mat; control duong: fullName CO")
    void neverLeaksPasswordHashOrNormalizedName() {
        Set<String> fields = Arrays.stream(AdminUserResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        // 1. CONTROL DUONG: phep do nhin thay duoc danh sach truong
        assertTrue(fields.contains("fullName"), "Phep do phai nhin thay duoc mot truong hop le");
        // 2. Hai khang dinh phu dinh — chi co nghia sau buoc 1
        assertFalse(fields.contains("passwordHash"), "§B.12.3: khong bao gio kem password, ke ca hash");
        assertFalse(fields.contains("fullNameNormalized"), "Cot phai sinh khong duoc len day");
    }

    /**
     * {@code toAdminResponse} chép đúng năm trường dữ liệu và dịch vai trò sang chữ thường.
     */
    @Test
    @DisplayName("toAdminResponse chep du sau truong, role la chu thuong")
    void mapsAllSixFields() {
        AdminUserResponse response = UserMapper.toAdminResponse(genUser(),
                List.of(UserDomainService.ROLE_CODE_CUSTOMER));

        assertEquals(9L, response.getId());
        assertEquals("Đỗ Thị Hoa", response.getFullName());
        assertEquals("hoa@nongsansach.vn", response.getEmail());
        assertEquals("0911222333", response.getPhone());
        assertEquals("/images/avatar/hoa.jpg", response.getAvatar());
        assertEquals(UserMapper.WIRE_ROLE_CUSTOMER, response.getRole());
    }

    /**
     * <b>{@code ADMIN} thắng khi một tài khoản mang cả hai vai trò.</b>
     * <p>
     * Cột {@code role} của bảng là một giá trị đơn còn {@code user_role} là quan hệ nhiều-nhiều,
     * nên phải có một luật ưu tiên — và nó phải nghiêng về vai trò <i>mạnh hơn</i>: hiển thị "khách
     * hàng" cho một tài khoản có quyền quản trị là nói sai về đúng thứ người đọc bảng cần biết.
     */
    @Test
    @DisplayName("Tai khoan mang ca hai vai tro hien la admin, khong phai customer")
    void adminWinsWhenUserHasBothRoles() {
        assertEquals(UserMapper.WIRE_ROLE_ADMIN, UserMapper.toAdminResponse(genUser(),
                List.of(UserDomainService.ROLE_CODE_CUSTOMER, UserDomainService.ROLE_CODE_ADMIN)).getRole());
        assertEquals(UserMapper.WIRE_ROLE_ADMIN, UserMapper.toAdminResponse(genUser(),
                List.of(UserDomainService.ROLE_CODE_ADMIN, UserDomainService.ROLE_CODE_CUSTOMER)).getRole());
    }

    /**
     * Không nhận ra vai trò nào thì {@code role} là {@code null} — <b>không đoán</b>. Rơi về
     * {@code customer} sẽ khiến bảng hiển thị một sự thật sai.
     */
    @Test
    @DisplayName("Khong nhan ra vai tro nao thi role la null, khong roi ve customer")
    void unknownRoleCodesMapToNull() {
        assertNull(UserMapper.toAdminResponse(genUser(), List.of()).getRole());
        assertNull(UserMapper.toAdminResponse(genUser(), List.of("MODERATOR")).getRole());
        assertNull(UserMapper.toAdminResponse(genUser(), null).getRole());
    }

    /** Null-guard bắt buộc của mọi {@code *Mapper} (coding-conventions §7). */
    @Test
    @DisplayName("user null cho null")
    void nullUserMapsToNull() {
        assertNull(UserMapper.toAdminResponse(null, List.of(UserDomainService.ROLE_CODE_CUSTOMER)));
    }

    // ========== BO LOC ?role= ==========

    /**
     * <b>{@code role} bỏ trống ⇒ {@code CUSTOMER}</b> — §B.12.3, và đây là chỗ duy nhất luật đó
     * được cưỡng chế sau khi frontend gỡ {@code DEFAULT_ROLE} của nó.
     *
     * @param wire giá trị rỗng
     */
    @ParameterizedTest(name = "role=[{0}] -> CUSTOMER")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("role bo trong nghia la CUSTOMER, khong phai 'moi vai tro'")
    void blankRoleDefaultsToCustomer(String wire) {
        assertEquals(UserDomainService.ROLE_CODE_CUSTOMER, CustomerControllerMapper.toRoleCode(wire));
        assertEquals(UserDomainService.ROLE_CODE_CUSTOMER,
                CustomerControllerMapper.toFilter(null, wire, 1, 10).getRoleCode());
    }

    /**
     * @param wire giá trị trên dây
     * @param expected mã vai trò trong DB
     */
    @ParameterizedTest(name = "role={0} -> {1}")
    @CsvSource({
            "customer, CUSTOMER",
            "admin,    ADMIN",
            "ADMIN,    ADMIN",
            "Customer, CUSTOMER"
    })
    @DisplayName("Hai vai tro tren day dich dung sang ma cua DB, khong phan biet hoa thuong")
    void mapsWireRoleValues(String wire, String expected) {
        assertEquals(expected, CustomerControllerMapper.toRoleCode(wire));
    }

    /**
     * Vai trò lạ đi qua nguyên dạng (đã hoa) — nó không khớp dòng nào trong bảng {@code role} nên
     * kết quả là <b>tập rỗng</b>, đúng như {@code applyFilters} của frontend.
     * <p>
     * Khẳng định "không rơi về {@code CUSTOMER}" là phần quan trọng: rơi về mặc định sẽ trả lời một
     * câu khác câu được hỏi.
     *
     * @param wire vai trò không tồn tại
     */
    @ParameterizedTest(name = "role={0} -> tap rong")
    @ValueSource(strings = {"xyz", "moderator", "guest"})
    @DisplayName("Vai tro la cho TAP RONG, khong roi ve CUSTOMER va khong bo loc")
    void unknownRoleMeansEmptyResult(String wire) {
        String roleCode = CustomerControllerMapper.toRoleCode(wire);
        assertEquals(wire.toUpperCase(Locale.ROOT), roleCode);
        assertNotEquals(UserDomainService.ROLE_CODE_CUSTOMER, roleCode,
                "Vai tro la khong duoc am tham tro thanh CUSTOMER");
    }

    /**
     * {@code q} đi qua còn <b>nguyên dấu</b> — phép bỏ dấu là quy tắc nghiệp vụ và nó nằm ở domain
     * service, không ở mapper biên.
     */
    @Test
    @DisplayName("q di qua con NGUYEN DAU; mapper bien chi trim")
    void keywordKeepsDiacriticsAtBoundary() {
        UserFilter filter = CustomerControllerMapper.toFilter("  Đỗ Thị Hoa  ", null, 3, 25);
        assertEquals("Đỗ Thị Hoa", filter.getKeyword());
        assertEquals(3, filter.getPage());
        assertEquals(25, filter.getLimit());
        // Va day la thu domain service se lam voi no
        assertEquals("do thi hoa", TextNormalizer.genSearchKeyword(filter.getKeyword()));
        assertNull(CustomerControllerMapper.toFilter("   ", null, 1, 10).getKeyword());
    }

    /**
     * @return một tài khoản đủ trường để map
     */
    private User genUser() {
        return new User()
                .setId(9L)
                .setFullName("Đỗ Thị Hoa")
                .setFullNameNormalized("do thi hoa")
                .setEmail("hoa@nongsansach.vn")
                .setPhone("0911222333")
                .setAvatar("/images/avatar/hoa.jpg")
                .setPasswordHash("$2a$10$khong-bao-gio-len-day");
    }
}
