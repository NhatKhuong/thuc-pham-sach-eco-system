package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh đăng ký tài khoản — {@code POST /api/auth/register} sau khi qua validate.
 * <p>
 * {@code password} là mật khẩu <b>thô</b>: nó dừng lại ở {@code AuthDomainService}, chỗ duy nhất
 * được phép băm nó, và không bao giờ đi tiếp xuống DB hay ra response.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCommand {

    private String fullName;

    private String email;

    private String phone;

    /** Mật khẩu thô; chỉ dùng để băm, không bao giờ được log hay trả ra. */
    private String password;
}
