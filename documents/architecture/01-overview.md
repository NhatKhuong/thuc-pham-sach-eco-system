# Architecture — overview

Tài liệu chuẩn kiến trúc cho **mọi dự án mới dùng chung stack và design system này**. Business có thể khác (đặt vé, đặt phòng, bán hàng, booking lịch…) nhưng khung module, chiều phụ thuộc, và các bất biến về đúng đắn thì giữ nguyên.

Bản tham chiếu là `xxxx.com` — hệ đặt vé flash sale (stock giới hạn, concurrency cao, không được oversell). Mọi ví dụ trong tài liệu này trích từ đó.

> **Tài liệu này là luật.** Đọc trước khi code. Khi ticket mâu thuẫn với tài liệu → **dừng và flag**, không tự hoà giải.
> Mục §11 liệt kê những chỗ dự án tham chiếu làm **sai** — đọc kỹ, đừng chép.

---

## 1. Shape

Maven multi-module. Chiều phụ thuộc là **bất biến**, được cưỡng chế bằng POM — không bao giờ thêm dependency đảo chiều:

```
<app>-start → <app>-controller → <app>-application → { <app>-domain, <app>-infrastructure }
                                                        <app>-infrastructure → <app>-domain
```

| Module | Base package | Chứa gì | Không được chứa |
|---|---|---|---|
| `*-start` | `com.<org>` | `StartApplication`, **`application.yml` duy nhất**, `logback-spring.xml` | Business logic |
| `*-controller` | `com.<org>.ddd.controller` | `http/` REST controller, `dto/` request, `model/vo/` envelope, `mapper/`, `config/`, `exception/` | Truy cập DB / Redis / Kafka trực tiếp |
| `*-application` | `com.<org>.ddd.application` | Use case (`service/<aggregate>/`), `cronjob/`, `model/{command,response,cache}`, `mapper/`, cache service, Kafka consumer | Native SQL, `RedisTemplate` trần |
| `*-domain` | `com.<org>.ddd.domain` | `model/entity/`, `repository/` (**interface/port**), `service/` + `service/impl/` | **Mọi phụ thuộc module khác**. Không Redis, không Kafka, không `*JPAMapper` |
| `*-infrastructure` | `com.<org>.ddd.infrastructure` | `persistence/{mapper,repository,dataobject}`, `cache/redis/`, `distributed/redisson/`, `mq/`, `gateway/`, `config/` | Use case nghiệp vụ |

Hai quy tắc phái sinh, vi phạm là hỏng mô hình:

- **`domain` khai báo port, `infrastructure` implement adapter.** `domain.repository.XxxRepository` ← `infrastructure.persistence.repository.XxxRepositoryImpl` (bọc `XxxJPAMapper` của Spring Data). Domain là độc lập, không bị phụ thuộc vào bất kỳ thành phần nào.
- **Chỉ `*-start` có `src/main/resources`.** Module khác không có file cấu hình riêng.

---

## 2. Stack chuẩn

| Thành phần | Version | Ghi chú                                                                            |
|---|--|------------------------------------------------------------------------------------|
| Java | **21** | `spring.threads.virtual.enabled: true` — virtual threads bật sẵn                   |
| Spring Boot | **3.3.5** | Import BOM `spring-boot-dependencies`, **không** dùng `spring-boot-starter-parent` |
| MySQL | 8.0 | HikariCP, `maximum-pool-size: 100`                                                 |
| Redis | 7.x | Lettuce cho data; **Redisson 3.17.1** riêng cho distributed lock                   |
| Kafka | 3.7 | KRaft mode, không Zookeeper                                                        |
| Guava | 32.1.2-jre | L1 cache in-process (`CacheBuilder`)                                               |
| Resilience4j | 2.1.0 | Circuit breaker + rate limiter                                                     |
| Actuator + micrometer-registry-prometheus | 1.13.6 |                                                                                    |
| logstash-logback-encoder | 8.0 | Log JSON → Logstash TCP                                                            |
| Spring Security |  | Xác thực và phân quyền theo RBAC                                                   |
| Swagger |  | Quản lý document cho API                                                           |
**Cố ý không có** — đừng thêm nếu không có lý do được duyệt:

- **MapStruct** → converter viết tay (`*Mapper` với method `public static`). Đổi lấy tính minh bạch khi debug.

