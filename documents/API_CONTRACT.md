# Hợp đồng API — đầu bài cho backend Spring Boot

Tài liệu này mô tả **chính xác những gì frontend đang mong đợi**. Nó không phải bản đề xuất — frontend đã được xây và kiểm thử xong theo đúng các shape dưới đây, nên backend khớp được bao nhiêu thì phần frontend phải sửa ít bấy nhiêu.

> **Nguồn chân lý thực sự vẫn là code:** 45 hàm trong `src/api/*.api.ts` và 12 file trong `src/types/`. Mỗi hàm đều có sẵn dòng comment `Khi có backend: ...` ghi đúng lời gọi sẽ thay thế. Tài liệu này gom chúng lại một chỗ để bàn giao.

**Trạng thái:** frontend chạy hoàn toàn bằng mock JSON trong `src/mocks/`. Chưa có một request HTTP thật nào được phát ra.

---

## A. Quy ước chung

### A.1 Base URL và proxy

| Môi trường | `VITE_API_BASE_URL` | Đường đi |
|---|---|---|
| Dev (khuyến nghị) | để **trống** | axios dùng `/api` → proxy của Vite chuyển tiếp sang `http://localhost:8080` → **không dính CORS** |
| Dev (backend ở máy khác) | URL tuyệt đối | trình duyệt gọi thẳng → **backend phải cấu hình CORS** |
| Production | URL tuyệt đối | — |

Proxy khai báo trong `vite.config.ts`. Backend **giữ tiền tố `/api`** cho mọi endpoint dưới đây (`/products` trong bảng nghĩa là `/api/products`).

### A.2 Xác thực

`Authorization: Bearer <accessToken>` — `client.ts` tự gắn vào mọi request khi đã đăng nhập, không hàm nào phải tự làm.

- **Access token** nên có hạn ngắn (15–60 phút).
- **Refresh token** hạn dài hơn, trả trong body khi đăng nhập/đăng ký.
- Gặp **401**, `client.ts` tự gọi `POST /auth/refresh` một lần rồi phát lại request. Thất bại thì xoá phiên và đưa về `/dang-nhap`.

Cột **Auth** trong bảng endpoint: ✅ = bắt buộc có token, ⬜ = công khai.

### A.3 Dạng lỗi — `ProblemDetail` (RFC 7807)

Dạng mặc định của Spring Boot 3, bật bằng `spring.mvc.problemdetails.enabled=true`.

```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Đơn hàng cần tối thiểu 300.000 ₫ để dùng mã này.",
  "instance": "/api/coupons/validate",
  "errors": { "code": "Mã không hợp lệ" }
}
```

> **`detail` PHẢI viết bằng tiếng Việt, dành cho người dùng cuối đọc.**
>
> Đây là điều kiện quan trọng nhất của cả tài liệu. Frontend hiển thị thẳng chuỗi này ở **24 chỗ** (form đăng nhập, đăng ký, mã giảm giá, đánh giá, liên hệ, sổ địa chỉ, và mọi `ErrorState`). Nếu `detail` là tiếng Anh hoặc là stack trace, người dùng sẽ đọc được nguyên văn.
>
> Frontend có bảng thông điệp dự phòng theo mã HTTP (`src/lib/apiError.ts`) nhưng đó chỉ là lưới an toàn — nó chỉ nói được "Dữ liệu gửi lên không hợp lệ", không nói được *sai ở đâu*.

`errors` là **phần mở rộng ngoài chuẩn**, không bắt buộc: map `tên trường → thông điệp`, dùng cho lỗi validate theo từng ô nhập. Frontend đọc vào `ApiError.fieldErrors`.

### A.4 Dạng phân trang

Backend **map `Page<T>` của Spring Data sang dạng dưới đây** thay vì trả mặc định:

```json
{ "items": [ ... ], "total": 42, "page": 1, "limit": 12, "totalPages": 4 }
```

| Trường ở đây | Trường Spring mặc định | Khác biệt |
|---|---|---|
| `items` | `content` | chỉ khác tên |
| `total` | `totalElements` | chỉ khác tên |
| `page` | `number` | **đánh số từ 1**, Spring đánh từ 0 |
| `limit` | `size` | chỉ khác tên |
| `totalPages` | `totalPages` | giống |

