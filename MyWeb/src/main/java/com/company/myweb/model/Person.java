package com.company.myweb.model;

import com.company.myweb.myValidation.FieldValueMatchValidator;
import com.company.myweb.myValidation.PasswordValidator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * 使用者 Entity — 對應 DB 表 `person`（schema.sql）
 * 關聯：Role、Address、Plan（外鍵在此），Course（多對多，中介表 person_courses）
 * 註：表單驗證註解為權宜作法，嚴謹寫法應搬到 DTO 層
 */
@Getter
@Setter
@Entity
// 自訂 class-level validator：檢查同一物件內兩個 field 值是否相等（跨欄位驗證）
//   - 這裡用來確保「密碼 vs 確認密碼」「Email vs 確認 Email」兩兩一致
//   - 實作邏輯在 myValidation/FieldValueMatchValidatorImpl（用 BeanWrapperImpl 反射取欄位值後 equals 比對）
//   - .List 是容器註解：Java 預設不允許在同一目標重複貼相同註解，需用容器包起來（也可改用 @Repeatable 的新寫法）
@FieldValueMatchValidator.List({
        @FieldValueMatchValidator(field = "password", fieldMatch = "confirmPassword", message = "⚠\uFE0F 密碼與確認密碼不一致"),
        @FieldValueMatchValidator(field = "email", fieldMatch = "confirmEmail", message = "⚠\uFE0F Email 與確認 Email 不一致")})
@ToString(callSuper = true)
public class Person extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int personId;

    @NotBlank(message = "⚠\uFE0F 姓名不可為空")
    private String name;

    @Pattern(regexp = "[0-9]{10}", message = "⚠\uFE0F 手機號碼必須為 10 位數字")
    private String mobile;

    private String email;
    
    @Transient // @Transient：表單專用欄位，不入 DB
    @JsonIgnore // 不讓表單確認欄位出現在 REST JSON；@Transient 才負責不存入 DB
    private String confirmEmail;

    // 自訂 field-level validator：檢查密碼強度（非 null、非弱密碼清單、長度 >= 8）
    //   - 實作在 myValidation/PasswordValidatorImpl
    //   - 三種失敗各有自訂訊息，不使用註解上的 message 預設值
    @PasswordValidator
    @JsonIgnore // 避免密碼（即使已加密）出現在 REST JSON，也不接受 JSON 回填
    private String password;

    @Transient // @Transient：表單專用欄位，不入 DB
    @JsonIgnore // 不讓表單確認欄位出現在 REST JSON；@Transient 才負責不存入 DB
    private String confirmPassword;

    /*
     * Field shadowing：Person 這邊再宣告一次 createdBy，遮蔽父類 BaseEntity 的 createdBy。
     * 效果是「同一個 Person 物件同時有兩個 createdBy field」— 父類的、子類的各佔一個記憶體位置。
     *
     * 為什麼要這樣：
     *   註冊流程使用者「尚未登入」→ AuditorAware 回傳 "anonymousUser"。
     *   若讓 BaseEntity 的 @CreatedBy 自動填值，會蓋掉 PersonService 手動設的 email，
     *   結果 DB 記錄成 anonymousUser、看不出是誰註冊。
     *
     * 遮蔽後兩個 field 的分工：
     *   - 父類 BaseEntity.createdBy 有 @CreatedBy 註解 → auditor 塞 "anonymousUser" 進去（僅存在記憶體、不寫 DB）
     *   - 子類 Person.createdBy 沒 @CreatedBy 註解 → PersonService 手動塞 email 進去，Hibernate 寫 DB 拿的是這個
     *   - Hibernate 內部規則：DB 欄位只能對映一個 Java field，同名時「子類優先」→ 父類 field 淪為記憶體孤兒，值不進 DB
     *   - 結果：DB 內 created_by = 註冊者 email
     *
     * 只影響 Person：Field shadowing 是 per-class 的，只在此類生效，不會外溢到其他繼承 BaseEntity 的 Entity。
     *   - Contact / Address / Role / Plan / Course 都沒 shadow → 走 BaseEntity 的正常稽核流程
     *     （登入者的 email，或未登入時的 "anonymousUser"）
     *   - News 不是 @Entity（走 JdbcTemplate），與此完全無關
     *   - 只有 Person 註冊有「chicken-and-egg」時序問題：被建立的人就是自己，但還沒登入
     *
     * 註：這是 workaround 手法，靠 Java 子類 field 覆蓋父類 field 的規則。
     *     未來重構可考慮取消 @CreatedBy 自動填、改由各 Service 手動維護。
     */
    private String createdBy;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role roles;

    @OneToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE})
    @JoinColumn(name = "address_id")
    private Address address;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    // owning side of 多對多：管理中介表 person_courses 的寫入
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "person_courses",
            joinColumns = @JoinColumn(name = "person_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    private Set<Course> courses = new HashSet<>();

}
