// Kiểm bất biến "không oversell" dưới tải cao qua HTTP thật (backlog 0035 Phase 4,
// architecture/01-overview.md §10). Chưa có hạ tầng k6 nào trong repo trước ticket này.
//
// KHÔNG PHẢI BẰNG CHỨNG CHO 10.000-20.000 req/s THẬT (Quyết định Owner #4, backlog 0035) — mức tải
// ở đây chỉ đi tới nơi máy dev chạy script này thực sự chịu được (hàng trăm virtual user), và số VU
// đạt được PHẢI được đọc trực tiếp từ phần tóm tắt k6 in ra cuối lần chạy, không phải một con số cố
// định ghi cứng ở đây. Benchmark hạ tầng phân tán thật (10-20k req/s) là việc Owner tự lên lịch riêng.
//
// CÁCH DÙNG:
//   1. docker compose -f environment/docker-compose-dev.yml up -d mysql redis   (app phải đang chạy)
//   2. K6_ADMIN_EMAIL / K6_ADMIN_PASSWORD mặc định khớp seed data (admin@nongsansach.vn / admin123)
//   3. k6 run benchmark/k6/flash-sale.js
//      hoặc qua Docker (không cần cài k6):
//      docker run --rm -i --network host grafana/k6 run - < benchmark/k6/flash-sale.js
//
// THAM SỐ CHỈNH ĐƯỢC QUA BIẾN MÔI TRƯỜNG (không sửa file):
//   K6_BASE_URL       mặc định http://localhost:8080
//   K6_INITIAL_STOCK  tồn kho seed cho sản phẩm test — mặc định 100
//   K6_MAX_VUS        virtual user tối đa — mặc định 200 (hạ xuống nếu máy dev yếu hơn)
//
// PHÁT HIỆN THẬT khi chạy lần đầu (backlog 0035 Phase 4, ghi lại để không ai chạy lại rồi tưởng là
// bug mới): `POST /orders` là endpoint WRITE, đi qua `ApiRateLimitInterceptor` (backlog 0021) —
// ngưỡng mặc định dev CHỈ 30 req/s (`RATE_LIMIT_WRITE_LIMIT`, application.yml). Với >30 VU bắn gần
// như đồng thời, phần lớn request nhận `429` KHÔNG PHẢI vì hết hàng — đó là lớp chống quá tải ở biên
// VÀO đang làm đúng việc của nó, một tầng hoàn toàn khác với bất biến oversell mà ticket này kiểm.
// Script coi `429` là kết quả HỢP LỆ (không tính vào `http_req_failed`) để không báo động giả, nhưng
// đo bất biến oversell cho CHÍNH XÁC thì phải nới ngưỡng này trước khi chạy — set biến môi trường
// `RATE_LIMIT_WRITE_LIMIT` (ví dụ 100000) khi khởi động app, đúng nếp "ngưỡng vận hành qua env var,
// không build lại" của coding-conventions §20. Owner cân nhắc: một flash sale thật cũng cần quyết
// định tương tự — có sẵn sàng nới rate-limit-write trong lúc campaign chạy hay không.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL = __ENV.K6_ADMIN_EMAIL || 'admin@nongsansach.vn';
const ADMIN_PASSWORD = __ENV.K6_ADMIN_PASSWORD || 'admin123';
const INITIAL_STOCK = Number(__ENV.K6_INITIAL_STOCK || 100);
const MAX_VUS = Number(__ENV.K6_MAX_VUS || 200);

// §Contract của ADR 0001 + architecture §7: mã HTTP thật thay envelope cũ, nên 409 (OUT_OF_STOCK) là
// một kết quả NGHIỆP VỤ hợp lệ dưới flash sale, không phải một lỗi hạ tầng — đây là chỗ tài liệu §7
// cảnh báo "threshold phải lọc theo status, không thì báo động giả". 429 cũng là hợp lệ ở đây vì
// cùng lý do (xem PHÁT HIỆN THẬT ở trên) — cả ba (201/409/429) đều là app trả lời có chủ đích, chỉ
// 5xx/timeout/connection-refused mới là "lỗi hạ tầng" theo đúng nghĩa `http_req_failed` cần bắt.
http.setResponseCallback(http.expectedStatuses(201, 409, 429));

export const ordersSuccess = new Counter('orders_success');
export const ordersOutOfStock = new Counter('orders_out_of_stock');
export const ordersRateLimited = new Counter('orders_rate_limited');
export const ordersUnexpected = new Counter('orders_unexpected');

