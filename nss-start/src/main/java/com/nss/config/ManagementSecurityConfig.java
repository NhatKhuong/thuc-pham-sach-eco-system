package com.nss.config;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Filter chain RIÊNG cho management port (backlog 0038) — KHÔNG phải một chỉnh sửa của
 * {@code SecurityConfig} ở {@code nss-controller}, mà là một context Spring HOÀN TOÀN KHÁC.
 * <p>
 * <b>Một giả định đã sai lúc lập ticket, và bằng chứng thật đã sửa nó.</b> Kỳ vọng ban đầu là
 * "management.server.port khác server.port thì mặc nhiên không đi qua SecurityFilterChain của
 * servlet context chính". Gọi {@code curl http://localhost:8081/actuator/prometheus} không kèm
 * token TRƯỚC KHI có class này trả về <b>401</b>, không phải 200 — vì Spring Boot vẫn đăng ký
 * {@code DelegatingFilterProxy} lên container quản lý riêng, và khi không có
 * {@code SecurityFilterChain} nào khai RIÊNG cho context quản lý,
 * {@code ManagementWebSecurityAutoConfiguration} back off do
 * {@code @ConditionalOnMissingBean(SecurityFilterChain.class)} tìm thấy bean
 * {@code SecurityConfig#securityFilterChain} của context cha — filter proxy rơi xuống dùng NHẦM
 * luật {@code anyRequest().authenticated()} vốn viết cho {@code /api/**}.
 * <p>
 * <b>Cách sửa đúng: một {@code SecurityFilterChain} khai trong context CON của management</b>,
 * đăng ký qua {@code @ManagementContextConfiguration(ManagementContextType.CHILD)} — cơ chế chính
 * thức của Spring Boot Actuator cho bean chỉ sống trong context quản lý riêng. Khi
 * {@code management.server.port} khác {@code server.port}, Spring Boot dựng một
 * {@code ApplicationContext} CON có servlet container embedded RIÊNG cho management; class này chỉ
 * được nạp vào context con đó (khai qua
 * {@code META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports}),
 * nên {@code securityFilterChain} ở đây <b>không bao giờ</b> áp lên {@code /api/**} — hai context là
 * hai {@code ApplicationContext} tách biệt, dù cùng chạy trong một JVM.
 * <p>
 * <b>{@code permitAll} ở đây an toàn vì {@code management.endpoints.web.exposure.include} đã siết
 * trước.</b> {@code application.yml} chỉ bật {@code prometheus,health} — mọi đường Actuator khác
 * (vd {@code /actuator/env}, {@code /actuator/beans}) không được MAP vào context này nên tự trả
 * {@code 404}, không phải việc filter chain này phải tự chặn.
 * <p>
 * <b>{@code securityMatcher("/actuator/**")} + {@code @Order(HIGHEST_PRECEDENCE)} — lớp phòng thủ
 * THỨ HAI, và nó KHÔNG thừa.</b> Ca thật đã đo: {@code @SpringBootTest} với {@code MockMvc} (mock
 * web environment, không server thật) KHÔNG dựng nổi context con riêng — cơ chế
 * {@code ManagementContextConfiguration.CHILD} chỉ tách context khi có embedded servlet container
 * thật, nên trong test bean {@code managementSecurityFilterChain} bị nạp thẳng vào CÙNG context
 * với {@code SecurityConfig#securityFilterChain}. Không khai {@code securityMatcher} thì hai chain
 * cùng khớp "any request" và Spring Security để bean này thắng — 45/81 test của
 * {@code SecurityRulesTest} đỏ ngay (401 kỳ vọng thành 422/500,
 * {@code NullPointerException: Jwt.getSubject()} vì request không còn đi qua converter JWT nữa).
 * Giới hạn matcher về đúng {@code /actuator/**} và xếp order trước làm chain này chỉ "nhận" đúng
 * nhánh của mình dù bị nạp ở đâu — vô hại trong test (rơi thẳng xuống chain thật cho mọi path
 * khác), và tương đương trong production thật (child context vốn đã cô lập, matcher chỉ là lưới an
 * toàn thứ hai).
 * <p>
 * <b>Dev-only, không auth</b> — cùng mức lộ diện đã chấp nhận cho {@code 3316}/{@code 9094}/
 * {@code 6390}/{@code 8089} ở {@code environment/docker-compose-dev.yml}, không phải một nhượng bộ
 * mới (xem backlog 0038, mục "Một giả định cần sửa").
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
public class ManagementSecurityConfig {

    /**
     * @param http bộ dựng filter chain của RIÊNG context quản lý (management port)
     * @return filter chain cho phép mọi request {@code /actuator/**}, không đòi JWT
     * @throws Exception khi cấu hình không hợp lệ
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
