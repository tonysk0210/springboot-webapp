# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 模組定位

`ConsumingRestService` 是倉庫下三個獨立 Spring Boot 專案之一（其他兩個為 `MyWeb`、`AdminActuator`，位於同層目錄）。三者 **沒有** 父層聚合 POM，各自建置與執行。

這個模組是**純 client 端的教學範例** — 沒有資料庫、沒有前端、沒有安全設定，唯一目的是示範**三種 HTTP client 寫法**去呼叫 `MyWeb` 的 `/api/contact`。

服務綁定 Spring Boot **4.1.0** + Java **25** + Spring Cloud **2025.1.2 (Oakwood)**，Port **8082**。

⚠️ **必須先啟動 `../MyWeb`（8081）**，否則所有端點都會 `ConnectException: Connection refused`。Feign 是 lazy 的，錯誤發生在第一次呼叫時、不是啟動時。

## 常用指令

在 ConsumingRestService 目錄下執行（此檔案所在目錄）：

```powershell
.\mvnw.cmd spring-boot:run     # 啟動（8082，含 devtools 熱重載）
.\mvnw.cmd clean package       # 建置
.\mvnw.cmd test                # 測試
```

## 架構

根 package 為 **`com.company.ConsumingRestService`**（⚠️ **大寫開頭**，違反 Java 慣例，與 `MyWeb` 的全小寫 `com.company.myweb` 不一致；改動時沿用現況即可）。

```
com/company/ConsumingRestService/
├── ConsumingRestServiceApplication.java   # @SpringBootApplication + @EnableFeignClients
├── config/ProjectConfiguration.java       # 三個 HTTP client bean，各自預設 Basic Auth
├── controller/ContactRestController.java  # 三個對外端點，各用一種 client
├── dto/                                   # Contact、Response（獨立複製的 POJO，非共用 jar）
└── proxy/OpenFeignRestClient.java         # @FeignClient 宣告式介面
```

### 對外端點（8082）

| Method | 路徑 | 使用的 client | 回傳型別 |
|---|---|---|---|
| `GET` | `/getMessages?status=OPEN` | **Feign** | `List<Contact>` |
| `POST` | `/saveMessages` | **RestTemplate** | `ResponseEntity<Response>` |
| `POST` | `/saveMessagesWebClient` | **WebClient** | `Mono<ResponseEntity<Response>>` |

兩個寫入端點都**原樣傳遞上游 MyWeb 的 status code（`201 Created`）**。關鍵在回傳型別包了 `ResponseEntity`：

- RestTemplate 版 — `restTemplate.exchange(...)` 本來就回 `ResponseEntity`
- WebClient 版 — 必須用 `.retrieve().toEntity(Response.class)`；**換成 `.bodyToMono(Response.class)` 會丟掉狀態碼**，這一層就退回 Spring 預設的 200，造成「body 寫 `statusCode: 201`、外層 HTTP 卻是 200」的不一致

### 三個 client 的分工

| | Feign | RestTemplate | WebClient |
|---|---|---|---|
| 風格 | **宣告式**（只定義 interface，實作由 Feign 執行期動態代理） | 命令式、阻塞 | 命令式、反應式 |
| 認證設定 | `BasicAuthRequestInterceptor` | `RestTemplateBuilder.basicAuthentication(...)` | `ExchangeFilterFunctions.basicAuthentication(...)` |
| 來源模組 | `spring-cloud-starter-openfeign` | `spring-boot-starter-restclient` | `spring-boot-starter-webflux` |

`@EnableFeignClients(basePackages = "com.company.ConsumingRestService.proxy")` 掃的是 **package** 不是類別名 —— 所以 `OpenFeignRestClient` 改名不需要動這行。

## ⚠️ 已知問題（動這個模組前先看）

### 1. 目標網址寫死在原始碼**三處**

