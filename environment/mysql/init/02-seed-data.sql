-- ============================================================================
-- 02-seed-data.sql - du lieu dev, sinh tu mock cua frontend (C:\fe_base\code_space_1\src\mocks)
-- Ticket: management/backlog/0006-seed-du-lieu-dev-tu-mock.md
--
-- Nguon: brands.json - categories.json - products.json - reviews.json - coupons.json - locations.json
--        (about/posts/testimonials KHONG seed - 0004 da loai chung khoi schema)
--
-- CANH BAO - SCRIPT NAY RESET DU LIEU. Khoi DELETE o dau file xoa sach ca
-- customer_order / order_item / order_status_history / address / refresh_token - nghia la
-- MOI don hang va dia chi lap trinh vien tu tao luc test deu mat. Phai xoa chung vi chung
-- tham chieu `user`, ma `user` thi bi seed lai. Chay lai script = ve dung moc sach nay.
--
-- Idempotent: DELETE theo thu tu nguoc FK roi INSERT voi id tuong minh. Chay bao nhieu lan
-- cung ra dung mot trang thai - moi moc thoi gian la literal co dinh, khong dung NOW().
--
-- Moc thoi gian UTC dung chung cho cac bang khong co createdAt trong mock: 2026-08-22 00:00:00.000000
-- product.created_at / review.created_at lay tu `createdAt` cua mock (YYYY-MM-DD -> 00:00:00.000000), gio UTC.
--
-- rating / review_count cua product duoc TINH LAI tu review that, KHONG chep so cua mock.
-- Lam tron HALF-UP 1 chu so (coding-conventions Sec.15): 4.25 -> 4.3, KHONG phai 4.2.
-- Mock co y ghi so ao (san pham 33: reviewCount=216 nhung chi co 3 review) va chinh frontend
-- ghi chu rang backend se la nguon chan ly. Seed nguyen xi thi DB bat nhat ngay tu ngay dau.
-- He qua da biet va da chap nhan: 24/42 san pham khong co review -> rating=0.0, review_count=0.
--
-- Duong dan anh giu nguyen dang tuong doi /images/... Backend khong luu file anh, chi luu
-- chuoi duong dan; file that do dev-server cua frontend phuc vu.
-- ============================================================================

SET NAMES utf8mb4;

-- ==================== XOA THEO THU TU NGUOC FK ====================
-- Bat buoc dung thu tu nay: con truoc, cha sau. Doi cho la dinh loi khoa ngoai.
DELETE FROM order_status_history;
DELETE FROM order_item;
DELETE FROM customer_order;
DELETE FROM address;
DELETE FROM refresh_token;
DELETE FROM user_role;
DELETE FROM role_permission;
DELETE FROM `user`;                                -- `user` la tu khoa MySQL, phai backtick
DELETE FROM role;
DELETE FROM permission;
DELETE FROM review;
DELETE FROM product_image;
DELETE FROM product;
DELETE FROM category WHERE parent_id IS NOT NULL;  -- danh muc con truoc (category tu tham chieu)
DELETE FROM category;                              -- roi den danh muc goc
DELETE FROM brand;
DELETE FROM coupon;
DELETE FROM ward;
DELETE FROM district;
DELETE FROM province;

-- ==================== THEM THEO THU TU THUAN FK ====================

-- province - 10 tinh/thanh. code la chuoi CO SO 0 DUNG DAU ('01'), bat buoc boc nhay:
-- viet tran thi '01' thanh 1 va toan bo lien ket district.province_code -> province.code vo hieu.
INSERT INTO province (code, name) VALUES
  ('79', 'TP. Hồ Chí Minh'),
  ('01', 'TP. Hà Nội'),
  ('48', 'TP. Đà Nẵng'),
  ('68', 'Lâm Đồng'),
  ('31', 'TP. Hải Phòng'),
  ('92', 'TP. Cần Thơ'),
  ('75', 'Đồng Nai'),
  ('74', 'Bình Dương'),
  ('56', 'Khánh Hoà'),
  ('82', 'Tiền Giang');

-- district - 27 quan/huyen. code cung la chuoi co so 0 dung dau ('001').
INSERT INTO district (code, province_code, name) VALUES
  ('760', '79', 'Quận 1'),
  ('764', '79', 'Quận Gò Vấp'),
  ('765', '79', 'Quận Bình Thạnh'),
  ('768', '79', 'Quận 7'),
  ('769', '79', 'TP. Thủ Đức'),
  ('001', '01', 'Quận Ba Đình'),
  ('002', '01', 'Quận Hoàn Kiếm'),
  ('003', '01', 'Quận Cầu Giấy'),
  ('004', '01', 'Quận Đống Đa'),
  ('490', '48', 'Quận Hải Châu'),
  ('491', '48', 'Quận Thanh Khê'),
  ('492', '48', 'Quận Sơn Trà'),
  ('672', '68', 'TP. Đà Lạt'),
  ('673', '68', 'TP. Bảo Lộc'),
  ('674', '68', 'Huyện Đơn Dương'),
  ('303', '31', 'Quận Hồng Bàng'),
  ('304', '31', 'Quận Ngô Quyền'),
  ('916', '92', 'Quận Ninh Kiều'),
  ('917', '92', 'Quận Bình Thuỷ'),
  ('731', '75', 'TP. Biên Hoà'),
  ('734', '75', 'Huyện Long Thành'),
  ('718', '74', 'TP. Thủ Dầu Một'),
  ('721', '74', 'TP. Dĩ An'),
  ('568', '56', 'TP. Nha Trang'),
  ('569', '56', 'TP. Cam Ranh'),
  ('815', '82', 'TP. Mỹ Tho'),
  ('818', '82', 'Huyện Cái Bè');

-- ward - 112 phuong/xa. Chen id tuong minh 1-112 du cot la auto_increment:
-- id co dinh la thu cho phep chay lai script ra dung cung mot trang thai.
INSERT INTO ward (id, district_code, name) VALUES
  (1, '760', 'Phường Bến Nghé'),
  (2, '760', 'Phường Bến Thành'),
  (3, '760', 'Phường Đa Kao'),
  (4, '760', 'Phường Nguyễn Thái Bình'),
  (5, '760', 'Phường Cầu Kho'),
  (6, '764', 'Phường 1'),
  (7, '764', 'Phường 3'),
  (8, '764', 'Phường 10'),
  (9, '764', 'Phường 12'),
  (10, '764', 'Phường 17'),
  (11, '765', 'Phường 11'),
  (12, '765', 'Phường 13'),
  (13, '765', 'Phường 22'),
  (14, '765', 'Phường 25'),
  (15, '765', 'Phường 26'),
  (16, '768', 'Phường Tân Thuận Đông'),
  (17, '768', 'Phường Tân Phong'),
  (18, '768', 'Phường Tân Phú'),
  (19, '768', 'Phường Phú Mỹ'),
  (20, '769', 'Phường Linh Trung'),
  (21, '769', 'Phường Hiệp Bình Chánh'),
  (22, '769', 'Phường An Phú'),
  (23, '769', 'Phường Thảo Điền'),
  (24, '001', 'Phường Phúc Xá'),
  (25, '001', 'Phường Trúc Bạch'),
  (26, '001', 'Phường Ngọc Hà'),
  (27, '001', 'Phường Kim Mã'),
  (28, '002', 'Phường Hàng Bạc'),
  (29, '002', 'Phường Hàng Bồ'),
  (30, '002', 'Phường Cửa Nam'),
  (31, '002', 'Phường Tràng Tiền'),
  (32, '003', 'Phường Dịch Vọng'),
  (33, '003', 'Phường Mai Dịch'),
  (34, '003', 'Phường Nghĩa Đô'),
  (35, '003', 'Phường Yên Hoà'),
  (36, '004', 'Phường Láng Hạ'),
  (37, '004', 'Phường Khương Thượng'),
  (38, '004', 'Phường Ô Chợ Dừa'),
  (39, '004', 'Phường Trung Liệt'),
  (40, '490', 'Phường Thạch Thang'),
  (41, '490', 'Phường Hải Châu I'),
  (42, '490', 'Phường Nam Dương'),
  (43, '490', 'Phường Bình Thuận'),
  (44, '491', 'Phường Tam Thuận'),
  (45, '491', 'Phường Xuân Hà'),
  (46, '491', 'Phường An Khê'),
  (47, '491', 'Phường Hoà Khê'),
  (48, '492', 'Phường An Hải Bắc'),
  (49, '492', 'Phường Mân Thái'),
  (50, '492', 'Phường Phước Mỹ'),
  (51, '492', 'Phường Thọ Quang'),
  (52, '672', 'Phường 1'),
  (53, '672', 'Phường 2'),
  (54, '672', 'Phường 4'),
  (55, '672', 'Phường 8'),
  (56, '672', 'Xã Xuân Thọ'),
  (57, '673', 'Phường Lộc Phát'),
  (58, '673', 'Phường Lộc Tiến'),
  (59, '673', 'Phường B''Lao'),
  (60, '673', 'Xã Đại Lào'),
  (61, '674', 'Thị trấn Thạnh Mỹ'),
  (62, '674', 'Thị trấn D''Ran'),
  (63, '674', 'Xã Lạc Xuân'),
  (64, '674', 'Xã Ka Đô'),
  (65, '303', 'Phường Quán Toan'),
  (66, '303', 'Phường Hùng Vương'),
  (67, '303', 'Phường Sở Dầu'),
  (68, '303', 'Phường Thượng Lý'),
  (69, '304', 'Phường Máy Chai'),
  (70, '304', 'Phường Máy Tơ'),
  (71, '304', 'Phường Vạn Mỹ'),
  (72, '304', 'Phường Lạc Viên'),
  (73, '916', 'Phường Cái Khế'),
  (74, '916', 'Phường An Hoà'),
  (75, '916', 'Phường Tân An'),
  (76, '916', 'Phường Xuân Khánh'),
  (77, '917', 'Phường Bình Thuỷ'),
  (78, '917', 'Phường Trà An'),
  (79, '917', 'Phường Long Hoà'),
  (80, '917', 'Phường An Thới'),
  (81, '731', 'Phường Trảng Dài'),
  (82, '731', 'Phường Tân Phong'),
  (83, '731', 'Phường Quyết Thắng'),
  (84, '731', 'Phường Long Bình'),
  (85, '734', 'Thị trấn Long Thành'),
  (86, '734', 'Xã An Phước'),
  (87, '734', 'Xã Bình Sơn'),
  (88, '734', 'Xã Lộc An'),
  (89, '718', 'Phường Hiệp Thành'),
  (90, '718', 'Phường Phú Lợi'),
  (91, '718', 'Phường Chánh Nghĩa'),
  (92, '718', 'Phường Phú Cường'),
  (93, '721', 'Phường Dĩ An'),
  (94, '721', 'Phường Tân Bình'),
  (95, '721', 'Phường Đông Hoà'),
  (96, '721', 'Phường An Bình'),
  (97, '568', 'Phường Lộc Thọ'),
  (98, '568', 'Phường Vĩnh Hải'),
  (99, '568', 'Phường Phước Long'),
  (100, '568', 'Phường Vạn Thắng'),
  (101, '569', 'Phường Cam Nghĩa'),
  (102, '569', 'Phường Cam Phúc Bắc'),
  (103, '569', 'Phường Ba Ngòi'),
  (104, '569', 'Xã Cam Thành Nam'),
  (105, '815', 'Phường 1'),
  (106, '815', 'Phường 4'),
  (107, '815', 'Phường 6'),
  (108, '815', 'Xã Mỹ Phong'),
  (109, '818', 'Thị trấn Cái Bè'),
  (110, '818', 'Xã Hoà Khánh'),
  (111, '818', 'Xã Đông Hoà Hiệp'),
  (112, '818', 'Xã Tân Thanh');

