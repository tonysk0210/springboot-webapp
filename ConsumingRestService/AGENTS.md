# ConsumingRestService 模組規範

## 模組定位

`ConsumingRestService` 是 Java 25 / Spring Boot 4.1 的 REST Client 示範應用程式，預設執行於 `http://localhost:8082`。它不直接連接資料庫，而是呼叫 `MyWeb` 的 REST API，示範三種 HTTP Client：

- OpenFeign：`OpenFeignRestClient`。
- RestTemplate：`/saveMessages`。
- WebClient：`/saveMessagesWebClient`。

## 專案結構

- `controller/ContactRestController.java`：對外提供 Client 示範端點。
- `proxy/OpenFeignRestClient.java`：宣告式 OpenFeign Client。
- `config/ProjectConfiguration.java`：Feign、RestTemplate 與 WebClient 的 Basic Auth 設定。
- `dto/`：`Contact` 與 `Response` 資料傳輸物件。
- `src/main/resources/application.properties`：應用程式 Port 設定。
- `src/test/java/`：Spring Boot Test。

## 對外端點與目標服務

- `GET /getMessages?status=OPEN`：使用 OpenFeign 呼叫 `MyWeb` 的 `GET /api/contact/getContactMessageByStatus`。
- `POST /saveMessages`：使用 RestTemplate 呼叫 `MyWeb` 的 `POST /api/contact/saveContactMessage`。
- `POST /saveMessagesWebClient`：使用 WebClient 呼叫相同的 MyWeb POST API，回傳 `Mono<Response>`。

啟動本模組前，先確認 `MyWeb` 已在 `8081` 啟動。目前 OpenFeign URL 與其他 Client URI 都是固定的 `http://localhost:8081`；雖然 POM 包含 LoadBalancer，現有程式尚未使用服務發現或動態負載平衡。

## 認證與資料處理

`ProjectConfiguration` 目前為三種 Client 設定 MyWeb 的 HTTP Basic Auth，並由 MyWeb 驗證 `ROLE_ADMIN`。呼叫端不應將真實帳密硬編碼在 Java 或 properties；正式環境應改用環境變數、外部設定或秘密管理工具。

`Contact` 與 `Response` 是遠端 API 的 DTO，不是 JPA Entity。修改欄位時，需確認 JSON 結構與 MyWeb 的 `/api/contact/**` 契約一致。

## 建置、測試與開發規範

在 `ConsumingRestService/` 目錄執行：

- `.\mvnw.cmd spring-boot:run`：啟動 REST Client 示範服務。
- `.\mvnw.cmd test`：執行測試。
- `.\mvnw.cmd clean package`：測試並建立 JAR。

修改外部 HTTP 呼叫時，至少驗證成功回應、認證失敗、遠端服務未啟動與錯誤回應轉換。不要在 Controller 內加入資料庫存取；外部呼叫設定集中於 `config/`，Feign 介面集中於 `proxy/`，傳輸模型集中於 `dto/`。
