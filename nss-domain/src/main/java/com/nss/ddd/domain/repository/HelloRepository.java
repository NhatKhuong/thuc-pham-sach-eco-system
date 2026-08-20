package com.nss.ddd.domain.repository;

/**
 * PORT của luồng `hello` — domain khai báo, infrastructure implement.
 * <p>
 * Ở compile-time domain không biết adapter nào tồn tại; Spring nối adapter vào lúc chạy.
 * Đây chính là bất biến "domain không phụ thuộc module nào" được thể hiện bằng code.
 */
public interface HelloRepository {

    /**
     * @return lời chào do tầng hạ tầng cung cấp
     */
    String findGreeting();

    /**
     * @return tên tầng đã sinh ra lời chào — bằng chứng chuỗi đi ra từ infrastructure
     */
    String findSource();
}
