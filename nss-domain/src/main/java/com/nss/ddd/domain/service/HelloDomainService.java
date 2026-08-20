package com.nss.ddd.domain.service;

/**
 * Domain service của luồng `hello`.
 * <p>
 * Chỉ biết port {@code HelloRepository}, không biết adapter nào đang được nối vào.
 */
public interface HelloDomainService {

    /**
     * @return lời chào lấy qua port
     */
    String getGreeting();

    /**
     * @return tên tầng đã sinh ra lời chào
     */
    String getSource();
}
