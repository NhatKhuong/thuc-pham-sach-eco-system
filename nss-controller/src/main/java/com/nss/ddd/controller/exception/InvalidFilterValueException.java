package com.nss.ddd.controller.exception;

/**
 * Tham số lọc kiểu <b>tập đóng</b> (enum) nhận một giá trị không nằm trong tập hợp lệ — vế 1 của
 * [ADR 0007](../../../../../../../management/decisions/0007-tham-so-loc-gia-tri-la.md).
 * <p>
 * <b>Khác {@code InvalidProductDataException}: đây LÀ lỗi validate theo trường, không phải lỗi quy
 * tắc nghiệp vụ.</b> Nó có một tham số cụ thể để chỉ vào ({@link #parameterName}), nên
 * {@code GlobalExceptionHandler} phải trả 422 <b>kèm</b> map {@code errors} — đúng hình dạng của
 * {@code MethodArgumentNotValidException}, chỉ khác nguồn: tham số lọc đến từ query string
 * ({@code @RequestParam}), không đi qua {@code @Valid @RequestBody} nên
 * {@code MethodArgumentNotValidException} không bắt được ca này (ADR 0007 mục "Phải canh chừng").
 * <p>
 * <b>Hiện chỉ áp cho {@code sort} của {@code GET /products}.</b> {@code category}/{@code q}/
 * {@code ids} là tập <i>mở</i> — giá trị lạ của chúng cho ra tập rỗng bằng chính cấu tạo của mệnh đề
 * SQL, không cần ném exception nào (ADR 0007 vế 2). Ba endpoint admin cùng lớp lỗi này
 * ({@code stockStatus}, {@code sort} của {@code /admin/products}) thuộc phạm vi backlog 0025, ticket
 * riêng vì đó là contract change trên bề mặt đang chạy.
 */
public class InvalidFilterValueException extends RuntimeException {

    /** Tên tham số trên dây — trở thành khoá của map {@code errors}. */
    private final String parameterName;

    /**
     * @param parameterName tên tham số trên dây, ví dụ {@code "sort"}
     * @param message thông điệp tiếng Việt cho người dùng cuối, liệt kê giá trị hợp lệ
     */
    public InvalidFilterValueException(String parameterName, String message) {
        super(message);
        this.parameterName = parameterName;
    }

    /**
     * @return tên tham số trên dây — trở thành khoá của map {@code errors}
     */
    public String getParameterName() {
        return parameterName;
    }
}