-- brand - 9 thuong hieu, giu nguyen id cua mock.
INSERT INTO brand (id, name, logo, created_at) VALUES
  (1, 'Đà Lạt Organic', '/images/brands/da-lat-organic.svg', '2026-08-22 00:00:00.000000'),
  (2, 'Nông Trại Xanh', '/images/brands/nong-trai-xanh.svg', '2026-08-22 00:00:00.000000'),
  (3, 'Vườn Nhà Việt', '/images/brands/vuon-nha-viet.svg', '2026-08-22 00:00:00.000000'),
  (4, 'Nuts House', '/images/brands/nuts-house.svg', '2026-08-22 00:00:00.000000'),
  (5, 'Meat Master', '/images/brands/meat-master.svg', '2026-08-22 00:00:00.000000'),
  (6, 'Dairy Farm', '/images/brands/dairy-farm.svg', '2026-08-22 00:00:00.000000'),
  (7, 'Clean Food Co.', '/images/brands/clean-food-co.svg', '2026-08-22 00:00:00.000000'),
  (8, 'Mộc Châu Milk', '/images/brands/moc-chau-milk.svg', '2026-08-22 00:00:00.000000'),
  (9, 'Fresh Juice Lab', '/images/brands/fresh-juice-lab.svg', '2026-08-22 00:00:00.000000');

-- category - 11 danh muc: 7 goc roi 4 con. Goc PHAI chen truoc vi parent_id tro nguoc
-- vao chinh bang nay (fk_category_parent).
INSERT INTO category (id, parent_id, name, slug, description, image, created_at) VALUES
  (1, NULL, 'Rau củ hữu cơ', 'rau-cu', 'Rau xanh, củ quả canh tác hữu cơ, thu hoạch trong ngày.', '/images/categories/rau-cu.jpg', '2026-08-22 00:00:00.000000'),
  (2, NULL, 'Trái cây & hạt', 'trai-cay-hat', 'Trái cây theo mùa và các loại hạt dinh dưỡng.', '/images/categories/trai-cay-hat.jpg', '2026-08-22 00:00:00.000000'),
  (3, NULL, 'Thịt hữu cơ', 'thit-huu-co', 'Thịt từ vật nuôi thả tự nhiên, không kháng sinh.', '/images/categories/thit-huu-co.jpg', '2026-08-22 00:00:00.000000'),
  (4, NULL, 'Bơ & trứng', 'bo-trung', 'Bơ tự nhiên và trứng gà thả vườn.', '/images/categories/bo-trung.jpg', '2026-08-22 00:00:00.000000'),
  (5, NULL, 'Thực phẩm sạch', 'thuc-pham-sach', 'Hải sản, ngũ cốc và thực phẩm khô đạt chuẩn an toàn.', '/images/categories/thuc-pham-sach.jpg', '2026-08-22 00:00:00.000000'),
  (6, NULL, 'Sữa & kem', 'sua-kem', 'Sữa tươi thanh trùng và các chế phẩm từ sữa.', '/images/categories/sua-kem.jpg', '2026-08-22 00:00:00.000000'),
  (7, NULL, 'Nước ép hữu cơ', 'nuoc-ep', 'Nước ép nguyên chất, không đường, không chất bảo quản.', '/images/categories/nuoc-ep.jpg', '2026-08-22 00:00:00.000000'),
  (101, 1, 'Rau ăn lá', 'rau-an-la', 'Cải, xà lách, rau thơm các loại.', '/images/categories/rau-an-la.jpg', '2026-08-22 00:00:00.000000'),
  (102, 1, 'Củ quả', 'cu-qua', 'Cà rốt, khoai, bí, hành tỏi.', '/images/categories/cu-qua.jpg', '2026-08-22 00:00:00.000000'),
  (201, 2, 'Trái cây tươi', 'trai-cay-tuoi', 'Trái cây theo mùa, hái chín tự nhiên.', '/images/categories/trai-cay-tuoi.jpg', '2026-08-22 00:00:00.000000'),
  (202, 2, 'Hạt dinh dưỡng', 'hat-dinh-duong', 'Hạnh nhân, óc chó, macca, hạt chia.', '/images/categories/hat-dinh-duong.jpg', '2026-08-22 00:00:00.000000');

