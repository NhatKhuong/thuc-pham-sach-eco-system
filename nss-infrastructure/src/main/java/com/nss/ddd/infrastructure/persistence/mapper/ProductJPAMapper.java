package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code product} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * Mọi đường đọc <b>phục vụ {@code ProductResponse}</b> đều {@code LEFT JOIN FETCH} {@code category}
 * và {@code brand}. Lý do phải viết ra: {@code open-in-view: false} nên session đóng ngay khi
 * repository trả về, và {@code ProductResponse} cần {@code categoryId} / {@code brandId}. Để lazy
 * thì việc đọc id đi qua proxy — hoặc ném {@code LazyInitializationException}, hoặc bắn thêm một
 * truy vấn cho mỗi sản phẩm. Quan hệ {@code @ManyToOne} nên fetch join không nhân bản dòng và không
 * phá phân trang.
 * <p>
 * <b>Ngoại lệ có chủ ý: {@link #findActiveByIdIn}</b> cố ý <i>không</i> fetch join. Nó phục vụ việc
 * đối chiếu giỏ hàng, vốn chỉ đọc {@code id}, {@code stock} và {@code effectivePrice} — ba cột vô
 * hướng nằm sẵn trên chính dòng {@code product}, không đi qua proxy nào. Kéo thêm hai bảng cho mỗi
 * món trong giỏ là trả giá cho dữ liệu không ai đọc. Ai thêm một trường quan hệ vào đường giỏ hàng
 * sau này thì phải thêm fetch join <i>trong cùng lần sửa</i>, nếu không
 * {@code LazyInitializationException} sẽ nổ ở tầng trên.
 */
public interface ProductJPAMapper extends JpaRepository<Product, Long> {

    /**
     * Mệnh đề lọc của {@code GET /admin/products} — <b>khai một lần, dùng cho CẢ BA truy vấn</b>:
     * trang, đếm-của-trang, và {@code lowStockCount} của §B.12.4.
     * <p>
     * <b>Backlog 0019 rút nó ra thành hằng; trước đó nó là hai chuỗi chép nhau.</b> Lý do phải rút:
     * §B.12.4 chốt {@code lowStockCount} phải <i>bằng</i> {@code total} của
     * {@code ?stockStatus=low_stock}, tức có thêm một chỗ thứ ba phải nói y hệt. Ba bản viết tay
     * thì bản thứ ba là bản sẽ bị quên, và triệu chứng là ô chỉ số nói một đằng còn danh sách lọc
     * ra một nẻo — <b>không lỗi nào nổ ra</b>. Cùng lý lẽ đã áp cho cặp {@code value} /
     * {@code countQuery}: lệch nhau thì {@code items} và {@code total} nói về hai tập khác nhau, và
     * triệu chứng là một cái nút "trang sau" dẫn tới trang trống.
     * <p>
     * <b>Bốn tham số đều nullable và {@code null} nghĩa là KHÔNG lọc theo tiêu chí đó.</b> Cả bốn
     * là kiểu vô hướng ({@code String} / {@code Integer}) chứ không phải collection — cố ý: một
     * {@code IN :ids} với danh sách rỗng dịch ra {@code in ()} và MySQL từ chối cú pháp đó, đúng
     * cái bẫy đã ghi ở {@link #findActiveByIdIn(java.util.Collection)}.
     * <p>
     * <b>Điều kiện danh mục là {@code c.slug = :slug OR cp.slug = :slug}</b>, tức "chính nó hoặc
     * con trực tiếp của nó" — tương đương chính xác với {@code resolveCategoryIds} của frontend
     * ({@code adminProducts.api.ts:35-42}) vì {@code uk_slug} bảo đảm slug danh mục là duy nhất.
     * Slug không khớp danh mục nào thì không dòng nào thoả, tức <b>tập rỗng</b>. Cháu (cấp 2) nằm
     * ngoài phạm vi ở cả hai phía.
     * <p>
     * <b>Hai alias {@code c} / {@code cp} phải do câu truy vấn gọi nó khai bằng LEFT JOIN tường
     * minh, không dùng đường dẫn ngầm {@code p.category.parent.slug}.</b> Đây là chỗ sai im lặng
     * nguy hiểm nhất: Hibernate dịch một đường dẫn ngầm qua quan hệ to-one trong {@code WHERE}
     * thành <b>INNER JOIN</b>, và INNER JOIN lọc toàn bộ tập dòng <i>trước khi</i> mệnh đề
     * {@code OR} được tính. Hệ quả là mọi sản phẩm thuộc danh mục gốc ({@code parent_id IS NULL})
     * biến mất khỏi kết quả — kể cả khi không lọc theo danh mục. Không exception, không cảnh báo,
     * chỉ là thiếu dòng.
     * <p>
     * <b>{@code ESCAPE '!'} chứ không phải dấu gạch chéo ngược.</b> Nếu không escape, một
     * {@code q} chứa {@code %} hoặc {@code _} trở thành ký tự đại diện và bộ lọc âm thầm trả về
     * nhiều dòng hơn số dòng thật sự khớp. Chọn {@code !} vì gạch chéo ngược còn là ký tự escape
     * của chính chuỗi MySQL, nên nó phải nhân đôi qua hai tầng và rất dễ đếm nhầm.
     */
    String ADMIN_FILTER = " WHERE p.isActive = true"
            + " AND (:pattern IS NULL"
            + "      OR p.nameNormalized LIKE :pattern ESCAPE '!'"
            + "      OR p.slug LIKE :pattern ESCAPE '!')"
            + " AND (:categorySlug IS NULL OR c.slug = :categorySlug OR cp.slug = :categorySlug)"
            + " AND (:minStock IS NULL OR p.stock >= :minStock)"
            + " AND (:maxStock IS NULL OR p.stock <= :maxStock)";

    /**
     * Một trang sản phẩm còn hiệu lực <b>có lọc</b> — đường đọc của {@code GET /admin/products}
     * (API_CONTRACT §B.12.1).
     * <p>
     * <b>KHÔNG có {@code ORDER BY} trong chuỗi truy vấn.</b> Thứ tự ở đây do client chọn (5 giá trị của
     * {@code sort}), nên nó phải đi vào qua {@code Sort} của {@code Pageable} — Spring Data nối
     * mệnh đề {@code order by} vào cuối. Nhúng cứng một {@code ORDER BY} vào chuỗi rồi lại truyền
     * {@code Sort} sẽ cho ra <i>hai</i> mệnh đề chồng nhau, và mệnh đề nhúng cứng thắng: mọi giá trị
     * {@code sort} trả về cùng một thứ tự, HTTP 200, không lỗi nào. Tính ổn định của phân trang
     * được giữ bằng cách {@code ProductRepositoryImpl} luôn kèm một khoá phụ theo {@code id}.
     * <p>
     * <b>Điều kiện lọc nằm ở {@link #ADMIN_FILTER}</b> — một chuỗi dùng chung cho truy vấn trang,
     * truy vấn đếm, và {@link #countAdminProducts}. Đọc javadoc của nó trước khi sửa bất cứ mệnh đề
     * nào ở đây.
     * <p>
     * {@code countQuery} khai tường minh — Spring Data không suy được câu đếm từ truy vấn có
     * {@code JOIN FETCH}; nó dùng lại đúng {@link #ADMIN_FILTER} nhưng bỏ phần {@code FETCH}, vì
     * đếm thì không cần nạp quan hệ nào.
     *
     * @param pattern mẫu {@code LIKE} đã bọc {@code %} và đã escape; {@code null} là không tìm
     * @param categorySlug slug danh mục cha hoặc con; {@code null} là không lọc
     * @param minStock tồn kho tối thiểu, đã bao gồm; {@code null} là không chặn dưới
     * @param maxStock tồn kho tối đa, đã bao gồm; {@code null} là không chặn trên
     * @param pageable trang cần lấy, <b>đã đánh số từ 0</b> và <b>đã mang {@code Sort}</b>
     * @return trang sản phẩm kèm tổng số dòng khớp điều kiện lọc
     */
    @Query(value = "SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category c"
            + " LEFT JOIN FETCH p.brand"
            + " LEFT JOIN c.parent cp"
            + ADMIN_FILTER,
            countQuery = "SELECT COUNT(p) FROM Product p"
                    + " LEFT JOIN p.category c"
                    + " LEFT JOIN c.parent cp"
                    + ADMIN_FILTER)
    Page<Product> findAdminPage(@Param("pattern") String pattern,
                                @Param("categorySlug") String categorySlug,
                                @Param("minStock") Integer minStock,
                                @Param("maxStock") Integer maxStock,
                                Pageable pageable);

    /**
     * Đếm sản phẩm khớp <b>đúng</b> {@link #ADMIN_FILTER} — nguồn của {@code lowStockCount}
     * (§B.12.4).
     * <p>
     * <b>Tồn tại để {@code lowStockCount} và {@code total} của
     * {@code GET /admin/products?stockStatus=low_stock} không bao giờ lệch nhau.</b> Hợp đồng chốt
     * hai chỗ đó dùng đúng một ngưỡng; cho cả hai đi qua cùng một mệnh đề lọc là cách giữ điều đó
     * đúng theo <i>cấu tạo</i> thay vì theo may mắn. Lệch nhau thì ô chỉ số nói một đằng, danh sách
     * lọc ra một nẻo, và không có lỗi nào nổ ra.
     * <p>
     * Hai {@code LEFT JOIN} vẫn phải có dù phép đếm không cần dữ liệu của chúng: mệnh đề
     * {@code :categorySlug} tham chiếu alias {@code c} và {@code cp}. Bỏ chúng đi thì chuỗi dùng
     * chung không còn dùng chung được nữa.
     *
     * @param pattern mẫu {@code LIKE}; {@code null} là không tìm — {@code lowStockCount} luôn truyền
     *                {@code null}
     * @param categorySlug slug danh mục; {@code null} là không lọc
     * @param minStock tồn kho tối thiểu, đã bao gồm; {@code null} là không chặn dưới
     * @param maxStock tồn kho tối đa, đã bao gồm; {@code null} là không chặn trên
     * @return số dòng khớp điều kiện lọc
     */
    @Query("SELECT COUNT(p) FROM Product p"
            + " LEFT JOIN p.category c"
            + " LEFT JOIN c.parent cp"
            + ADMIN_FILTER)
    long countAdminProducts(@Param("pattern") String pattern,
                            @Param("categorySlug") String categorySlug,
                            @Param("minStock") Integer minStock,
                            @Param("maxStock") Integer maxStock);

    /**
     * @param slug slug cần tra
     * @return sản phẩm còn hiệu lực, hoặc rỗng
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.slug = :slug AND p.isActive = true")
    Optional<Product> findActiveBySlug(@Param("slug") String slug);

    /**
     * @param id khóa chính
     * @return sản phẩm còn hiệu lực, hoặc rỗng
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findActiveById(@Param("id") Long id);

    /**
     * Nhiều sản phẩm còn hiệu lực trong một lượt — đường đọc của {@code POST /api/cart/validate}.
     * <p>
     * <b>Không {@code JOIN FETCH}</b>: xem javadoc của interface. Không {@code ORDER BY} vì phía
     * gọi đánh chỉ mục theo id chứ không duyệt tuần tự, và một {@code ORDER BY} thừa trên đường
     * nóng chỉ tốn công sắp xếp cho thứ tự không ai đọc.
     * <p>
     * <b>Phía gọi phải chặn danh sách rỗng trước khi tới đây.</b> {@code IN :ids} với một
     * collection rỗng dịch ra {@code in ()}, và MySQL từ chối cú pháp đó bằng một
     * {@code SQLSyntaxErrorException} — một lỗi 500 cho ca giỏ rỗng, vốn là ca hợp lệ nhất trong cả
     * endpoint. Chỗ chặn nằm ở {@code ProductRepositoryImpl}.
     *
     * @param ids các khóa chính cần tra, <b>không rỗng</b>
     * @return các sản phẩm còn hiệu lực khớp {@code ids}, thứ tự không đảm bảo
     */
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.isActive = true")
    List<Product> findActiveByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Đếm cả bản ghi đã xoá mềm — {@code uk_slug} nằm trên toàn bảng, không quan tâm {@code is_active}.
     *
     * @param slug slug cần kiểm
     * @return true nếu đã có dòng nào giữ slug này
     */
    boolean existsBySlug(String slug);

    /**
     * Xoá mềm bằng UPDATE có điều kiện — {@code AND p.isActive = true} khiến lần gọi thứ hai trả 0
     * dòng, nhờ đó tầng trên phân biệt được "đã xoá xong" với "không có gì để xoá".
     *
     * @param id khóa chính
     * @param deletedAt thời điểm xoá, giờ UTC
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.isActive = false, p.updatedAt = :deletedAt"
            + " WHERE p.id = :id AND p.isActive = true")
    int markInactive(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * {@code shortDescription} đã thay {@code đ}/{@code Đ} bằng {@code d}/{@code D}, dùng làm vế trái
     * cho mọi so khớp {@code q} trên cột này.
     * <p>
     * <b>{@code shortDescription} không có cột {@code *_normalized} riêng như {@code name}</b> —
     * chưa từng cần tới cho tới ticket này. So trực tiếp {@code p.shortDescription LIKE :pattern}
     * (với {@code pattern} đã bỏ dấu ở domain service) vẫn đúng cho phần lớn ký tự nhờ
     * {@code utf8mb4_unicode_ci} tự gập hoa/thường và <b>hầu hết</b> dấu thanh/dấu móc (đo được: đủ
     * để {@code q=nuoc} khớp "nước", {@code q=cam sanh} khớp "cam sành") — <b>trừ đúng một chữ cái:
     * {@code đ}</b>, y hệt giới hạn đã ghi ở {@code coding-conventions.md} §18. Thiếu {@code REPLACE}
     * này, {@code q=khong duong} sẽ KHÔNG khớp một {@code shortDescription} chứa "không đường" — một
     * lỗi lọt lưới khi đo trực tiếp trên dữ liệu seed thật (backlog 0024).
     * <p>
     * <b>Chỉ cần một chiều {@code đ}{@literal ->}{@code d}, không cần bốn bước đầy đủ của
     * {@code TextNormalizer}.</b> Vế {@code :pattern} đã đi qua đủ bốn bước ở domain service rồi;
     * collation lo phần hoa/thường và phần lớn dấu thanh/dấu móc. {@code REPLACE} ở đây chỉ vá đúng
     * lỗ hổng còn lại của collation, không phải một bản sao thứ hai của phép bỏ dấu.
     */
    String SHORT_DESCRIPTION_D_FOLDED =
            "REPLACE(REPLACE(p.shortDescription, 'đ', 'd'), 'Đ', 'D')";

    /**
     * Mệnh đề lọc của {@code GET /products} công khai (API_CONTRACT §B.1) — khai một lần, dùng cho
     * CẢ HAI truy vấn: trang và đếm-của-trang.
     * <p>
     * <b>Khác {@link #ADMIN_FILTER} ở hai chỗ, cả hai đều cố ý</b> (xem javadoc
     * {@code ProductControllerMapper} và {@code PublicProductFilter}):
     * <ul>
     *   <li>Mẫu {@code LIKE} khớp {@code p.nameNormalized} HOẶC {@link #SHORT_DESCRIPTION_D_FOLDED} —
     *       <b>không</b> khớp {@code p.slug} như bên admin;</li>
     *   <li>Thêm bốn cặp điều kiện giá/đánh giá/tồn kho/cờ mà bên quản trị không có, và bớt
     *       {@code stockStatus} ba-trạng-thái (thay bằng {@code inStockOnly} boolean).</li>
     * </ul>
     * <b>Điều kiện danh mục dùng lại đúng mệnh đề của {@link #ADMIN_FILTER}</b>
     * ({@code c.slug = :categorySlug OR cp.slug = :categorySlug}) — cùng quy ước "một cấp con".
     * <p>
     * Bốn cờ boolean nhận kiểu {@code Boolean} chứ không {@code boolean}: {@code null} và
     * {@code false} đều nghĩa là không lọc, viết {@code :flag IS NULL OR :flag = false OR <điều kiện>}
     * để không phải ép JPQL nhận {@code null} vào một so sánh {@code = true}.
     */
    String PUBLIC_FILTER = " WHERE p.isActive = true"
            + " AND (:pattern IS NULL"
            + "      OR p.nameNormalized LIKE :pattern ESCAPE '!'"
            + "      OR " + SHORT_DESCRIPTION_D_FOLDED + " LIKE :pattern ESCAPE '!')"
            + " AND (:categorySlug IS NULL OR c.slug = :categorySlug OR cp.slug = :categorySlug)"
            + " AND (:minPrice IS NULL OR p.effectivePrice >= :minPrice)"
            + " AND (:maxPrice IS NULL OR p.effectivePrice <= :maxPrice)"
            + " AND (:minRating IS NULL OR p.rating >= :minRating)"
            + " AND (:inStockOnly IS NULL OR :inStockOnly = false OR p.stock > 0)"
            + " AND (:onSaleOnly IS NULL OR :onSaleOnly = false OR p.salePrice IS NOT NULL)"
            + " AND (:isFeatured IS NULL OR :isFeatured = false OR p.isFeatured = true)"
            + " AND (:isBestSeller IS NULL OR :isBestSeller = false OR p.isBestSeller = true)";

    /**
     * Một trang sản phẩm còn hiệu lực <b>có lọc</b> — đường đọc của {@code GET /products} công khai
     * (API_CONTRACT §B.1).
     * <p>
     * Cùng kỷ luật với {@link #findAdminPage}: KHÔNG {@code ORDER BY} nhúng cứng trong chuỗi truy
     * vấn — thứ tự do client chọn đi vào qua {@code Sort} của {@code Pageable}, và
     * {@code ProductRepositoryImpl} luôn kèm khoá phụ theo {@code id} để phân trang ổn định.
     * <p>
     * {@code countQuery} khai tường minh — Spring Data không suy được câu đếm từ truy vấn có
     * {@code JOIN FETCH}; nó dùng lại đúng {@link #PUBLIC_FILTER} nhưng bỏ phần {@code FETCH}.
     *
     * @param pattern mẫu {@code LIKE} đã bọc {@code %} và đã escape; {@code null} là không tìm
     * @param categorySlug slug danh mục cha hoặc con; {@code null} là không lọc
     * @param minPrice giá thấp nhất theo {@code effectivePrice}, đã bao gồm; {@code null} là không chặn dưới
     * @param maxPrice giá cao nhất theo {@code effectivePrice}, đã bao gồm; {@code null} là không chặn trên
     * @param minRating điểm đánh giá thấp nhất, đã bao gồm; {@code null} là không chặn
     * @param inStockOnly {@code true} = chỉ còn hàng; {@code null}/{@code false} = không lọc
     * @param onSaleOnly {@code true} = chỉ đang giảm giá; {@code null}/{@code false} = không lọc
     * @param isFeatured {@code true} = chỉ nổi bật; {@code null}/{@code false} = không lọc
     * @param isBestSeller {@code true} = chỉ bán chạy; {@code null}/{@code false} = không lọc
     * @param pageable trang cần lấy, <b>đã đánh số từ 0</b> và <b>đã mang {@code Sort}</b>
     * @return trang sản phẩm kèm tổng số dòng khớp điều kiện lọc
     */
    @Query(value = "SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category c"
            + " LEFT JOIN FETCH p.brand"
            + " LEFT JOIN c.parent cp"
            + PUBLIC_FILTER,
            countQuery = "SELECT COUNT(p) FROM Product p"
                    + " LEFT JOIN p.category c"
                    + " LEFT JOIN c.parent cp"
                    + PUBLIC_FILTER)
    Page<Product> findPublicPage(@Param("pattern") String pattern,
                                 @Param("categorySlug") String categorySlug,
                                 @Param("minPrice") Long minPrice,
                                 @Param("maxPrice") Long maxPrice,
                                 @Param("minRating") BigDecimal minRating,
                                 @Param("inStockOnly") Boolean inStockOnly,
                                 @Param("onSaleOnly") Boolean onSaleOnly,
                                 @Param("isFeatured") Boolean isFeatured,
                                 @Param("isBestSeller") Boolean isBestSeller,
                                 Pageable pageable);

    /**
     * Sản phẩm cùng danh mục — nguồn của {@code GET /products/{slug}/related} (API_CONTRACT §B.1).
     * <p>
     * <b>Thứ tự cố định (bán chạy, rồi đánh giá, rồi id), không nhận {@code sort} của client</b> —
     * đây là gợi ý tại chỗ, không phải một trang duyệt. Khoá phụ theo {@code id} giữ kết quả ổn định
     * giữa các lần gọi khi nhiều sản phẩm hoà {@code sold}/{@code rating}.
     * <p>
     * Method trả {@code List} kèm tham số {@code Pageable} — quy ước của Spring Data để giới hạn số
     * dòng mà không cần một {@code Page} đầy đủ (không ai đọc {@code totalElements} ở đây).
     *
     * @param categoryId danh mục của sản phẩm gốc
     * @param excludeId id của sản phẩm gốc, loại khỏi kết quả
     * @param pageable chỉ dùng để giới hạn số dòng ({@code PageRequest.of(0, limit)})
     * @return sản phẩm cùng danh mục còn hiệu lực, tối đa {@code pageable.getPageSize()} phần tử
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.category.id = :categoryId AND p.id <> :excludeId AND p.isActive = true"
            + " ORDER BY p.sold DESC, p.rating DESC, p.id ASC")
    List<Product> findRelated(@Param("categoryId") Long categoryId, @Param("excludeId") Long excludeId,
                              Pageable pageable);

    /**
     * Gợi ý tìm kiếm — nguồn của {@code GET /products/suggest} (API_CONTRACT §B.1).
     * <p>
     * Cùng mẫu {@code LIKE} với {@link #PUBLIC_FILTER}, kể cả phần {@link #SHORT_DESCRIPTION_D_FOLDED}:
     * khớp {@code nameNormalized} HOẶC {@code shortDescription} (đã thay {@code đ}{@literal ->}{@code d}).
     * Sắp theo {@code sold DESC} — gợi ý ưu tiên sản phẩm phổ biến trước — kèm khoá phụ theo {@code id}.
     *
     * @param pattern mẫu {@code LIKE} đã bọc {@code %} và đã escape; {@code null} là không tìm (trả rỗng)
     * @param pageable chỉ dùng để giới hạn số dòng ({@code PageRequest.of(0, limit)})
     * @return sản phẩm còn hiệu lực khớp {@code pattern}, tối đa {@code pageable.getPageSize()} phần tử
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.isActive = true"
            + "      AND :pattern IS NOT NULL"
            + "      AND (p.nameNormalized LIKE :pattern ESCAPE '!' OR " + SHORT_DESCRIPTION_D_FOLDED
            + " LIKE :pattern ESCAPE '!')"
            + " ORDER BY p.sold DESC, p.id ASC")
    List<Product> findSuggestions(@Param("pattern") String pattern, Pageable pageable);

    /**
     * Giá thấp nhất trong các sản phẩm còn hiệu lực — nửa {@code min} của
     * {@code GET /products/price-range} (API_CONTRACT §B.1).
     *
     * @return giá thấp nhất theo {@code effectivePrice}, hoặc {@code null} khi không có sản phẩm nào
     */
    @Query("SELECT MIN(p.effectivePrice) FROM Product p WHERE p.isActive = true")
    Long findMinEffectivePrice();

    /**
     * Giá cao nhất trong các sản phẩm còn hiệu lực — nửa {@code max} của
     * {@code GET /products/price-range} (API_CONTRACT §B.1).
     *
     * @return giá cao nhất theo {@code effectivePrice}, hoặc {@code null} khi không có sản phẩm nào
     */
    @Query("SELECT MAX(p.effectivePrice) FROM Product p WHERE p.isActive = true")
    Long findMaxEffectivePrice();

    /**
     * Trừ tồn kho bằng <b>conditional UPDATE</b> (backlog 0014 §Contract 8).
     * <p>
     * <b>Vế {@code p.stock >= :quantity} là toàn bộ cơ chế chống bán quá kho, và nó phải nằm trong
     * chính câu UPDATE này.</b> Đọc tồn kho, so trong Java, rồi mới ghi sẽ để lại một cửa sổ giữa
     * hai bước: hai request đồng thời cùng đọc thấy còn 1 sẽ cùng ghi thành -1, và không ràng buộc
     * nào của MySQL chặn lại. Ở đây engine khoá dòng ngay trong UPDATE, nên bên thua nhận về 0 dòng
     * ảnh hưởng và tầng trên biến nó thành 409 kèm rollback.
     * <p>
     * <b>Không {@code clearAutomatically}, không {@code flushAutomatically}.</b> Luồng tạo đơn đọc
     * {@code Product} <i>trước</i> bước này và không bao giờ đọc lại {@code stock} sau đó, nên con
     * số tồn kho cũ còn nằm trong persistence context không đi vào phép tính nào. Bật
     * {@code clearAutomatically} sẽ đẩy mọi entity khác của cùng transaction ra khỏi context và
     * biến một luồng đang đúng thành một luồng phải nạp lại tất cả.
     *
     * @param id khóa chính của sản phẩm
     * @param quantity số lượng cần trừ, phải dương
     * @return số dòng bị ảnh hưởng — {@code 1} là trừ được; {@code 0} là không đủ tồn kho, id không
     *         tồn tại, hoặc sản phẩm đã bị xoá mềm
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity"
            + " WHERE p.id = :id AND p.isActive = true AND p.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * Hoàn tồn kho bằng UPDATE — đối xứng {@link #decreaseStock}, không cần vế {@code >=} vì là phép
     * cộng (backlog 0035 Phase 2, §Contract của ticket).
     *
     * @param id khóa chính của sản phẩm
     * @param quantity số lượng cần hoàn, phải dương
     * @return số dòng bị ảnh hưởng — {@code 1} là hoàn được; {@code 0} là id không tồn tại hoặc sản
     *         phẩm đã bị xoá mềm
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity"
            + " WHERE p.id = :id AND p.isActive = true")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
