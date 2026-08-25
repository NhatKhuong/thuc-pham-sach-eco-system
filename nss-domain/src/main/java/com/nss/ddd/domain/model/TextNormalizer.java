package com.nss.ddd.domain.model;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Phép <b>bỏ dấu để tìm kiếm</b> — bản duy nhất trong toàn dự án
 * ({@code coding-conventions.md} §18).
 * <p>
 * <b>Vì sao nó phải là một class dùng chung thay vì một private helper.</b> §18 nói rõ hàm này có
 * đúng <i>một</i> bản, vì giá trị được sinh ở một nơi (lúc ghi, đổ vào cột {@code *_normalized}) và
 * được đối chiếu ở một nơi khác (lúc đọc, chuẩn hoá tham số {@code q}). Từ backlog 0019 trở đi có
 * <b>ba</b> cột dùng chung phép này — {@code product.name_normalized},
 * {@code customer_order.full_name_normalized} và {@code user.full_name_normalized} — nên chỗ ở của
 * nó không còn là một domain service cụ thể nào. Chép sang bản thứ hai là cách chắc chắn để hai bên
 * lệch nhau vào đúng lúc chỉ một bên được sửa, và triệu chứng là <i>"tìm không ra"</i> chứ không
 * phải một lỗi.
 * <p>
 * <b>Bốn bước, đúng thứ tự này</b> (§18):
 * <ol>
 *   <li>{@link Normalizer#normalize} với {@code NFD} — tách dấu thanh khỏi nguyên âm;</li>
 *   <li>bỏ dải {@code \p{InCombiningDiacriticalMarks}};</li>
 *   <li><b>{@code đ} → {@code d}, {@code Đ} → {@code D}</b> — bước hay bị quên nhất;</li>
 *   <li>{@code toLowerCase()}.</li>
 * </ol>
 * Bước 3 phải viết tay vì {@code đ} <b>không phải</b> một {@code d} có dấu — nó là một chữ cái Latin
 * riêng và NFD không tách nó ra được. Đo trên chính MySQL của dự án, collation
 * {@code utf8mb4_unicode_ci} gập được hoa/thường và dấu thanh nhưng <b>không</b> gập {@code đ}:
 * {@code 'Nguyễn Văn An' LIKE '%nguyen%'} ra {@code 1}, còn {@code 'Đậu Hà Lan' LIKE '%dau%'} ra
 * {@code 0} (control âm {@code '%xyz%'} cũng ra {@code 0}). Nghĩa là <b>không mượn được collation
 * để khỏi phải làm bước này</b>.
 * <p>
 * Class tiện ích stateless, method {@code public static}, không phải Spring bean — cùng khuôn với
 * các {@code *Mapper} viết tay (coding-conventions §7).
 */
public final class TextNormalizer {

    /** Dấu thanh và dấu phụ sau khi tách bằng NFD — bảng Unicode "Combining Diacritical Marks". */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Class tiện ích, không có thể hiện.
     */
    private TextNormalizer() {
    }

    /**
     * Bỏ dấu và hạ chữ thường — giá trị đi vào cột {@code *_normalized}.
     *
     * @param text chuỗi nguồn
     * @return chuỗi đã bỏ dấu và hạ chữ thường, hoặc {@code null} khi {@code text} rỗng
     */
    public static String genNormalized(String text) {
        if (text == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return withoutMarks
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();
    }

    /**
     * Chuẩn hoá tham số {@code q} về đúng dạng đang nằm trong cột {@code *_normalized}.
     * <p>
     * <b>Gọi lại {@link #genNormalized(String)} chứ không chép lại phép bỏ dấu</b> — hai vế của một
     * phép so sánh chuỗi phải đi qua cùng một hàm.
     * <p>
     * <b>Chuỗi rỗng hoặc toàn khoảng trắng trả {@code null} — tức KHÔNG lọc.</b> Trả chuỗi rỗng thì
     * mẫu {@code LIKE '%%'} khớp mọi dòng, nghĩa là cùng một kết quả nhưng đi qua một câu SQL không
     * dùng được index; còn nếu về sau có ai đổi sang so bằng thì nó khớp <i>không</i> dòng nào.
     * {@code null} nói đúng ý định ngay tại kiểu dữ liệu.
     *
     * @param keyword từ khoá thô client gửi, có thể {@code null}
     * @return từ khoá đã bỏ dấu và hạ chữ thường, hoặc {@code null} khi không có gì để tìm
     */
    public static String genSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return genNormalized(keyword.trim());
    }
}