-- product - 42 san pham.
-- KHONG liet ke cot effective_price: no la GENERATED ALWAYS ... STORED, MySQL nem loi 3105 neu chen.
-- name_normalized dien ngay tai day (bo dau + ha chu thuong) de idx_name_normalized co tac dung
-- va tim kiem khong dau thu duoc ngay trong giai doan dev; service co the ghi de ve sau.
-- rating/review_count: tinh lai tu bang review, KHONG phai so cua mock.
INSERT INTO product (id, name, name_normalized, slug, price, sale_price, unit, origin,
                     short_description, description, category_id, brand_id,
                     stock, sold, rating, review_count, is_featured, is_best_seller, created_at) VALUES
  (1, 'Cải ngọt hữu cơ', 'cai ngot huu co', 'cai-ngot-huu-co', 32000, 26000, 'bó 300g', 'Đà Lạt, Lâm Đồng',
   'Cải ngọt canh tác hữu cơ, lá xanh mướt, ngọt tự nhiên.', 'Cải ngọt được trồng theo tiêu chuẩn hữu cơ tại nông trại Đà Lạt, không dùng thuốc trừ sâu hoá học và phân bón tổng hợp. Rau được thu hoạch vào sáng sớm, làm mát ngay và giao trong ngày để giữ trọn độ giòn ngọt. Thích hợp luộc, xào tỏi hoặc nấu canh thịt bằm.', 101, 1,
   120, 380, 4.5, 2, b'1', b'1', '2026-07-02 00:00:00.000000'),
  (2, 'Cải tím hữu cơ', 'cai tim huu co', 'cai-tim-huu-co', 55000, 47000, 'kg', 'Đà Lạt, Lâm Đồng',
   'Bắp cải tím giàu anthocyanin, giòn ngọt, hợp làm salad.', 'Cải tím hữu cơ có màu tím đậm đặc trưng nhờ hàm lượng anthocyanin cao — một chất chống oxy hoá tự nhiên. Bắp chắc tay, lá giòn, vị ngọt nhẹ. Dùng làm salad trộn, muối chua hoặc xào nhanh với dầu ô liu.', 101, 1,
   64, 210, 0.0, 0, b'1', b'0', '2026-06-18 00:00:00.000000'),
  (3, 'Xà lách Romaine hữu cơ', 'xa lach romaine huu co', 'xa-lach-romaine', 45000, NULL, 'kg', 'Đơn Dương, Lâm Đồng',
   'Lá dày, giòn rụm, chuẩn nguyên liệu salad Caesar.', 'Xà lách Romaine trồng trong nhà kính theo quy trình hữu cơ, tưới bằng nước giếng khoan đã lọc. Lá dài, sống lá dày và giòn, giữ được độ tươi 5–7 ngày khi bảo quản lạnh. Là lựa chọn số một cho món salad Caesar và các loại wrap.', 101, 1,
   85, 295, 0.0, 0, b'0', b'1', '2026-07-20 00:00:00.000000'),
  (4, 'Rau muống hữu cơ', 'rau muong huu co', 'rau-muong-huu-co', 22000, NULL, 'bó 400g', 'Củ Chi, TP.HCM',
   'Cọng nhỏ, non mềm, không thuốc kích thích.', 'Rau muống trồng trên đất sạch tại Củ Chi, hoàn toàn không sử dụng thuốc kích thích tăng trưởng. Cọng nhỏ đều, đốt ngắn, luộc lên vẫn giữ màu xanh và độ giòn. Nước luộc trong, không có vị chát.', 101, 2,
   150, 420, 0.0, 0, b'0', b'1', '2026-05-30 00:00:00.000000'),
  (5, 'Cà rốt hữu cơ', 'ca rot huu co', 'ca-rot-huu-co', 48000, 39000, 'kg', 'Đà Lạt, Lâm Đồng',
   'Củ chắc, ngọt đậm, giàu beta-caroten.', 'Cà rốt Đà Lạt canh tác hữu cơ trên đất bazan, củ thẳng, vỏ mỏng, lõi nhỏ nên rất ngọt. Hàm lượng beta-caroten cao, tốt cho thị lực và làn da. Dùng ép nước, hầm xương, xào hoặc ăn sống đều ngon.', 102, 1,
   200, 610, 4.8, 4, b'1', b'1', '2026-07-11 00:00:00.000000'),
  (6, 'Khoai tây hữu cơ', 'khoai tay huu co', 'khoai-tay-huu-co', 42000, NULL, 'kg', 'Đà Lạt, Lâm Đồng',
   'Vỏ mỏng, ruột vàng bở, không mọc mầm.', 'Khoai tây hữu cơ giống Atlantic, củ tròn đều, vỏ mỏng dễ gọt, ruột vàng. Được bảo quản trong kho mát tối nên không mọc mầm, không xanh vỏ. Thích hợp chiên, nghiền hoặc hầm.', 102, 1,
   175, 330, 0.0, 0, b'0', b'0', '2026-06-05 00:00:00.000000'),
  (7, 'Hành tím hữu cơ', 'hanh tim huu co', 'hanh-tim-huu-co', 68000, 55000, 'kg', 'Vĩnh Châu, Sóc Trăng',
   'Hành tím Vĩnh Châu, thơm nồng, để được lâu.', 'Hành tím trồng trên đất giồng cát Vĩnh Châu — vùng trồng hành nổi tiếng nhất Việt Nam. Củ chắc, vỏ khô bóng, mùi thơm nồng đặc trưng. Phi lên vàng giòn và rất thơm, bảo quản nơi khô ráo được 2–3 tháng.', 102, 2,
   90, 275, 0.0, 0, b'0', b'0', '2026-04-22 00:00:00.000000'),
  (8, 'Bí đỏ hữu cơ', 'bi do huu co', 'bi-do-huu-co', 35000, NULL, 'kg', 'Gia Lai',
   'Ruột đặc, bột nhiều, ngọt thanh.', 'Bí đỏ hồ lô trồng hữu cơ tại Gia Lai, để già trên giàn nên ruột đặc, ít hạt, nhiều bột. Vị ngọt thanh tự nhiên, rất hợp nấu cháo cho bé, hầm xương hoặc làm súp kem.', 102, 2,
   110, 180, 0.0, 0, b'0', b'0', '2026-05-14 00:00:00.000000'),
  (9, 'Cà chua bi hữu cơ', 'ca chua bi huu co', 'ca-chua-bi-huu-co', 58000, 49000, 'hộp 500g', 'Đà Lạt, Lâm Đồng',
   'Ngọt như trái cây, ăn vặt hoặc trộn salad.', 'Cà chua bi socola trồng thuỷ canh hữu cơ trong nhà kính Đà Lạt. Quả nhỏ đều, vỏ mỏng, độ ngọt cao hơn hẳn cà chua thường nên nhiều khách mua để ăn vặt thay trái cây. Không dùng thuốc bảo quản.', 102, 1,
   70, 540, 5.0, 2, b'1', b'1', '2026-08-01 00:00:00.000000'),
  (10, 'Bắp cải trắng hữu cơ', 'bap cai trang huu co', 'bap-cai-trang-huu-co', 28000, NULL, 'kg', 'Đà Lạt, Lâm Đồng',
   'Bắp chặt, lá trắng ngà, ngọt nước.', 'Bắp cải trắng Đà Lạt canh tác hữu cơ, bắp chặt tay, lá cuốn kín và trắng ngà. Luộc hoặc nấu canh đều cho nước ngọt tự nhiên. Bảo quản ngăn mát được tới 2 tuần.', 101, 2,
   0, 240, 0.0, 0, b'0', b'0', '2026-03-28 00:00:00.000000'),
  (11, 'Cam sành hữu cơ', 'cam sanh huu co', 'cam-sanh-huu-co', 89000, 72000, 'kg', 'Tam Bình, Vĩnh Long',
   'Vỏ xanh, ruột vàng, nhiều nước, ngọt đậm.', 'Cam sành Tam Bình trồng theo hướng hữu cơ, không dùng thuốc bảo quản sau thu hoạch. Quả nặng tay, vỏ xanh sần đặc trưng, ruột vàng cam, nhiều nước và ngọt đậm với chút chua nhẹ cân bằng. Vắt nước hoặc ăn tươi đều tuyệt.', 201, 3,
   160, 720, 4.3, 4, b'1', b'1', '2026-07-25 00:00:00.000000'),
  (12, 'Chuối già hữu cơ', 'chuoi gia huu co', 'chuoi-gia-huu-co', 45000, 37000, 'nải ~1.2kg', 'Đồng Nai',
   'Chín cây tự nhiên, thơm ngọt, không dùng thuốc ép chín.', 'Chuối già Nam Mỹ trồng hữu cơ tại Đồng Nai, để chín tự nhiên trong kho mát thay vì dùng hoá chất ép chín. Quả dài đều, vỏ mỏng, thịt vàng ngọt và thơm. Giàu kali, rất tốt cho người tập luyện thể thao.', 201, 3,
   130, 480, 0.0, 0, b'1', b'1', '2026-07-08 00:00:00.000000'),
  (13, 'Bơ 034 hữu cơ', 'bo 034 huu co', 'bo-034-huu-co', 120000, 85000, 'kg', 'Bảo Lộc, Lâm Đồng',
   'Quả dài đặc trưng, cơm vàng dẻo, hạt lép.', 'Bơ 034 Bảo Lộc nổi tiếng với hình dáng thuôn dài, hạt lép nhỏ và cơm dày vàng ươm. Thịt bơ dẻo, béo ngậy, không xơ. Ăn kèm sữa đặc, làm sinh tố hoặc ăn kèm bánh mì đều rất hợp.', 201, 3,
   55, 310, 4.5, 2, b'1', b'0', '2026-06-28 00:00:00.000000'),
  (14, 'Dưa hấu không hạt hữu cơ', 'dua hau khong hat huu co', 'dua-hau-khong-hat', 65000, NULL, 'quả ~3kg', 'Long An',
   'Ruột đỏ au, ngọt mát, tiện ăn vì không hạt.', 'Dưa hấu không hạt trồng hữu cơ tại Long An, ruột đỏ au, độ ngọt ổn định trên 12 Brix. Vỏ mỏng nên tỉ lệ ăn được cao. Không hạt nên rất tiện cho trẻ nhỏ và khi làm nước ép.', 201, 3,
   45, 195, 0.0, 0, b'0', b'0', '2026-06-12 00:00:00.000000'),
  (15, 'Xoài cát Hoà Lộc', 'xoai cat hoa loc', 'xoai-cat-hoa-loc', 145000, 119000, 'kg', 'Cái Bè, Tiền Giang',
   'Đặc sản Tiền Giang, thịt mịn, ngọt thanh, thơm nức.', 'Xoài cát Hoà Lộc chính gốc Cái Bè — giống xoài ngon bậc nhất Việt Nam. Quả to, vỏ vàng ươm khi chín, thịt mịn không xơ, vị ngọt thanh và hương thơm đặc trưng lan toả. Được bao trái từ khi còn non nên không cần phun thuốc.', 201, 3,
   38, 425, 4.7, 3, b'1', b'1', '2026-08-05 00:00:00.000000'),
  (16, 'Thanh long ruột đỏ hữu cơ', 'thanh long ruot do huu co', 'thanh-long-ruot-do', 72000, NULL, 'kg', 'Bình Thuận',
   'Ruột đỏ tím, ngọt hơn thanh long trắng.', 'Thanh long ruột đỏ Bình Thuận canh tác hữu cơ, ruột màu đỏ tím đậm nhờ hàm lượng betacyanin cao. Vị ngọt đậm hơn hẳn thanh long ruột trắng, hạt nhỏ giòn. Giàu chất xơ, hỗ trợ tiêu hoá.', 201, 3,
   88, 210, 0.0, 0, b'0', b'0', '2026-05-20 00:00:00.000000'),
  (17, 'Hạt óc chó Mỹ', 'hat oc cho my', 'hat-oc-cho-my', 320000, 265000, 'túi 500g', 'California, Hoa Kỳ',
   'Nhân đầy, béo bùi, giàu Omega-3.', 'Hạt óc chó California nhập khẩu, vỏ mỏng dễ tách, nhân đầy và trắng ngà. Giàu Omega-3, vitamin E và chất chống oxy hoá — đặc biệt tốt cho mẹ bầu và người làm việc trí óc. Đóng gói hút chân không giữ độ giòn.', 202, 4,
   60, 890, 4.8, 4, b'1', b'1', '2026-07-15 00:00:00.000000'),
  (18, 'Hạnh nhân rang bơ', 'hanh nhan rang bo', 'hanh-nhan-rang-bo', 285000, NULL, 'túi 500g', 'California, Hoa Kỳ',
   'Giòn tan, thơm bơ nhẹ, ít muối.', 'Hạnh nhân nguyên hạt rang bơ thủ công ở nhiệt độ thấp để giữ dưỡng chất. Vị béo bùi, thơm nhẹ mùi bơ, lượng muối vừa phải. Là món ăn vặt lành mạnh, giàu protein thực vật và chất xơ.', 202, 4,
   75, 620, 0.0, 0, b'0', b'1', '2026-06-22 00:00:00.000000'),
  (19, 'Hạt chia Úc', 'hat chia uc', 'hat-chia-uc', 175000, 149000, 'túi 500g', 'Úc',
   'Nở gấp 10 lần, hỗ trợ giảm cân và tiêu hoá.', 'Hạt chia đen nhập khẩu từ Úc, hạt đều và sạch tạp chất. Ngâm nước nở gấp 10 lần tạo lớp gel giàu chất xơ hoà tan, tạo cảm giác no lâu. Pha nước chanh, sữa chua hoặc sinh tố đều tiện.', 202, 4,
   95, 510, 0.0, 0, b'0', b'0', '2026-05-08 00:00:00.000000'),
  (20, 'Thịt bò Úc hữu cơ', 'thit bo uc huu co', 'thit-bo-uc-huu-co', 485000, 399000, 'kg', 'Queensland, Úc',
   'Bò ăn cỏ tự nhiên, thớ thịt mềm, vân mỡ đẹp.', 'Thịt bò từ đàn bò nuôi thả ăn cỏ tự nhiên tại Queensland, không dùng hormone tăng trưởng hay kháng sinh. Thớ thịt mịn, vân mỡ phân bố đều nên khi áp chảo giữ được độ mềm và ngọt. Bảo quản và vận chuyển bằng chuỗi lạnh khép kín.', 3, 5,
   42, 380, 4.7, 3, b'1', b'1', '2026-07-30 00:00:00.000000'),
  (21, 'Gà ta thả vườn', 'ga ta tha vuon', 'ga-ta-tha-vuon', 185000, 159000, 'con ~1.5kg', 'Bình Định',
   'Nuôi thả 4 tháng, da vàng giòn, thịt săn chắc.', 'Gà ta thả vườn nuôi tự nhiên trên 4 tháng, ăn thóc và côn trùng, không dùng cám tăng trọng. Da vàng mỏng, thịt săn chắc và ngọt đậm, xương giòn. Luộc, hấp lá chanh hoặc nấu cháo đều rất hợp.', 3, 5,
   58, 445, 4.7, 3, b'1', b'1', '2026-07-18 00:00:00.000000'),
  (22, 'Thịt heo hữu cơ ba chỉ', 'thit heo huu co ba chi', 'thit-heo-huu-co', 215000, NULL, 'kg', 'Hoà Bình',
   'Ba chỉ nhiều lớp, mỡ trong, không hôi.', 'Thịt ba chỉ từ heo nuôi hữu cơ tại Hoà Bình, ăn cám gạo và rau củ, không kháng sinh. Miếng thịt có 5–7 lớp nạc mỡ xen kẽ, mỡ trong và thơm, không có mùi hôi. Lý tưởng cho món kho tàu, nướng hoặc luộc cuốn bánh tráng.', 3, 5,
   36, 290, 0.0, 0, b'0', b'0', '2026-06-15 00:00:00.000000'),
  (23, 'Sườn non heo hữu cơ', 'suon non heo huu co', 'suon-non-heo-huu-co', 265000, 229000, 'kg', 'Hoà Bình',
   'Sườn non mềm, tỉ lệ thịt cao, ngọt nước.', 'Sườn non lấy từ phần xương sườn cụt, dẻ sườn dày thịt và ít xương. Hầm cho nước dùng trong và ngọt tự nhiên, rim mặn ngọt thì thịt mềm rục. Được pha lóc và cấp đông ngay sau giết mổ.', 3, 5,
   28, 215, 0.0, 0, b'0', b'1', '2026-07-04 00:00:00.000000'),
  (24, 'Ức gà phi lê hữu cơ', 'uc ga phi le huu co', 'uc-ga-phi-le', 145000, NULL, 'kg', 'Bình Định',
   'Phi lê sạch xương, chuẩn cho người tập gym.', 'Ức gà phi lê từ gà nuôi hữu cơ, đã lọc sạch xương và da. Hàm lượng protein cao, ít béo — lựa chọn quen thuộc của người ăn kiêng và tập thể hình. Đóng gói hút chân không theo khay 500g tiện chia bữa.', 3, 5,
   64, 520, 0.0, 0, b'0', b'0', '2026-05-25 00:00:00.000000'),
  (25, 'Bò Mỹ thái lát nhúng lẩu', 'bo my thai lat nhung lau', 'bo-my-thai-lat', 395000, 329000, 'khay 500g', 'Nebraska, Hoa Kỳ',
   'Lát mỏng đều, vân mỡ đẹp, nhúng lẩu là mềm.', 'Thịt bò Mỹ phần nạc vai thái lát mỏng bằng máy, độ dày đồng đều 1.5mm. Vân mỡ marbling rõ nên nhúng lẩu chỉ 10 giây đã mềm và ngọt. Cấp đông nhanh ở -40°C giữ nguyên cấu trúc thớ thịt.', 3, 5,
   0, 340, 0.0, 0, b'1', b'0', '2026-06-30 00:00:00.000000'),
  (26, 'Trứng gà thả vườn', 'trung ga tha vuon', 'trung-ga-tha-vuon', 62000, 52000, 'hộp 10 quả', 'Đồng Nai',
   'Lòng đỏ cam đậm, không tanh.', 'Trứng từ gà mái thả vườn ăn ngô và rau xanh, không dùng phẩm màu tạo lòng đỏ. Lòng đỏ màu cam tự nhiên, đặc và không tanh; lòng trắng trong, dẻo. Trứng được thu gom và giao trong vòng 48 giờ.', 4, 6,
   220, 980, 4.8, 4, b'1', b'1', '2026-08-02 00:00:00.000000'),
  (27, 'Trứng vịt hữu cơ', 'trung vit huu co', 'trung-vit-lon-huu-co', 58000, NULL, 'hộp 10 quả', 'Long An',
   'Quả to, lòng đỏ béo, hợp làm bánh.', 'Trứng vịt từ đàn vịt chạy đồng tại Long An. Quả to hơn trứng gà, lòng đỏ lớn và béo hơn nên rất hợp làm bánh trung thu, bánh flan hoặc kho thịt.', 4, 6,
   140, 360, 0.0, 0, b'0', b'0', '2026-06-08 00:00:00.000000'),
  (28, 'Bơ lạt hữu cơ', 'bo lat huu co', 'bo-lat-huu-co', 165000, 139000, 'hộp 200g', 'New Zealand',
   'Béo thơm tự nhiên, làm bánh chuẩn vị.', 'Bơ lạt sản xuất từ sữa bò ăn cỏ New Zealand, hàm lượng béo 82%. Không thêm muối nên kiểm soát được vị khi làm bánh. Màu vàng nhạt tự nhiên, tan chảy mượt và dậy mùi thơm sữa.', 4, 6,
   48, 265, 5.0, 1, b'0', b'1', '2026-07-12 00:00:00.000000'),
  (29, 'Phô mai Cheddar khối', 'pho mai cheddar khoi', 'pho-mai-cheddar', 195000, NULL, 'khối 250g', 'New Zealand',
   'Ủ 6 tháng, vị đậm, dễ bào sợi.', 'Phô mai Cheddar ủ tự nhiên 6 tháng cho vị đậm và hậu vị bùi. Kết cấu chắc, dễ bào sợi hoặc cắt lát. Dùng cho mì Ý, bánh mì nướng, salad hoặc ăn kèm rượu vang.', 4, 6,
   35, 190, 0.0, 0, b'0', b'0', '2026-05-18 00:00:00.000000'),
  (30, 'Gạo lứt đỏ hữu cơ', 'gao lut do huu co', 'gao-lut-huu-co', 98000, 82000, 'túi 2kg', 'Sóc Trăng',
   'Giữ nguyên cám, giàu chất xơ, chỉ số đường huyết thấp.', 'Gạo lứt đỏ canh tác hữu cơ tại vùng lúa Sóc Trăng, chỉ xay bỏ vỏ trấu nên giữ nguyên lớp cám giàu vitamin nhóm B và chất xơ. Chỉ số đường huyết thấp, phù hợp cho người tiểu đường và ăn kiêng. Ngâm 2 giờ trước khi nấu để cơm mềm.', 5, 7,
   180, 680, 4.7, 3, b'1', b'1', '2026-07-22 00:00:00.000000'),
  (31, 'Tôm thẻ tươi sống', 'tom the tuoi song', 'tom-the-tuoi', 340000, 289000, 'kg', 'Cà Mau',
   'Nuôi quảng canh, thịt chắc và ngọt.', 'Tôm thẻ nuôi quảng canh trong rừng ngập mặn Cà Mau, mật độ thấp nên thịt chắc và ngọt tự nhiên. Không dùng kháng sinh, đạt chuẩn xuất khẩu. Tôm được giữ sống đến khi đóng gói rồi cấp đông nhanh.', 5, 7,
   32, 310, 5.0, 1, b'1', b'0', '2026-07-28 00:00:00.000000'),
  (32, 'Cá hồi Na Uy phi lê', 'ca hoi na uy phi le', 'ca-hoi-nauy-phi-le', 620000, NULL, 'kg', 'Na Uy',
   'Đạt chuẩn sashimi, thớ cam cam, béo mềm.', 'Phi lê cá hồi Đại Tây Dương nhập khẩu trực tiếp từ Na Uy, đạt chuẩn ăn sống. Thớ thịt màu cam sáng với vân mỡ trắng đều, béo mềm và không tanh. Vận chuyển bằng chuỗi lạnh, giữ nhiệt độ ổn định dưới 4°C.', 5, 7,
   24, 285, 4.7, 3, b'1', b'1', '2026-08-08 00:00:00.000000'),
  (33, 'Mật ong rừng nguyên chất', 'mat ong rung nguyen chat', 'mat-ong-rung', 285000, 245000, 'chai 500ml', 'Đắk Lắk',
   'Nguyên chất 100%, không pha đường.', 'Mật ong khai thác từ rừng Đắk Lắk, lọc thô bằng vải để giữ lại phấn hoa và enzyme tự nhiên. Màu hổ phách đậm, sánh đặc, có mùi hoa rừng đặc trưng. Cam kết không pha đường, không chất tạo màu.', 5, 7,
   70, 750, 4.7, 3, b'0', b'1', '2026-06-25 00:00:00.000000'),
  (34, 'Dầu ô liu Extra Virgin', 'dau o liu extra virgin', 'dau-oliu-extra-virgin', 375000, NULL, 'chai 750ml', 'Tây Ban Nha',
   'Ép lạnh lần đầu, độ acid dưới 0.5%.', 'Dầu ô liu nguyên chất ép lạnh lần đầu từ ô liu Picual Tây Ban Nha, độ acid dưới 0.5%. Màu xanh vàng, hương trái cây tươi và hậu vị cay nhẹ ở cổ họng — dấu hiệu của polyphenol cao. Dùng trộn salad hoặc rưới lên món đã nấu.', 5, 7,
   55, 240, 0.0, 0, b'0', b'0', '2026-05-05 00:00:00.000000'),
  (35, 'Sữa tươi thanh trùng không đường', 'sua tuoi thanh trung khong duong', 'sua-tuoi-thanh-trung', 42000, 35000, 'chai 1L', 'Mộc Châu, Sơn La',
   'Thanh trùng nhẹ, giữ trọn vị sữa bò tươi.', 'Sữa bò tươi từ trang trại Mộc Châu, thanh trùng ở 75°C trong 15 giây để diệt khuẩn mà vẫn giữ được hương vị và dưỡng chất. Không đường, không chất bảo quản. Bảo quản lạnh 2–6°C, dùng trong 7 ngày.', 6, 8,
   200, 860, 4.7, 3, b'1', b'1', '2026-08-03 00:00:00.000000'),
  (36, 'Sữa chua Hy Lạp không đường', 'sua chua hy lap khong duong', 'sua-chua-hy-lap', 68000, NULL, 'hũ 500g', 'Mộc Châu, Sơn La',
   'Đặc sánh, protein gấp đôi sữa chua thường.', 'Sữa chua Hy Lạp lọc bỏ bớt whey nên kết cấu đặc sánh, hàm lượng protein cao gấp đôi sữa chua thông thường. Vị chua thanh tự nhiên, không thêm đường. Ăn kèm mật ong, granola hoặc trái cây tươi.', 6, 8,
   90, 430, 5.0, 1, b'0', b'1', '2026-07-06 00:00:00.000000'),
  (37, 'Kem tươi Whipping', 'kem tuoi whipping', 'kem-tuoi-whipping', 115000, 95000, 'hộp 500ml', 'New Zealand',
   'Béo 35%, đánh bông nhanh và giữ form tốt.', 'Kem tươi động vật hàm lượng béo 35%, đánh bông nhanh và giữ form ổn định trong nhiều giờ. Vị béo thanh, thơm sữa tự nhiên. Dùng trang trí bánh, làm mousse hoặc pha cà phê.', 6, 8,
   42, 175, 0.0, 0, b'0', b'0', '2026-06-02 00:00:00.000000'),
  (38, 'Sữa hạnh nhân không đường', 'sua hanh nhan khong duong', 'sua-hanh-nhan', 78000, NULL, 'chai 1L', 'Việt Nam',
   'Thuần chay, không lactose, ít calo.', 'Sữa hạnh nhân làm từ hạnh nhân nguyên hạt xay và lọc, không thêm đường hay chất làm đặc. Phù hợp cho người ăn chay, không dung nạp lactose hoặc đang ăn kiêng. Lắc đều trước khi dùng.', 6, 8,
   110, 320, 0.0, 0, b'0', b'0', '2026-05-12 00:00:00.000000'),
  (39, 'Nước ép cam nguyên chất', 'nuoc ep cam nguyen chat', 'nuoc-ep-cam-nguyen-chat', 95000, 79000, 'chai 1L', 'Vĩnh Long',
   'Ép từ cam sành tươi, không đường, không nước lã.', 'Nước ép từ 100% cam sành tươi, ép lạnh trong ngày và đóng chai ngay. Không thêm đường, nước hay chất bảo quản nên giữ nguyên vitamin C. Bảo quản lạnh, dùng trong 5 ngày.', 7, 9,
   85, 590, 4.5, 2, b'1', b'1', '2026-08-10 00:00:00.000000'),
  (40, 'Nước ép cà rốt táo', 'nuoc ep ca rot tao', 'nuoc-ep-ca-rot-tao', 89000, NULL, 'chai 1L', 'Việt Nam',
   'Vị ngọt dịu, đẹp da, sáng mắt.', 'Hỗn hợp cà rốt Đà Lạt và táo ép lạnh theo tỉ lệ 6:4, cho vị ngọt dịu tự nhiên mà không cần thêm đường. Giàu beta-caroten và chất chống oxy hoá, hỗ trợ làn da và thị lực.', 7, 9,
   72, 340, 0.0, 0, b'0', b'0', '2026-07-01 00:00:00.000000'),
  (41, 'Nước ép cần tây detox', 'nuoc ep can tay detox', 'nuoc-ep-can-tay', 105000, 88000, 'chai 1L', 'Đà Lạt, Lâm Đồng',
   'Thanh lọc cơ thể, hỗ trợ giảm cân.', 'Nước ép cần tây nguyên chất ép lạnh từ cần tây hữu cơ Đà Lạt. Vị thanh mát, hơi chát nhẹ đặc trưng. Nhiều khách dùng vào buổi sáng khi bụng đói để thanh lọc cơ thể. Lắc đều trước khi uống.', 7, 9,
   60, 410, 0.0, 0, b'0', b'1', '2026-06-20 00:00:00.000000'),
  (42, 'Nước ép dưa hấu bạc hà', 'nuoc ep dua hau bac ha', 'nuoc-ep-dua-hau-bac-ha', 85000, NULL, 'chai 1L', 'Long An',
   'Giải nhiệt tức thì, thơm mát bạc hà.', 'Dưa hấu ruột đỏ ép lạnh kết hợp lá bạc hà tươi, cho vị ngọt mát và hậu the nhẹ. Không đường, không đá pha loãng. Món giải nhiệt lý tưởng cho ngày nắng.', 7, 9,
   0, 225, 0.0, 0, b'0', b'0', '2026-05-28 00:00:00.000000');

