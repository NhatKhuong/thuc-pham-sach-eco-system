# Coding conventions

Quy ước bắt buộc cho mọi dự án dùng stack ở [`architecture/01-overview.m`](overview.md). **Đây là luật, không phải gợi ý** — khi ticket mâu thuẫn với tài liệu này thì **dừng và flag**, không tự hoà giải.

Ví dụ trích từ dự án tham chiếu `xxxx.com`. Chỗ nào dự án tham chiếu làm sai, tài liệu ghi rõ **"đừng chép"** và nêu cách đúng.

---

## 1. Ngôn ngữ

| Thành phần | Ngôn ngữ |
|---|---|
| Comment, javadoc | **Tiếng Việt** |
| Message nghiệp vụ trả cho người dùng | **Tiếng Việt** (`"Hết vé, vui lòng thử lại sau"`) |
| Identifier (class, method, biến) | Tiếng Anh |
| Log message | Tiếng Anh |
| Validation message (`@NotNull(message=...)`) | Tiếng Anh |
| Business error code | Tiếng Anh UPPER_SNAKE (`OUT_OF_STOCK`) |

---

## 2. Package layout

| Module | Sub-package bắt buộc |
|---|---|
| `*-controller` | `http/` · `dto/` · `model/vo/` · `model/enums/` · `mapper/` · `config/` · `exception/` |
| `*-application` | `service/<aggregate>/` + `/impl/` + `/cache/` + `/mq/` · `model/` · `model/command/` · `model/response/` · `model/cache/` · `mapper/` · `cronjob/` |
| `*-domain` | `model/entity/` · `repository/` · `service/` + `service/impl/` |
| `*-infrastructure` | `persistence/{mapper,repository,dataobject}/` · `cache/redis/` · `distributed/redisson/{config,impl}/` · `mq/` · `gateway/` · `config/` |

- Domain service và app service: interface ở `service/<aggregate>/`, impl ở `service/<aggregate>/impl/`.
- **Dự án mới dùng `repository` đúng chính tả.** Dự án tham chiếu viết `respository` — nếu sửa thì sửa toàn repo một lần, tuyệt đối không để hai cách viết cùng tồn tại.

---

## 3. Hậu tố class

| Hậu tố | Ở đâu | Ví dụ |
|---|---|---|
| `*AppService` / `*AppServiceImpl` | application | `TicketOrderAppService`, `OrderMQAppServiceImpl` |
| `*DomainService` / `*DomainServiceImpl` | domain | `TickerOrderDomainService` |
| `*Repository` (**port**) | domain `repository/` | `IdempotencyKeyRepository` |
| `*RepositoryImpl` (**adapter**) | infrastructure | `IdempotencyKeyRepositoryImpl` |
| `*JPAMapper` (Spring Data interface) | infrastructure `persistence/mapper/` | `OutboxEventJPAMapper` — `JPA` viết hoa, field theo tên class: `outboxEventJPAMapper` |
| `*DTO` | application `model/` | `TicketOrderDTO` |
| `*Request` (**không** `*Req`) | controller `dto/` | `CreateBookingRequest` |
| `*Command` | application `model/command/` | `CreateTicketCommand` |
| `*Response` (**không** `*Resp`) | application `model/response/` | `PlaceOrderResponse` |
| `*Mapper` (converter viết tay) | application/controller `mapper/` | `TicketMapper`, `TicketControllerMapper` |
| `*Controller` | controller `http/` | `OrderMQController` |
| `*Job` | application `cronjob/` | `OutboxPublisherJob` |
| `*Consumer` / `*Producer` | application `.../mq/`, infrastructure `mq/` | `KafkaOrderConsumer` |
| `*Config` | `config/` | `KafkaTopicConfig` |
| `*CacheService` | application `.../cache/` | `StockOrderCacheService` |
| `*Message` | infrastructure `mq/` | `PlaceOrderMQMessage` |
| `*DO` | infrastructure `persistence/dataobject/` | `PaymentTransactionDO` — chỉ khi row shape lệch entity |
| `*Exception` | controller `exception/` | `InvalidSignatureException` |

- **Entity là danh từ trần**, không hậu tố: `Ticket`, `OrderQueue`, `OutboxEvent`.
- **Cấm biến thể `*InfrasRepositoryImpl`** (dự án tham chiếu có `OrderDeductionInfrasRepositoryImpl`, `HiInfrasRepositoryImpl` — đừng chép). Một hậu tố cho một vai trò.
- Stereotype: `*RepositoryImpl` dùng **`@Repository`**, service dùng `@Service`, job/consumer dùng `@Component`. Dự án tham chiếu đặt `@Service` lên 8/10 repository impl — sai, đừng chép.

