# api — backend service 

Backend Spring-boot provide API for Frontend System follow api contract in API_CONTRACT.md (/documents/API_CONTRACT.md) 

- **Stack:** JAVA · SpringBoot · MYSQL.
- **Owned by:** the `api` sub-agent (`../../management/.claude/agents/api.md`)
- **Its law:** [`documents/`](documents/) — architecture, coding conventions, and the format the agent reports back in.

The PM never edits this folder directly; it delegates to the `api` agent with a ticket as the spec.

## Schema

Nguồn chân lý của schema là **entity** ở `nss-domain/src/main/java/com/nss/ddd/domain/model/entity/`
([ADR 0002](../../management/decisions/0002-schema-nguon-chan-ly.md)). Dev chạy `ddl-auto: update`,
nên khởi động app là schema hiện hành.

`environment/mysql/init/01-schema.sql` là **bản kết xuất, cấm sửa tay**. Sinh lại bằng:

```bash
mvn clean install -DskipTests
rm -f environment/mysql/init/01-schema.sql
java -jar nss-start/target/nss-start-1.0.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --spring.jpa.hibernate.ddl-auto=none \
  --spring.datasource.hikari.initialization-fail-timeout=-1 \
  --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=environment/mysql/init/01-schema.sql
```

**Bước `rm -f` là bắt buộc, không phải cho gọn.** Hibernate mở `create-target` ở chế độ **append**:
chạy lệnh lên một file đã tồn tại thì nội dung mới được **nối thêm** vào cuối chứ không ghi đè, ra
file 766 dòng thay vì 383 với mọi `CREATE TABLE` lặp hai lần. Xoá file trước khi sinh là cách duy
nhất chắc chắn kết quả chỉ chứa đúng một lần kết xuất — đừng trông vào hành vi mặc định của công cụ.

Hai cờ `allow_jdbc_metadata_access=false` và `initialization-fail-timeout=-1` khiến lệnh chạy được
**không cần MySQL sống** — kết xuất đọc metadata của entity, không đọc database.

Sinh xong thì **kiểm ngay**:

```bash
wc -l environment/mysql/init/01-schema.sql            # phải ra 383
git diff --stat environment/mysql/init/01-schema.sql  # phải RỖNG (không in gì)
```

Diff rỗng là **bài kiểm entity chưa trôi khỏi schema**, không phải thủ tục cho có: nó nói rằng schema
sinh từ entity hiện tại giống hệt bản đang commit. Diff không rỗng nghĩa là entity đã đổi mà bản kết
xuất chưa theo kịp — đọc diff, xác nhận đúng ý định, rồi commit bản vừa sinh; **tuyệt đối không sửa
tay file SQL** ([ADR 0002](../../management/decisions/0002-schema-nguon-chan-ly.md)). Con số 383 khác
đi mà không do entity đổi thì gần như chắc chắn là quên `rm -f`.

> **Cảnh báo — `environment/mysql/init/` **đang được mount** vào `docker-entrypoint-initdb.d/`, nên
> file này được MySQL *thực thi*, không còn nằm im.** Một bản sinh sai — ví dụ file 766 dòng do quên
> `rm -f`, mang `CREATE TABLE` trùng — làm container **chết ngay lúc khởi tạo**, và triệu chứng hiện
> ra dưới dạng "DB không lên" chứ không phải "file SQL sai". Kiểm số dòng và diff trước khi commit.

**Luật đi kèm việc mount: sửa entity thì sinh lại `01-schema.sql` trong cùng lần sửa đó.**
Quên bước này thì container dựng mới chạy schema **cũ**, rồi `ddl-auto: update` vá phần chênh.
`update` chỉ **thêm** — không xoá cột thừa, không đổi kiểu cột — nên DB rơi vào trạng thái lai giữa
entity mới và schema cũ, và **không có gì báo lỗi**. Triệu chứng sẽ xuất hiện ở một câu truy vấn
trả sai kết quả, chứ không ở lúc khởi động, nên rất tốn thời gian truy ngược.

## Seed data

`environment/mysql/init/02-seed-data.sql` nạp dữ liệu dev **sinh từ mock của frontend**
(`C:\fe_base\code_space_1\src\mocks`, chỉ đọc) — xem [backlog/0006](../../management/backlog/0006-seed-du-lieu-dev-tu-mock.md).
Container dựng mới là có sẵn 9 brand · 11 category · 42 product · 84 product_image · 48 review ·
3 coupon · 10 province / 27 district / 112 ward · 8 permission · 2 role · 2 user.

Ba điểm cần biết trước khi sửa file này:

