package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của một người dùng trên bề mặt dây — khớp <b>đúng</b> type {@code User} của frontend
 * ({@code src/types/user.ts}, API_CONTRACT §B.4).
 * <p>
 * <b>Đúng năm trường, không hơn.</b> Danh sách field ở đây là một khoá cứng của ticket 0010, và
 * mọi thứ vắng mặt đều vắng mặt có chủ ý:
 * <ul>
 *   <li>{@code passwordHash} — §B.4 #1: {@code User} trả về không bao giờ chứa password, kể cả đã
 *       băm.</li>
 *   <li>{@code role} / {@code permissions} — RBAC là thuần server-side; javadoc của entity
 *       {@code User} đã cấm sẵn việc để {@code Role} / {@code Permission} rò ra response. Vai trò
 *       đi trong claim {@code roles} của access token, không đi trong body.</li>
 *   <li>{@code createdAt} / {@code updatedAt} — không có trong type phía client.</li>
 * </ul>
 * Thêm một trường vào đây là <b>thay đổi contract</b>, không phải tiện tay.
 * <p>
 * {@code id} là <b>số</b>, không phải chuỗi UUID.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;

    private String fullName;

    private String email;

    private String phone;

    /** Đường dẫn ảnh <b>tương đối</b> {@code /images/...}; {@code null} khi chưa có (§A.5). */
    private String avatar;
}
