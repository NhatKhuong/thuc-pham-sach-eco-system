package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của `GET /api/hello`.
 * <p>
 * DTO trần, không bọc envelope — theo ADR 0001 (format I/O đi theo API_CONTRACT.md).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class HelloResponse {

    /**
     * Lời chào lấy qua port `HelloRepository`.
     */
    private String message;

    /**
     * Tầng đã sinh ra `message` — ở bộ xương này luôn là `infrastructure`.
     */
    private String source;

    /**
     * Static factory — không `new` trực tiếp ở call site (coding-conventions muc 7).
     *
     * @param message lời chào
     * @param source  tầng đã sinh ra lời chào
     * @return response đã dựng xong
     */
    public static HelloResponse of(String message, String source) {
        return new HelloResponse()
                .setMessage(message)
                .setSource(source);
    }
}
