# Coding conventions

Quy ước bắt buộc cho mọi dự án dùng stack ở [`architecture/01-overview.m`](overview.md). **Đây là luật, không phải gợi ý** — khi ticket mâu thuẫn với tài liệu này thì **dừng và flag**, không tự hoà giải.

Ví dụ trích từ dự án tham chiếu `xxxx.com`. Chỗ nào dự án tham chiếu làm sai, tài liệu ghi rõ **"đừng chép"** và nêu cách đúng.

> **Mọi con trỏ tới `API_CONTRACT.md` trong file này đều ghi kèm *"mirror, đồng bộ `<ngày>`"*.** `API_CONTRACT.md` nằm ở đây là **bản sao**; nguồn thật ở board frontend. Ngày đồng bộ **không** ngăn được lệch — nó chỉ làm sự lỗi thời **nhìn thấy được**: ngày càng cũ thì độ tin của trích dẫn càng thấp, và người đọc biết phải đi đối chiếu nguồn. Sửa nội dung trích thì cập nhật ngày trong cùng lần sửa.

---

## 1. Ngôn ngữ

| Thành phần | Ngôn ngữ |
|---|---|
| Comment, javadoc | **Tiếng Việt** |
| Message nghiệp vụ trả cho người dùng | **Tiếng Việt** (`"Hết vé, vui lòng thử lại sau"`) |
| Identifier (class, method, biến) | Tiếng Anh |
| Log message | Tiếng Anh |
| Validation message (`@NotNull(message=...)`) | **Tiếng Việt** (`"Email không đúng định dạng."`) |
| Business error code | Tiếng Anh UPPER_SNAKE (`OUT_OF_STOCK`) |

**Ranh giới là *ai đọc chuỗi*, không phải *chuỗi được viết ở đâu*.** Validation message trông như một chi tiết kỹ thuật vì nó nằm trong annotation cạnh code, nhưng nó không dừng lại ở đó: Spring đặt nó vào map `errors` của response **`422`** (`API_CONTRACT.md` §A.3 — mirror, đồng bộ 2026-08-26) và frontend dán thẳng nó vào ô nhập tương ứng. Người đọc là **người dùng cuối**, nên nó thuộc dòng *"message nghiệp vụ trả cho người dùng"* ở trên, **không** thuộc dòng *"log message"* (người đọc là kỹ sư).

Hệ quả cụ thể:

- Viết thẳng câu tiếng Việt **trong chính annotation**. **Không có tầng i18n / `MessageSource` / `Accept-Language`** — hệ thống chỉ một ngôn ngữ, nên đừng dựng khoá message để tra bảng.
- **Không lặp lại đường dẫn của trường** trong câu — khoá của map `errors` đã mang đường dẫn rồi: `shipping.email` nhận `"Email nhận thông tin đơn hàng không đúng định dạng."`, không phải `"shipping.email không đúng định dạng"`.
- Giọng câu cùng giọng với `detail` của `422`: nói *sai ở đâu và sửa thế nào*, không phải tên ràng buộc (`"@Size"`, `"must not be blank"`).

Ghi nhận ở [backlog 0023](../../../management/backlog/0023-thong-diep-validate-tieng-viet.md) (95 thông điệp / 15 file DTO đã chuyển sang tiếng Việt) và [backlog 0026](../../../management/backlog/0026-coding-conventions-muc-1-validation-message.md) (kéo dòng luật này về khớp).

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