export const options = {
    // teardownTimeout mac dinh 10s la khong du khi doc lai san pham qua chinh duong HTTP dang bi
    // ram VU khac chiem dung (Docker Desktop tren Windows chuyen tiep host.docker.internal qua NAT,
    // vong lap ket noi don le duoi tai cao — xem PHAT HIEN THAT o duoi).
    teardownTimeout: '60s',
    scenarios: {
        flash_sale_burst: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: MAX_VUS }, // dong loat mo tab F5 lien tuc
                { duration: '10s', target: MAX_VUS },
                { duration: '5s', target: 0 },
            ],
        },
    },
    thresholds: {
        // 409 da bi loai khoi http_req_failed boi expectedStatuses o tren — con so nay chi con bat
        // loi ha tang that (5xx, timeout, connection refused).
        http_req_failed: ['rate<0.01'],
        // Bat bien "khong bao gio co response nao ngoai 201/409/429/loi ha tang that" — xem checks o duoi.
        checks: ['rate>0.99'],
    },
};

// setup() chay MOT LAN truoc khi rai VU — dung dung account admin da seed (02-seed-data.sql) de
// dung mot san pham rieng cho benchmark nay, khong dung chung san pham voi du lieu demo khac.
export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status !== 200) {
        throw new Error(`k6 setup: dang nhap admin that bai — status=${loginRes.status} body=${loginRes.body}`);
    }
    const token = loginRes.json('token');

    const createRes = http.post(
        `${BASE_URL}/api/admin/products`,
        JSON.stringify({
            name: `K6 Flash Sale ${Date.now()}`,
            price: 10000,
            unit: 'cai',
            stock: INITIAL_STOCK,
            categoryId: 1,
        }),
        { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } }
    );
    if (createRes.status !== 201) {
        throw new Error(`k6 setup: tao san pham benchmark that bai — status=${createRes.status} body=${createRes.body}`);
    }
    // ADR 0001: khong envelope — body CHINH LA ProductResponse, khong bi boc trong { product: ... }.
    const product = createRes.json();
    console.log(`k6 setup: san pham benchmark id=${product.id} slug=${product.slug} stock=${INITIAL_STOCK}`);

    return { productId: product.id, token, slug: product.slug };
}

export default function (data) {
    const payload = JSON.stringify({
        items: [{ productId: data.productId, name: 'K6 Flash Sale', quantity: 1, price: 10000 }],
        shipping: {
            fullName: `K6 VU ${__VU} iter ${__ITER}`,
            phone: '0900000000',
            email: `k6-vu${__VU}-${__ITER}@vidu.vn`,
            province: 'HCM',
            district: 'Q1',
            ward: 'P.Ben Nghe',
            street: '1 Le Loi',
        },
        paymentMethod: 'cod',
    });

    const res = http.post(`${BASE_URL}/api/orders`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    const isSuccess = res.status === 201;
    const isOutOfStock = res.status === 409;
    const isRateLimited = res.status === 429;
    check(res, {
        'status la 201 (thanh cong), 409 (het hang) hoac 429 (rate limit bien vao)':
            () => isSuccess || isOutOfStock || isRateLimited,
    });

    if (isSuccess) {
        ordersSuccess.add(1);
    } else if (isOutOfStock) {
        ordersOutOfStock.add(1);
    } else if (isRateLimited) {
        ordersRateLimited.add(1);
    } else {
        ordersUnexpected.add(1);
        console.error(`k6: response ngoai du kien — status=${res.status} body=${res.body}`);
    }
}

// teardown() doc lai chinh san pham benchmark de in ra bang chung cuoi cung — bat bien can kiem la
// stock KHONG BAO GIO am, va (o muc do doc duoc tu HTTP, khong query DB truc tiep) stock cuoi cung
// phai >= 0. So sanh chinh xac orders_success == INITIAL_STOCK - finalStock nam o phan tom tat
// Counter cuoi log k6 (orders_success), doi chieu thu cong voi dong nay khi doc ket qua.
// LUU Y: o may dev tai (Windows, Docker Desktop), teardown() thinh thoang timeout do dong ket noi
// TCP cua cac iteration bi ngat (ramp-down) chua kip don sach — day la mot bat tien COSMETIC, KHONG
// anh huong bang chung chinh: khoi THRESHOLDS/TOTAL RESULTS da in xong TRUOC khi teardown chay, va
// do la noi doc duoc orders_success/orders_out_of_stock that su.
export function teardown(data) {
    const res = http.get(`${BASE_URL}/api/products/${data.slug}`);
    if (res.status !== 200) {
        console.error(`k6 teardown: khong doc lai duoc san pham benchmark — status=${res.status}`);
        return;
    }
    const finalStock = res.json('stock');
    console.log(`k6 teardown: san pham id=${data.productId} slug=${data.slug} stock cuoi cung=${finalStock}`);
    if (finalStock < 0) {
        console.error(`k6 teardown: BAT BIEN VI PHAM — stock am (${finalStock})! Day la oversell that su.`);
    } else {
        console.log('k6 teardown: stock >= 0 — khong co dau hieu oversell tu ket qua doc duoc qua HTTP.');
    }
}