---

## 4. Đặt tên method

- Đọc: `find*` cho repository / kết quả nullable / collection (`findByToken`, `findPage`); `get*` cho scalar, accessor, cache read (`getStockAvailable`, `getEffectivePrice`). **Không dùng `query*`.**
- Ghi: `save` (JPA), `insert*` (native), `update*`, `delete*`, `markPublished`.
- Boolean: biến local `is*` / `has*` (`isRedisDecremented`, `isLock`), method `hasSignedIn`, `tryInsert`.
- Private helper: `gen*` (sinh key), `ensure*`, `extract*`, `to*` (convert), `failed*` (dựng response lỗi).
- Controller: động từ trước, phản chiếu URL (`placeOrderCAS`, `getListOrderByUserPaged`, `activeTicket`).

---

## 5. Lombok & dependency injection

**Lombok được dùng:**

```java
@Data                       // mọi data class (entity, DTO, command, request, VO, message)
@Accessors(chain = true)    // khi cần dựng fluent: new OrderQueue().setToken(t).setStatus(0)
@NoArgsConstructor
@AllArgsConstructor         // luôn đi cặp, đặt sau @Data
@Slf4j                      // mọi class có log
@RequiredArgsConstructor    // DI — xem dưới
```

**Cấm:** `@Builder`, `@Getter` / `@Setter` lẻ, `@EqualsAndHashCode` tuỳ biến.

Thứ tự annotation trên entity:

```java
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox_event", indexes = {@Index(name = "idx_status_created", columnList = "status, created_at")})
public class OutboxEvent { ... }
```

**Dependency injection: constructor injection, không ngoại lệ.**

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderConsumer {
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TickerOrderDomainService tickerOrderDomainService;
    private final StockOrderCacheService stockOrderCacheService;
}
```

Cấm luôn: `@Resource`, khai `@Autowired` ở giữa thân class, dùng type fully-qualified thay vì import.

---

## 6. JPA entity

- Entity ở `domain/model/entity/`; module domain là nơi duy nhất khai `spring-boot-starter-data-jpa` phía domain.
- `@Table(name = ...)` **chỉ khi** tên bảng lệch tên class. Trùng thì bỏ.
- Id: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`. Natural key thì `@Id` trần, không `@GeneratedValue`:
  ```java
  @Id
  @Column(length = 64, nullable = false)
  private String token;
  ```
- `@Column` **chỉ để khai ràng buộc** (`nullable`, `unique`, `length`, `columnDefinition`), **không** dùng để đặt `name=` — dựa vào naming strategy camelCase → snake_case. Ngoại lệ duy nhất: class `*DO` map tường minh từng cột.
- **Thời gian: `LocalDateTime`.
- `createdAt` / `updatedAt` set **thủ công trong domain service** (`ticket.setCreatedAt(LocalDateTime.now())`); không dùng JPA auditing, không `@MappedSuperclass`.
- **Không `@Version`.** Đồng thời (concurrency) xử lý bằng conditional UPDATE + Lua CAS, xem `overview.md` §5.
- Trạng thái nghiệp vụ lưu `int` + chú thích ngay trên field: `// 0=PENDING, 1=SUCCESS, 2=FAILED`.

---

## 7. DTO / Command / Mapper

Luồng chuyển đổi ba tầng, mỗi ranh giới một kiểu:

```
HTTP JSON -> controller/dto/*Request
          -> (*ControllerMapper) -> application/model/command/*Command
          -> (*Mapper.toEntity)  -> domain/model/entity/*
          -> (*Mapper.toDTO)     -> application/model/*DTO
          -> ResultMessage<T>
```

- `*Request`: `@Data` trần, validation bằng **`jakarta.validation`** (cấm `javax.validation`), date có `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`, request lồng nhau đánh `@Valid` trên field. Controller luôn `@Valid @RequestBody`.
- `*Mapper` là class **stateless, method `public static`, không phải Spring bean**, luôn null-guard:
  ```java
  public static TicketDTO toDTO(Ticket ticket) {
      if (ticket == null) return null;
      ...
  }
  ```
- `*Response` nghiệp vụ dùng **static factory**, không new trực tiếp ở call site:
  ```java
  public static PlaceOrderResponse failed(String code, String message) {
      return new PlaceOrderResponse().setSuccess(false).setCode(code).setMessage(message);
  }
  ```
