package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload trả về của {@code register} / {@code login} / {@code refresh} — khớp {@code AuthResponse}
 * của frontend (API_CONTRACT §B.4, §D #5).
 * <p>
 * <b>Trường access token tên là {@code token}, KHÔNG phải {@code accessToken}.</b> Đây là khoá cứng
 * số 1 của ticket 0010: {@code client.ts} đọc đúng khoá đó để gắn header
 * {@code Authorization: Bearer}. Đổi tên trường là gãy đăng nhập ở toàn bộ frontend, và triệu
 * chứng là "đăng nhập xong vẫn bị coi như chưa đăng nhập" chứ không phải một lỗi rõ ràng.
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private UserResponse user;

    /** Access token JWT — tên trường là {@code token}, xem javadoc cấp class. */
    private String token;

    /** Refresh token; đổi mỗi lần {@code refresh} vì cơ chế xoay vòng (ADR 0003). */
    private String refreshToken;
}
