package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh đăng nhập — {@code POST /api/auth/login} sau khi qua validate.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class LoginCommand {

    private String email;

    /** Mật khẩu thô; chỉ dùng để đối chiếu với hash, không bao giờ được log hay trả ra. */
    private String password;
}
