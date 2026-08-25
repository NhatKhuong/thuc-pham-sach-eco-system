package com.nss;

import com.nss.ddd.domain.model.TextNormalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@link TextNormalizer} — <b>bản duy nhất</b> của phép bỏ dấu để tìm kiếm
 * ({@code coding-conventions.md} §18).
 * <p>
 * <b>Ca quan trọng nhất ở đây là chữ {@code đ}, và nó quan trọng vì một lý do đo được:</b> collation
 * {@code utf8mb4_unicode_ci} của MySQL gập được hoa/thường và dấu thanh, nhưng <b>không</b> gập
 * {@code đ}. Đo trên chính container của dự án ngày 2026-08-25:
 * <ul>
 *   <li>{@code 'Nguyễn Văn An' LIKE '%nguyen%'} {@literal ->} <b>1</b> (control dương: collation
 *       <i>có</i> gập dấu thanh);</li>
 *   <li>{@code 'Đậu Hà Lan' LIKE '%dau%'} {@literal ->} <b>0</b>;</li>
 *   <li>{@code 'Đậu Hà Lan' LIKE '%xyz%'} {@literal ->} <b>0</b> (control âm).</li>
 * </ul>
 * Nghĩa là <b>không mượn được collation để khỏi phải làm bước 3</b>, và cột {@code *_normalized}
 * phải tự mang giá trị đã đổi {@code đ} {@literal ->} {@code d}. {@code Đỗ}, {@code Đặng},
 * {@code Đào}, {@code Đinh} là những họ Việt rất phổ biến nên phần trượt không phải một góc hiếm.
 * <p>
 * <b>{@code Normalizer} với NFD KHÔNG tách được {@code đ}</b> — nó là một chữ cái Latin riêng chứ
 * không phải một {@code d} có dấu. Đó là lý do bước 3 phải viết tay, và là lý do ca dưới đây khoá
 * cả bốn dạng: {@code đ} thường, {@code Đ} hoa, và cả hai lẫn với dấu thanh.
 */
class TextNormalizerTest {

    /**
     * @param input chuỗi nguồn
     * @param expected chuỗi đã bỏ dấu, hạ chữ thường
     */
    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource({
            "Nguyễn Văn An,      nguyen van an",
            "Lê Thị Bích,        le thi bich",
            "Đỗ Thị Hoa,         do thi hoa",
            "Đậu Hà Lan,         dau ha lan",
            "ĐẶNG ĐÌNH ĐỘ,       dang dinh do",
            "đường phèn,         duong phen",
            "Quản trị hệ thống,  quan tri he thong",
            "Ca Rot,             ca rot",
            "0901234567,         0901234567"
    })
    @DisplayName("Bo dau, ha chu thuong, va doi CA HAI dang cua chu D-co-gach-ngang")
    void normalizesVietnameseText(String input, String expected) {
        assertEquals(expected, TextNormalizer.genNormalized(input));
    }

    /**
     * <b>Bước 3 nhìn thấy được: chữ {@code đ} phải biến mất khỏi kết quả.</b>
     * <p>
     * Ca này khoá đúng thứ mà {@code Normalizer} NFD một mình <i>không</i> làm được. Bỏ bước
     * {@code đ} {@literal ->} {@code d} thì hai chuỗi dưới đây vẫn còn ký tự {@code đ} và mọi phép
     * so với {@code "dau"} / {@code "do"} sẽ trượt.
     */
    @Test
    @DisplayName("Chu D-co-gach-ngang KHONG con sot lai trong ket qua")
    void strokedDIsAlwaysReplaced() {
        String normalized = TextNormalizer.genNormalized("Đậu đỏ Đà Lạt");
        assertEquals("dau do da lat", normalized);
        // Khang dinh truc tiep: khong ky tu nao trong ket qua la chu D-co-gach-ngang
        assertEquals(-1, normalized.indexOf('đ'), "Chu 'd' co gach ngang khong duoc sot lai");
        assertEquals(-1, normalized.indexOf('Đ'), "Chu 'D' co gach ngang khong duoc sot lai");
    }

    /**
     * {@code genSearchKeyword} <b>trim rồi bỏ dấu</b>, và chuỗi rỗng cho {@code null} — tức
     * <i>không lọc</i>.
     * <p>
     * Trả chuỗi rỗng thì mẫu {@code LIKE '%%'} khớp mọi dòng — cùng kết quả nhưng đi qua một câu
     * SQL không dùng được index; và nếu về sau có ai đổi sang so bằng thì nó khớp <i>không</i> dòng
     * nào. {@code null} nói đúng ý định ngay tại kiểu dữ liệu.
     *
     * @param blank chuỗi rỗng hoặc toàn khoảng trắng
     */
    @ParameterizedTest(name = "genSearchKeyword([{0}]) -> null")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Tu khoa rong cho null — tuc KHONG loc, khong phai khop moi dong")
    void blankKeywordMeansNoFilter(String blank) {
        assertNull(TextNormalizer.genSearchKeyword(blank));
    }

    /**
     * Từ khoá và giá trị trong cột đi qua <b>cùng một hàm</b> — đó là toàn bộ lý do §18 cấm chép nó
     * ra bản thứ hai.
     * <p>
     * Ca này khẳng định điều đó ở dạng kiểm được: chuỗi người dùng gõ (có dấu, thừa khoảng trắng,
     * hoa thường lẫn lộn) sau khi chuẩn hoá phải là <b>chuỗi con</b> của giá trị đang nằm trong cột.
     */
    @Test
    @DisplayName("Tu khoa da chuan hoa la CHUOI CON cua gia tri trong cot — hai ve cung mot ham")
    void keywordMatchesStoredValue() {
        String stored = TextNormalizer.genNormalized("Đỗ Thị Hoa");
        assertEquals("do thi hoa", stored);
        assertEquals("do thi hoa", TextNormalizer.genSearchKeyword("  Đỗ Thị Hoa  "));
        for (String typed : new String[]{"do thi", "DO THI HOA", "Đỗ Thị", "thi hoa", "Hoa"}) {
            assertTrue(stored.contains(TextNormalizer.genSearchKeyword(typed)),
                    "Tu khoa [" + typed + "] phai khop gia tri dang nam trong cot");
        }
    }

    /** Null-guard: {@code null} vào cho {@code null} ra. */
    @Test
    @DisplayName("null vao cho null ra")
    void nullIsGuarded() {
        assertNull(TextNormalizer.genNormalized(null));
        assertNull(TextNormalizer.genSearchKeyword(null));
    }
}
