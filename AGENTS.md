# Repository Guidelines

## 專案結構與模組配置

此 repository 包含三個獨立的 Java 25 / Spring Boot 4.1 Maven 專案，根目錄沒有聚合 `pom.xml`：

- `MyWeb/`：主要的 Spring MVC 教育／課程管理應用程式。使用 Thymeleaf、Spring Security、JPA/Hibernate、H2、Spring Data REST、Actuator 與 Spring Boot Admin Client。
- `AdminActuator/`：Spring Boot Admin Server，使用 `@EnableAdminServer`，預設在 `8083` 提供監控儀表板。
- `ConsumingRestService/`：REST Client 示範應用程式，預設在 `8082`；使用 OpenFeign、RestTemplate 與 WebClient 呼叫 `MyWeb` 的 REST API。

每個模組都有自己的 `pom.xml`、Maven Wrapper、`src/main/java/`、`src/main/resources/` 與 `src/test/java/`。不要提交任何模組的 `target/`。

## 建置、測試與本機開發

所有命令都應在目標模組內執行；Windows PowerShell 範例：

- `cd MyWeb; .\mvnw.cmd spring-boot:run`：在 `http://localhost:8081` 啟動主要網站。
- `cd AdminActuator; .\mvnw.cmd spring-boot:run`：在 `http://localhost:8083` 啟動監控儀表板。
- `cd ConsumingRestService; .\mvnw.cmd spring-boot:run`：在 `http://localhost:8082` 啟動 REST Client 示範服務。
- `cd <module>; .\mvnw.cmd test`：編譯並執行目前模組的測試。
- `cd <module>; .\mvnw.cmd clean package`：清除舊產物、測試並建立 JAR。

跨服務驗證時，先啟動 `MyWeb`，再啟動 `ConsumingRestService`。若要查看監控，先啟動 `AdminActuator`，再啟動 `MyWeb`，讓 Spring Boot Admin Client 註冊。不要提交任何模組的 `target/`。

## 模組間介面

`MyWeb` 提供兩類 REST API：

- 手寫 API：`/api/contact/**`，由 `com.company.myweb.rest.ContactRestController` 提供。
- Spring Data REST API：`/spring-data-api/**`，由 `JpaRepository` 自動暴露，並提供 HAL Explorer。

`MyWeb` 的 Actuator base path 是 `/myWeb/actuator`，與應用程式共用 `8081` port。`AdminActuator` 透過 Spring Boot Admin Client 監控這些 Actuator 端點。

## 程式風格與命名

Java 使用 4 個空格縮排；類別採 `PascalCase`，方法與欄位採 `camelCase`，常數採 `UPPER_SNAKE_CASE`。沿用 `*Controller`、`*Service`、`*Repository`、`*Configuration`、`*Client` 等後綴及現有 package 分層。

Controller 保持精簡，業務邏輯放入 service，資料存取交由 repository，外部 HTTP 整合集中於 `proxy/`、`*Client` 或 `config/`。`ConsumingRestService` 的 Feign 介面命名為 `OpenFeignRestClient`，資料傳輸物件位於 `dto/`。

專案未設定獨立 formatter；提交前應使用 IDE 格式化，避免無關的大範圍排版變更。

## 測試規範

測試使用 JUnit 5、Spring Boot Test，測試類命名為 `*Tests`。修改 Controller、Security、Validation、Persistence 或外部 HTTP 整合時，應涵蓋成功與錯誤路徑，並至少執行受影響模組的 `.\mvnw.cmd test`。

## Commit 與 Pull Request

新提交請使用具體祈使句，例如 `Add contact client error handling`。PR 應說明目的、受影響模組、驗證命令及相關 issue；畫面變更附前後截圖，跨服務變更列出啟動順序與 ports。避免混入 IDE 設定、`target/` 或無關重構。

## 設定與安全性

設定集中於各模組的 `application.properties`。不得提交真實密碼、API key 或環境專屬端點。`MyWeb` 目前示範使用：

- Form Login：`/login`，成功後導向 `/dashboard`。
- Basic Auth：供 AdminActuator 與 REST Client 呼叫受保護端點。
- `/student/**`：需要 `ROLE_STUDENT`。
- `/admin/**`、`/api/**`、`/spring-data-api/**`、`/myWeb/actuator/**`：需要 `ROLE_ADMIN`。

目前 Actuator 暴露範圍為 `*`、H2 Console 已啟用，且 Admin Client metadata 含示範帳密；這些設定只適合本機開發。調整 Security、H2 Console、Actuator 暴露範圍或管理憑證時，須在 PR 說明風險與本機驗證方式。