`page` đánh số từ 1 vì nó đi thẳng lên URL mà người dùng nhìn thấy (`/cua-hang?page=2`). **Backend chịu trách nhiệm trừ 1** khi dựng `Pageable`.

Mặc định: `limit = 12` cho sản phẩm, `6` cho bài viết.

### A.5 Kiểu dữ liệu

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Tiền | **số nguyên VNĐ**, không thập phân, không dấu phân cách | `449000` |
| Ngày giờ | chuỗi ISO 8601 | `"2026-08-17T10:30:00Z"` |
| Đường dẫn ảnh | **tương đối**, bắt đầu bằng `/images/` | `/images/rau-cu/ca-rot-1.jpg` |
| Slug | không dấu, nối bằng gạch ngang | `ca-rot-huu-co` |
| Giá trị không có | `null`, **không dùng chuỗi rỗng** | `"salePrice": null` |

> **Ảnh phải là đường dẫn tương đối.** Frontend ghép tiền tố bằng `VITE_IMAGE_BASE_URL` ở lớp API (`src/lib/image.ts`), nên chuyển kho ảnh sang S3/CDN chỉ tốn một biến môi trường. Backend trả URL tuyệt đối sẽ phá cơ chế này.

---

## B. Bảng endpoint

45 endpoint. Tên hàm ở cột đầu là hàm frontend đang gọi — tìm trong `src/api/` để xem chi tiết.

### B.1 Sản phẩm — `products.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getProducts` | `GET /products` | query: `q`, `category`, `minPrice`, `maxPrice`, `minRating`, `inStockOnly`, `onSaleOnly`, `isFeatured`, `isBestSeller`, `sort`, `page`, `limit` | `Paginated<Product>` | — | ⬜ |
| `getProductBySlug` | `GET /products/{slug}` | — | `Product` | 404 | ⬜ |
| `getProductsByIds` | `GET /products?ids=1,2,3` | query `ids` | `Product[]` | — | ⬜ |
| `getRelatedProducts` | `GET /products/{slug}/related` | query `limit` (mặc định 4) | `Product[]` | 404 | ⬜ |
| `searchSuggestions` | `GET /products/suggest` | query `q`, `limit` (mặc định 5) | `Product[]` | — | ⬜ |
| `getPriceRange` | `GET /products/price-range` | — | `{ min, max }` | — | ⬜ |

`sort` nhận đúng 5 giá trị: `newest` · `price_asc` · `price_desc` · `best_selling` · `rating`.

`q` tìm trong `name` và `shortDescription`, **bỏ dấu** và không phân biệt hoa thường — "cam" khớp "Cam sành hữu cơ".

> **Cả lọc lẫn sắp xếp theo giá đều dùng `salePrice ?? price`**, không dùng `price`.
>
> `minPrice` / `maxPrice` lọc theo **giá thực tế phải trả**, và `price_asc` / `price_desc` cũng sắp theo giá đó. Backend dùng `price` sẽ cho kết quả khác với con số người dùng đang nhìn trên thẻ sản phẩm — một sản phẩm giá gốc 600k đang giảm còn 400k phải lọt vào khoảng lọc "dưới 500k". Đây là loại lệch **không gây lỗi**, chỉ khiến bộ lọc trả sai âm thầm.

### B.2 Danh mục — `categories.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getCategories` | `GET /categories` | — | `Category[]` | — | ⬜ |
| `getRootCategories` | `GET /categories?root=true` | query `root` | `Category[]` | — | ⬜ |
| `getCategoryBySlug` | `GET /categories/{slug}` | — | `Category` | 404 | ⬜ |

`Category.productCount` do backend tính. Với danh mục gốc, con số này **gồm cả sản phẩm của danh mục con** — sidebar bộ lọc hiển thị đúng như vậy.

