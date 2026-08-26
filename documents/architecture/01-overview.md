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
| Resilience4j | **2.1.0** | **Đã nối dây** ([ADR 0005](../../../../management/decisions/0005-lop-bao-ve-resilience4j.md), backlog 0021): `resilience4j-ratelimiter` ở `*-controller` — biên **vào**, ba tier `auth`/`write`/`read`, vượt trần trả 429; `resilience4j-circuitbreaker` ở `*-application` — biên **ra**, bọc đường gửi SMTP. Version pin bằng **import `resilience4j-bom`** ở root pom ⇒ đúng **một** `<version>` (`pom.xml:94`) cho cả dòng. **Không** dùng `resilience4j-spring-boot3` (starter đó kéo theo AOP + Actuator) |
| Actuator + micrometer-registry-prometheus | 1.13.6 |                                                                                    |
| logstash-logback-encoder | 8.0 | Log JSON → Logstash TCP                                                            |
| Spring Security |  | Xác thực và phân quyền theo RBAC                                                   |
| Swagger | **2.6.0** | `springdoc-openapi-starter-webmvc-ui`, khai ở `*-controller`; BOM không quản version nên pin ở root pom |
| Gửi mail (SMTP) |  | `spring-boot-starter-mail`, khai ở `*-application`. Cấu hình qua `spring.mail.*` theo nếp `${ENV_VAR:dev-default}`; **credential SMTP là bí mật thứ hai của hệ thống sau `jwt-secret`**. Dev trỏ sẵn vào SMTP catcher trong `environment/docker-compose-dev.yml` (Mailpit, SMTP 1025, giao diện web 8025) — bắt mọi thư và **không chuyển tiếp đi đâu**, nên không có nguy cơ gửi nhầm mail thật lúc kiểm thử. Gửi chạy `@Async` (ADR 0004) |

**Cố ý không có** — đừng thêm nếu không có lý do được duyệt:

- **MapStruct** → converter viết tay (`*Mapper` với method `public static`). Đổi lấy tính minh bạch khi debug.

**Có tên ở bảng trên nhưng CHƯA nối dây.** Đo trên source, không phỏng đoán — đếm **khai báo
`<artifactId>` thật**, không đếm chuỗi xuất hiện trong file:

```bash
# Control duong chay TRUOC — phai ra khac 0. Chay TRUOC chu khong sau, vi `grep -c` tra 0 thi DONG
# THOI tra exit 1: du de lam dut mot chuoi `&&` va nuot mat chinh control dung sau no, trong khi con
# so am van in ra va doc nhu mot pass sach.
grep -rhoi "<artifactId>[^<]*mysql[^<]*</artifactId>" --include=pom.xml . | wc -l   # 1
grep -rhoi "<artifactId>[^<]*mail[^<]*</artifactId>"  --include=pom.xml . | wc -l   # 1
# Dem dependency THAT, khong dinh vao comment
for t in kafka guava actuator micrometer logstash resilience redis lettuce redisson; do
  echo "$t: $(grep -rhoi "<artifactId>[^<]*$t[^<]*</artifactId>" --include=pom.xml . | wc -l)"
done
```

Đo ngày **2026-08-26** — `kafka 0`, `guava 0`, `actuator 0`, `micrometer 0`, `logstash 0`,
**`resilience 3`**, `redis 0`, `lettuce 0`, `redisson 0`; control dương `mysql 1`, `mail 1` ⇒ **tám
trên chín ra 0**. Còn chưa nối dây: Redis, Lettuce, Redisson, Kafka, Guava, Actuator,
`micrometer-registry-*`, logstash-logback-encoder. **Resilience4j đã rời danh sách này** — 3 khai báo
ở đúng 3 file: `pom.xml:93` (BOM, chỉ pin version), `nss-controller/pom.xml:59` (`ratelimiter`),
`nss-application/pom.xml:83` (`circuitbreaker`).

> **Chữ `registry` trong "micrometer" là load-bearing — đừng rút gọn thành "không có micrometer".**
> Jar đóng gói **có** mang `micrometer-observation-1.13.6` + `micrometer-commons-1.13.6`; nguồn là
> `spring-security-core:6.3.4`, **không** phải Resilience4j (hai jar đó cũng có mặt ở những module
> không hề có Resilience4j). Câu đúng là **`micrometer-registry-*` = 0** và `spring-boot-actuator`
> = 0 — tức đường xuất metric ra ngoài chưa được nối, chứ không phải cả họ micrometer vắng mặt. Đo
> ngày 2026-08-26 trên `nss-start/target/nss-start-*.jar` bằng `unzip -l | grep -c`: control dương
> `BOOT-INF/lib/spring-security` → **7**; `BOOT-INF/lib/micrometer-registry` → **0**;
> `BOOT-INF/lib/spring-boot-actuator` → **0**.

