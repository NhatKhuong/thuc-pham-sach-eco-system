package com.nss.ddd.infrastructure.persistence.mapper;

/**
 * Một dòng {@code (userId, roleCode)} của
 * {@link UserRoleJPAMapper#findRoleCodesByUserIds(java.util.Collection)} — kiểu mang kết quả
 * <b>của riêng tầng hạ tầng</b>.
 * <p>
 * <b>Tồn tại để không phải map {@code Object[]} theo vị trí.</b> coding-conventions §7 chỉ cho phép
 * map theo vị trí ở <i>native query</i>; đây là JPQL, và JPQL dựng thẳng được kiểu kết quả bằng
 * constructor expression. Đổi lại là một tên gọi cho từng cột thay vì hai chỉ số dễ hoán vị.
 * <p>
 * <b>Nằm ở infrastructure chứ không ở {@code domain/model/}, khác {@code PageResult},
 * {@code DailyRevenue} và {@code StatusCount}.</b> Ba kiểu kia đi <i>lên</i> qua port nên chúng phải
 * do domain sở hữu; kiểu này thì không bao giờ rời khỏi ranh giới adapter —
 * {@code UserRoleRepositoryImpl} gom nó thành {@code Map<Long, List<String>>} ngay tại chỗ, và port
 * chỉ nhìn thấy cái map đó. Đẩy nó xuống domain là bắt domain biết về hình dạng một dòng kết quả
 * SQL.
 * <p>
 * Là {@code record} chứ không {@code @Data} class: nó bất biến, chỉ có hai giá trị vô hướng, và
 * Hibernate gọi được constructor của record trong constructor expression. Quy ước Lombok của
 * coding-conventions §5 nói về <i>data class</i> có setter (entity, DTO, command) — thứ này không
 * phải một trong số đó.
 *
 * @param userId khoá chính của tài khoản
 * @param roleCode mã vai trò UPPER_SNAKE, ví dụ {@code CUSTOMER}
 */
public record UserRoleCode(Long userId, String roleCode) {
}