### B.3 Bài viết — `posts.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getPosts` | `GET /posts` | query: `q`, `category`, `page`, `limit` | `Paginated<Post>` | — | ⬜ |
| `getPostBySlug` | `GET /posts/{slug}` | — | `Post` | 404 | ⬜ |
| `getLatestPosts` | `GET /posts/latest` | query `limit` (mặc định 4) | `Post[]` | — | ⬜ |
| `getRelatedPosts` | `GET /posts/{slug}/related` | query `limit` (mặc định 3) | `Post[]` | 404 | ⬜ |
| `getPostCategories` | `GET /posts/categories` | — | `PostCategory[]` | — | ⬜ |

- `q` tìm trong **cả tiêu đề lẫn tóm tắt** (`title` + `excerpt`), không phân biệt hoa thường.
  > **Code hiện tại KHÔNG bỏ dấu ở đây**, khác với tìm kiếm sản phẩm — `posts.api.ts` chỉ gọi `toLowerCase()`, nên gõ "rau huu co" sẽ không ra bài "rau hữu cơ". **Khuyến nghị backend bỏ dấu cho cả hai**; phía frontend không phải sửa gì. Xem `docs/schema.md` mục 5.
- `category` lọc theo **slug** (`kien-thuc`), không phải tên hiển thị.
- `Post.content` dùng **tập Markdown rút gọn**: `##`, `###`, `-`, `1.`, `>`, `**đậm**`. Bộ render ở `src/components/blog/PostContent.tsx` chỉ hỗ trợ đúng sáu cú pháp đó và **cố ý không dùng `dangerouslySetInnerHTML`**. Backend trả HTML thô sẽ hiện ra dưới dạng văn bản thuần, không phải HTML.
- `getRelatedPosts` trả bài **cùng chuyên mục** và **không chứa chính bài đang đọc**.

### B.4 Xác thực — `auth.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `login` | `POST /auth/login` | `LoginPayload` | `AuthResponse` | 401 sai thông tin | ⬜ |
| `register` | `POST /auth/register` | `RegisterPayload` | `AuthResponse` | 409 email trùng | ⬜ |
| `refreshSession` | `POST /auth/refresh` | `{ refreshToken }` | `AuthResponse` | 401 hết hạn | ⬜ |
| `logout` | `POST /auth/logout` | `{ refreshToken }` | `204` | — | ✅ |
| `forgotPassword` | `POST /auth/forgot-password` | `{ email }` | `204` | 400 | ⬜ |
| `updateProfile` | `PUT /auth/me` | `Partial<User>` | `User` | 404, 409 email trùng | ✅ |
| `changePassword` | `PUT /auth/password` | `ChangePasswordPayload` | `204` | 401, 422 sai mật khẩu cũ | ✅ |

**Bốn điều bắt buộc:**

1. `User` trả về **không bao giờ chứa password** — kể cả dạng đã hash.
2. `updateProfile` **không được cho phép ghi đè `id`**, kể cả khi client gửi lên.
3. `logout` phải **thu hồi refresh token**, nếu không nó vẫn dùng được đến khi hết hạn dù người dùng đã thoát.
4. `forgotPassword` **luôn trả 204**, kể cả khi email không tồn tại. Trả 404 sẽ biến endpoint này thành công cụ dò xem địa chỉ nào đã đăng ký.

`getCurrentUserId()` trong cùng file **không phải endpoint** — nó giải id từ token phía client. Khi ghép backend thật, id lấy từ JWT ở phía server.

### B.5 Sổ địa chỉ — `addresses.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getMyAddresses` | `GET /addresses` | — | `Address[]` | 401 | ✅ |
| `createAddress` | `POST /addresses` | `AddressPayload` | `Address` | 401 | ✅ |
| `updateAddress` | `PUT /addresses/{id}` | `AddressPayload` | `Address` | 401, 404 | ✅ |
| `deleteAddress` | `DELETE /addresses/{id}` | — | `204` | 401, 404 | ✅ |
| `setDefaultAddress` | `PUT /addresses/{id}/default` | — | `204` | 401, 404 | ✅ |

