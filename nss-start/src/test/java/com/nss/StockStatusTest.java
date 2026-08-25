package com.nss;

import com.nss.ddd.domain.model.StockStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@link StockStatus} là một <b>phân hoạch</b> thật sự — không chồng lấn, không kẽ hở.
 * <p>
 * <b>Đây là thứ duy nhất bắt được lỗi ranh giới ở bộ lọc tồn kho, và lý do phải kiểm bằng cách này
 * chứ không bằng một request:</b> viết {@code in_stock} thành {@code stock > 0} thay vì
 * {@code stock > 10} cho ra một hệ chạy hoàn toàn bình thường — mọi request trả 200, mọi dòng trả
 * về đều "đúng" theo bộ lọc của chính nó — chỉ có điều {@code low_stock} và {@code in_stock} chồng
 * lên nhau ở khoảng {@code [1, 10]}. Không ràng buộc nào, không exception nào, và không assertion
 * nào về mã trạng thái phát hiện ra. Phép đo bắt được nó là phép <i>đếm</i>: mỗi giá trị tồn kho
 * phải thuộc về <b>đúng một</b> trạng thái.
 */
class StockStatusTest {

    /** Quét từ dưới 0 tới trên ngưỡng một quãng đủ rộng để phủ mọi ranh giới. */
    private static final int SCAN_FROM = -5;

    private static final int SCAN_TO = 30;

    /**
     * @param stock giá trị tồn kho
     * @param status trạng thái cần kiểm
     * @return true nếu {@code stock} nằm trong khoảng của {@code status}
     */
    private static boolean covers(int stock, StockStatus status) {
        Integer min = status.getMinStock();
        Integer max = status.getMaxStock();
        return (min == null || stock >= min) && (max == null || stock <= max);
    }

    @Test
    @DisplayName("Nguong la 10, khop LOW_STOCK_THRESHOLD cua frontend")
    void thresholdIsTen() {
        assertEquals(10, StockStatus.LOW_STOCK_THRESHOLD,
                "Nguong phai khop lib/constants.ts cua frontend; lech la bo loc va nhan hien thi noi hai dang");
    }

    /**
     * <b>Ca quan trọng nhất của file.</b> Mỗi giá trị tồn kho thuộc về đúng một trạng thái — không
     * hai (chồng lấn), không không (kẽ hở).
     */
    @Test
    @DisplayName("Moi gia tri stock thuoc DUNG MOT trang thai — khong chong lan, khong ke ho")
    void everyStockValueBelongsToExactlyOneStatus() {
        for (int stock = SCAN_FROM; stock <= SCAN_TO; stock++) {
            List<StockStatus> matched = new ArrayList<>();
            for (StockStatus status : StockStatus.values()) {
                if (covers(stock, status)) {
                    matched.add(status);
                }
            }
            assertEquals(1, matched.size(),
                    "stock=" + stock + " khop " + matched.size() + " trang thai (" + matched
                            + "), phai khop dung 1");
        }
    }

    /**
     * Ranh giới, viết ra từng con số một.
     * <p>
     * Ca quét ở trên đã chứng minh tính phân hoạch, nhưng một phân hoạch <i>lệch đi một</i> vẫn là
     * một phân hoạch hợp lệ. Bảng này ghim đúng chỗ các ranh giới phải nằm.
     *
     * @param stock giá trị tồn kho
     * @param expected tên trạng thái mong đợi
     */
    @ParameterizedTest(name = "stock={0} -> {1}")
    @CsvSource({
            "-1, OUT_OF_STOCK",
            "0,  OUT_OF_STOCK",
            "1,  LOW_STOCK",
            "9,  LOW_STOCK",
            "10, LOW_STOCK",
            "11, IN_STOCK",
            "24, IN_STOCK"
    })
    @DisplayName("Ranh gioi: 0 la het hang, 10 van la sap het, 11 moi la con hang")
    void boundariesLandOnTheRightStatus(int stock, StockStatus expected) {
        assertTrue(covers(stock, expected), "stock=" + stock + " phai thuoc " + expected);
    }

    /**
     * <b>Control dương cho ca ranh giới: {@code in_stock} là {@code > 10}, KHÔNG phải {@code > 0}.</b>
     * <p>
     * Viết ra thành một ca riêng vì đây là lỗi cụ thể mà ticket cảnh báo, và vì nó đọc được thành
     * lời: nếu ai đó đổi {@code getMinStock()} của {@code IN_STOCK} thành 1, ca này đỏ ngay và
     * thông điệp nói thẳng ra chuyện gì đã xảy ra.
     */
    @Test
    @DisplayName("IN_STOCK bat dau tu 11, KHONG phai tu 1 — day la loi hay gap nhat")
    void inStockStartsAboveThresholdNotAboveZero() {
        assertEquals(11, StockStatus.IN_STOCK.getMinStock(),
                "IN_STOCK phai la stock > 10; viet > 0 thi no chong len LOW_STOCK va khong loi nao no ra");
        assertNull(StockStatus.IN_STOCK.getMaxStock(), "IN_STOCK khong chan tren");
    }

    @Test
    @DisplayName("OUT_OF_STOCK khong chan duoi, LOW_STOCK chan ca hai dau")
    void openAndClosedBoundsAreDeclaredCorrectly() {
        assertNull(StockStatus.OUT_OF_STOCK.getMinStock(), "OUT_OF_STOCK khong chan duoi");
        assertEquals(0, StockStatus.OUT_OF_STOCK.getMaxStock());
        assertEquals(1, StockStatus.LOW_STOCK.getMinStock());
        assertEquals(StockStatus.LOW_STOCK_THRESHOLD, StockStatus.LOW_STOCK.getMaxStock());
    }

    /**
     * Mỗi hằng đều trả lời được cả hai biên — chặn một {@code switch} thiếu nhánh sau này.
     */
    @Test
    @DisplayName("Ca ba hang deu tra loi duoc hai bien, khong nem loi")
    void everyConstantAnswersBothBounds() {
        for (StockStatus status : StockStatus.values()) {
            Integer min = status.getMinStock();
            Integer max = status.getMaxStock();
            assertTrue(min != null || max != null, status + " phai chan it nhat mot dau");
            if (min != null && max != null) {
                assertTrue(min <= max, status + " co bien duoi lon hon bien tren");
            }
        }
        assertNotNull(StockStatus.valueOf("LOW_STOCK"));
    }
}
