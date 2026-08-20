package com.nss.ddd.infrastructure.config;

import org.hibernate.dialect.MySQLDialect;

/**
 * Dialect MySQL ép charset/collation ở mức bảng.
 * <p>
 * {@code architecture/01-overview.md} §3 bắt mọi bảng phải là
 * {@code ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci}, và
 * [ADR 0002] giữ nguyên hiệu lực phần quy ước đó. Hibernate mặc định chỉ sinh
 * {@code engine=InnoDB}, phần charset để bảng thừa kế từ database — mà database do
 * {@code createDatabaseIfNotExist=true} tạo ra lại mang collation mặc định của server
 * (MySQL 8 là {@code utf8mb4_0900_ai_ci}), không phải {@code utf8mb4_unicode_ci}.
 * <p>
 * Khai ở đây một lần thì mọi bảng và cả file SQL kết xuất đều đúng, không phụ thuộc
 * cấu hình sẵn có của máy chạy.
 */
public class MySQLUtf8mb4Dialect extends MySQLDialect {

    @Override
    public String getTableTypeString() {
        return " engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci";
    }
}
