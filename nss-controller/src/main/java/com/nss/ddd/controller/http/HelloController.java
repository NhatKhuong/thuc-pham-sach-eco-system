package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.HelloResponse;
import com.nss.ddd.application.service.hello.HelloAppService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint kiểm tra bộ xương: chứng minh một request đi xuyên đủ 5 module.
 * <p>
 * Trả DTO trần, không bọc `ResultMessage` — theo ADR 0001.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HelloController {

    private final HelloAppService helloAppService;

    @GetMapping("/hello")
    public HelloResponse getHello() {
        log.info("HelloController:->getHello");
        return helloAppService.getHello();
    }
}