**Phép đo tương ứng trên `application.yml` phải bỏ comment TRƯỚC khi đếm.** Bản cũ của tài liệu này
grep chuỗi trần trên cả file và khẳng định `redis` / `resilience` / `kafka` đều **0 hit**. Phép đo đó
**vừa tự hỏng lần thứ hai**, ngay trong backlog 0021 và đúng cùng cơ chế mô tả ở khung dưới: đo trần
hôm nay ra `redis` **1** (`application.yml:126` — một *comment* ghi rằng khi cần bể đếm dùng chung thì
Redis mới là câu trả lời; **không** có dependency lẫn khoá cấu hình Redis nào) và `resilience` **2**
(comment `:110`, `:122`). Sửa phép đo, không sửa con số — đếm **khoá cấu hình thật**:

```bash
Y=nss-start/src/main/resources/application.yml
# Control duong chay TRUOC — phai ra khac 0
for t in mysql mail; do echo "$t: $(sed 's/#.*$//' $Y | grep -ci "$t" || true)"; done
# Phep kiem am — dem tren noi dung DA BO COMMENT
for t in redis resilience kafka; do echo "$t: $(sed 's/#.*$//' $Y | grep -ci "$t" || true)"; done
```

Đo ngày 2026-08-26: `redis 0 · resilience 0 · kafka 0`, control dương `mysql 3 · mail 15`. **Số 0 ở
đây nói đúng thứ nó định nói:** không có khoá `spring.data.redis.*`, không có khoá `resilience4j.*`.
Ngưỡng của lớp bảo vệ sống dưới namespace của chính dự án (`nss.rate-limit.*`,
`nss.mail.circuit-breaker.*`) và được đọc bằng `@Value` — Resilience4j ở đây dùng **API lập trình**,
không qua autoconfiguration, nên nối dây nó **không** sinh ra khoá `resilience4j.*` nào.

> **Vì sao phải đếm `<artifactId>` chứ không `grep` chuỗi trần:** phép đo cũ trong tài liệu này đếm
> chuỗi, và nó **tự hỏng ngay trong ticket đầu tiên viết một comment nhắc tên Redis** —
> `nss-application/pom.xml` giải thích vì sao *không* dùng Resilience4j, thế là `grep -ril resilience`
> ra 1 file và con số "0 hit" thành sai trong khi không có dependency nào được thêm. Một phép đo mà
> việc *ghi lại lý do* làm nó sai thì nó đang phạt đúng thứ đáng khuyến khích.
>
> **Lần thứ hai, 2026-08-26:** phép đo trên pom đã được sửa sang `<artifactId>`, nhưng phép đo trên
> `application.yml` **bị bỏ lại ở dạng grep trần** — và nó hỏng lại y hệt khi backlog 0021 viết comment
> giải thích cả Redis lẫn Resilience4j. Bài học: **sửa một phép đo hỏng thì phải sửa mọi phép đo cùng
> cơ chế trong cùng lần**, nếu không là hẹn nó hỏng lần thứ ba.

Phân biệt "có tên nhưng chưa nối dây" với "không có tên" là **load-bearing, đừng xoá cho gọn**: một
thành phần *có tên* trong bảng thì nối nó lên là **thi hành tài liệu**; một thành phần *không có
tên* ở bất kỳ dạng nào thì thêm nó là **mở rộng stack** và phải cập nhật bảng này trong cùng ticket.
Mail vừa đi qua đúng con đường thứ hai ở backlog 0017 — đó là lý do dòng "Gửi mail (SMTP)" ra đời.