-- product_image - 84 anh (moi san pham dung 2). sort_order 0-based, nho hon hien thi truoc.
INSERT INTO product_image (id, product_id, url, sort_order) VALUES
  (1, 1, '/images/products/rau-cu/cai-ngot-huu-co-1.jpg', 0),
  (2, 1, '/images/products/rau-cu/cai-ngot-huu-co-2.jpg', 1),
  (3, 2, '/images/products/rau-cu/cai-tim-huu-co-1.jpg', 0),
  (4, 2, '/images/products/rau-cu/cai-tim-huu-co-2.jpg', 1),
  (5, 3, '/images/products/rau-cu/xa-lach-romaine-1.jpg', 0),
  (6, 3, '/images/products/rau-cu/xa-lach-romaine-2.jpg', 1),
  (7, 4, '/images/products/rau-cu/rau-muong-huu-co-1.jpg', 0),
  (8, 4, '/images/products/rau-cu/rau-muong-huu-co-2.jpg', 1),
  (9, 5, '/images/products/rau-cu/ca-rot-huu-co-1.jpg', 0),
  (10, 5, '/images/products/rau-cu/ca-rot-huu-co-2.jpg', 1),
  (11, 6, '/images/products/rau-cu/khoai-tay-huu-co-1.jpg', 0),
  (12, 6, '/images/products/rau-cu/khoai-tay-huu-co-2.jpg', 1),
  (13, 7, '/images/products/rau-cu/hanh-tim-huu-co-1.jpg', 0),
  (14, 7, '/images/products/rau-cu/hanh-tim-huu-co-2.jpg', 1),
  (15, 8, '/images/products/rau-cu/bi-do-huu-co-1.jpg', 0),
  (16, 8, '/images/products/rau-cu/bi-do-huu-co-2.jpg', 1),
  (17, 9, '/images/products/rau-cu/ca-chua-bi-huu-co-1.jpg', 0),
  (18, 9, '/images/products/rau-cu/ca-chua-bi-huu-co-2.jpg', 1),
  (19, 10, '/images/products/rau-cu/bap-cai-trang-huu-co-1.jpg', 0),
  (20, 10, '/images/products/rau-cu/bap-cai-trang-huu-co-2.jpg', 1),
  (21, 11, '/images/products/trai-cay-hat/cam-sanh-huu-co-1.jpg', 0),
  (22, 11, '/images/products/trai-cay-hat/cam-sanh-huu-co-2.jpg', 1),
  (23, 12, '/images/products/trai-cay-hat/chuoi-gia-huu-co-1.jpg', 0),
  (24, 12, '/images/products/trai-cay-hat/chuoi-gia-huu-co-2.jpg', 1),
  (25, 13, '/images/products/trai-cay-hat/bo-034-huu-co-1.jpg', 0),
  (26, 13, '/images/products/trai-cay-hat/bo-034-huu-co-2.jpg', 1),
  (27, 14, '/images/products/trai-cay-hat/dua-hau-khong-hat-1.jpg', 0),
  (28, 14, '/images/products/trai-cay-hat/dua-hau-khong-hat-2.jpg', 1),
  (29, 15, '/images/products/trai-cay-hat/xoai-cat-hoa-loc-1.jpg', 0),
  (30, 15, '/images/products/trai-cay-hat/xoai-cat-hoa-loc-2.jpg', 1),
  (31, 16, '/images/products/trai-cay-hat/thanh-long-ruot-do-1.jpg', 0),
  (32, 16, '/images/products/trai-cay-hat/thanh-long-ruot-do-2.jpg', 1),
  (33, 17, '/images/products/trai-cay-hat/hat-oc-cho-my-1.jpg', 0),
  (34, 17, '/images/products/trai-cay-hat/hat-oc-cho-my-2.jpg', 1),
  (35, 18, '/images/products/trai-cay-hat/hanh-nhan-rang-bo-1.jpg', 0),
  (36, 18, '/images/products/trai-cay-hat/hanh-nhan-rang-bo-2.jpg', 1),
  (37, 19, '/images/products/trai-cay-hat/hat-chia-uc-1.jpg', 0),
  (38, 19, '/images/products/trai-cay-hat/hat-chia-uc-2.jpg', 1),
  (39, 20, '/images/products/thit-huu-co/thit-bo-uc-huu-co-1.jpg', 0),
  (40, 20, '/images/products/thit-huu-co/thit-bo-uc-huu-co-2.jpg', 1),
  (41, 21, '/images/products/thit-huu-co/ga-ta-tha-vuon-1.jpg', 0),
  (42, 21, '/images/products/thit-huu-co/ga-ta-tha-vuon-2.jpg', 1),
  (43, 22, '/images/products/thit-huu-co/thit-heo-huu-co-1.jpg', 0),
  (44, 22, '/images/products/thit-huu-co/thit-heo-huu-co-2.jpg', 1),
  (45, 23, '/images/products/thit-huu-co/suon-non-heo-huu-co-1.jpg', 0),
  (46, 23, '/images/products/thit-huu-co/suon-non-heo-huu-co-2.jpg', 1),
  (47, 24, '/images/products/thit-huu-co/uc-ga-phi-le-1.jpg', 0),
  (48, 24, '/images/products/thit-huu-co/uc-ga-phi-le-2.jpg', 1),
  (49, 25, '/images/products/thit-huu-co/bo-my-thai-lat-1.jpg', 0),
  (50, 25, '/images/products/thit-huu-co/bo-my-thai-lat-2.jpg', 1),
  (51, 26, '/images/products/bo-trung/trung-ga-tha-vuon-1.jpg', 0),
  (52, 26, '/images/products/bo-trung/trung-ga-tha-vuon-2.jpg', 1),
  (53, 27, '/images/products/bo-trung/trung-vit-lon-huu-co-1.jpg', 0),
  (54, 27, '/images/products/bo-trung/trung-vit-lon-huu-co-2.jpg', 1),
  (55, 28, '/images/products/bo-trung/bo-lat-huu-co-1.jpg', 0),
  (56, 28, '/images/products/bo-trung/bo-lat-huu-co-2.jpg', 1),
  (57, 29, '/images/products/bo-trung/pho-mai-cheddar-1.jpg', 0),
  (58, 29, '/images/products/bo-trung/pho-mai-cheddar-2.jpg', 1),
  (59, 30, '/images/products/thuc-pham-sach/gao-lut-huu-co-1.jpg', 0),
  (60, 30, '/images/products/thuc-pham-sach/gao-lut-huu-co-2.jpg', 1),
  (61, 31, '/images/products/thuc-pham-sach/tom-the-tuoi-1.jpg', 0),
  (62, 31, '/images/products/thuc-pham-sach/tom-the-tuoi-2.jpg', 1),
  (63, 32, '/images/products/thuc-pham-sach/ca-hoi-nauy-phi-le-1.jpg', 0),
  (64, 32, '/images/products/thuc-pham-sach/ca-hoi-nauy-phi-le-2.jpg', 1),
  (65, 33, '/images/products/thuc-pham-sach/mat-ong-rung-1.jpg', 0),
  (66, 33, '/images/products/thuc-pham-sach/mat-ong-rung-2.jpg', 1),
  (67, 34, '/images/products/thuc-pham-sach/dau-oliu-extra-virgin-1.jpg', 0),
  (68, 34, '/images/products/thuc-pham-sach/dau-oliu-extra-virgin-2.jpg', 1),
  (69, 35, '/images/products/sua-kem/sua-tuoi-thanh-trung-1.jpg', 0),
  (70, 35, '/images/products/sua-kem/sua-tuoi-thanh-trung-2.jpg', 1),
  (71, 36, '/images/products/sua-kem/sua-chua-hy-lap-1.jpg', 0),
  (72, 36, '/images/products/sua-kem/sua-chua-hy-lap-2.jpg', 1),
  (73, 37, '/images/products/sua-kem/kem-tuoi-whipping-1.jpg', 0),
  (74, 37, '/images/products/sua-kem/kem-tuoi-whipping-2.jpg', 1),
  (75, 38, '/images/products/sua-kem/sua-hanh-nhan-1.jpg', 0),
  (76, 38, '/images/products/sua-kem/sua-hanh-nhan-2.jpg', 1),
  (77, 39, '/images/products/nuoc-ep/nuoc-ep-cam-nguyen-chat-1.jpg', 0),
  (78, 39, '/images/products/nuoc-ep/nuoc-ep-cam-nguyen-chat-2.jpg', 1),
  (79, 40, '/images/products/nuoc-ep/nuoc-ep-ca-rot-tao-1.jpg', 0),
  (80, 40, '/images/products/nuoc-ep/nuoc-ep-ca-rot-tao-2.jpg', 1),
  (81, 41, '/images/products/nuoc-ep/nuoc-ep-can-tay-1.jpg', 0),
  (82, 41, '/images/products/nuoc-ep/nuoc-ep-can-tay-2.jpg', 1),
  (83, 42, '/images/products/nuoc-ep/nuoc-ep-dua-hau-bac-ha-1.jpg', 0),
  (84, 42, '/images/products/nuoc-ep/nuoc-ep-dua-hau-bac-ha-2.jpg', 1);

