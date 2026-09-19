# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 模組定位

`AdminActuator` 是倉庫下三個獨立 Spring Boot 專案之一（其他兩個為 `MyWeb`、`ConsumingRestService`，位於同層目錄）。三者 **沒有** 父層聚合 POM，各自建置與執行。

這個模組的責任只有一件事：**當 Spring Boot Admin Server（Codecentric），提供監控 `MyWeb` 的 Web UI**。

服務綁定 Spring Boot **4.1.0** + Java **25**，Port **8083**。

## 這是整個 repo 最小的模組

| 項目 | 內容 |
|---|---|
| Java 檔 | **1 個** — `AdminActuatorApplication.java` |
| 設定 | **2 行** — `spring.application.name` + `server.port=8083` |
| 依賴 | `spring-boot-starter-webmvc` + `spring-boot-admin-starter-server`（+ test） |
| 自訂程式碼 | **無** |

進入點只有兩個註解：

```java
@EnableAdminServer      // ← 這一個註解就是整個模組的全部功能
@SpringBootApplication
public class AdminActuatorApplication { ... }
```

**要改這個模組時請先確認是否真的需要寫 Java 程式碼** —— 絕大多數需求（輪詢頻率、通知、安全性）都是加 `application.properties` 屬性或依賴就能達成。

## 常用指令

在 AdminActuator 目錄下執行（此檔案所在目錄）：

```powershell
.\mvnw.cmd spring-boot:run     # 啟動（8083）
.\mvnw.cmd clean package       # 建置
.\mvnw.cmd test                # 測試
```

**啟動順序**：`AdminActuator` 要**先於** `MyWeb` 啟動 —— `MyWeb` 的 `spring.boot.admin.client.enabled=true`，啟動時會主動向 8083 註冊；8083 沒開的話 MyWeb 的 console 會一直刷連線失敗。

本模組**不依賴任何其他模組**，可以單獨啟動（只是儀表板上會沒有任何 instance）。

## 監控機制

註冊是 **client 主動 push**，不是 server 掃描：

```
MyWeb 啟動
   ↓ ① POST 註冊自己（帶 service-base-url / management-base-url / Basic Auth 帳密）
AdminActuator(8083)
   ↓ ② 反向定期輪詢 MyWeb 的 /myWeb/actuator/**
MyWeb 回 health / info / metrics / logfile / loggers
```

所有連線資訊（8083 的位址、回呼用的帳密）都設定在 **`MyWeb` 那一側**的 `application.properties`，這裡不需要也不該列出被監控的對象。

### 實測輪詢頻率（預設值，本模組未覆寫）

| 來源 | 端點 | 週期 | 每次請求數 |
|---|---|---|---|
| Server 背景健康檢查 | `/health` | 約 **20 秒** | 1 |
| Server 背景資訊更新 | `/info` | 約 **60 秒** | 1 |
| **Web UI 開著時** | `/metrics/**` | 約每分鐘一波 | **~24** |

⚠️ **關掉瀏覽器上的 Admin 頁面，輪詢量會從每分鐘 60+ 次掉到約 4 次。** MyWeb 的 actuator 走 Basic Auth 且無 session，每個請求都完整跑一次認證（BCrypt + 3 筆 SQL）—— 這是 MyWeb log 容易被洗版的根源。

### 要調整輪詢頻率

本模組的 `application.properties` 目前只有兩行、完全吃預設值。要放慢就加：

```properties
spring.boot.admin.monitor.status-interval=30s
spring.boot.admin.monitor.status-lifetime=30s
spring.boot.admin.monitor.info-interval=5m
```

⚠️ `status-interval` 與 `status-lifetime` **必須一起改** —— 只改前者會被後者的快取擋住，看不到效果。預設值（取自 `spring-boot-admin-server-4.1.2.jar` 的 `spring-configuration-metadata.json`）：`status-interval` / `status-lifetime` 皆 `10s`、`info-interval` / `info-lifetime` 皆 `1m`、`default-timeout` `10s`、`status-max-backoff` `60s`。

## 版本鏈鎖定

`spring-boot-admin.version` = **4.1.2**（對應 Boot 4.1），由 `spring-boot-admin-dependencies` BOM 匯入。

⚠️ **這個版本號必須與 `../MyWeb/pom.xml` 內的 `spring-boot-admin.version` 完全一致** —— server 與 client 版本不合會註冊失敗或功能異常。升級時兩邊要一起改。

## 安全性

**目前完全沒有安全設定** —— pom 裡沒有 `spring-boot-starter-security`，`http://localhost:8083` 任何人都能開，而儀表板上可以看到 MyWeb 的環境變數、Bean、執行緒傾印、log 檔內容。

本機開發無妨；若要對外暴露，至少要加 `spring-boot-starter-security` 並設定登入。

> 註：儀表板顯示的 `user.password` 欄位是遮蔽過的（`******`），但 `/env` 之類的端點內容仍可能包含敏感資訊。

## 測試現況

`src/test/java` 只有 `AdminActuatorApplicationTests`，內容是空的 `contextLoads()`。框架為 JUnit 5 + Spring Boot Test。測試類命名 `*Tests`。

## Boot 4 / Java 25 注意事項

- **`spring-boot-starter-web` 已 deprecated** → 本模組使用 `spring-boot-starter-webmvc`
- 本模組**沒有** Lombok，所以不需要 `maven-compiler-plugin` 的 `<annotationProcessorPaths>` 設定（另外兩個模組有）
- `spring-boot-maven-plugin` 使用預設設定（`MyWeb` 那邊為了 log 路徑額外設了 `<workingDirectory>`，這裡不需要）