- Cấm `BeanUtils.copyProperties` để thay mapper. Map `Object[]` theo vị trí chỉ được dùng cho native query, và phải có comment index ngay cạnh.

---

## 8. Transaction

1. **Chỉ import `org.springframework.transaction.annotation.Transactional`.
2. `@Transactional(rollbackFor = Exception.class)` cho mọi write path nghiệp vụ (consumer xử lý đơn, huỷ đơn). `@Transactional` trần cho write đơn lẻ.
3. **`TransactionTemplate` khi write nằm trong lambda hoặc self-call** — kèm comment giải thích lý do:
   ```java
   // TransactionTemplate để wrap 2 write trong 1 transaction mà không cần @Transactional
   // (self-call trong cùng class không đi qua Spring AOP proxy)
   OrderQueue queue = transactionTemplate.execute(txStatus -> {
       orderQueueRepository.save(q);
       outboxEventRepository.save(outboxEvent);
       return q;
   });
   ```
4. **Idempotency key phải nằm trong cùng transaction với business data.** Rollback thì key rollback theo, redelivery mới xử lý lại được.
5. Không dùng `propagation` / `isolation` / `readOnly` nếu không có lý do viết ra được trong comment.
6. Nhất quán phân tán bằng **SAGA compensation**, không XA.

---

## 9. Logging

- `@Slf4j` duy nhất. Cấm `LoggerFactory` thủ công, cấm `System.out.println`.
- **Luôn dùng `{}` placeholder**, không nối chuỗi. Exception là **tham số cuối, không có placeholder tương ứng**:
  ```java
  log.error("placeOrderCAS: error for ticketId={}", ticketId, e);
  ```
- Format thống nhất theo tầng:

  | Tầng | Format | Ví dụ |
    |---|---|---|
  | Controller | `ClassName:->method \| key={}` | `log.info("OrderMQController:->placeOrderMQ | ticketId={} qty={}", ...)` |
  | Application | `method: trạng thái \| key={}` | `log.info("placeOrderCAS: success | orderNumber={}", ...)` |
  | Luồng async | `[TAG] action key={}` | `log.info("[IDEMPOTENCY] Duplicate skip token={}", token)` |

- Mức log: `info` cho mốc luồng chính, `warn` cho thất bại nghiệp vụ dự kiến được, `error` cho ngoại lệ ngoài dự kiến (luôn kèm object exception), `debug` cho chi tiết chẩn đoán.
- Mỗi luồng bất đồng bộ phải log đủ để lần ngược: token / mã đơn ở mọi bước.

---

## 10. Comment & javadoc

- Javadoc cấp class cho mọi class có logic — nói **vai trò trong DDD** và ràng buộc, không mô tả lại chữ ký:
  ```java
  /**
   * INSERT IGNORE — cổng idempotency atomic.
   * @return true  -> token mới, tiếp tục xử lý
   *         false -> duplicate (Kafka retry / rebalance), skip
   */
  ```
- Javadoc field của entity ghi cột tương ứng: `Tương ứng với cột \`stock_available\`.`
- Method dài: đánh số bước `// 1. …`, `// 2. …` theo đúng thứ tự thực thi.
- Chia nhóm method bằng banner: `// ========== CACHE METHODS ==========`.
- Chỗ nào đánh đổi thiết kế thì viết block comment nêu **+ / −** như `OutboxPublisherJob.publishRowByRow`. Đây là style tốt nhất trong repo, nhân rộng.
- Tag javadoc: `@param` / `@return` / `@throws`. **Không** `@author`, `@date`, `@version` — git giữ những thông tin đó.
- **Cấm để lại code comment-out.** Dự án tham chiếu có nhiều khối 20-45 dòng bị comment (`TicketOrderAppServiceImpl`, `RedisConfig`, `PaymentAppServiceImpl`) — xoá, git giữ lịch sử.

---

## 11. Error handling

**Pattern A — thất bại nghiệp vụ là giá trị trả về, không phải exception.** HTTP 200, `success=false`, code UPPER_SNAKE, message tiếng Việt:

```java
return PlaceOrderResponse.failed("OUT_OF_STOCK", "Hết vé, vui lòng thử lại sau");
```

**Pattern B — `@RestControllerAdvice` là bắt buộc ở dự án mới.** Dự án tham chiếu **không có** global handler → mọi lỗi ngoài dự kiến trả body `{timestamp, status, error, path}` không khớp `ResultMessage`, và frontend vỡ khi parse. Handler tối thiểu phải phủ:

