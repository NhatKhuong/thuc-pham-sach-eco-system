package com.nss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Điểm khởi động duy nhất của service.
 * <p>
 * Base package `com.nss` bao trọn `com.nss.ddd.*`, nên một `@SpringBootApplication`
 * quét đủ bean của cả 5 module — không cần `scanBasePackages`.
 * <p>
 * <b>`@EnableAsync` phục vụ đúng một thứ hôm nay: đường gửi mail (backlog 0017).</b> Nó không phải
 * một tối ưu hiệu năng mà là điều kiện để `POST /auth/forgot-password` không phân biệt được giữa
 * "email có thật" và "email không tồn tại" <i>bằng thời gian phản hồi</i> — mã trạng thái thì dễ
 * làm cho giống nhau, thời gian thì không. Lý do đầy đủ nằm ở javadoc của `MailAppService`.
 * <p>
 * Cố ý <b>không</b> khai `TaskExecutor` riêng: `spring.threads.virtual.enabled: true` đã bật ở
 * `application.yml` nên Spring Boot 3.3 tự cấp cho `@Async` một executor chạy trên virtual thread.
 * Thêm một pool thủ công ở đây sẽ <i>ghi đè</i> lựa chọn đó và lặng lẽ đưa luồng gửi mail về pool
 * nền tảng.
 */
@EnableAsync
@SpringBootApplication
public class StartApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }
}