-- review - 48 danh gia, giu nguyen id cua mock.
INSERT INTO review (id, product_id, author_name, rating, content, created_at) VALUES
  (1, 5, 'Nguyễn Thị Mai', 5, 'Cà rốt củ nào củ nấy chắc tay, gọt vỏ ra ruột vàng đậm. Ép nước cho bé uống rất ngọt, không cần thêm đường.', '2026-08-12 00:00:00.000000'),
  (2, 5, 'Trần Quốc Bảo', 5, 'Mua lần thứ tư rồi. Lần nào cũng tươi, cuống còn xanh. Để ngăn mát được hơn một tuần vẫn giòn.', '2026-08-05 00:00:00.000000'),
  (3, 5, 'Lê Thị Hồng', 4, 'Chất lượng tốt nhưng đợt này củ hơi nhỏ hơn lần trước. Vẫn ngọt và thơm.', '2026-07-28 00:00:00.000000'),
  (4, 5, 'Phạm Văn Đức', 5, 'Giao nhanh, đóng gói kỹ, không có củ nào bị dập. Giá hợp lý so với chất lượng.', '2026-07-19 00:00:00.000000'),
  (5, 11, 'Đặng Thu Trang', 5, 'Cam mọng nước, vắt một quả được gần nửa ly. Vị ngọt đậm có chút chua nhẹ rất vừa miệng.', '2026-08-14 00:00:00.000000'),
  (6, 11, 'Hoàng Minh Tuấn', 4, 'Cam ngon, vỏ hơi dày một chút nên gọt ăn tươi hơi cực. Vắt nước thì tuyệt.', '2026-08-08 00:00:00.000000'),
  (7, 11, 'Vũ Thị Lan Anh', 5, 'Đặt 3kg cho cả nhà, hết trong bốn ngày. Con mình bình thường không thích cam mà uống hết hai ly liền.', '2026-07-30 00:00:00.000000'),
  (8, 11, 'Bùi Thanh Sơn', 3, 'Đợt này có vài quả bị khô nước, chắc do vận chuyển. Bên shop có hỗ trợ đổi nên vẫn ổn.', '2026-07-21 00:00:00.000000'),
  (9, 15, 'Ngô Kim Chi', 5, 'Xoài cát Hoà Lộc chuẩn vị, thịt mịn không xơ, thơm nức cả bếp. Đáng đồng tiền.', '2026-08-15 00:00:00.000000'),
  (10, 15, 'Trịnh Văn Hùng', 5, 'Quả to đều, chín tới, không bị sượng. Mua biếu người nhà ai cũng khen.', '2026-08-09 00:00:00.000000'),
  (11, 15, 'Lý Thị Hoa', 4, 'Ngon nhưng giá hơi cao. Bù lại chất lượng ổn định qua các lần mua.', '2026-08-01 00:00:00.000000'),
  (12, 17, 'Nguyễn Hải Yến', 5, 'Hạt đầy, nhân trắng, tách vỏ dễ. Mình đang bầu nên ăn mỗi ngày một nắm, thấy rất yên tâm về nguồn gốc.', '2026-08-13 00:00:00.000000'),
  (13, 17, 'Đỗ Minh Quân', 5, 'Đóng gói hút chân không nên giữ độ giòn tốt. Mở ra hai tuần vẫn thơm.', '2026-08-06 00:00:00.000000'),
  (14, 17, 'Phan Thị Ngọc', 4, 'Chất lượng tốt, chỉ tiếc là có vài hạt bị lép. Tỉ lệ không đáng kể.', '2026-07-25 00:00:00.000000'),
  (15, 17, 'Cao Văn Thịnh', 5, 'So với hàng ngoài chợ thì hơn hẳn về độ béo bùi. Sẽ mua lại.', '2026-07-14 00:00:00.000000'),
  (16, 20, 'Trần Đình Khoa', 5, 'Thịt bò thớ mịn, vân mỡ đẹp. Áp chảo medium rare mềm và ngọt, không cần ướp nhiều.', '2026-08-16 00:00:00.000000'),
  (17, 20, 'Nguyễn Thuý Vy', 5, 'Giao bằng thùng lạnh, thịt về vẫn còn đông đá. Rất chuyên nghiệp.', '2026-08-10 00:00:00.000000'),
  (18, 20, 'Lâm Chí Cường', 4, 'Thịt ngon nhưng miếng cắt hơi không đều, chỗ dày chỗ mỏng nên canh lửa hơi khó.', '2026-07-29 00:00:00.000000'),
  (19, 21, 'Võ Thị Bích', 5, 'Gà da vàng mỏng, thịt săn chắc, luộc lên nước ngọt lịm. Đúng vị gà ta ngày xưa.', '2026-08-11 00:00:00.000000'),
  (20, 21, 'Đinh Văn Long', 5, 'Làm sạch sẵn, về chỉ việc nấu. Tiện cho người đi làm như mình.', '2026-08-03 00:00:00.000000'),
  (21, 21, 'Hồ Thị Diễm', 4, 'Gà ngon, con hơi nhỏ hơn mô tả một chút nhưng vẫn đủ cho nhà bốn người.', '2026-07-22 00:00:00.000000'),
  (22, 26, 'Nguyễn Văn Tài', 5, 'Lòng đỏ cam đậm tự nhiên, đánh lên bông rất đẹp. Chiên ốp la không hề tanh.', '2026-08-15 00:00:00.000000'),
  (23, 26, 'Trương Mỹ Linh', 5, 'Trứng về nguyên vẹn không vỡ quả nào, hộp chống sốc tốt. Mua đều đặn hàng tuần.', '2026-08-07 00:00:00.000000'),
  (24, 26, 'Lưu Quang Vinh', 5, 'Con mình dị ứng với trứng công nghiệp nhưng ăn loại này thì không sao. Rất mừng.', '2026-07-31 00:00:00.000000'),
  (25, 26, 'Nguyễn Thị Cẩm', 4, 'Chất lượng tốt, giá nhỉnh hơn siêu thị nhưng đổi lại yên tâm nguồn gốc.', '2026-07-18 00:00:00.000000'),
  (26, 30, 'Phạm Anh Thư', 5, 'Gạo thơm, nấu lên dẻo vừa, không bị khô như mấy loại gạo lứt khác. Ngâm 2 tiếng là mềm.', '2026-08-12 00:00:00.000000'),
  (27, 30, 'Tô Văn Bình', 4, 'Mình bị tiểu đường, ăn loại này thấy đường huyết ổn hơn hẳn gạo trắng. Vị hơi lạ lúc đầu nhưng quen dần.', '2026-08-02 00:00:00.000000'),
  (28, 30, 'Nguyễn Hồng Nhung', 5, 'Túi 2kg dùng được gần một tháng cho hai người. Đóng gói chắc chắn, không bị mọt.', '2026-07-20 00:00:00.000000'),
  (29, 32, 'Đoàn Thị Kim', 5, 'Cá hồi tươi, thớ cam sáng, vân mỡ đều. Làm sashimi ăn sống hoàn toàn yên tâm.', '2026-08-16 00:00:00.000000'),
  (30, 32, 'Mai Xuân Trường', 5, 'Miếng phi lê dày, ít xương dăm. Áp chảo da giòn rụm, thịt bên trong vẫn mềm.', '2026-08-08 00:00:00.000000'),
  (31, 32, 'Nguyễn Thanh Hà', 4, 'Chất lượng tốt nhưng giá khá cao. Chỉ dám mua vào dịp cuối tuần.', '2026-07-27 00:00:00.000000'),
  (32, 33, 'Trần Bảo Ngọc', 5, 'Mật sánh đặc, mùi hoa rừng rõ rệt. Để tủ lạnh không bị đóng đường như hàng pha.', '2026-08-14 00:00:00.000000'),
  (33, 33, 'Nguyễn Đức Hiếu', 5, 'Pha nước chanh mật ong buổi sáng rất thơm. Chai 500ml dùng được hơn hai tháng.', '2026-08-04 00:00:00.000000'),
  (34, 33, 'Lê Minh Châu', 4, 'Mật ngon, chỉ góp ý là nắp chai hơi khó mở lần đầu.', '2026-07-23 00:00:00.000000'),
  (35, 35, 'Hà Thị Thu', 5, 'Sữa thơm béo tự nhiên, uống là biết ngay khác sữa tiệt trùng. Con mình uống mỗi sáng.', '2026-08-15 00:00:00.000000'),
  (36, 35, 'Nguyễn Trọng Nghĩa', 5, 'Giao bằng xe lạnh, chai về vẫn mát. Hạn dùng còn dài.', '2026-08-09 00:00:00.000000'),
  (37, 35, 'Bùi Thị Phương', 4, 'Ngon nhưng hạn ngắn quá, phải uống nhanh trong tuần. Cũng dễ hiểu vì là sữa thanh trùng.', '2026-07-26 00:00:00.000000'),
  (38, 39, 'Nguyễn Khánh Ly', 5, 'Vị y như tự vắt ở nhà, không hề có vị đường hay hương liệu. Rất đáng tiền.', '2026-08-13 00:00:00.000000'),
  (39, 39, 'Trần Việt Anh', 4, 'Nước ép ngon nhưng có lắng cặn dưới đáy, phải lắc đều. Chắc do không lọc kỹ nên giữ được dưỡng chất.', '2026-08-01 00:00:00.000000'),
  (40, 9, 'Phạm Thị Yến', 5, 'Cà chua bi ngọt như trái cây, con mình ăn thay bánh kẹo. Quả nhỏ đều, vỏ mỏng.', '2026-08-11 00:00:00.000000'),
  (41, 9, 'Nguyễn Duy Khang', 5, 'Trộn salad rất hợp. Mua hộp 500g ăn được ba bữa.', '2026-07-30 00:00:00.000000'),
  (42, 1, 'Lê Thị Kim Oanh', 5, 'Cải ngọt lá xanh mướt, không có vết sâu. Xào tỏi ngọt và giòn.', '2026-08-10 00:00:00.000000'),
  (43, 1, 'Đặng Văn Nam', 4, 'Rau tươi, chỉ hơi ít so với bó ngoài chợ cùng giá. Bù lại sạch và yên tâm.', '2026-07-24 00:00:00.000000'),
  (44, 13, 'Trần Thị Hạnh', 5, 'Bơ 034 cơm dày vàng ươm, hạt lép đúng như mô tả. Dằm sữa đặc ngon tuyệt.', '2026-08-06 00:00:00.000000'),
  (45, 13, 'Nguyễn Phú Cường', 4, 'Bơ ngon nhưng về còn hơi cứng, phải ủ thêm hai ngày mới ăn được.', '2026-07-27 00:00:00.000000'),
  (46, 28, 'Vương Thị Mỹ', 5, 'Bơ lạt thơm mùi sữa, làm bánh quy lên màu và mùi rất chuẩn. Sẽ mua lại.', '2026-08-05 00:00:00.000000'),
  (47, 31, 'Nguyễn Tuấn Kiệt', 5, 'Tôm thịt chắc, luộc lên đỏ au và ngọt. Không bị bở như tôm nuôi công nghiệp.', '2026-08-12 00:00:00.000000'),
  (48, 36, 'Lê Hoài Thương', 5, 'Sữa chua đặc sánh, chua thanh, ăn với mật ong và granola là hết một hũ.', '2026-08-07 00:00:00.000000');