**Cái giá của Resilience4j đã được trả — và trả ở đúng một chỗ.** Cả Redis lẫn Resilience4j đều **nằm
ngoài BOM `spring-boot-dependencies` 3.3.5**, nên nối dây chúng là thêm một `<version>` phải tự pin và
tự theo dõi tương thích — đúng trục bảo trì mà ADR 0003 đã từ chối một lần khi loại `jjwt`. Backlog
0021 chấp nhận cái giá đó **có điều kiện**, và trả nó bằng cách **import `resilience4j-bom`**: hai
artifact đang dùng (`ratelimiter` ở biên vào, `circuitbreaker` ở biên ra) dùng chung
`resilience4j-core`, nhưng vẫn chỉ có **đúng một** thẻ `<version>` (`pom.xml:94`) và **đúng một**
property (`pom.xml:39`) cho cả dòng — không có chỗ thứ hai để lệch khi nâng version, và không có hai
artifact cùng dòng lệch nhau ở chỗ không ai nhìn. Lý do đầy đủ, các phương án đã loại, ba thứ phải
canh chừng, và **điều kiện đảo ngược** mà chính backlog 0017 tự viết ra:
[ADR 0005](../../../../management/decisions/0005-lop-bao-ve-resilience4j.md) — đọc trước khi đụng vào
lớp này.

**Redis thì vẫn chưa, và lý do cũ vẫn nguyên giá trị.** Cả `ForgotPasswordRateLimiter` lẫn ba tier
RateLimiter đều đếm **trong bộ nhớ của một tiến trình**: chạy N instance thì trần thật gấp N lần, và
mỗi lần khởi động lại là một lần xoá sạch. Khi cần một bể đếm **dùng chung giữa các instance** thì
Redis mới là câu trả lời đúng — không phải chỉnh con số, và **cũng không phải Resilience4j**, vì
`RateLimiter` của nó cũng chỉ là một bể permit process-local. "Có dựng Redis không, để làm gì" vẫn là
một câu hỏi đang mở.

**`ForgotPasswordRateLimiter` vẫn tự viết, nhưng lý do đã đổi.** Không còn là "R4j quá đắt cho một
`Map` đếm số" — R4j nay đã ở trong classpath và không tốn thêm gì. Lý do thật là **hai khoá**: lớp đó
khoá theo **IP** *và* theo **email đích**, còn `RateLimiter` của Resilience4j là một bể permit gắn với
*một tên instance*, không phải map theo khoá. Hai lớp giải hai bài toán khác nhau và **cả hai đều ở
lại** — lớp global chống **quá tải** (không khoá, trần theo nhóm endpoint), lớp cũ chống **lạm dụng**
(dò tài khoản, phát tán thư rác). Chi tiết ở javadoc `ForgotPasswordRateLimiter`.

**Timeout SMTP là điều kiện tiên quyết của breaker — và nó đã có sẵn từ trước.**
`application.yml:58-60` đặt cả ba `spring.mail.properties.mail.smtp.{connectiontimeout, timeout,
writetimeout}` = **5000ms** (backlog 0017 thêm, kèm comment giải thích ở `:56-57`). **Một giá trị nằm trong YAML không tự chứng minh nó
tới được JavaMail**: sai key, sai chỗ lồng, hay sai kiểu đều làm nó bị bỏ qua *im lặng* và mặc định vô
hạn quay lại — không có timeout cắn thật thì breaker chẳng đếm được gì, vì mỗi lần gửi treo mãi thay
vì thất bại. Backlog 0021 Phase 2 đo bằng một SMTP **hố đen** (cổng accept nhưng không nói gì — khác
cổng đóng, thứ fail nhanh và không kiểm được gì): một lần `send()` thất bại ở **5103ms**, chuỗi nguyên
nhân `MailSendException` ← `jakarta.mail.MessagingException` ← `java.net.SocketTimeoutException`.

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

Hiện trạng dự án tham chiếu: **một** `application.yml`, không profile, mọi host hardcode localhost (MySQL `3316`, Redis `6319`, Kafka `9094`, app `1122`). Giá trị Tomcat / Kafka đã tinh chỉnh của **nó** — `accept-count: 2000`, `max-connections: 10000`, consumer `auto-offset-reset: earliest`.

**Dự án này không có khoá nào trong ba khoá đó** — đừng chép chúng sang chỉ vì dòng trên đọc như một chỉ dẫn. Đo ngày **2026-08-26** trên `nss-start/src/main/resources/application.yml`, **bỏ comment trước khi đếm** (cùng phép đo §2: `sed 's/#.*$//' $Y | grep -ci "$t"`): `accept-count` **0** · `max-connections` **0** · `auto-offset-reset` **0**; control dương chạy **TRƯỚC** — `maximum-pool-size` **1**, `port` **2**. `kafka` cũng **0** (§2), nên chưa có consumer nào để mà đặt `auto-offset-reset`; server chạy Tomcat mặc định của Spring Boot, không khoá `server.tomcat.*` nào.