- Địa chỉ **thuộc về người dùng trong JWT**. Sửa/xoá địa chỉ của người khác phải trả **403 hoặc 404**, không được 200.
- `setDefaultAddress` phải **bỏ cờ mặc định của địa chỉ cũ** trong cùng một giao dịch. Hai địa chỉ cùng `isDefault: true` sẽ khiến trang thanh toán điền sẵn tuỳ tiện.
- Địa chỉ đầu tiên của một tài khoản nên tự động thành mặc định.

### B.6 Đơn hàng — `orders.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `validateCart` | `POST /cart/validate` | `{ items: CartItem[] }` | `CartIssue[]` | — | ⬜ |
| `createOrder` | `POST /orders` | `CreateOrderPayload` | `Order` | 400 giỏ trống, 409 hết hàng, 422 mã giảm giá sai | ⬜ |
| `getMyOrders` | `GET /orders/me` | — | `Order[]` | 401 | ✅ |
| `getOrderByCode` | `GET /orders/{code}` | — | `Order` | 404 | ⬜ |

- `createOrder` là **⬜ công khai**: khách vãng lai đặt hàng được. Có token thì backend gán `userId`, không có thì `userId: null`.
- `getOrderByCode` cũng công khai — đây là **lối duy nhất** để khách vãng lai xem lại đơn của mình, vì `getMyOrders` lọc nghiêm ngặt theo `userId`. Mã đơn (`NSS-20260817-0001`) vì vậy nên **khó đoán** ở môi trường thật; dạng tuần tự hiện tại chỉ hợp với mock.
- `validateCart` trả về mảng vấn đề, rỗng nghĩa là giỏ hợp lệ. `out_of_stock` và `insufficient_stock` **chặn** thanh toán; `price_changed` chỉ cảnh báo.

`calcShippingFee()` trong cùng file **không phải endpoint** — chỉ là ước tính hiển thị ở client (miễn phí từ 500.000 ₫, dưới ngưỡng là 30.000 ₫). Xem phần C.

### B.7 Mã giảm giá — `coupons.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `validateCoupon` | `POST /coupons/validate` | `{ code, subtotal }` | `Coupon` | 404 không tồn tại, 422 chưa đủ giá trị tối thiểu | ⬜ |
| `getActiveCoupons` | `GET /coupons/active` | — | `Coupon[]` | — | ⬜ |

Frontend **chỉ lưu chuỗi mã** trong giỏ hàng, không lưu cả object `Coupon`, và xác thực lại mỗi khi giá trị đơn thay đổi. Lý do: giỏ hàng nằm trong localStorage nhiều ngày — áp mã lúc đơn 300k rồi xoá bớt hàng còn 100k thì mã phải hết hiệu lực ngay.

### B.8 Đánh giá — `reviews.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getProductReviews` | `GET /products/{id}/reviews` | — | `Review[]` | — | ⬜ |
| `getReviewSummary` | `GET /products/{id}/reviews/summary` | — | `ReviewSummary` | — | ⬜ |
| `createReview` | `POST /products/{id}/reviews` | `CreateReviewPayload` | `Review` | 422 nội dung < 10 ký tự hoặc sao ngoài 1–5 | ⬜ |

`ReviewSummary.distribution` là object khoá `'1'`…`'5'`, giá trị là số lượt — dùng vẽ biểu đồ phân bố sao.

### B.9 Địa giới hành chính — `locations.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getProvinces` | `GET /locations/provinces` | — | `Province[]` | — | ⬜ |
| `getDistricts` | `GET /locations/provinces/{code}/districts` | — | `District[]` | — | ⬜ |
| `getWards` | `GET /locations/districts/{code}/wards` | — | **`string[]`** | — | ⬜ |

> **`getWards` trả mảng chuỗi tên, KHÔNG phải object.** Đây là chủ ý, không phải sót: `Address.ward` và `ShippingInfo.ward` đều lưu tên phường chứ không lưu mã, nên `{ code, name }` sẽ không ai dùng tới. Tỉnh và quận thì ngược lại — cần cả mã vì ô `<Select>` chạy theo mã.

Mock hiện chỉ có **10 tỉnh rút gọn**. Backend cấp bộ đầy đủ 63 tỉnh; chữ ký ba hàm giữ nguyên.