- **`rating` / `review_count` của product được tính lại từ bảng `review`**, không chép số của mock,
  làm tròn **HALF-UP** 1 chữ số — cùng quy ước với `ROUND()` của MySQL mà service sẽ dùng lúc tính lại
  ([coding-conventions §15](documents/coding-conventions.md#15-số-học--làm-tròn)). Hai nơi lệch quy ước thì
  giá trị nhảy 0.1 và trông y hệt một cái bug.
  Mock cố ý ghi số ảo (sản phẩm 33: `reviewCount: 216` nhưng chỉ có 3 review) và chính FE ghi chú
  rằng backend sẽ là nguồn chân lý. Hệ quả đã chấp nhận: **24/42 sản phẩm không có review nào** →
  `rating = 0.0`, `review_count = 0`.
- **File này RESET dữ liệu.** Khối `DELETE` ở đầu xoá cả `customer_order` / `order_item` /
  `order_status_history` / `address` / `refresh_token` — mọi đơn hàng bạn tự tạo lúc test đều mất.
  Bắt buộc phải vậy vì chúng tham chiếu `user`, mà `user` thì bị seed lại.
- **`password_hash` là bcrypt của mật khẩu dev đã biết** (`demo@nongsansach.vn` / `123456`,
  `admin@nongsansach.vn` / `admin123`) — dev-only, tuyệt đối không mang lên môi trường thật.

**Reset về mốc sạch** — `initdb.d` chỉ chạy khi datadir rỗng, nên phải xoá cả container lẫn `data/`:

```bash
docker stop nss-event-mysql && docker rm nss-event-mysql   # theo TÊN, không dùng `compose down`
rm -rf environment/data                                    # điều kiện duy nhất để initdb.d chạy lại
docker compose -f environment/docker-compose-dev.yml up -d
```

Chỉ muốn nạp lại dữ liệu mà **giữ nguyên container** thì chạy thẳng file seed — nó idempotent
(DELETE theo thứ tự ngược FK rồi INSERT với id tường minh, mọi mốc thời gian là literal cố định,
không dùng `NOW()`), nên chạy bao nhiêu lần cũng ra đúng một trạng thái:

```bash
docker exec -i nss-event-mysql mysql -uroot -p<mat-khau> --default-character-set=utf8mb4 nssdbs \
  < environment/mysql/init/02-seed-data.sql
```

### Kết nối

Dev dùng MySQL 8.0 chạy trong container (`environment/docker-compose-dev.yml`) — database `nssdbs`,
host port **3316**, user `root`.

1. Copy `environment/.env.example` → `environment/.env` rồi điền `MYSQL_ROOT_PASSWORD`.
   `.env` bị gitignore, **không bao giờ commit**.
2. Bật database:

```bash
docker compose -f environment/docker-compose-dev.yml up -d
```

3. Chạy app — datasource đọc `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` từ biến môi trường,
   mặc định đã trỏ `jdbc:mysql://localhost:3316/nssdbs` với user `root`;
   **không commit mật khẩu vào repo**:

```bash
DB_PASSWORD=<mat-khau-mysql> mvn -pl nss-start spring-boot:run
```

Compose **có** mount `environment/mysql/init` vào `/docker-entrypoint-initdb.d/`, nên container dựng
mới tự chạy `01-schema.sql` rồi `02-seed-data.sql` (thứ tự alphabet) — lên là có sẵn cả schema lẫn
dữ liệu, không phải boot app rồi chạy thêm bước tay nào. Trước đây không mount nên container mới
luôn rỗng 0 bản ghi, và không làm việc được với tầng repository / API.

Đánh đổi đã nhìn thấy và đã chấp nhận: với container mới, **schema do file `.sql` tạo chứ không do
Hibernate**. [ADR 0002](../../management/decisions/0002-schema-nguon-chan-ly.md) không bị vi phạm —
`01-schema.sql` vẫn là bản kết xuất từ entity và vẫn cấm sửa tay — cái mất là phép kiểm "Hibernate
tự sinh schema" không còn chạy mặc định. Đó chính là lý do có **luật sinh lại `01-schema.sql` cùng
lần sửa entity** ở mục [Schema](#schema) phía trên; lane test `db` vẫn giữ phần kiểm cấu trúc.

**Dừng database — dùng `docker stop`, KHÔNG dùng `docker compose down`:**

```bash
docker stop nss-event-mysql          # dừng, giữ nguyên container và dữ liệu
docker start nss-event-mysql         # bật lại
```

Dữ liệu nằm ở bind mount `environment/data/db_data`, nên `stop`/`start` không mất gì và
MySQL không phải khởi tạo lại. Chỉ xoá hẳn khi thật sự muốn làm lại từ đầu:
`docker rm -f nss-event-mysql` rồi xoá thư mục `environment/data/`.

**Vì sao file compose có dòng `name: nss-event` — đừng xoá nó đi cho gọn.** Không có dòng đó,
Compose suy project name từ tên thư mục cha (`environment`), và trên máy này nó trùng với một
stack `pre-event-*` không liên quan cũng sống trong thư mục tên `environment`. Cùng project name
+ cùng service name `mysql` = cùng danh tính với Compose, nên `up` tái tạo nhầm service của stack
kia (đã xảy ra thật, mất một container), còn `down` sẽ xoá sạch 11 container của họ. Đó cũng là lý
do mục trên nói dừng DB bằng `docker stop` theo tên chứ không bằng `docker compose down`.
Xem [bugs/0001](../../management/bugs/0001-compose-project-name-va-cham.md).

### Test

Hai làn, tách nhau bằng JUnit tag:

| Làn | Chạy bằng | Cần MySQL |
|---|---|---|
| Mặc định | `mvn clean package` | **Không** |
| Schema | `mvn -pl nss-start test -Dexcluded.test.groups= -Dgroups=db` | **Có** |

`SchemaSmokeTest` đánh `@Tag("db")` và bị surefire loại khỏi làn mặc định
(`<excludedGroups>` trong `nss-start/pom.xml`). Lý do tách: build phải chạy được trên máy
chưa cài MySQL, còn việc kiểm schema thì bắt buộc phải có database thật — đọc
`information_schema` chứ không tin annotation.
