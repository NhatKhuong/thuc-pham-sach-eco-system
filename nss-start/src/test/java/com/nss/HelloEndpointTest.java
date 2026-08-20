package com.nss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test này gánh hai việc cùng lúc:
 * <p>
 * 1. Spring context nạp được với đủ bean của cả 5 module.
 * 2. Luồng `hello` chạy xuyên module — `source` bằng `infrastructure` chứng minh
 *    chuỗi thật sự đi ra từ adapter, không phải hardcode ở controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HelloEndpointTest {

    private final MockMvc mockMvc;

    @Autowired
    HelloEndpointTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("GET /api/hello tra 200 va payload sinh ra tu infrastructure")
    void getHelloReturnsGreetingFromInfrastructure() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello DDD"))
                .andExpect(jsonPath("$.source").value("infrastructure"));
    }
}
