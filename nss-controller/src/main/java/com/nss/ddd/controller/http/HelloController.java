package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.HelloResponse;
import com.nss.ddd.application.service.hello.HelloAppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Hệ thống",
        description = "Endpoint kiểm tra sức khoẻ của bộ xương, không phục vụ nghiệp vụ nào.")
public class HelloController {

    private final HelloAppService helloAppService;

    /**
     * @return lời chào kèm nguồn sinh ra nó
     */
    @Operation(summary = "Kiểm tra bộ xương",
            description = """
                    Trả về một lời chào cùng nguồn sinh ra nó, chứng minh request đi xuyên đủ \
                    5 module (start → controller → application → domain / infrastructure).

                    Không chạm cơ sở dữ liệu, không cần tham số.""")
    @ApiResponse(responseCode = "200", description = "Lời chào kèm nguồn sinh ra nó")
    @GetMapping("/hello")
    public HelloResponse getHello() {
        log.info("HelloController:->getHello");
        return helloAppService.getHello();
    }
}
