package com.nss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm schema <b>thật sự nằm trong MySQL</b>, không kiểm annotation.
 * <p>
 * Khoảng cách giữa hai thứ đó là chỗ lỗi hay trốn: một {@code @Index} viết sai tên cột vẫn
 * biên dịch được, một {@code columnDefinition} sai cú pháp vẫn qua được mọi test không chạm DB.
 * Nên mọi khẳng định ở đây đều đọc từ {@code information_schema} sau khi Hibernate đã tạo bảng.
 * <p>
 * <b>Đánh {@code @Tag("db")} và bị surefire loại khỏi build mặc định</b> — nó cần một MySQL sống,
 * mà điều đó không được phép là điều kiện để {@code mvn clean package} chạy xanh. Chạy riêng bằng:
 * <pre>
 * mvn -pl nss-start test -Dexcluded.test.groups= -Dgroups=db
 * </pre>
 * Dùng {@link DataSource} và JDBC trần chứ không dùng {@code JdbcTemplate}
 * ({@code coding-conventions.md} §12 cấm), cũng không qua JPA — vì thứ cần kiểm ở đây chính là
 * lớp mà JPA vừa sinh ra.
 */
@SpringBootTest
@Tag("db")
class SchemaSmokeTest {

    /**
     * 5 (sản phẩm) + 7 (user & quyền) + 5 (mua hàng) + 3 (địa giới).
     * <p>
     * <b>19 → 20 ở backlog 0017, và con số này chỉ được đổi khi có một ADR chống lưng.</b> Nó tồn
     * tại để chặn việc thêm bảng <i>lặng lẽ</i>: {@code ddl-auto: update} khiến một entity mới biến
     * thành một bảng mới mà không ai phải phê duyệt gì, và ADR 0003 dòng 41 nêu đúng ví dụ
     * {@code password_reset_token} làm tín hiệu phải quay lại hỏi PM. ADR 0004 là chỗ Owner duyệt
     * tường minh <b>đúng một bảng</b> đó — không phải một giấy phép chung. Bảng tiếp theo vẫn phải
     * quay lại hỏi.
     * <p>
     * Sửa hằng số này rồi báo "test vẫn xanh" là làm mất đúng thứ nó bảo vệ.
     */
    private static final int EXPECTED_TABLE_COUNT = 20;

    /**
     * <b>16 → 17 ở backlog 0017</b> — đúng một khoá ngoại mới:
     * {@code password_reset_token.user_id} → {@code user.id} ({@code fk_password_reset_token_user}).
     * <p>
     * Đếm riêng khỏi số bảng vì hai con số hỏng theo hai kiểu khác nhau: một bảng mới <i>quên</i>
     * khoá ngoại vẫn giữ nguyên số bảng đúng, và một dòng token mồ côi thì không có gì phát hiện ra
     * cho tới lúc cần biết nó thuộc về ai.
     */
    private static final int EXPECTED_FOREIGN_KEY_COUNT = 17;

    private final DataSource dataSource;

    @Autowired
    SchemaSmokeTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    @DisplayName("Schema co dung 20 bang")
    void schemaHasExpectedTableCount() throws SQLException {
        String actual = getScalar("SELECT COUNT(*) FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'");
        assertEquals(String.valueOf(EXPECTED_TABLE_COUNT), actual,
                "So bang trong nss_db khong dung " + EXPECTED_TABLE_COUNT);
    }

    @Test
    @DisplayName("Moi bang deu la utf8mb4_unicode_ci")
    void everyTableUsesUnicodeCollation() throws SQLException {
        String offenders = getScalar("SELECT COUNT(*) FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
                + " AND table_collation <> 'utf8mb4_unicode_ci'");
        assertEquals("0", offenders, "Co bang khong dung collation utf8mb4_unicode_ci");
    }

    @Test
    @DisplayName("Moi cot deu co COMMENT")
    void everyColumnHasComment() throws SQLException {
        String offenders = getScalar("SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND column_comment = ''");
        assertEquals("0", offenders, "Co cot thieu COMMENT (architecture muc 3)");
    }

    @Test
    @DisplayName("Khoa ngoai ton tai that trong DB")
    void foreignKeysExistInDatabase() throws SQLException {
        String actual = getScalar("SELECT COUNT(*) FROM information_schema.key_column_usage"
                + " WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL");
        assertEquals(String.valueOf(EXPECTED_FOREIGN_KEY_COUNT), actual,
                "So khoa ngoai khong dung ky vong");
    }

    @Test
    @DisplayName("customer_order.user_id nullable cho don khach vang lai")
    void customerOrderUserIdIsNullable() throws SQLException {
        String actual = getScalar("SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = 'customer_order'"
                + " AND column_name = 'user_id'");
        assertEquals("YES", actual, "user_id phai nullable — don khach vang lai khong co chu don");
    }

    @Test
    @DisplayName("product.effective_price la cot sinh STORED")
    void productEffectivePriceIsStoredGeneratedColumn() throws SQLException {
        String extra = getScalar("SELECT extra FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = 'product'"
                + " AND column_name = 'effective_price'");
        assertTrue(extra != null && extra.toUpperCase().contains("STORED GENERATED"),
                "effective_price phai la STORED GENERATED, nhan duoc: " + extra);

        String expression = getScalar("SELECT generation_expression FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = 'product'"
                + " AND column_name = 'effective_price'");
        assertTrue(expression != null && expression.toLowerCase().contains("sale_price")
                        && expression.toLowerCase().contains("price"),
                "Bieu thuc cot sinh khong phai COALESCE(sale_price, price): " + expression);
    }

    @Test
    @DisplayName("product co index idx_name_normalized va idx_effective_price")
    void productHasSearchAndPriceIndexes() throws SQLException {
        String actual = getScalar("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics"
                + " WHERE table_schema = DATABASE() AND table_name = 'product'"
                + " AND index_name IN ('idx_name_normalized', 'idx_effective_price')");
        assertEquals("2", actual, "Thieu index cho tim kiem khong dau hoac cho loc/sap xep theo gia");
    }

    /**
     * Chạy một truy vấn trả về đúng một ô và lấy giá trị dạng chuỗi.
     *
     * @return giá trị ô đầu tiên, hoặc {@code null} khi truy vấn không trả dòng nào
     */
    private String getScalar(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }
}
