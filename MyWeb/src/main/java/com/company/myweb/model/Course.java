package com.company.myweb.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * 課程 Entity — 對應 DB 表 `courses`（schema.sql）
 * 與 Person 為多對多（雙向），中介表為 `person_courses`
 * owning side 在 Person.courses（管理 @JoinTable），此類為 mappedBy 反向端
 * 註：欄位驗證註解為表單直接綁 Entity 的權宜作法，嚴謹應搬到 DTO 層
 */
@Getter
@Setter
@Entity
@Table(name = "courses")
public class Course extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int courseId;

    @NotBlank(message = "⚠\uFE0F 課程名稱必填")
    private String name;

    @NotBlank(message = "⚠\uFE0F 課程費用必填")
    @Pattern(regexp = "[0-9]+", message = "⚠\uFE0F 僅允許純數字")
    private String fees;

    // 反向端（inverse side）：mappedBy 指向 Person.courses 這個 owning side 欄位
    //   - owning side（Person.courses）負責寫入中介表 person_courses（新增/刪除選課紀錄）
    //   - 這裡的 persons 只是 read-only 視角：查「哪些學生選了這門課」用
    //   - 直接改動這裡的 Set（例如 courses.getPersons().add(person)）不會被 JPA 寫進 DB
    // EAGER：查課程時順便把選課學生一起載入（小專案可接受，大量學生時建議改 LAZY 避免 N+1）
    // 初始化為空 HashSet：避免呼叫端遇到 null → NPE
    @ManyToMany(mappedBy = "courses", fetch = FetchType.EAGER)
    private Set<Person> persons = new HashSet<>();

}