---

## 3. Data

- **Schema-first.** `spring.jpa.hibernate.ddl-auto: update`. Nguồn chân lý duy nhất là `environment/mysql/init/*.sql`, áp dụng lần đầu khi container MySQL khởi tạo. Đổi schema = sửa file SQL + tạo lại volume.
- Đặt tên: bảng/cột `snake_case`, index `idx_<col>`, unique `uk_<col>`, `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`, mỗi cột có `COMMENT`.
- **Entity name có thể lệch table name** — khai `@Table(name = "...")` khi lệch, ví dụ entity `TicketDetail` → bảng `ticket_item`.

### Bảng theo tháng (tuỳ chọn cho bảng ghi tăng nhanh)

Với bảng chỉ ghi thêm và tăng nhanh (đơn hàng, log giao dịch), tách theo tháng `<entity>_yyyyMM`, tạo just-in-time:

```java
private static final Map<String, Boolean> tableCreatedCache = new ConcurrentHashMap<>();
// double-checked locking → CREATE TABLE IF NOT EXISTS chỉ chạy 1 lần / bảng / JVM
```

Ràng buộc bắt buộc khi dùng pattern này:

1. Tháng được **suy ngược từ mã đơn**, không lưu riêng → mã đơn phải giữ dạng `PREFIX-...-<System.currentTimeMillis()>`. Đổi format = mọi lệnh tra cứu / huỷ đơn gãy.
2. DDL trong MySQL là implicit-commit → `CREATE TABLE` bên trong `@Transactional` không rollback được. Chấp nhận, và **không** để `ensureTableExists` ném exception làm gãy luồng chính.
3. Template DDL và phần map `Object[]` phía đọc phải sửa **cùng lúc** — thứ tự cột là load-bearing.
4. Đọc phân trang bằng **keyset** (`WHERE id < :lastId ORDER BY id DESC LIMIT :limit`), không `OFFSET`.

---

## 4. Cache 3 tầng

Đường đọc cho dữ liệu tra cứu nóng (chi tiết sản phẩm, giá):

```
Guava local cache (process-local, expireAfterWrite 5 phút)
        ↓ miss
Redis (JSON, key <DOMAIN>:<ENTITY>:{id})
        ↓ miss
MySQL — bọc trong Redisson lock để chống cache stampede
```

- Lock quanh lần load DB: `tryLock(1, 5, TimeUnit.SECONDS)`; **double-check Redis bên trong lock**; `finally { unlock(); }` không có ngoại lệ.
- Cache mang theo `version` (`System.currentTimeMillis()`) để client hỏi có bản mới hơn không.
- **Số lượng tồn / counter KHÔNG đi qua cache đọc này.** Nó sống ở key riêng dạng số nguyên (§5).

### Namespace key chuẩn

| Loại | Dạng | Ví dụ |
|---|---|---|
| Data object | `<DOMAIN>:<ENTITY>:{id}` | `PRO_TICKET:ITEM:23` |
| Counter | `<DOMAIN>:{id}:<FIELD>` | `TICKET:23:STOCK` |
| Distributed lock | `LOCK:<ACTION>:{id}` | `LOCK:CANCEL_ORDER:{orderNumber}` |

Quy tắc: key **luôn** sinh qua một method `genXxxKey(...)`, không viết inline; dùng dấu `:` phân tách; **luôn đặt TTL**.

---

## 5. Bất biến về đúng đắn

Áp dụng cho **mọi tài nguyên có số lượng giới hạn** — vé, suất, slot, mã giảm giá, tồn kho.

> **Redis là cổng atomic. MySQL là lưới an toàn. Cache đã trừ thì phải hoàn.**

**Tầng 1 — cổng atomic bằng Lua** (một round trip, không race):

```lua
local stock = redis.call('GET', KEYS[1]);
if stock == false then return -1 end;          -- key chưa có → cold cache
stock = tonumber(stock);
if (stock >= tonumber(ARGV[1])) then
    redis.call('SET', KEYS[1], stock - tonumber(ARGV[1]));
    return 1;                                   -- trừ thành công
end;
return 0;                                       -- không đủ
```

Contract trả về: `-1` = miss (warm cache rồi retry **đúng một lần**), `0` = không đủ → trả `OUT_OF_STOCK`, `1` = đã trừ.

