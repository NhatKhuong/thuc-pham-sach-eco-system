package com.nss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động duy nhất của service.
 * <p>
 * Base package `com.nss` bao trọn `com.nss.ddd.*`, nên một `@SpringBootApplication`
 * quét đủ bean của cả 5 module — không cần `scanBasePackages`.
 */
@SpringBootApplication
public class StartApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }
}