| Exception | HTTP | Trả về |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `ResultUtil.error(400, <field errors>)` |
| `IllegalArgumentException` | 400 | `ResultUtil.error(400, e.getMessage())` |
| `Exception` | 500 | `ResultUtil.error(500, "<message chung>")` + `log.error` kèm exception |

Có advice rồi thì **bỏ try/catch trùng lặp trong controller**.

**Pattern C — catch ladder từ cụ thể đến tổng quát**, mỗi nhánh compensate cache:

```java
} catch (PessimisticLockException e) {
    log.warn("Pessimistic locking failed for ticketId={}", ticketId);
    if (isRedisDecremented) stockOrderCacheService.increaseStockCache(ticketId, quantity);
    return false;
} catch (Exception e) {
    log.error("Unexpected error when decreasing stock for ticketId={}", ticketId, e);
    if (isRedisDecremented) stockOrderCacheService.increaseStockCache(ticketId, quantity);
    return false;
}
```

**Cấm tuyệt đối:**

- `catch (Exception e) { throw new RuntimeException(e); }` — mất kiểu, mất thông điệp.
- Nuốt exception mà không log.
- Ném `RuntimeException` với message trần từ service (dùng exception có kiểu).
- Thất bại của cache làm gãy luồng nghiệp vụ — cache lỗi thì `log.warn` rồi đi tiếp.

---

## 12. SQL

Bốn cơ chế, mỗi cái một chỗ dùng, không lẫn:

| Cơ chế | Dùng khi | Ở đâu |
|---|---|---|
| Derived query | Truy vấn đơn suy được từ tên method | `*JPAMapper` |
| JPQL `@Query` | **Mặc định** cho mọi thứ còn lại | `*JPAMapper` |
| Native `@Query(nativeQuery = true)` | Cú pháp riêng MySQL (`INSERT IGNORE`) | `*JPAMapper` |
| `EntityManager.createNativeQuery` | **Chỉ** khi tên bảng động (bảng theo tháng) | `*RepositoryImpl` |

Quy tắc chung:

- **Chỉ tham số `:named`** + `@Param`. Cấm `?1` / `?`. Thứ duy nhất được nối chuỗi vào SQL là **tên bảng** suy ra nội bộ (`yyyyMM`), không bao giờ là input người dùng.
- `@Modifying` + `@Transactional` trên write; method trả `int` rows-affected; `*RepositoryImpl` đổi sang `boolean`:
  ```java
  return ticketOrderJPAMapper.decreaseStockLevel1(ticketId, quantity) > 0;
  ```
- JPQL viết theo **tên entity / field**, không tên bảng / cột. Xuống dòng bằng `"..." +` và chừa dấu cách cuối mỗi mảnh.
- Phân trang: `Pageable` cho JPQL; **keyset** (`WHERE id < :lastId`) cho native, không `OFFSET`.
- **Không dùng `JdbcTemplate`.**

---

## 13. Redis & distributed lock

- Key sinh qua private method, không inline: `private String genEventItemKey(Long id) { return "PRO_TICKET:ITEM:" + id; }`. Theo namespace ở `overview.md` §4, phân tách bằng `:`.
- **Mọi giá trị cache phải có TTL.** Dự án tham chiếu `set` không TTL nên key sống mãi — đừng chép.
- Script Lua khai dạng hằng, dựng `DefaultRedisScript` một lần (SHA được client cache):
  ```java
  private static final String LUA_DEDUCT = "...";
  private static final DefaultRedisScript<Long> SCRIPT_DEDUCT = new DefaultRedisScript<>(LUA_DEDUCT, Long.class);
  ```
- Lock Redisson: `tryLock` có timeout (không `lock()` vô hạn), luôn `finally { unlock(); }`, và unlock phải kiểm `isLocked() && isHeldByCurrentThread()`.
- Redis chỉ được chạm từ `*-application` (qua `*CacheService`) và `*-infrastructure`. Domain không biết Redis tồn tại.

---

## 14. Config & hằng số

- Hằng dùng chung khai `public static final` trên chính `@Configuration` sở hữu nó, tham chiếu qua tên class:
  ```java
  public static final String ORDER_PLACE_TOPIC = "order-place-topic";
  kafkaTemplate.send(KafkaTopicConfig.ORDER_PLACE_TOPIC, key, message);
  ```
