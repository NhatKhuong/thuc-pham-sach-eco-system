package com.nss;

import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bất biến "không oversell" dưới tải đồng thời (backlog 0035 Phase 4, architecture/01-overview.md
 * §5, Quyết định Owner #4).
 * <p>
 * <b>@Tag("db")</b> — cần MySQL <b>và</b> Redis thật ({@code environment/docker-compose-dev.yml}),
 * cùng lane với mọi test đánh tag này; bị loại khỏi build mặc định, chạy riêng bằng
 * {@code mvn -pl nss-start test -Dexcluded.test.groups= -Dgroups=db}.
 * <p>
 * <b>Bắn N request {@code createOrder} đồng thời qua virtual thread</b> ({@code N > tồn kho ban
 * đầu}, mỗi request mua đúng 1 đơn vị) — kiểm tra bất biến {@code orders_success <= STOCK} ở đúng
 * mức tải máy dev đạt được, không phải 10-20k req/s thật (Quyết định Owner #4, xem Outcome của
 * backlog 0035).
 * <p>
 * <b>Đây là bằng chứng cho TOÀN BỘ đường ống Phase 0-3</b>, không chỉ Tầng 2: nếu Redis đang chạy,
 * phần lớn request bị Tầng 1 (Lua atomic gate) chặn trước khi chạm MySQL; nếu Redis không chạy (ví
 * dụ ai đó quên {@code docker compose up -d redis} khi chạy riêng test này), StockCacheServiceImpl tự
 * coi mọi lời gọi là {@code MISS} và bất biến vẫn đúng nhờ Tầng 2 — bài test này không phân biệt được
 * hai ca đó, và đó là chủ ý: bất biến phải đúng trong CẢ HAI trường hợp.
 */
@SpringBootTest
@Tag("db")
class FlashSaleConcurrencyIntegrationTest {

    /** Tồn kho ban đầu — nhỏ để dễ khẳng định chính xác, không phải giới hạn kỹ thuật. */
    private static final int INITIAL_STOCK = 20;

    /** N > STOCK — đủ để chắc chắn có request bị từ chối nếu bất biến bị phá. */
    private static final int CONCURRENT_REQUESTS = 150;

    private static final long AWAIT_SECONDS = 60;

    @Autowired
    private OrderAppService orderAppService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("N request dong thoi (N > stock) -> orders_success == stock ban dau, Product.stock cuoi = 0")
    void concurrentOrdersNeverOversell() throws Exception {
        Long productId = genSeedProduct().getId();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger outOfStockCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(CONCURRENT_REQUESTS);
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLine.await();
                    OrderMutationResponse result = orderAppService.createOrder(genOrderCommand(productId));
                    if (result.getOrder() != null) {
                        successCount.incrementAndGet();
                    } else if (OrderMutationResponse.CODE_OUT_OF_STOCK.equals(result.getCode())) {
                        outOfStockCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        // Toan bo N thread da san sang cho o latch — tha CUNG LUC de toi da hoa dong thoi that su,
        // khong phai N request tuan tu nhanh.
        startLine.countDown();
        for (Future<?> future : futures) {
            future.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(INITIAL_STOCK, successCount.get(),
                "orders_success phai dung bang ton kho ban dau — bat bien orders_success <= STOCK");
        assertEquals(CONCURRENT_REQUESTS - INITIAL_STOCK, outOfStockCount.get(),
                "so request con lai phai nhan 409 OUT_OF_STOCK, khong phai loi khac");

        int finalStock = genCurrentStock(productId);
        assertEquals(0, finalStock, "Product.stock cuoi cung phai bang 0, KHONG BAO GIO am");
        assertTrue(finalStock >= 0, "Product.stock khong bao gio am — bang chung truc tiep chong oversell");
    }

    private CreateOrderCommand genOrderCommand(Long productId) {
        return new CreateOrderCommand()
                .setUserId(null)
                .setItems(List.of(new CartItemCommand()
                        .setProductId(productId).setName("San pham flash sale").setQuantity(1)))
                .setShipping(new ShippingInfoCommand()
                        .setFullName("Khach Flash Sale").setPhone("0900000000")
                        .setEmail("flash-sale-" + System.nanoTime() + "@vidu.vn")
                        .setProvince("HCM").setDistrict("Q1").setWard("P.Bến Nghé").setStreet("1 Lê Lợi"))
                .setPaymentMethod("cod");
    }

    private Product genSeedProduct() {
        Category category = categoryRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed data thieu category id=1 — kiem tra environment/mysql/init/02-seed-data.sql"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        return productRepository.save(new Product()
                .setSlug("flash-sale-test-" + System.nanoTime())
                .setName("San pham flash sale (test)")
                .setPrice(10_000L)
                .setUnit("cai")
                .setStock(INITIAL_STOCK)
                .setSold(0)
                .setRating(BigDecimal.ZERO)
                .setReviewCount(0)
                .setIsFeatured(false)
                .setIsBestSeller(false)
                .setIsActive(true)
                .setCategory(category)
                .setCreatedAt(now)
                .setUpdatedAt(now));
    }

    private int genCurrentStock(Long productId) throws Exception {
        String sql = "SELECT stock FROM product WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }
}
