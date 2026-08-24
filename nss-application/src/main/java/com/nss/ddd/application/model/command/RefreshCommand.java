package com.nss.ddd.application.model.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Lệnh gia hạn phiên — {@code POST /api/auth/refresh}.
 * <p>
 * Không mang {@code userId}: chủ sở hữu được suy ra từ chính dòng {@code refresh_token} trong DB,
 * không nhận từ client (§C.2).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class RefreshCommand {

    private String refreshToken;
}