- **Không có hậu tố `*DTO` trong dự án này** — dòng luật cũ trỏ vào `application model/`, và thư mục đó chỉ chứa `.gitkeep`. Đo 2026-08-26: **0** file `*DTO.java`, **0** lần gọi `toDTO`; control dương cùng lệnh: **19** file `*Response.java`, **87** lần gọi `toResponse`. Ranh giới thật là **chiều đi**, không phải một hậu tố chung:
  - **Vào** — `*Request` (**15** file, controller `dto/`, hình dạng JSON của client) → `*Command` (**13** file, `application model/command/`, ý định đã làm sạch cho tầng application).
  - **Ra** — `*Response` (**19** file, `application model/response/`), mang **hai** vai đừng lẫn: payload thật lên dây (`ProductResponse`), và **kết quả nghiệp vụ chỉ sống trong tiến trình** (`OrderMutationResponse`) — vai thứ hai bị `OrderController.extractOrThrow` (`:273-287`) bóc ở biên controller và **không bao giờ lên dây**.
  - Từ *"DTO"* vẫn dùng được như **tên khái niệm chung** trong văn xuôi và javadoc (14 javadoc trong code đang viết *"trả DTO trần"*); cấm là cấm dùng nó làm **hậu tố class**.
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
          -> (*ControllerMapper)  -> application/model/command/*Command
          -> (*Mapper.toEntity)   -> domain/model/entity/*
          -> (*Mapper.toResponse) -> application/model/response/*Response
          -> trả thẳng ra HTTP, KHÔNG envelope (ADR 0001) — lỗi thì là `ProblemDetail`, xem §11
```

- `*Request`: `@Data` trần, validation bằng **`jakarta.validation`** (cấm `javax.validation`), date có `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`, request lồng nhau đánh `@Valid` trên field. Controller luôn `@Valid @RequestBody`.
- `*Mapper` là class **stateless, method `public static`, không phải Spring bean**, luôn null-guard:
  ```java
  // ProductMapper.java:94-97
  public static ProductResponse toResponse(Product product, List<ProductImage> images) {
      if (product == null) {
          return null;
      }
      ...
  }
  ```
- `*Response` nghiệp vụ dùng **static factory**, không new trực tiếp ở call site:
  ```java
  // OrderMutationResponse.java:99 — KHÔNG có field `success`; vắng `order` chính là tín hiệu thất bại
  public static OrderMutationResponse failed(String code, String message) {
      return new OrderMutationResponse()
              .setCode(code)
              .setMessage(message);
  }
  ```
  Kết quả này **không lên dây**: `OrderController.extractOrThrow` (`:273-287`) bóc nó thành payload thật hoặc exception mang mã HTTP thật (§11 · `architecture/01-overview.md` §7). Một `success=false` đi ra ngoài HTTP là envelope cũ, thứ [ADR 0001](../../../management/decisions/0001-api-response-envelope.md) đã bỏ.
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

**Pattern A — thất bại nghiệp vụ là *giá trị trả về* trong tầng application, không phải exception.** Static factory, code UPPER_SNAKE, message tiếng Việt:

```java
return OrderMutationResponse.failed(OrderMutationResponse.CODE_OUT_OF_STOCK,
        "Sản phẩm đã hết hàng, vui lòng chọn sản phẩm khác.");
```

**Nhưng giá trị đó dừng lại ở ranh giới controller.** Controller dịch kết quả thất bại thành exception mang **mã HTTP thật** (`OrderController.extractOrThrow` là mẫu chuẩn), rồi `@RestControllerAdvice` biến exception thành `ProblemDetail`. **Không bao giờ trả HTTP 200 cho một thất bại**, và **không có envelope** bọc quanh payload.

> **Vì sao mã HTTP thật — và vì sao `422` chứ không phải `400`.**
>
> Frontend phân biệt *lỗi ô nhập* với *lỗi nghiệp vụ* bằng **sự có mặt của khoá `errors`**, không phải bằng cách đoán theo mã HTTP (`API_CONTRACT.md` §A.3 — mirror, đồng bộ 2026-08-26):
>
> - **Lỗi validate theo trường** (`@Valid` trượt) → **`422` + map `errors`** (`tên trường -> câu tiếng Việt`). Frontend dán từng câu vào đúng ô nhập.
> - **Lỗi quy tắc nghiệp vụ** → **mã HTTP thật theo ngữ nghĩa** + `detail`, **không có `errors`**. Frontend hiển thị `detail` ở mức form.
>
> `400` là mặc định của Spring cho lỗi bind và **không phân biệt được hai loại đó**. Trả `400` không kèm `errors` cho một lỗi validate là **đổi contract trong im lặng**: body vẫn parse được, vẫn đúng hình dạng `ProblemDetail`, nên trông y hệt như đã chạy đúng — [backlog 0008](../../../management/backlog/0008-api-crud-san-pham.md) ghi lại đúng ca này và gọi nó là thứ khó nhận ra nhất.
>
> HTTP 200 cho thất bại còn hỏng nặng hơn: cơ chế **tự refresh khi gặp `401`** (`API_CONTRACT.md` §A.2 — mirror, đồng bộ 2026-08-26) mất sạch tín hiệu để bắt. Đó là lý do [ADR 0001](../../../management/decisions/0001-api-response-envelope.md) chốt `ProblemDetail` + mã HTTP thật và **thay thế** envelope cũ `ResultMessage<T>` + HTTP 200 + `ResultUtil.error(...)`. Envelope đó **không tồn tại trong code** — đừng chép lại từ dự án tham chiếu.

Mã theo ngữ nghĩa, đúng như `GlobalExceptionHandler` đang chạy:

| Tình huống | HTTP | Ví dụ exception |
|---|---|---|
| Không tìm thấy tài nguyên | 404 | `ProductNotFoundException`, `OrderNotFoundException` |
| Chưa xác thực / sai thông tin đăng nhập | 401 | `InvalidCredentialsException` |
| Trùng khoá, xung đột trạng thái | 409 | `DuplicateEmailException`, `OutOfStockException` |
| Vi phạm quy tắc nghiệp vụ | 422 | `CouponNotApplicableException`, `InvalidOrderDataException` |
| Request sai dạng, ngoài phạm vi `@Valid` | 400 | `InvalidDateRangeException`, `EmptyOrderException` |
| Quá ngưỡng gọi | 429 | `TooManyRequestsException` |

**Pattern B — `@RestControllerAdvice` là bắt buộc ở dự án mới.** Dự án tham chiếu **không có** global handler → mọi lỗi ngoài dự kiến trả body `{timestamp, status, error, path}` thay vì `ProblemDetail`, và frontend vỡ khi parse. Handler tối thiểu phải phủ:

| Exception | HTTP | Trả về |
|---|---|---|
| `MethodArgumentNotValidException` | **422** | `ProblemDetail` + property `errors` (`tên trường -> thông điệp`); `detail` là câu chung |
| `IllegalArgumentException` | **400** | `ProblemDetail` + `detail` là **hằng số chung**, không phải `e.getMessage()` |
| `Exception` | **500** | `ProblemDetail` + `detail` chung + `log.error` kèm exception |

Ba ràng buộc đi kèm, mỗi cái đều từng làm hỏng một lần:

- **Advice bắt `MethodArgumentNotValidException` phải xếp `@Order(Ordered.HIGHEST_PRECEDENCE)`.** `spring.mvc.problemdetails.enabled` khiến Spring tự đăng ký `ProblemDetailsExceptionHandler` ở order 0, và nó cũng nhận exception này — không xếp trước nó thì lỗi validate trả **`400` "Invalid request content."** thay vì `422` kèm `errors`. Vẫn đúng hình dạng `ProblemDetail`, nên rất dễ tưởng là đã chạy đúng.
- **`@ExceptionHandler(Exception.class)` phải nằm ở một advice riêng, `@Order(Ordered.LOWEST_PRECEDENCE)`.** Spring chọn advice theo `@Order` *rồi* mới chọn method theo kiểu exception, nên gộp nó vào advice xếp trước thì nó nuốt cả 405 / 415 / 404-không-có-handler mà Spring vốn trả đúng, và biến tất cả thành 500. Xem `UnexpectedExceptionHandler`.
- **Không đổ `e.getMessage()` vào `detail` cho lỗi kỹ thuật** (`IllegalArgumentException`, `Exception`) — chuỗi đó viết cho kỹ sư đọc, không cho người dùng cuối; dùng hằng số chung. Exception **nghiệp vụ** thì ngược lại: message của nó vốn đã là câu tiếng Việt viết cho người dùng, nên `e.getMessage()` đi thẳng vào `detail`. Cùng một ranh giới *"ai đọc chuỗi"* ở §1.

Có advice rồi thì **bỏ try/catch trùng lặp trong controller**.

Ghi nhận ở [ADR 0001](../../../management/decisions/0001-api-response-envelope.md) (chốt `ProblemDetail` + mã HTTP thật, thay thế envelope `ResultMessage` / `ResultUtil`) và [backlog 0028](../../../management/backlog/0028-coding-conventions-muc-11-resultutil-400.md) (kéo mục này về khớp code).

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

Cùng loại lỗi, khác phép toán: quy ước **bỏ dấu** cho tìm kiếm được pin ở §18.

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
- [ ] Không chép lại phép bỏ dấu thành bản thứ hai — dùng lại đúng một hàm (§18)
- [ ] Không đặt ranh giới breaker ngoài khối tự nuốt exception; không để `RateLimiter` chạy `timeoutDuration` mặc định (§20)
- [ ] Không thêm `@Scheduled` job mới mà thiếu smoke assertion xác nhận `@EnableScheduling` thực sự bật (bean `ScheduledAnnotationBeanPostProcessor` trong context, không chờ timer) — thiếu `@EnableScheduling` không lỗi gì lúc khởi động, job chỉ lặng lẽ không bao giờ chạy; ca thật: `OutboxPublisherJob` ở backlog 0032, mẫu test ở `SchedulingEnabledSmokeTest` (backlog 0033)

---

## 18. Chuẩn hoá chuỗi để tìm kiếm

Cùng loại với §15, chỉ khác phép toán: **`name_normalized` được sinh ở một nơi và được đối chiếu ở
một nơi khác**, nên cách bỏ dấu là *một phần của contract*, không phải chi tiết cài đặt.

**Quy ước đã chốt — bốn bước, đúng thứ tự này:**

1. `Normalizer.normalize(text, NFD)` — tách dấu thanh khỏi nguyên âm;
2. bỏ dải `\p{InCombiningDiacriticalMarks}`;
3. **`đ` → `d`, `Đ` → `D`** — bước hay bị quên nhất;
4. `toLowerCase()`.

Bước 3 phải viết tay vì `đ` **không phải** một `d` có dấu — nó là một chữ cái Latin riêng và NFD
không tách nó ra được. Thiếu nó thì "Đậu Hà Lan" ra `dau ha lan` chỉ nhờ `toLowerCase` bắt được `Đ`
hoa, còn `đ` thường thì lọt lại — và tìm kiếm bỏ dấu trượt đúng những từ tiếng Việt hay gặp nhất.
Collation `utf8mb4_unicode_ci` gập được hoa/thường và dấu thanh nhưng **không** gập `đ`, nên không
mượn được collation để khỏi phải làm bước này.

**Hàm này có đúng MỘT bản.** `ProductDomainServiceImpl#genNameNormalized` sinh giá trị lưu vào cột,
và `#genSearchKeyword` chuẩn hoá tham số `q` bằng cách gọi lại chính nó. Slug dùng lại luôn bốn bước
đó rồi thêm ba bước riêng (`#genSlugified`). **Chép phép bỏ dấu ra bản thứ hai là cách chắc chắn để
hai bên lệch nhau vào đúng lúc chỉ một bên được sửa** — và triệu chứng là "tìm không ra", không phải
một lỗi.

### Lệch đã biết với frontend — chấp nhận được, nhưng phải ghi ra

`normalize()` trong `src/api/adminProducts.api.ts` **không** làm bước 3 (chỉ `slugify` bên đó mới
làm). Backend có làm, và còn khớp thêm cột `slug` bên cạnh `name_normalized`.

⇒ **Tập kết quả của backend là _siêu tập_ của mock frontend**, không bao giờ là tập con. `q=dau` ở
backend ra "Đậu Hà Lan" qua *cả* tên lẫn slug; mock chỉ ra qua slug.

Chiều lệch này là chiều an toàn: người dùng thấy *nhiều* kết quả hơn mock, không phải *ít* hơn. Nếu
FE muốn hai bên khớp tuyệt đối thì sửa `normalize()` bên họ — **không** gỡ bước 3 ở đây, vì làm vậy
là phá tìm kiếm tiếng Việt để chiều một lớp mock sắp bị thay.

### Mẫu `LIKE` là việc của adapter, không phải của domain

Domain trả về **từ khoá đã bỏ dấu**; `ProductRepositoryImpl#genLikePattern` mới bọc `%` và escape.
Đầu vào người dùng phải được escape (`%`, `_`, và chính ký tự escape) rồi khai `ESCAPE '!'` — không
escape thì `q=100%` biến thành ký tự đại diện và trả về **nhiều dòng hơn số dòng thật sự khớp**, một
kết quả sai trông y hệt một kết quả đúng. Dùng `!` chứ không dùng gạch chéo ngược: gạch chéo ngược
còn là ký tự escape của chính chuỗi MySQL nên nó phải nhân đôi qua hai tầng và rất dễ đếm nhầm.

Ghi nhận ở [backlog 0018](../../../management/backlog/0018-admin-products-namespace.md).

---

*§19 đang được **[backlog 0020](../../../management/backlog/0020-coding-conventions-18-19.md) giữ chỗ**, chưa viết. Dòng này ở đây để khoảng trống giữa §18 và §20 đọc ra là "chưa tới", không phải "đã bị xoá" — 0020 sẽ thay chính dòng này. **Đừng renumber §20 xuống 19.***

---

## 20. Resilience — RateLimiter & CircuitBreaker

Áp dụng cho mọi thứ dựng bằng Resilience4j ([ADR 0005](../../../management/decisions/0005-lop-bao-ve-resilience4j.md), backlog 0021). Sáu điều dưới đây đều bảo vệ khỏi **cùng một kiểu hỏng**: lớp bảo vệ không bao giờ kích hoạt, trong khi build xanh, test xanh, và không có triệu chứng nào nhìn thấy.

**1. Một tên instance, khai thành hằng — không rải chuỗi trần.** Tên instance là thứ nối cấu hình, dòng log, và test lại với nhau; gõ tay ở ba chỗ là ba chỗ lệch được, và triệu chứng của lệch là *"breaker không làm gì cả"* chứ không phải một lỗi.

```java
/** Tên instance breaker — xuất hiện trong mọi dòng log transition, đừng đổi mà không sửa runbook. */
private static final String CIRCUIT_BREAKER_NAME = "mail";
```

**2. Ngưỡng nằm sau env var theo nếp `${ENV_VAR:dev-default}` — không tạo profile mới.** Ngưỡng rate limit và ngưỡng breaker là **con số vận hành**: đặt thấp quá thì tự chặn người dùng thật, đặt cao quá thì lớp bảo vệ chỉ là trang trí. Phải chỉnh được mà không build lại, và phải chỉnh trong cùng nếp cấu hình đang có chứ không đẻ thêm một trục profile mới để quên.

```yaml
rate-limit:
  read:
    limit-for-period: ${RATE_LIMIT_READ_LIMIT:100}
    limit-refresh-period: ${RATE_LIMIT_READ_PERIOD:PT1S}
