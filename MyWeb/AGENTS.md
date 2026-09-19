# Repository Guidelines

## 專案結構與模組配置

`MyWeb` 是 Java 25 / Spring Boot 4.1 的 Spring MVC 應用程式，預設執行於 `8081`。它同時提供 Thymeleaf 網頁、手寫 REST API、Spring Data REST API、Actuator 端點與 Spring Boot Admin Client。

Java 原始碼位於 `src/main/java/com/company/myweb/`，主要分層如下：

- `controller/`：Thymeleaf MVC Controller。
- `rest/`：手寫 REST Controller，例如 `/api/contact/**`。
- `service/`：業務邏輯與密碼處理。
- `repository/`：Spring Data JPA 與 JdbcTemplate 資料存取。
- `model/`：Entity、表單 Model 與資料模型。
- `config/`、`config/security/`：應用程式、Actuator 與 Spring Security 設定。
- `aspect/`：AOP logging。
- `auditor/`：JPA auditing 的 `AuditorAware`。
- `myValidation/`：自訂 Bean Validation。

Thymeleaf 頁面位於 `src/main/resources/templates/`：`nav/` 放公開頁面，`authenticated/` 放登入後頁面，`text/` 放教學說明 fragment。與 `/dashboard` 相關的 UI 文案統一使用「個人儀表板」。資料庫初始化檔位於 `src/main/resources/sql/`；測試放在 `src/test/java/`；`target/` 為產物，不得提交。

## 建置、測試與本機開發

在 `MyWeb/` 目錄使用 Maven Wrapper：

- `.\mvnw.cmd spring-boot:run`：啟動 `http://localhost:8081`。
- `.\mvnw.cmd test`：執行所有 JUnit 測試。
- `.\mvnw.cmd clean test`：清除舊產物後重新編譯及測試。
- `.\mvnw.cmd clean package`：測試並建立可執行 JAR。
- `.\mvnw.cmd resources:resources`：更新模板、CSS 與 JavaScript 等資源。

本機使用 H2 記憶體資料庫，由 `sql/schema.sql` 與 `sql/data.sql` 初始化；`spring.jpa.hibernate.ddl-auto=validate` 只驗證 Entity 與 schema，不修改資料庫結構。

## API 與 Security

手寫 REST API：

- `/api/contact/**`：由 `rest/ContactRestController` 提供，可回傳 JSON 或 XML。
- `GET /api/contact/getContactMessageByStatus?status=OPEN`：依狀態查詢聯絡訊息。
- `POST /api/contact/saveContactMessage`：儲存聯絡訊息。

Spring Data REST：

- base path：`/spring-data-api`。
- 依 `JpaRepository` 自動暴露 Entity Repository API。
- HAL Explorer 供瀏覽 API 資源。

Actuator：

- base path：`/myWeb/actuator`。
- 與應用程式共用 `8081`。
- 目前 `management.endpoints.web.exposure.include=*`，只適合本機開發。
- `MyWebActuatorInfoContributor` 提供自訂 `/info` 內容。

Security 規則由 `config/security/SpringSecurityConfig` 管理：

- `/dashboard`、`/profilePage`、`/updateProfile`：需要登入。
- `/student/**`：需要 `ROLE_STUDENT`。
- `/admin/**`、`/api/**`、`/spring-data-api/**`、`/myWeb/actuator/**`：需要 `ROLE_ADMIN`。
- Form Login 使用 `/login`，成功後預設導向 `/dashboard`。
- HTTP Basic 供 AdminActuator 輪詢 Actuator 與外部 REST Client 呼叫受保護 API。

## 程式風格與命名

Java 使用 4 個空格縮排；類別採 `PascalCase`，方法及欄位採 `camelCase`，常數採 `UPPER_SNAKE_CASE`。沿用 `*Controller`、`*Service`、`*Repository` 後綴及現有 package 分層。Controller 應負責 request/response flow，業務邏輯放入 service，資料存取交由 repository。

所有頁面以繁體中文呈現，route、model key、資料欄位及技術識別字保持原文。CSS 由 `static/css/app.css` 統一載入：基礎 token 放 `foundation.css`，共用 BEM 元件放 `components.css`，頁面特例放 `pages.css`。避免 inline style 與重複 selector；JavaScript 共用行為放 `static/js/app.js`。

## 測試規範

使用 JUnit 5、Spring Boot Test 與 Spring Security Test。測試類命名為 `*Tests`，方法描述行為，例如 `registerRejectsDuplicateEmail()`。修改 Controller、Security、Validation、Persistence 或 REST API 時，需涵蓋成功與失敗路徑。UI 修改至少檢查桌面與行動版，確認無文字重疊或水平頁面溢位。

## Commit 與 Pull Request

新提交應使用具體祈使句，例如 `Fix contact API validation`。PR 需說明目的、主要變更、驗證指令及相關 issue；UI 變更附前後截圖，Security、Actuator 或 REST API 變更說明測試方式。不要提交憑證、`target/`、IDE 設定或無關格式化變更。

## 設定與安全性

`application.properties` 包含 H2、JPA、Security、Spring Data REST、Actuator、Logback 與 Spring Boot Admin Client 設定。不得提交真實密碼、API key 或環境專屬憑證。註冊密碼必須透過 `PasswordEncoder`（BCrypt）處理；不要在 log、REST response 或 Actuator info 暴露明文密碼、BCrypt hash 或不必要的個人資料。

修改權限規則、H2 Console、Session、Actuator 暴露範圍、Admin Client 管理憑證或 REST API 方法時，應在 PR 中記錄風險與本機驗證方式。