-- coupon - 3 ma. type la chuoi trong mock nhung int trong DB: 'percent' -> 0, 'fixed' -> 1.
-- description o bang nay la NOT NULL (khac cac bang khac); usage_limit/starts_at/ends_at de NULL.
INSERT INTO coupon (code, type, value, min_order_value, description, usage_limit,
                    starts_at, ends_at, is_active, used_count, created_at) VALUES
  ('CHAOBAN10', 0, 10, 200000, 'Giảm 10% cho đơn từ 200.000 ₫ — dành cho khách hàng mới.', NULL,
   NULL, NULL, b'1', 0, '2026-08-22 00:00:00.000000'),
  ('FREESHIP', 1, 30000, 150000, 'Miễn phí vận chuyển cho đơn từ 150.000 ₫.', NULL,
   NULL, NULL, b'1', 0, '2026-08-22 00:00:00.000000'),
  ('HUUCO50', 1, 50000, 500000, 'Giảm ngay 50.000 ₫ cho đơn từ 500.000 ₫.', NULL,
   NULL, NULL, b'1', 0, '2026-08-22 00:00:00.000000');

-- permission - 8 quyen don le cua mo hinh RBAC.
INSERT INTO permission (id, code, name, description, created_at) VALUES
  (1, 'PRODUCT_READ', 'Xem san pham', 'Doc danh sach va chi tiet san pham', '2026-08-22 00:00:00.000000'),
  (2, 'PRODUCT_MANAGE', 'Quan ly san pham', 'Them, sua, xoa san pham', '2026-08-22 00:00:00.000000'),
  (3, 'ORDER_READ', 'Xem don hang', 'Doc don hang cua chinh minh hoac cua he thong', '2026-08-22 00:00:00.000000'),
  (4, 'ORDER_MANAGE', 'Quan ly don hang', 'Xac nhan, chuyen trang thai, huy don hang', '2026-08-22 00:00:00.000000'),
  (5, 'USER_READ', 'Xem nguoi dung', 'Doc thong tin tai khoan nguoi dung', '2026-08-22 00:00:00.000000'),
  (6, 'USER_MANAGE', 'Quan ly nguoi dung', 'Tao, sua, khoa tai khoan nguoi dung', '2026-08-22 00:00:00.000000'),
  (7, 'CATEGORY_MANAGE', 'Quan ly danh muc', 'Them, sua, xoa danh muc san pham', '2026-08-22 00:00:00.000000'),
  (8, 'COUPON_MANAGE', 'Quan ly ma giam gia', 'Them, sua, bat tat ma giam gia', '2026-08-22 00:00:00.000000');