**Tầng 2 — conditional UPDATE trên MySQL** (chốt chặn cuối, không dùng `SELECT … FOR UPDATE`):

```sql
UPDATE ticket_item
   SET stock_available = stock_available - :quantity, updated_at = CURRENT_TIMESTAMP
 WHERE id = :ticketId AND stock_available >= :quantity
```

Rows-affected `> 0` là thành công. Repository đổi `int` → `boolean`, domain không thấy khái niệm rows-affected.

**Tầng 3 — SAGA compensation (BẮT BUỘC).** Ngay khi Lua trả `1`, cache **đã bị tiêu**. Mọi nhánh `return` sớm và mọi `catch` sau điểm đó phải hoàn lại:

```java
if (!ok) {
    stockOrderCacheService.increaseStockCache(ticketId, quantity); // LUA_RESTORE
    return PlaceOrderResponse.failed("STOCK_CONFLICT", "Đặt vé không thành công, vui lòng thử lại");
}
```

Thêm một nhánh lỗi mới sau cổng Lua mà quên compensate = rò rỉ tồn vĩnh viễn = **bán hụt**. Đây là cách dễ nhất để phá hệ thống này.

---

## 6. Hai luồng ghi chuẩn

Cùng dùng cổng Lua ở §5, khác nhau ở chỗ đặt ranh giới đồng bộ.

**Luồng A — đồng bộ (CAS).** Dùng khi tải vừa phải và client cần kết quả ngay.

```
1. Lua deduct (miss → warm + retry 1 lần)
2. Conditional UPDATE trên MySQL   → fail: compensate cache, trả STOCK_CONFLICT
3. Resolve giá                     → fail: compensate cache, trả PRICE_NOT_FOUND
4. Sinh mã đơn: PREFIX-<terminal>-<userId>-<seq>-<millis>
5. INSERT vào bảng tháng           → trả success + mã đơn
```

Method này **cố ý không `@Transactional`** — nhất quán do compensation lo, không do transaction.

**Luồng B — bất đồng bộ (Outbox + MQ).** Dùng cho flash sale thật: trả token ngay, không giữ connection.

```
POST /order/mq
  | Lua deduct (reject sớm nếu hết)
  | token = "MQ-" + 16 hex UUID
  v
TransactionTemplate {            <- 1 transaction, 2 write
    INSERT <business>_queue (token, status=0)
    INSERT outbox_event      (aggregate_id=token, event_type, payload=JSON, status=0)
}                                -> trả token cho client NGAY
  v
@Scheduled(fixedDelay=1000) OutboxPublisherJob
    SELECT ... WHERE status=0 ORDER BY created_at LIMIT 500
    send Kafka -> chờ broker ACK -> UPDATE status=1
  v
@KafkaListener(concurrency=N) + @Transactional(rollbackFor = Exception.class)
    INSERT IGNORE idempotency_key(token)   <- affected=0 => duplicate, skip
    conditional UPDATE stock               <- fail => compensate cache + queue status=2
    INSERT business row
    UPDATE <business>_queue SET status=1 (hoặc 2)
  v
GET /order/mq/status/{token}     <- client polling: 0=PENDING, 1=SUCCESS, 2=FAILED
```

Bốn quy tắc không được phá:

1. **Outbox row và business row viết trong cùng transaction.** Không bao giờ gọi Kafka trực tiếp trong request.
2. **`INSERT IGNORE idempotency_key` nằm BÊN TRONG transaction của consumer.** Rollback thì key cũng rollback → Kafka redeliver xử lý lại được. Đưa nó ra ngoài = âm thầm mất đơn.
3. **`TransactionTemplate`, không `@Transactional`**, khi write nằm trong self-call / lambda — self-call không đi qua Spring AOP proxy.
4. Publisher chỉ `UPDATE status=1` **sau khi** broker ACK. Fail thì để nguyên `status=0`, chu kỳ sau retry.

---

## 7. Response envelope

Mọi controller trả `ResultMessage<T>` dựng qua `ResultUtil.data(...)` / `.success()` / `.error(...)`:

```json
{ "success": true, "message": "success", "code": 200, "timestamp": 1766000000000, "result": {} }
```

