package com.nss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.UserMapper;
import com.nss.ddd.application.model.command.RegisterCommand;
import com.nss.ddd.application.model.response.AuthResponse;
import com.nss.ddd.application.model.response.UserResponse;
import com.nss.ddd.domain.model.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code UserMapper} và hình dạng {@code AuthResponse} — logic thuần, không Spring context,
 * không database.
 * <p>
 * Ba khẳng định ở đây là ba khoá cứng của contract mà ticket 0010 nêu đích danh, và cả ba đều thuộc
 * loại <b>hỏng trong im lặng</b>:
 * <ul>
 *   <li>Trường access token tên là {@code token}, không phải {@code accessToken} — đổi tên thì
 *       backend vẫn 200, frontend vẫn parse được JSON, chỉ là không bao giờ đăng nhập xong.</li>
 *   <li>{@code UserResponse} đúng năm trường — thêm {@code role} hay {@code passwordHash} vào là
 *       rò rỉ, và không có test nào khác bắt được.</li>
 *   <li>Không có bất kỳ đường nào để {@code passwordHash} đi từ entity sang response.</li>
 * </ul>
 * Dùng Jackson tuần tự hoá thật thay vì đọc danh sách field bằng mắt, cùng lý do
 * {@code ProductMapperTest} làm vậy.
 */
class UserMapperTest {

    private static final String PASSWORD_HASH = "$2a$10$.dr31WdMiDT/t/i5.2U.8uaILF5ttzbLjzwhUmqSL74cQQMfM48Sy";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("toResponse map dung 5 truong cua type User phia client")
    void toResponseMapsEveryClientField() {
        UserResponse response = UserMapper.toResponse(genUser());

        assertEquals(7L, response.getId());
        assertEquals("Nguyễn Văn An", response.getFullName());
        assertEquals("demo@nongsansach.vn", response.getEmail());
        assertEquals("0901234567", response.getPhone());
        assertEquals("/images/avatar/an.jpg", response.getAvatar());
    }

    @Test
    @DisplayName("UserResponse co dung 5 field, khong hon — them field la doi contract")
    void userResponseHasExactlyFiveFields() throws Exception {
        String json = objectMapper.writeValueAsString(UserMapper.toResponse(genUser()));

        assertEquals(5, objectMapper.readTree(json).size(), "UserResponse phai co dung 5 truong: " + json);
    }

    @Test
    @DisplayName("JSON cua UserResponse khong bao gio chua password hay vai tro")
    void responseJsonNeverLeaksSecrets() throws Exception {
        String json = objectMapper.writeValueAsString(UserMapper.toResponse(genUser()));

        // Control duong: grep phai bat duoc mot truong CO that, neu khong ba khang dinh duoi vo nghia
        assertTrue(json.contains("fullName"), "grep khong chay dung tren: " + json);
        assertFalse(json.toLowerCase().contains("password"), "Response ro ri password: " + json);
        assertFalse(json.contains(PASSWORD_HASH), "Response ro ri hash: " + json);
        assertFalse(json.toLowerCase().contains("role"), "Response ro ri vai tro: " + json);
    }

    @Test
    @DisplayName("AuthResponse dat ten truong la token, KHONG phai accessToken")
    void authResponseUsesTokenFieldName() throws Exception {
        AuthResponse authResponse = new AuthResponse()
                .setUser(UserMapper.toResponse(genUser()))
                .setToken("eyJhbGciOiJIUzI1NiJ9.payload.signature")
                .setRefreshToken("refresh-token-value");

        String json = objectMapper.writeValueAsString(authResponse);

        assertTrue(objectMapper.readTree(json).has("token"), "Thieu truong `token`: " + json);
        assertFalse(objectMapper.readTree(json).has("accessToken"),
                "`accessToken` la ten sai — client.ts doc `token`: " + json);
        assertTrue(objectMapper.readTree(json).has("refreshToken"));
    }

    @Test
    @DisplayName("toEntity khong dung toi passwordHash — bam la viec cua domain service")
    void toEntityLeavesHashingToDomainService() {
        User draft = UserMapper.toEntity(new RegisterCommand()
                .setFullName("Trần Thị Bình")
                .setEmail("binh@nongsansach.vn")
                .setPhone("0912345678")
                .setPassword("mat-khau-tho"));

        assertEquals("Trần Thị Bình", draft.getFullName());
        assertNull(draft.getPasswordHash(), "toEntity khong duoc dat passwordHash");
        assertNull(draft.getCreatedAt(), "Moc thoi gian do domain service dong dau");
    }

    @Test
    @DisplayName("Null vao thi null ra o ca hai chieu")
    void nullGuards() {
        assertNull(UserMapper.toResponse(null));
        assertNull(UserMapper.toEntity(null));
    }

    /**
     * @return tài khoản đầy đủ, mang cả hash và mốc thời gian để chứng minh chúng không rò ra
     */
    private User genUser() {
        return new User()
                .setId(7L)
                .setFullName("Nguyễn Văn An")
                .setEmail("demo@nongsansach.vn")
                .setPhone("0901234567")
                .setAvatar("/images/avatar/an.jpg")
                .setPasswordHash(PASSWORD_HASH)
                .setCreatedAt(LocalDateTime.of(2026, 8, 22, 0, 0))
                .setUpdatedAt(LocalDateTime.of(2026, 8, 22, 0, 0));
    }
}