-- role - 2 vai tro.
INSERT INTO role (id, code, name, description, created_at) VALUES
  (1, 'ADMIN', 'Quan tri vien', 'Co day du 8 quyen', '2026-08-22 00:00:00.000000'),
  (2, 'CUSTOMER', 'Khach hang', 'Chi cac quyen doc cong khai', '2026-08-22 00:00:00.000000');

-- role_permission - ADMIN nhan ca 8 quyen; CUSTOMER chi PRODUCT_READ va ORDER_READ.
INSERT INTO role_permission (id, role_id, permission_id, created_at) VALUES
  (1, 1, 1, '2026-08-22 00:00:00.000000'),
  (2, 1, 2, '2026-08-22 00:00:00.000000'),
  (3, 1, 3, '2026-08-22 00:00:00.000000'),
  (4, 1, 4, '2026-08-22 00:00:00.000000'),
  (5, 1, 5, '2026-08-22 00:00:00.000000'),
  (6, 1, 6, '2026-08-22 00:00:00.000000'),
  (7, 1, 7, '2026-08-22 00:00:00.000000'),
  (8, 1, 8, '2026-08-22 00:00:00.000000'),
  (9, 2, 1, '2026-08-22 00:00:00.000000'),
  (10, 2, 3, '2026-08-22 00:00:00.000000');