| 檔案 | client | 內容 |
|---|---|---|
| `proxy/OpenFeignRestClient.java` | Feign | `@FeignClient(url = "http://localhost:8081/api/contact")` |
| `controller/ContactRestController.java` | RestTemplate | 方法內 `String uri = "http://localhost:8081/api/contact/saveContactMessage"` |
| `controller/ContactRestController.java` | WebClient | 同上（**同一個字串重複兩次**） |

三處都是**編譯期常數**，換環境要改程式碼重編譯，不能用環境變數或啟動參數覆寫。**改網址要三處一起改。** 要外部化請抽成 `@ConfigurationProperties`（`@FeignClient` 的 `url` 支援 `${...}` placeholder）。

### 2. 帳密硬編碼三次 + 8082 完全不設防

`config/ProjectConfiguration` 把 `admin@gmail.com` / `admin` 寫死在三個 bean 裡。同時 pom **沒有** `spring-boot-starter-security` —— 8082 的三個端點任何人都能打。

效果上 8082 是一個**無認證的代理**：透過它就能用 admin 權限操作 MyWeb 的 `/api/contact`。教學專案無妨，但這是本模組最該注意的一點。

### 3. `@Headers` 不會生效

```java
@GetMapping("/getContactMessageByStatus")
@Headers(value = "Content-Type: application/json")   // ← 無效
```

`feign.Headers` 是 Feign **原生註解**，只有 `feign.Contract.Default` 會處理。Spring Cloud OpenFeign 預設注入的是 `SpringMvcContract`（處理 `@GetMapping`、`@RequestParam`）。要用原生註解必須自己覆寫 `Contract` bean。而且 GET 沒有 body，指定 `Content-Type` 本來也沒意義（該用 `Accept`）。**可以直接刪掉。**

### 4. `spring-cloud-starter-loadbalancer` 形同未使用

pom 引入了負載平衡，但 `@FeignClient` 指定了 `url` → **直接打該位址，完全不走服務發現與 LoadBalancer**。`name = "contact-service"` 在這種情況下只剩「設定分組標籤」的意義。

### 5. 同時引入 webmvc + webflux → 跑在 Tomcat

兩者並存時 **MVC 勝出**，所以這個 app 是傳統阻塞式 servlet 容器。`WebClient` 仍可用（它只是個 HTTP client），`Mono` 回傳型別在 MVC 下走 async servlet 也能運作 —— 但拿不到端到端非阻塞的效益。這是為了在同一個 app 裡展示兩種 client 的**刻意取捨**。

### 6. DTO 是獨立複製的，不與 MyWeb 共用

`dto/Contact`、`dto/Response` 跟 MyWeb 的同名類別沒有任何關聯（沒有共用 jar）。這是模組解耦的常見做法，代價是**欄位要手動同步** —— MyWeb 的 `Contact` 加欄位，這邊不改就收不到。

## 測試現況

`src/test/java` 只有 `ConsumingRestServiceApplicationTests`，內容是空的 `contextLoads()`。框架為 JUnit 5 + Spring Boot Test。測試類命名 `*Tests`。

⚠️ 要注意 `contextLoads()` 也會嘗試建立 Feign client bean；若日後加了啟動期就連線的邏輯，測試會依賴 MyWeb 有沒有啟動。

## Boot 4 / Java 25 注意事項

- **`spring-boot-starter-web` 已 deprecated** → 使用 `spring-boot-starter-webmvc`
- **`RestTemplateBuilder` 在 Boot 4 被搬家**：`org.springframework.boot.web.client` → **`org.springframework.boot.restclient`**，所以必須明確引入 `spring-boot-starter-restclient`
- **Java 23+ 停用預設的 classpath 隱式 annotation processing** → `pom.xml` 已顯式宣告 Lombok 的 `<annotationProcessorPaths>`，**勿移除**，否則 `@Data`、`@Slf4j` 不生效
- **Spring Cloud 版本鏈**：`spring-cloud.version` = `2025.1.2`（Oakwood）對應 Boot 4.1，由 `spring-cloud-dependencies` BOM 匯入
