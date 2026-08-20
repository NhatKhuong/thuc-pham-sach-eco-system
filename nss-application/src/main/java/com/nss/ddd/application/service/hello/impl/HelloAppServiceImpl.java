package com.nss.ddd.application.service.hello.impl;

import com.nss.ddd.application.model.response.HelloResponse;
import com.nss.ddd.application.service.hello.HelloAppService;
import com.nss.ddd.domain.service.HelloDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * Hiện thực use case `hello`.
 * <p>
 * Application chỉ điều phối: gọi domain service rồi dựng response.
 * Không có quy tắc nghiệp vụ nào nằm ở đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelloAppServiceImpl implements HelloAppService {

    private final HelloDomainService helloDomainService;

    @Override
    public HelloResponse getHello() {
        // 1. Lấy lời chào — chuỗi này bắt nguồn từ adapter ở infrastructure
        String message = helloDomainService.getGreeting();
        // 2. Lấy tên tầng đã sinh ra lời chào, cũng đi ra từ chính adapter đó
        String source = helloDomainService.getSource();
        log.info("getHello: success | source={}", source);
        return HelloResponse.of(message, source);
    }
}