-- user - 2 tai khoan dev.
-- !!! CANH BAO: password_hash duoi day la bcrypt cua mat khau DEV DA BIET, ghi cong khai o day.
-- !!! demo@nongsansach.vn  -> '123456'  (khop tai khoan demo trong auth.api.ts cua frontend)
-- !!! admin@nongsansach.vn -> 'admin123'
-- !!! TUYET DOI KHONG mang file nay len bat ky moi truong that nao.
-- full_name_normalized la cot PHAI SINH cua backlog 0019: full_name da bo dau va ha chu thuong,
-- theo dung bon buoc cua coding-conventions §18 (ke ca buoc doi chu D-co-gach-ngang thanh chu d
-- thuong, buoc ma NFD KHONG lam ho duoc). No la thu ma `q` cua GET /admin/customers so khop;
-- de NULL thi hai tai khoan seed nay khong bao gio tim ra duoc.
INSERT INTO `user` (id, email, password_hash, full_name, full_name_normalized, phone, avatar, created_at) VALUES
  (1, 'demo@nongsansach.vn', '$2a$10$.dr31WdMiDT/t/i5.2U.8uaILF5ttzbLjzwhUmqSL74cQQMfM48Sy',
   'Nguyễn Văn An', 'nguyen van an', '0901234567', NULL, '2026-08-22 00:00:00.000000'),
  (2, 'admin@nongsansach.vn', '$2a$10$2v0zKpUNHhcns/FatbcjZu2WjTHSffOIsIBo4yKzKUsy6iX7lxyYy',
   'Quản trị hệ thống', 'quan tri he thong', '0909999999', NULL, '2026-08-22 00:00:00.000000');

-- user_role - demo la CUSTOMER, admin la ADMIN.
INSERT INTO user_role (id, user_id, role_id, created_at) VALUES
  (1, 1, 2, '2026-08-22 00:00:00.000000'),
  (2, 2, 1, '2026-08-22 00:00:00.000000');
