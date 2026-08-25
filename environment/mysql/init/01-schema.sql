
    create table address (
        is_default bit not null comment 'Co phai dia chi mac dinh cua user hay khong',
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        user_id bigint not null comment 'Chu so huu dia chi',
        district_code varchar(16) not null comment 'Ma quan/huyen, ban chup tai thoi diem luu',
        province_code varchar(16) not null comment 'Ma tinh/thanh, ban chup tai thoi diem luu',
        phone varchar(20) not null comment 'So dien thoai nguoi nhan',
        district varchar(128) not null comment 'Ten quan/huyen, ban chup tai thoi diem luu',
        full_name varchar(128) not null comment 'Ho ten nguoi nhan hang',
        province varchar(128) not null comment 'Ten tinh/thanh, ban chup tai thoi diem luu',
        ward varchar(128) not null comment 'Ten phuong/xa; khong luu ma vi contract chi tra ten',
        street varchar(255) not null comment 'So nha va ten duong',
        primary key (id)
    ) comment='So dia chi cua nguoi dung' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table brand (
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        name varchar(160) not null comment 'Ten thuong hieu',
        logo varchar(255) comment 'Duong dan logo tuong doi, bat dau bang /images/',
        primary key (id)
    ) comment='Thuong hieu gan voi san pham' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table category (
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        parent_id bigint comment 'Danh muc cha; null la danh muc goc',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        name varchar(160) not null comment 'Ten hien thi cua danh muc',
        slug varchar(160) not null comment 'Slug khong dau, duy nhat, dung lam duong dan',
        description varchar(500) comment 'Mo ta ngan cua danh muc',
        image varchar(255) comment 'Duong dan anh tuong doi, bat dau bang /images/',
        primary key (id)
    ) comment='Danh muc san pham, cay tu tham chieu qua parent_id' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table coupon (
        is_active bit not null comment 'Ma co dang duoc bat hay khong',
        type integer not null comment 'Kieu giam gia: 0=PERCENT, 1=FIXED',
        usage_limit integer comment 'Tong so luot duoc dung; null la khong gioi han',
        used_count integer not null comment 'So luot da dung',
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        ends_at datetime(6) comment 'Thoi diem het hieu luc, gio UTC; null la khong gioi han',
        min_order_value bigint not null comment 'Gia tri don toi thieu de dung ma, so nguyen VND',
        starts_at datetime(6) comment 'Thoi diem bat dau hieu luc, gio UTC; null la khong gioi han',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        value bigint not null comment 'Gia tri giam: phan tram neu type=0, so nguyen VND neu type=1',
        code varchar(32) not null comment 'Ma giam gia, khoa chinh tu nhien',
        description varchar(255) not null comment 'Mo ta ma giam gia hien thi cho nguoi dung',
        primary key (code)
    ) comment='Ma giam gia; code la natural key' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table customer_order (
        payment_method integer not null comment 'Phuong thuc thanh toan: 0=COD, 1=BANK_TRANSFER, 2=MOMO, 3=VNPAY',
        status integer not null comment 'Trang thai don: 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED',
        created_at datetime(6) not null comment 'Thoi diem dat don, luu theo gio UTC',
        discount bigint not null comment 'So tien duoc giam, so nguyen VND',
        id bigint not null auto_increment comment 'Khoa chinh',
        shipping_fee bigint not null comment 'Phi van chuyen, so nguyen VND',
        subtotal bigint not null comment 'Tong tien hang truoc giam gia, so nguyen VND',
        total bigint not null comment 'Tong tien phai tra, so nguyen VND',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        user_id bigint comment 'Chu don; null la don khach vang lai',
        phone varchar(20) not null comment 'So dien thoai nguoi nhan',
        code varchar(32) not null comment 'Ma don hien thi cho khach, duy nhat, vi du NSS-20260817-0001',
        coupon_code varchar(32) comment 'Ma giam gia da ap, ban chup; null neu khong ap ma',
        district varchar(128) not null comment 'Ten quan/huyen giao hang',
        full_name varchar(128) not null comment 'Ho ten nguoi nhan hang',
        full_name_normalized varchar(128) comment 'Ho ten nguoi nhan da bo dau va ha chu thuong, phuc vu tim kiem khong dau',
        province varchar(128) not null comment 'Ten tinh/thanh giao hang',
        ward varchar(128) not null comment 'Ten phuong/xa giao hang',
        email varchar(160) not null comment 'Email nhan xac nhan don hang',
        note varchar(500) comment 'Ghi chu giao hang cua khach; null neu khong co',
        street varchar(255) not null comment 'So nha va ten duong giao hang',
        primary key (id)
    ) comment='Don hang; ten bang lech ten entity vi ORDER la tu khoa MySQL' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table district (
        code varchar(16) not null comment 'Ma quan/huyen, khoa chinh tu nhien',
        province_code varchar(16) not null comment 'Tinh/thanh chua quan/huyen nay',
        name varchar(128) not null comment 'Ten quan/huyen',
        primary key (code)
    ) comment='Quan/huyen; code la natural key' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table order_item (
        quantity integer not null comment 'So luong dat',
        id bigint not null auto_increment comment 'Khoa chinh',
        order_id bigint not null comment 'Don hang chua dong nay',
        original_price bigint not null comment 'Gia goc mot don vi de hien thi gach ngang, so nguyen VND',
        price bigint not null comment 'Gia thuc te da ban mot don vi, so nguyen VND',
        product_id bigint not null comment 'ID san pham tai thoi diem dat; co y khong co khoa ngoai',
        unit varchar(32) not null comment 'Don vi tinh, ban chup tai thoi diem dat',
        slug varchar(160) not null comment 'Slug san pham, ban chup tai thoi diem dat',
        image varchar(255) comment 'Anh san pham, ban chup, duong dan tuong doi',
        name varchar(255) not null comment 'Ten san pham, ban chup tai thoi diem dat',
        primary key (id)
    ) comment='Dong hang trong don, la ban chup san pham tai thoi diem dat' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table order_status_history (
        from_status integer comment 'Trang thai truoc khi chuyen; null la dong dau tien luc tao don',
        to_status integer not null comment 'Trang thai sau khi chuyen: 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED',
        created_at datetime(6) not null comment 'Thoi diem chuyen trang thai, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        order_id bigint not null comment 'Don hang duoc chuyen trang thai',
        changed_by varchar(128) comment 'Dinh danh nguoi hoac he thong thuc hien chuyen trang thai',
        note varchar(255) comment 'Ghi chu ly do chuyen trang thai',
        primary key (id)
    ) comment='Nhat ky chuyen trang thai cua don hang' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table password_reset_token (
        is_used bit not null comment 'Da dung hay chua; dat mat khau thanh cong dat cot nay thanh true',
        created_at datetime(6) not null comment 'Thoi diem phat token, luu theo gio UTC',
        expires_at datetime(6) not null comment 'Thoi diem het han, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        user_id bigint not null comment 'Nguoi dung yeu cau dat lai mat khau',
        token_hash varchar(64) not null comment 'SHA-256 cua token dang hex, duy nhat; chuoi tho khong bao gio duoc luu',
        primary key (id)
    ) comment='Token dat lai mat khau, dung mot lan, luu duoi dang hash' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table permission (
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        code varchar(64) not null comment 'Ma quyen dang UPPER_SNAKE, duy nhat',
        name varchar(128) not null comment 'Ten hien thi cua quyen',
        description varchar(255) comment 'Mo ta quyen',
        primary key (id)
    ) comment='Quyen han don le trong mo hinh RBAC' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table product (
        is_active BIT DEFAULT TRUE not null comment 'Con hieu luc hay da bi xoa mem; false la da xoa mem, cot noi bo khong lo ra response',
        is_best_seller bit not null comment 'Co phai san pham ban chay',
        is_featured bit not null comment 'Co phai san pham noi bat',
        rating decimal(2,1) not null comment 'Diem danh gia trung binh, thang 0.0-5.0',
        review_count integer not null comment 'So luot danh gia, tinh lai khi co danh gia moi',
        sold integer not null comment 'So luong da ban, dung cho sap xep best_selling',
        stock integer not null comment 'So luong con trong kho; 0 la het hang',
        brand_id bigint comment 'Thuong hieu cua san pham; null neu khong gan',
        category_id bigint not null comment 'Danh muc cua san pham',
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC; co so cho sap xep newest',
        effective_price BIGINT GENERATED ALWAYS AS (COALESCE(sale_price, price)) STORED comment 'Gia thuc te phai tra = COALESCE(sale_price, price), cot sinh STORED',
        id bigint not null auto_increment comment 'Khoa chinh',
        price bigint not null comment 'Gia goc, so nguyen VND',
        sale_price bigint comment 'Gia khuyen mai, so nguyen VND; null la khong giam gia',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        unit varchar(32) not null comment 'Don vi tinh hien thi canh gia',
        origin varchar(128) comment 'Xuat xu san pham',
        slug varchar(160) not null comment 'Slug khong dau, duy nhat, dung lam duong dan',
        short_description varchar(500) comment 'Mo ta ngan hien thi tren the san pham',
        description TEXT comment 'Mo ta day du cua san pham',
        name varchar(255) not null comment 'Ten hien thi cua san pham',
        name_normalized varchar(255) comment 'Ten da bo dau va ha chu thuong, phuc vu tim kiem khong dau',
        primary key (id)
    ) comment='San pham' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table product_image (
        sort_order integer not null comment 'Thu tu hien thi trong gallery, nho hon dung truoc',
        id bigint not null auto_increment comment 'Khoa chinh',
        product_id bigint not null comment 'San pham so huu anh nay',
        url varchar(255) not null comment 'Duong dan anh tuong doi, bat dau bang /images/',
        primary key (id)
    ) comment='Anh cua san pham, hien thuc cua Product.images' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table province (
        code varchar(16) not null comment 'Ma tinh/thanh, khoa chinh tu nhien',
        name varchar(128) not null comment 'Ten tinh/thanh',
        primary key (code)
    ) comment='Tinh/thanh pho; code la natural key' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table refresh_token (
        is_revoked bit not null comment 'Da bi thu hoi hay chua; logout dat cot nay thanh true',
        created_at datetime(6) not null comment 'Thoi diem phat token, luu theo gio UTC',
        expires_at datetime(6) not null comment 'Thoi diem het han, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        user_id bigint not null comment 'Nguoi dung so huu token',
        token varchar(512) not null comment 'Chuoi refresh token, duy nhat',
        primary key (id)
    ) comment='Refresh token da phat cho mot phien dang nhap' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table review (
        rating integer not null comment 'Diem danh gia, so nguyen tu 1 den 5',
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        product_id bigint not null comment 'San pham duoc danh gia',
        author_name varchar(128) not null comment 'Ten nguoi danh gia tu khai',
        content TEXT not null comment 'Noi dung danh gia, toi thieu 10 ky tu',
        primary key (id)
    ) comment='Danh gia cua khach ve san pham' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table role (
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        code varchar(64) not null comment 'Ma vai tro dang UPPER_SNAKE, duy nhat',
        name varchar(128) not null comment 'Ten hien thi cua vai tro',
        description varchar(255) comment 'Mo ta vai tro',
        primary key (id)
    ) comment='Vai tro trong mo hinh RBAC' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table role_permission (
        created_at datetime(6) not null comment 'Thoi diem cap quyen, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        permission_id bigint not null comment 'Quyen duoc cap',
        role_id bigint not null comment 'Vai tro duoc cap quyen',
        primary key (id)
    ) comment='Bang noi role - permission' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table user (
        created_at datetime(6) not null comment 'Thoi diem tao, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        updated_at datetime(6) comment 'Thoi diem cap nhat gan nhat, luu theo gio UTC',
        phone varchar(20) not null comment 'So dien thoai lien he',
        password_hash varchar(100) not null comment 'Bam mat khau; tuyet doi khong tra ra response',
        full_name varchar(128) not null comment 'Ho ten day du',
        full_name_normalized varchar(128) comment 'Ho ten da bo dau va ha chu thuong, phuc vu tim kiem khong dau',
        email varchar(160) not null comment 'Email dang nhap, duy nhat toan he',
        avatar varchar(255) comment 'Duong dan anh dai dien tuong doi; null neu chua co',
        primary key (id)
    ) comment='Tai khoan nguoi dung' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table user_role (
        created_at datetime(6) not null comment 'Thoi diem gan vai tro, luu theo gio UTC',
        id bigint not null auto_increment comment 'Khoa chinh',
        role_id bigint not null comment 'Vai tro duoc gan',
        user_id bigint not null comment 'Nguoi dung duoc gan vai tro',
        primary key (id)
    ) comment='Bang noi user - role' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create table ward (
        id bigint not null auto_increment comment 'Khoa chinh',
        district_code varchar(16) not null comment 'Quan/huyen chua phuong/xa nay',
        name varchar(128) not null comment 'Ten phuong/xa',
        primary key (id)
    ) comment='Phuong/xa' engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

    create index idx_user_id 
       on address (user_id);

    alter table category 
       add constraint uk_slug unique (slug);

    create index idx_user_id 
       on customer_order (user_id);

    create index idx_status 
       on customer_order (status);

    create index idx_created_at 
       on customer_order (created_at);

    create index idx_full_name_normalized 
       on customer_order (full_name_normalized);

    alter table customer_order 
       add constraint uk_code unique (code);

    create index idx_province_code 
       on district (province_code);

    create index idx_order_id 
       on order_item (order_id);

    create index idx_product_id 
       on order_item (product_id);

    create index idx_order_id 
       on order_status_history (order_id);

    create index idx_user_id 
       on password_reset_token (user_id);

    alter table password_reset_token 
       add constraint uk_token_hash unique (token_hash);

    alter table permission 
       add constraint uk_code unique (code);

    create index idx_name_normalized 
       on product (name_normalized);

    create index idx_effective_price 
       on product (effective_price);

    create index idx_category_id 
       on product (category_id);

    create index idx_brand_id 
       on product (brand_id);

    alter table product 
       add constraint uk_slug unique (slug);

    create index idx_product_id 
       on product_image (product_id);

    create index idx_user_id 
       on refresh_token (user_id);

    alter table refresh_token 
       add constraint uk_token unique (token);

    create index idx_product_id 
       on review (product_id);

    alter table role 
       add constraint uk_code unique (code);

    create index idx_permission_id 
       on role_permission (permission_id);

    alter table role_permission 
       add constraint uk_role_id_permission_id unique (role_id, permission_id);

    create index idx_full_name_normalized 
       on user (full_name_normalized);

    alter table user 
       add constraint uk_email unique (email);

    create index idx_role_id 
       on user_role (role_id);

    alter table user_role 
       add constraint uk_user_id_role_id unique (user_id, role_id);

    create index idx_district_code 
       on ward (district_code);

    alter table address 
       add constraint fk_address_user 
       foreign key (user_id) 
       references user (id);

    alter table category 
       add constraint fk_category_parent 
       foreign key (parent_id) 
       references category (id);

    alter table customer_order 
       add constraint fk_customer_order_user 
       foreign key (user_id) 
       references user (id);

    alter table district 
       add constraint fk_district_province 
       foreign key (province_code) 
       references province (code);

    alter table order_item 
       add constraint fk_order_item_order 
       foreign key (order_id) 
       references customer_order (id);

    alter table order_status_history 
       add constraint fk_order_status_history_order 
       foreign key (order_id) 
       references customer_order (id);

    alter table password_reset_token 
       add constraint fk_password_reset_token_user 
       foreign key (user_id) 
       references user (id);

    alter table product 
       add constraint fk_product_brand 
       foreign key (brand_id) 
       references brand (id);

    alter table product 
       add constraint fk_product_category 
       foreign key (category_id) 
       references category (id);

    alter table product_image 
       add constraint fk_product_image_product 
       foreign key (product_id) 
       references product (id);

    alter table refresh_token 
       add constraint fk_refresh_token_user 
       foreign key (user_id) 
       references user (id);

    alter table review 
       add constraint fk_review_product 
       foreign key (product_id) 
       references product (id);

    alter table role_permission 
       add constraint fk_role_permission_permission 
       foreign key (permission_id) 
       references permission (id);

    alter table role_permission 
       add constraint fk_role_permission_role 
       foreign key (role_id) 
       references role (id);

    alter table user_role 
       add constraint fk_user_role_role 
       foreign key (role_id) 
       references role (id);

    alter table user_role 
       add constraint fk_user_role_user 
       foreign key (user_id) 
       references user (id);

    alter table ward 
       add constraint fk_ward_district 
       foreign key (district_code) 
       references district (code);
