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
java -jar nss-start/target/nss-start-1.0.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --spring.jpa.hibernate.ddl-auto=none \
  --spring.datasource.hikari.initialization-fail-timeout=-1 \
  --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create \
  --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=environment/mysql/init/01-schema.sql
```

Hai cờ `allow_jdbc_metadata_access=false` và `initialization-fail-timeout=-1` khiến lệnh chạy được
**không cần MySQL sống** — kết xuất đọc metadata của entity, không đọc database.

### Kết nối

Datasource đọc `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` từ biến môi trường; **không commit mật khẩu
vào repo**. Chạy dev:

```bash
DB_PASSWORD=<mat-khau-mysql> mvn -pl nss-start spring-boot:run
```

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
