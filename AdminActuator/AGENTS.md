# AdminActuator 模組規範

## 模組定位

`AdminActuator` 是 Java 25 / Spring Boot 4.1 的 Spring Boot Admin Server，預設執行於 `http://localhost:8083`。它使用 `@EnableAdminServer` 提供 Spring Boot Admin 監控儀表板，接收其他 Spring Boot 應用程式的 Client 註冊，並讀取其 Actuator health、info、metrics 與管理資訊。

本模組不是 `MyWeb` 的資料庫服務，也不負責直接操作 `MyWeb` 的 Entity 或 Repository。目前沒有啟用 Spring Security，因此本機 Dashboard 預設未受登入保護；正式環境若部署，必須另外設計管理介面的認證與授權。

## 專案結構

- `src/main/java/com/company/AdminActuator/AdminActuatorApplication.java`：應用程式入口與 `@EnableAdminServer`。
- `src/main/resources/application.properties`：應用程式名稱與 `8083` Port 設定。
- `src/test/java/`：Spring Boot Test。
- `pom.xml`：Spring MVC、Spring Boot Admin Server 與測試依賴。

## 建置、測試與啟動

在 `AdminActuator/` 目錄執行：

- `.\mvnw.cmd spring-boot:run`：啟動監控儀表板。
- `.\mvnw.cmd test`：執行測試。
- `.\mvnw.cmd clean package`：測試並建立 JAR。

跨服務監控驗證時，先啟動本模組，再啟動 `MyWeb`。`MyWeb` 的 Spring Boot Admin Client 會向 `http://localhost:8083` 註冊。

## 開發規範

Java 使用 4 個空格縮排；類別採 `PascalCase`，方法與欄位採 `camelCase`。保持 Server 設定與監控職責集中於本模組，不在此加入 `MyWeb` 的業務邏輯或資料存取。

## 安全性

不要提交真實管理憑證、API key 或環境專屬端點。若加入 Spring Security 或反向代理，應同步設定 Dashboard、註冊端點與 Actuator 查詢端點的認證，並在 PR 中說明驗證方式與風險。
