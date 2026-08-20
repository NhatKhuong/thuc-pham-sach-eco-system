package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.repository.HelloRepository;

import org.springframework.stereotype.Repository;

/**
 * ADAPTER cho port {@code HelloRepository}.
 * <p>
 * Bộ xương chưa có DB nên trả chuỗi hardcode. Cả hai giá trị đều sống ở đây
 * để trường `source` trong response là bằng chứng thật rằng chuỗi đi ra từ
 * infrastructure, không phải do controller tự bịa.
 */
@Repository
public class HelloRepositoryImpl implements HelloRepository {

    private static final String GREETING = "Hello DDD";

    private static final String SOURCE = "infrastructure";

    @Override
    public String findGreeting() {
        return GREETING;
    }

    @Override
    public String findSource() {
        return SOURCE;
    }
}
