package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.repository.HelloRepository;
import com.nss.ddd.domain.service.HelloDomainService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * Hiện thực domain service `hello`.
 * <p>
 * Phụ thuộc duy nhất là port {@code HelloRepository} — không có tham chiếu nào
 * tới module infrastructure ở compile-time.
 */
@Service
@RequiredArgsConstructor
public class HelloDomainServiceImpl implements HelloDomainService {

    private final HelloRepository helloRepository;

    @Override
    public String getGreeting() {
        return helloRepository.findGreeting();
    }

    @Override
    public String getSource() {
        return helloRepository.findSource();
    }
}
