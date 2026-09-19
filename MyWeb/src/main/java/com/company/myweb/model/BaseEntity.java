package com.company.myweb.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 呼叫 repository.save() 時，會透過 AuditingEntityListener 觸發稽核填欄位：
 * 1) createdAt 與 updatedAt 由 Spring 自動填 LocalDateTime.now()
 * 2) createdBy 與 updatedBy 由 AuditorAware bean（AuditAwareImpl）提供的值填入
 */
@Getter
@Setter
@ToString
@MappedSuperclass // JPA 註解：定義子類別共用欄位，但本身不會產生對應資料表
@EntityListeners(AuditingEntityListener.class)
// Spring Data JPA 提供的 JPA 監聽器，在 INSERT / UPDATE 之前自動偵測 Entity 內的 @CreatedDate / @CreatedBy / @LastModifiedDate / @LastModifiedBy
// 欄位，並填入時間戳與稽核者名字（後者透過你註冊的 AuditorAware bean 提供）。要三個元件配合：@EntityListeners（Entity 上）+ @EnableJpaAuditing（啟動類上）+ AuditorAware bean（提供 auditor 名字）。
public class BaseEntity {

    @CreatedDate
    @Column(updatable = false)
    @JsonIgnore // 隱藏於 REST JSON；不影響 JPA 對資料庫的持久化
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false)
    @JsonIgnore // 隱藏稽核者，不讓 REST API 輸出或接收此欄位
    private String createdBy;

    @LastModifiedDate
    @Column(insertable = false)
    @JsonIgnore // 隱藏於 REST JSON；資料庫欄位仍由 JPA 稽核機制管理
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(insertable = false)
    @JsonIgnore // 隱藏稽核者，不讓 REST API 輸出或接收此欄位
    private String updatedBy;
}