**Chuẩn cho dự án mới — bắt buộc:**

- Tách `application-dev.yml` / `application-prod.yml`; host, credential, CORS origin đọc từ biến môi trường.
- **Không secret trong source.** (Dự án tham chiếu có secret key cổng thanh toán viết thẳng trong `VnPayGatewayServiceImpl` — không lặp lại.)
- Cấu hình client hạ tầng đọc từ cùng một nguồn. Redisson phải lấy address từ `spring.data.redis.*`, không tự khai địa chỉ riêng.
- **Hikari `maximum-pool-size: 100` — giá trị đã tinh chỉnh, giữ nguyên trừ khi có số đo phản bác.** Đây là khoá **duy nhất** trong bốn khoá của dòng cũ thật sự tồn tại ở dự án này (`application.yml:29`), nên nó ở vế chuẩn chứ không ở vế hiện trạng dự án tham chiếu. Dưới virtual threads nó mới đúng là chỗ hàng đợi thật sự hình thành — **§9 nói đúng cùng điều này**. Con số `100` xuất hiện ở **ba** chỗ trong tài liệu (bảng §2, dòng này, §9) và ở `application.yml:29`; đổi nó thì phải đổi cả bốn trong cùng một lần.

---

## 9. Observability

Hiện trạng dự án tham chiếu — bốn gạch đầu dòng dưới đây mô tả **nó**, không phải dự án này:

- Actuator expose toàn bộ; Prometheus scrape `/actuator/prometheus` mỗi 5s.
- Histogram + SLO buckets (100ms / 500ms / 1s / 2s / 5s) cho `http.server.requests`; percentile p50/p95/p99, kèm `hikaricp.connections.acquire`.
- Log JSON → Logstash TCP `5044` qua `AsyncAppender` (`neverBlock=true`, `queueSize=512`) → ELK chết thì app vẫn chạy.
- Pattern logback của nó **đã in** `%X{traceId}`, nhưng **không ai set MDC** → trường đó in ra luôn rỗng.

**Dự án này chưa nối dây thứ nào ở trên** — đừng đọc mấy dòng trên như hiện trạng chỉ vì chúng nói bằng chi tiết vận hành. §2 đo trên source ngày **2026-08-26**, đếm khai báo `<artifactId>` thật: `actuator` **0** · `micrometer` **0** · `logstash` **0** (control dương `mysql` 1, `mail` 1). Actuator, `micrometer-registry-prometheus` và `logstash-logback-encoder` **có tên trong bảng §2 nhưng chưa nối dây**; `*-start` cũng **chưa có `logback-spring.xml`** nào. Nối chúng lên là **thi hành tài liệu**, nhưng nó mở một public surface mới phải khoá RBAC và khai trong `SecurityConfig` + Swagger — [ADR 0005](../../../../management/decisions/0005-lop-bao-ve-resilience4j.md) đã cố ý loại việc đó ra khỏi backlog 0021, nên nó là **một ticket riêng**, không phải việc làm kèm.

**Chuẩn cho dự án mới — bắt buộc:**

- **Một filter set `MDC traceId`, đi kèm pattern logback in `%X{traceId}`.** Hai nửa phải có cùng lúc: dự án tham chiếu có nửa sau mà thiếu nửa đầu nên trường đó luôn rỗng; dự án này thì chưa có nửa nào.
- **Đo `hikaricp.connections.acquire`.** Đây là nhận định kỹ thuật **chung**, không phải một chi tiết riêng của dự án tham chiếu: khi lời gọi DB là lời gọi **chặn**, trần thông lượng nằm ở **pool DB**, không phải ở pool thread nhận request. **Virtual threads làm nhận định này đúng hơn chứ không phải hết hiệu lực** — dự án này bật `spring.threads.virtual.enabled: true` (§2; `application.yml:11`), tức thread nhận request gần như không còn là giới hạn, nên `maximum-pool-size: 100` (`application.yml:29`) mới đúng là chỗ hàng đợi thật sự hình thành. Không đo nó thì p99 dâng lên mà không có số nào chỉ ra vì sao.

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

*Last updated: 2026-08-26 — cập nhật stamp này trong cùng lần sửa nội dung.*