- Hằng nội bộ class: `private static final` UPPER_SNAKE, khai ở đầu class.
- **Cấm hardcode host / port / secret trong `@Configuration`** — dùng `@ConfigurationProperties` (ưu tiên) hoặc `@Value`.
- YAML: 2 space, comment `#` tiếng Việt được, **không giữ khối cấu hình bị comment**.


---

## 15. Số học & làm tròn

**`product.rating` làm tròn HALF-UP, 1 chữ số thập phân.** `4.25 → 4.3`, **không phải** `4.2`.

Phía Java **phải khai `RoundingMode.HALF_UP` tường minh**:

```java
BigDecimal rating = BigDecimal.valueOf(sumRating)
        .divide(BigDecimal.valueOf(reviewCount), 1, RoundingMode.HALF_UP);
```

**Cấm** `Math.round` trên `double`, cấm `RoundingMode.HALF_EVEN`, và cấm mượn hàm `round()` mặc định
của ngôn ngữ khác khi sinh dữ liệu — `round()` của Python là half-to-even và cho ra `4.2`.

**Lý do phải viết ra, đừng xoá cho gọn.** `rating` được tính ở **hai nơi**: lúc seed dữ liệu
(`environment/mysql/init/02-seed-data.sql`) và lúc service tính lại khi có đánh giá mới. Hai nơi
dùng hai quy ước khác nhau thì giá trị **nhảy một bước 0.1 vào lúc không ai đang nhìn**, và triệu
chứng trông y hệt một cái bug — trong khi mỗi phép tính đều "đúng" theo quy ước của riêng nó, nên
truy ngược rất tốn thời gian.

Phát hiện ở [backlog 0006](../../../management/backlog/0006-seed-du-lieu-dev-tu-mock.md): sản phẩm 11
có `AVG(rating) = 4.2500` — đúng ranh giới `.x5`, và là sản phẩm duy nhất trong 42 rơi vào đó. Chọn
**half-up** vì runtime tính bằng SQL và `ROUND()` của MySQL là half-up, nên quy ước phải khớp engine
thật; ngoài ra half-up là thứ người dùng chờ đợi.

Quy tắc tổng quát rút ra: **giá trị nào được tính ở nhiều hơn một nơi thì quy ước làm tròn là một
phần của contract** — pin ở mục này *trước khi* viết chỗ tính thứ hai.

---

## 16. Format & tooling

- Java 4 space; YAML / XML / JSX 2 space. Không tab. UTF-8 toàn bộ.
- Brace K&R: `public void foo() {`, `} else {`, `} catch (Exception e) {`. Luôn có dấu cách sau `if` / `catch` / `finally`.
- Thứ tự import kiểu IntelliJ: `com.<org>.*` → third-party → `jakarta.*` → `lombok.*` → `org.*` → dòng trống → `java.*`.
- **Xoá import thừa** trước khi commit.
- Dòng dài giữ dưới ~120 ký tự.
- Dự án mới **nên thêm `.editorconfig`** (hiện chưa có Checkstyle / Spotless / editorconfig nào) để khỏi tranh cãi format.

---

## 17. Checklist cấm — soát trước khi giao code

- [ ] Không thêm dependency đảo chiều giữa các module
- [ ] Không có `@Autowired` field injection mới
- [ ] Không có `@Builder`, `@Getter` / `@Setter` lẻ
- [ ] Không có `java.util.Date` trong code
- [ ] Không làm tròn `rating` bằng `Math.round` / `HALF_EVEN` — chỉ `RoundingMode.HALF_UP` (§15)
- [ ] Không import `jakarta.transaction.Transactional` hay `javax.validation`
- [ ] Không có `catch (Exception e) { throw new RuntimeException(e); }`
- [ ] Không nuốt exception mà không log
- [ ] Không có tham số SQL vị trí `?1`; không nối input người dùng vào SQL
- [ ] Không `set` cache thiếu TTL; không viết Redis key inline
- [ ] Không có `tryLock` thiếu `finally { unlock(); }`
- [ ] Không hardcode host / port / secret trong source
- [ ] Không `System.out.println`, không nối chuỗi trong log
- [ ] Không để lại khối code comment-out
- [ ] Không thêm nhánh return / catch sau cổng Lua mà thiếu compensation
- [ ] Không đưa `INSERT IGNORE idempotency_key` ra ngoài transaction của consumer

---

*Last updated: 2026-08-22 — cập nhật stamp này trong cùng lần sửa nội dung.*
