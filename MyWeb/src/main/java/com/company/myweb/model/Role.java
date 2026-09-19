package com.company.myweb.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色 Entity — 對應 DB 表 `roles`（schema.sql）
 * 被 Person 單向引用：Person.roles 持有外鍵 role_id；本身無反向關聯欄位
 * 初始資料由 data.sql 種入（ADMIN / STUDENT），對應 SpringSecurity 的 hasRole("ADMIN") / hasRole("STUDENT")
 * 註：roleName 沒 @NotBlank / @Column(unique) 等約束，因為僅由系統管理員在 seed 階段設定，非表單輸入
 */
@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int roleId;

    private String roleName;
}