- Payload nằm ở field **`result`** — frontend đọc `response.data.result`.
- **Thất bại nghiệp vụ = HTTP 200 + `success=false`**, kèm `code` dạng UPPER_SNAKE (`OUT_OF_STOCK`, `TICKET_NOT_FOUND`, `STOCK_CONFLICT`, `PRICE_NOT_FOUND`, `SERVER_ERROR`) và `message` tiếng Việt cho người dùng.
- Chỉ lỗi hạ tầng thật mới trả 5xx. Nhờ vậy threshold `http_req_failed` của k6 chỉ bắt lỗi server thật, không bắt "hết hàng".

---

## 8. Config

Hiện trạng dự án tham chiếu: **một** `application.yml`, không profile, mọi host hardcode localhost (MySQL `3316`, Redis `6319`, Kafka `9094`, app `1122`).

**Chuẩn cho dự án mới — bắt buộc:**

- Tách `application-dev.yml` / `application-prod.yml`; host, credential, CORS origin đọc từ biến môi trường.
- **Không secret trong source.** (Dự án tham chiếu có secret key cổng thanh toán viết thẳng trong `VnPayGatewayServiceImpl` — không lặp lại.)
- Cấu hình client hạ tầng đọc từ cùng một nguồn. Redisson phải lấy address từ `spring.data.redis.*`, không tự khai địa chỉ riêng.

Giá trị đã tinh chỉnh, giữ nguyên trừ khi có số đo phản bác: Tomcat `accept-count: 2000` / `max-connections: 10000`, Hikari pool 100, Kafka consumer `auto-offset-reset: earliest`.

---

## 9. Observability

- Actuator expose toàn bộ; Prometheus scrape `/actuator/prometheus` mỗi 5s.
- Histogram + SLO buckets (100ms / 500ms / 1s / 2s / 5s) cho `http.server.requests`; percentile p50/p95/p99. Thêm `hikaricp.connections.acquire` — với luồng đồng bộ, **DB pool mới là trần thông lượng**, không phải request pool.
- Log JSON → Logstash TCP `5044` qua `AsyncAppender` (`neverBlock=true`, `queueSize=512`) → ELK chết thì app vẫn chạy.
- **Bắt buộc bổ sung ở dự án mới:** một filter set `MDC traceId`. Pattern logback đã in `%X{traceId}` nhưng chưa có ai set → hiện luôn rỗng.

---

## 10. Kiểm chứng

Dự án tham chiếu **không có unit test** (`src/test` rỗng, không module nào khai `spring-boot-starter-test`). Verify bằng k6 (`benchmark/k6/flash-sale.js`, assert oversell: `orders_success <= STOCK`), JMeter, và `/actuator`.

**Chuẩn cho dự án mới:** thêm `spring-boot-starter-test` ngay từ module đầu; test bắt buộc cho luồng đặt / ghi và cho compensation; **giữ k6** làm cổng kiểm bất biến "không bao giờ vượt quá tồn" — đó là thứ unit test không bắt được.

---

## 11. Nợ kỹ thuật — đã biết, đừng chép

| Vấn đề ở dự án tham chiếu | Làm đúng ở dự án mới |
|---|---|
| Package `domain.respository` sai chính tả | Dùng `domain.repository` |
| `spring-boot-maven-plugin` 3.4.0 lệch BOM 3.3.5 | Đồng bộ một version |
| Hai namespace validation cùng classpath (`javax` + `jakarta`) | Chỉ `jakarta.validation` |
| Redisson hardcode address, tách rời `spring.data.redis` | Một nguồn cấu hình |
| `@KafkaListener(concurrency="10")` trên topic 3 partition | `concurrency` ≤ số partition |
| Không có DLT / retry policy — mất bản ghi sau 10 lần thử | Cấu hình `DefaultErrorHandler` + DLT |
| Cache Redis không đặt TTL | Luôn `set` kèm TTL |
| Không có `@RestControllerAdvice` — lỗi ngoài dự kiến trả body sai envelope | Bắt buộc có (xem `coding-conventions.md` §11) |
| Nhiều khối code comment-out 20-45 dòng | Xoá; git giữ lịch sử |
| Không có test | Xem §10 |

---

*Đọc tiếp: [`coding-conventions.md`](coding-conventions.md) — quy ước viết code cưỡng chế các nguyên tắc trên.*

*Last updated: 2026-08-20 — cập nhật stamp này trong cùng lần sửa nội dung.*