```

**3. `RateLimiter` phải khai `timeoutDuration = 0`, và phải ĐO.** Mặc định của Resilience4j là **5 giây**, tức **xếp hàng** chứ không **từ chối**: hết permit thì caller bị park chờ và *không* nhận 429. Dưới tải đó là biến một lớp throttle thành một **bộ khuếch đại độ trễ** — không ai bị từ chối, mọi người cùng chậm. Bằng chứng bắt buộc là **p50 của một request bị từ chối**: phải ≈ 0ms (đo được **3.8ms**, backlog 0021 Phase 1), không phải ~5000ms. Không có con số đó thì không có gì phân biệt "từ chối" với "xếp hàng" — cả hai đều trông như một cấu hình đúng.

**4. Breaker mở KHÔNG được làm gãy luồng nghiệp vụ.** Mở thì `log.warn` rồi đi tiếp, **không sinh mã HTTP mới** — cùng luật với "thất bại của cache thì log rồi đi tiếp" ở §11. Endpoint đã khai một mã trả về thì giữ đúng mã đó ở **cả hai** nhánh. Hệ quả phải nhận: **log là tín hiệu duy nhất** thấy được breaker đang mở, nên dòng transition và dòng "skipped" là **bắt buộc**, không phải trang trí — và cả hai phải ở mức `warn`, đừng để chúng rơi vào nhánh `catch` tổng quát rồi bị ghi thành lỗi kèm stack trace, vì lúc đó không đếm được nữa.

**5. Ranh giới breaker nằm TRONG khối `catch` khi caller tự nuốt exception.** Bọc *ngoài* một method tự nuốt (`catch (Exception e) { log.error(...) }`) thì breaker thấy **0 lỗi vĩnh viễn** và **không bao giờ mở**, trong khi mọi test vẫn xanh. Và bọc **đúng** lời gọi ra ngoài, không bọc phần dựng dữ liệu: một payload sai định dạng là lỗi của **chính mình**: bọc nó vào thì nó cũng đẩy breaker mở, và một hạ tầng khoẻ mạnh bị ngắt vì lỗi của ta.

```java
try {
    MimeMessage message = javaMailSender.createMimeMessage();   // NGOAI breaker: loi cua chinh minh
    // ... dung message ...
    circuitBreaker.executeRunnable(() -> javaMailSender.send(message));  // TRONG: loi cua ben ngoai
} catch (CallNotPermittedException e) {
    log.warn("skipped, circuit breaker is open | breaker={}", CIRCUIT_BREAKER_NAME);
} catch (Exception e) {
    log.error("...", e);   // VAN nuot — method chay tren luong khac nen nem ra khong ai bat
}
```

**6. Giá trị cấu hình không hợp lệ ⇒ fail lúc khởi động — kể cả ràng buộc GIỮA hai khoá.** Tiền lệ `JwtConfig`. Kiểm từng khoá là chưa đủ: ví dụ có thật, Resilience4j **âm thầm hạ** `minimumNumberOfCalls` xuống bằng `slidingWindowSize` với cửa sổ `COUNT_BASED` — cấu hình đọc một đằng, breaker chạy một nẻo, không có triệu chứng nào. Nên `minimumNumberOfCalls > slidingWindowSize` phải **chết lúc khởi động** kèm tên khoá đầy đủ, chứ không được chạy tiếp với một giá trị không ai khai.

Đi cùng điều 6: **khoá nào đổi *ý nghĩa* của khoá khác thì khai cứng trong code, đừng đưa ra env var.** `slidingWindowType` đổi `slidingWindowSize` và `minimumNumberOfCalls` từ *số lời gọi* thành *số giây* — một lần chỉnh nhầm ở đó không có triệu chứng nào nhìn thấy. Cùng tiền lệ với `ApiRateLimitInterceptor.TIMEOUT_DURATION = Duration.ZERO` (điều 3): thứ mà chỉnh sai làm lớp bảo vệ *im lặng đổi bản chất* thì không thuộc về biến môi trường.

---

*Last updated: 2026-08-31 — §17: thêm checklist "`@Scheduled` job mới phải có smoke assertion xác
nhận `@EnableScheduling`", trỏ về ca thật `StartApplication` thiếu annotation này ở backlog 0032 và
test mẫu `SchedulingEnabledSmokeTest` (backlog 0033). Trước đó: 2026-08-26 — §3 + §7: bỏ hậu tố `*DTO` (0 file `*DTO.java`, 0 `toDTO`; thật là `*Response` 19 file / `toResponse` 87 lần), ghi ranh giới **vào `*Request`→`*Command`** / **ra `*Response`** và hai vai của `*Response`; ví dụ mapper + static factory thay bằng class có thật; mọi con trỏ `API_CONTRACT.md` ghi rõ **mirror + ngày đồng bộ** (backlog 0029). Trước đó: §11: kéo Pattern A/B về khớp code — `ProblemDetail` + mã HTTP thật thay envelope `ResultMessage`/`ResultUtil`, validate là **422 + `errors`** chứ không phải 400, kèm lý do và trỏ ADR 0001; §7 sửa đuôi chuỗi chuyển đổi (backlog 0028). Trước đó: §1 validation message chuyển sang **Tiếng Việt** + ghi ranh giới phân nhóm (backlog 0026). Cập nhật stamp này trong cùng lần sửa nội dung.*