### B.10 Nội dung marketing — `marketing.api.ts`

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getHeroSlides` | `GET /hero-slides` | — | `HeroSlide[]` | — | ⬜ |
| `getPromoBanners` | `GET /promo-banners` | — | `PromoBanner[]` | — | ⬜ |
| `getTestimonials` | `GET /testimonials` | — | `Testimonial[]` | — | ⬜ |
| `getBrands` | `GET /brands` | — | `Brand[]` | — | ⬜ |
| `subscribeNewsletter` | `POST /newsletter/subscribe` | `{ email }` | `204` | 400 email sai | ⬜ |

### B.11 Giới thiệu & Liên hệ

| Hàm frontend | Endpoint | Request | Response | Lỗi | Auth |
|---|---|---|---|---|---|
| `getAboutContent` | `GET /about` | — | `AboutContent` | — | ⬜ |
| `sendContactMessage` | `POST /contact` | `ContactPayload` | `ContactMessage` | 400 | ⬜ |

Toàn bộ nội dung trang Giới thiệu (câu chuyện, mốc thời gian, con số, cam kết) là **dữ liệu**, không viết cứng trong component — người vận hành sửa được mà không cần deploy.

---

## C. Backend là nguồn chân lý ở những chỗ này

Phần dễ bị bỏ sót nhất. **Sai ở đây thành lỗ hổng bảo mật, không phải lỗi hiển thị.**

### C.1 Không tin bất kỳ con số tiền nào client gửi lên

`CreateOrderPayload.items` là `CartItem[]`, mà `CartItem` **mang theo `price`, `originalPrice` và `stock`** — đó là bản chụp lúc khách thêm vào giỏ, để giỏ hàng hiển thị đúng khi sản phẩm đổi giá.

Backend khi nhận `POST /orders` **phải bỏ qua các trường đó** và tính lại từ database:

| Con số | Backend phải làm gì |
|---|---|
| `price` mỗi dòng | Tra lại từ database theo `productId` |
| `subtotal` | Tự tính từ giá vừa tra |
| `discount` | Tự xác thực lại mã giảm giá theo `subtotal` vừa tính |
| `shippingFee` | Tự tính — `calcShippingFee()` ở client chỉ là ước tính hiển thị |
| `total` | Tự tính |
| `stock` | Kiểm lại tồn kho thật, **không tin `stock` trong `CartItem`** |

Frontend hiện tính tất cả những con số này ở client vì chưa có backend. Khi ghép thật, các con số client tính chỉ dùng để **hiển thị trước khi bấm đặt hàng**; con số trong `Order` trả về mới là con số thật, và frontend hiển thị lại theo đó.

### C.2 `userId` lấy từ JWT, client không gửi

`CreateOrderPayload` **không có trường `userId`** — đây là chủ ý. Nếu client gửi lên thì ai cũng đặt hàng hộ người khác được. Mock đã mô phỏng đúng cách này: `createOrder` gọi `getCurrentUserId()` giải từ token thay vì nhận từ payload.

### C.3 Tổng hợp điểm đánh giá là việc của backend

`createReview` ở mock **không cập nhật** `rating` và `reviewCount` của sản phẩm — đã ghi rõ trong comment của `getReviewSummary()` để không bị nhầm là bug. Backend phải tính lại hai trường này khi có đánh giá mới.

### C.4 Kiểm quyền sở hữu

Mọi endpoint dưới `/addresses` và `/orders/me` phải lọc theo người dùng trong JWT. Không endpoint nào được nhận `userId` từ query hay body.

---

## D. Năm thay đổi hợp đồng đã tích luỹ

Ghi lại vì chúng phát sinh rải rác qua các giai đoạn, và đều đã có ghi chú tại chỗ trong code.

| # | Thay đổi | Từ GĐ | Khai báo ở |
|---|---|---|---|
| 1 | `PaymentMethod` thêm `'momo'` và `'vnpay'` — tập đầy đủ: `cod` · `bank_transfer` · `momo` · `vnpay` | 6 | `types/order.ts` |
| 2 | `Order.userId: number \| null` — `null` là đơn khách vãng lai. **Không** có trong `CreateOrderPayload` | 7 | `types/order.ts` |
| 3 | `Address` thêm `provinceCode` và `districtCode` — **giữ cả mã lẫn tên** | 7 | `types/user.ts` |
| 4 | `Post.categorySlug`; `getPostCategories()` trả `PostCategory[]` thay vì `string[]` | 8 | `types/post.ts` |
| 5 | `AuthResponse.refreshToken` | 10 | `types/user.ts` |

**Vì sao `Address` giữ cả mã lẫn tên (#3):** ô `<Select>` chọn địa giới hành chính chạy theo **mã**, còn `ShippingInfo` của đơn hàng lưu **tên**. Chỉ lưu tên thì mỗi lần mở lại form phải tra ngược tên → mã, và cơ chế đó vỡ ngay khi tên đơn vị hành chính thay đổi.

---

## E. Danh sách kiểm khi ghép backend

Làm theo thứ tự. Mỗi mục xong thì chạy lại bộ kiểm thử tương ứng.

### E.1 Trước khi sửa dòng code nào

- [ ] Điền `VITE_API_BASE_URL` trong `.env`, hoặc để trống và dựa vào proxy Vite
- [ ] **Xoá bốn khoá localStorage của mock** — `nss_mock_users`, `nss_mock_orders`, `nss_mock_addresses`, `nss_mock_contact_messages`. Không xoá thì dữ liệu giả lẫn với dữ liệu thật, và triệu chứng trông y hệt lỗi backend
- [ ] Xoá luôn `nss_auth_token` và `nss_refresh_token` — token mock không phải JWT thật

### E.2 Sửa từng file trong `src/api/`

Với mỗi hàm: thay **thân hàm** bằng đúng dòng đã ghi sẵn trong comment `Khi có backend: ...`, rồi:

- [ ] Bỏ `import ... from '@/mocks/...'`
- [ ] Bỏ `await delay(...)` — đã có độ trễ mạng thật
- [ ] Bỏ `imageUrl()` **chỉ khi** backend đã trả đường dẫn đúng dạng `/images/...` (xem A.5)
- [ ] **Giữ nguyên tên hàm, tham số và kiểu trả về** — đây là điều `CLAUDE.md` cấm sửa

Thứ tự đề xuất: `categories` → `products` → `posts` (công khai, dễ kiểm) → `auth` → `addresses` → `orders` (cần token, phụ thuộc nhau).

### E.3 Sau khi ghép xong

- [ ] Xoá thư mục `src/mocks/`
- [ ] Xoá `getCurrentUserId()` trong `auth.api.ts` nếu không còn ai gọi
- [ ] Kiểm tra `ApiError` hoạt động: gọi một endpoint sai chủ đích, xác nhận giao diện hiện **tiếng Việt** chứ không phải `"Request failed with status code 404"`
- [ ] Chạy lại cả 7 bộ kiểm thử

### E.4 Những gì KHÔNG phải sửa

Nếu thấy mình đang sửa những thứ này thì có gì đó sai:

- Component trong `src/components/` và `src/pages/` — chúng không biết dữ liệu đến từ đâu
- Hook trong `src/hooks/` — chỉ bọc hàm API bằng TanStack Query
- Store Zustand — giỏ hàng và wishlist là dữ liệu của thiết bị, không đi qua API
- `src/types/` — trừ khi backend thật sự trả shape khác, và khi đó phải cập nhật tài liệu này trước

---

## F. Đối chiếu nhanh

| Con số | Giá trị |
|---|---|
| Endpoint | 45 |
| File trong `src/api/` | 13 (12 file `.api.ts` + `client.ts`) |
| Hàm chỉ chạy ở client | 2 — `getCurrentUserId()`, `calcShippingFee()` |
| Kiểu dữ liệu | 12 file trong `src/types/` |
| Chỗ hiển thị `error.message` cho người dùng | 24 |

Thêm endpoint mới thì cập nhật cả bảng B **và** con số ở đây — lệch nhau nghĩa là có hàm chưa được ghi.
