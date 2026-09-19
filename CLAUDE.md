# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案結構

本倉庫根目錄下有 **三個獨立** 的 Spring Boot Maven 專案並列存放 — **沒有** 父層聚合 POM。每個專案都有自己的 `pom.xml` 與 Maven Wrapper（`mvnw` / `mvnw.cmd`），必須進入各自的目錄才能建置或執行。

| 模組 | Port | 用途 |
|------|------|------|
| `MyWeb` | 8081 | 主要 Web 應用：Thymeleaf 前端 + REST API + Spring Data REST + Actuator client |
| `AdminActuator` | 8083 | Spring Boot Admin **伺服器**（Codecentric），用於監控 `MyWeb` |
| `ConsumingRestService` | 8082 | 獨立 client 端，透過 OpenFeign / RestTemplate / WebClient 呼叫 `MyWeb` 的 `/api/contact` |

三個模組皆綁定 Spring Boot **4.1.0** + Java **25**；`ConsumingRestService` 額外用 Spring Cloud **2025.1.2 (Oakwood)**。`MyWeb` 與 `AdminActuator` 使用 codecentric spring-boot-admin **4.1.2**。

## 常用指令

以下指令請在對應模組目錄下執行（`MyWeb/`、`AdminActuator/` 或 `ConsumingRestService/`）：

```powershell
# 啟動應用程式（開發模式 — 部分模組含 spring-boot-devtools 支援熱重載）
.\mvnw.cmd spring-boot:run

# 建置
.\mvnw.cmd clean package

# 執行模組內所有測試
.\mvnw.cmd test

# 執行單一測試類別
.\mvnw.cmd test "-Dtest=SomeTestClass"

# 執行單一測試方法
.\mvnw.cmd test "-Dtest=SomeTestClass#someMethod"

# 只更新執行中應用的 static 資源與 templates（免重啟；配合 devtools 效果更好）
.\mvnw.cmd resources:resources
```

本機執行整套服務的典型順序：預設 `MyWeb` 的 Boot Admin client 為 **關閉**，可直接啟動 `MyWeb`；要監控時再啟動 `AdminActuator` 並把 `spring.boot.admin.client.enabled` 改為 `true`。若要測試 Feign client 才啟動 `ConsumingRestService`。三個 JVM 各佔一個 port，請在不同終端機分別啟動。

測試框架：**JUnit 5 + Spring Boot Test + Spring Security Test**（`MyWeb` 已引入 dependencies）。測試類命名 `*Tests`；測試方法以行為描述（例：`registerRejectsDuplicateEmail()`）。目前 `src/test/java` 除了空的 `MyWebApplicationTests` 之外都尚無內容，是接下來要補上的區塊。

## MyWeb 架構（主要應用）

### 進入點與關鍵註解
根 package 為 **`com.company.myweb`**（全小寫）。`MyWebApplication.java` 只帶三個 class-level 註解，因為 Entity / Repository 都位於 `com.company.myweb.*` 預設 scan 範圍內：
- `@SpringBootApplication` — 由 Boot 的 AOP autoconfigure 自動啟用 `aspect/LoggerAspect`，**不需要** 再寫 `@EnableAspectJAutoProxy`。該 aspect 的兩個 advice **切點範圍刻意不同**：`@Around`（執行時間 log）只涵蓋 `controller..*`、`rest..*`、`service..*` 三層（具名 pointcut `businessLayer()`）；`@AfterThrowing`（例外 log）維持 `com.company.myweb..*` 全範圍。理由：actuator 走 Basic Auth 且無 session，AdminActuator 每次輪詢都會重新驗證一次，若 `@Around` 全攔就會把 `config.security` 與 `repository` 的呼叫一併刷出來（單次輪詢約 7 行、且 `Person` 為 EAGER + `@Data` toString 會印出 BCrypt hash）。例外是低頻事件，全範圍不會產生噪音。兩個切點都再 `&& notRestExceptionHandler()` 排除 `rest/GlobalExceptionRestController`（它繼承的 `ResponseEntityExceptionHandler.handleException(..)` 是 `public final`，CGLIB 無法 override → 只要任一 advice 匹配到就會噴 WARN，**兩邊都要排除才會消失**）。同理 Entity/Repository 都在預設 scan 範圍，**不需要** 顯式 `@EntityScan` / `@EnableJpaRepositories`；新增時放在 `model/` 與 `repository/` 底下即可
- `@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")` — `auditor/AuditAwareImpl` 從 `SecurityContextHolder` 取得目前使用者名稱作為 `@CreatedBy` / `@LastModifiedBy` 欄位值；若無驗證則退回為 `"anonymousUser"`（**未登入註冊** 時仍能寫入 `person.created_by` 的關鍵）
- `@EnableConfigurationProperties(MyWebProperties.class)` — 顯式登記 `myweb.*` 屬性 bean，取代在 `MyWebProperties` 上加 `@Component`

### 資料層
- 使用內嵌 **H2** 記憶體資料庫（`jdbc:h2:mem:mydb`），Console 位於 `http://localhost:8081/h2-console`。⚠️ **devtools 熱重載會讓資料歸零** —— 關閉舊 ApplicationContext 時 Spring 的 `inMemoryDatabaseShutdownExecutor` 會關掉內嵌 DB，重啟後 `schema.sql` + `data.sql` 重跑，AUTO_INCREMENT 也重置。開發時請預期「改 Java 程式碼 → 手動建立的測試資料消失」。註：連線字串原本帶 `DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`，實測在目前配置下無可觀察效果（HikariCP 預設 `minimumIdle = maximumPoolSize = 10`，連線數不會歸零）故已移除；日後補整合測試或把 `minimum-idle` 調成 0 時可能需要加回 `DB_CLOSE_DELAY=-1`
- **`src/main/resources/sql/schema.sql` 是 schema 的唯一真相** — `spring.jpa.hibernate.ddl-auto=validate`，Hibernate 只在啟動時 **驗證** JPA Entity 對應現有 schema（型別/欄位缺失就直接啟動失敗，**不會** 依 Entity 修改 DB）。新增/變更欄位時：改 `sql/schema.sql` DDL **並** 同步 `model/` 底下 Entity 標註，兩邊不一致啟動就會炸
- 初始資料（含兩個 BCrypt 加密的預設帳號：`admin@gmail.com` / `admin` 與 `student@gmail.com` / `student`）放在 `src/main/resources/sql/data.sql`；路徑透過 `spring.sql.init.{schema,data}-locations` 明確指定
- JPA Entity：`Person`、`Roles`、`Address`、`Plan`、`Courses`、`Contact`。`Person` 擁有到 `Roles`、`Address`、`Plan` 的外鍵，並透過 `person_courses` 中介表對 `Courses` 做多對多 — 全部為 `FetchType.EAGER`
- 稽核欄位來自 `model/BaseEntity`；`Person` **刻意覆蓋** `createdBy` 欄位，以避免未登入註冊時 insert 失敗
- `model/` 底下有 **兩個非 JPA 類別**，勿誤加 `@Entity`：
  - `News` — 純 POJO，透過 `NewsRepository` 用 **`JdbcTemplate` + `BeanPropertyRowMapper`** 讀取；`news` 表只存在於 `schema.sql`
  - `Profile` — 表單/傳輸用 DTO（更新個人資料流程使用），不入庫，帶自己的 Bean Validation 註解

### 安全性模型（`config/security/`）
- 自訂 `UsernamePwdAuthenticationProvider`：以 email 從 `person` 表查詢使用者，並用 `BCryptPasswordEncoder` 驗證密碼
- 表單登入：`/login` → 成功導向 `/dashboard`，失敗導向 `/login?error=true`。登出是 `LoginController` 內的 **GET** handler（繞過預設 CSRF），內部手動呼叫 `SecurityContextLogoutHandler`
- `SpringSecurityConfig` 內的路由規則：
  - `/student/**` → `ROLE_STUDENT`
  - `/admin/**`、`/api/**`、`/spring-data-api/**`、`/myWeb/actuator/**` → `ROLE_ADMIN`
  - `/dashboard`、`/profilePage`、`/updateProfile` → 任何已登入使用者
  - 其他一律 `permitAll`（包含 H2 console）
- 以下路徑 **關閉 CSRF**：H2 console、`/api/**`、`/spring-data-api/**`、actuator base path
- `hasRole("X")` 對應的權限字串為 `ROLE_X` — 前綴 `ROLE_` 由 provider 的 `getGrantedAuthorities` 加上

### Controller 與 Service 慣例
- 平行的兩套 controller 樹：
  - `controller/` — 使用 Thymeleaf 渲染的 `@Controller`。回傳字串會對應到 `src/main/resources/templates/<name>.html`（**不需** 寫 `.html` 副檔名）
  - `controller/authenticated/` — 同上，但用於登入後頁面（dashboard、profile、admin、student）
  - `rest/` — `@RestController`，路徑前綴 `/api/**`，同時支援 JSON 與 XML 回應（classpath 已包含 Jackson XML dataformat），透過 `Accept` header 進行內容協商
- 沒有邏輯、只回傳 view 的路由可註冊在 `config/MyWebConfig#addViewControllers`（例：`/about`）
- `service/` 承載「寫入 + 業務邏輯」：`PersonService.savePerson`（重複 email 檢查、密碼加密、角色指派、手動 `createdBy`）、`ContactService`（狀態流轉、分頁查詢）。**新增寫入或跨 repository 的邏輯請放 service**；controller 只在單純讀取時直接呼叫 repository
- 自訂 Bean Validation 放在 `myValidation/`：`@PasswordValidator`（單欄位）、`@FieldValueMatchValidator`（class 層級，用於 `Person` 檢查 `password`/`confirmPassword` 與 `email`/`confirmEmail` 是否一致）
- 例外處理：MVC 由 `config/GlobalExceptionHandler` 處理；REST 由 `rest/GlobalExceptionRestController` 處理

### MyWeb 對外提供的端點
- 網頁 UI：`/`、`/home`、`/about`、`/contact`、`/news`、`/login`；註冊在 **`/public/register`**（表單 POST 到 `/public/createUser`）— `PublicController` 有 class 層 `@RequestMapping("/public")`
- 自訂 REST：`/api/contact/*`（見 `ContactRestController`）
- Spring Data REST 自動端點：`/spring-data-api/**`（HAL Explorer 位於 `/spring-data-api/`）
- Actuator：`/myWeb/actuator/**`（所有端點皆開啟 — `management.endpoints.web.exposure.include=*`）
- Boot Admin client **預設關閉**（`spring.boot.admin.client.enabled=false`），避免 `AdminActuator` 未啟動時 log 洗版。要恢復監控：將該屬性改為 `true` 並先啟動 `AdminActuator`（8083）。註冊時使用 instance metadata 內的 `admin@gmail.com` / `admin` 做 Basic Auth 讓 Admin server 回呼健康檢查

### 自訂應用程式屬性
前綴為 `myweb.*`，於 `config/MyWebProperties` 以 `@Validated` 綁定。目前只有 `myweb.paginationPageSize`（範圍 5–10）。新增可調參數請集中在此處，不要用 `@Value` 到處散落。

### 前端 CSS / JS 分層
`templates/` 只 link `static/css/app.css`；`app.css` **以 `@import` 串接三個 layer**（順序即優先序）：

1. `foundation.css` — 設計 token（顏色、字級、間距、breakpoint 等）
2. `components.css` — 跨頁重用的 BEM 元件（navbar、卡片、表單元件⋯）
3. `pages.css` — 只針對單一頁面的樣式覆寫

新增樣式時按語意選層：新的 design token → `foundation.css`；跨頁 reusable 元件 → `components.css`；只有某頁在用 → `pages.css`。避免 inline style 與重複 selector。共用 JS 行為統一放 `static/js/app.js`。

### Log 顏色
`constant/ProjectConstant` 定義了 ANSI 顏色碼，供 `LoggerAspect` 與部分 controller 使用。`application.properties` 內的 console log pattern 使用了 Logback 顏色轉換器 — 終端機會有彩色輸出。

log 同時落地為檔案（`logging.file.name=MyWeb/logs/myweb.log`，已加入 `.gitignore`）。路徑是相對於**工作目錄 = repo 根**；`mvnw spring-boot:run` 預設會把工作目錄設成 `MyWeb/`，所以 `MyWeb/pom.xml` 的 `spring-boot-maven-plugin` 加了 `<workingDirectory>${project.basedir}/..</workingDirectory>` 讓兩種啟動方式一致 — **改動任一邊，log 就會跑到別的地方**。這個屬性是 `/myWeb/actuator/logfile` 端點的**註冊前提** — 沒設就沒有該端點，Spring Boot Admin 的 Logfile 頁籤也會是空的。`logging.pattern.file` 刻意**不帶** ANSI 顏色轉換器，否則顏色碼會寫進檔案變成亂碼；輪替由 `logging.logback.rollingpolicy.*` 控制（單檔 10MB／保留 7 天／總量 100MB）。

**不要加回 `spring.jpa.show-sql=true`** — 它直接 `System.out.println`，不經 Logback，所以無法用 log level 控制、不進 log 檔、Admin 的 Logfile 頁籤看不到，而且會被 actuator 輪詢洗版（每次驗證 3 筆查詢）。要看 SQL 請改用 `logging.level.org.hibernate.SQL=DEBUG`（參數值再加 `org.hibernate.orm.jdbc.bind=TRACE`），可在 Boot Admin 的 Loggers 頁籤線上開關、免重啟。

### Lombok 慣例
專案 pom 內含 **Lombok**（`optional=true`），全 codebase 已廣泛使用，請 **遵循既有寫法**，勿手寫 constructor / getter / setter / logger：
- `@Slf4j` 取代手寫 `LoggerFactory.getLogger(...)`
- `@RequiredArgsConstructor` 產生 final 欄位的 constructor injection（相容 Spring 4.3+ 單一 constructor 自動注入，`@Autowired` 可省）
- Entity / DTO 常用 `@Data` + `@NoArgsConstructor`；繼承 `BaseEntity` 時搭配 `@ToString(callSuper = true)`
- **Java 23+ 停用了預設的 classpath 隱式 annotation processing** — `MyWeb/pom.xml` 與 `ConsumingRestService/pom.xml` 內已顯式配置 `maven-compiler-plugin` 的 `<annotationProcessorPaths>` 宣告 Lombok；若你新加使用 Lombok 的第三個模組，記得複製這段設定，否則 `@Slf4j`、`@Data` 等不會生效

## ConsumingRestService 架構

- `@EnableFeignClients(basePackages = "com.company.ConsumingRestService.proxy")` 啟用 `proxy/OpenFeignRestClient` Feign 介面
- 目標網址**寫死在原始碼三處**（必須先啟動 MyWeb）：`proxy/OpenFeignRestClient.java` 的 `@FeignClient(url=...)`、`controller/ContactRestController` 內 RestTemplate 與 WebClient 各自的 `String uri` 區域變數。改網址要三處一起改；要外部化請抽成 `@ConfigurationProperties`。DTO 位於 `dto/`（`Contact`、`Response`），非 `model/`
- `config/ProjectConfiguration` 定義了三個平行的 HTTP client（Feign、`RestTemplate`、`WebClient`），全部預先設定好 Basic Auth `admin@gmail.com` / `admin` — 呼叫端可依需求選擇對應的 client
- pom 同時引入 `spring-boot-starter-webmvc` 與 `spring-boot-starter-webflux`（因為同時用 `RestTemplate` 阻塞式與 `WebClient` 反應式）；另外 `spring-boot-starter-restclient` 是 Boot 4 拆分後 `RestTemplateBuilder` 的所在模組

## Boot 4 / Java 25 建置注意事項

以下是升級到 Spring Boot 4.1 + Java 25 過程中踩過、非 obvious 的點：

- **`spring-boot-starter-web` 已 deprecated** — 三個模組都改用 **`spring-boot-starter-webmvc`**（舊名仍可 delegate，但新程式碼與升級請用新名字讓 MVC vs WebFlux 意圖明確）
- **Boot 4 拆分了 autoconfigure 模組**，多個常用類別被搬到 feature-specific 套件：
  - `@EntityScan`：`org.springframework.boot.autoconfigure.domain` → **`org.springframework.boot.persistence.autoconfigure`**（本專案 Entity 已在預設 scan 範圍所以未用到；日後若把 model 移出 root package 需要顯式匯入時要走新路徑）
  - `PathRequest`（servlet）：`org.springframework.boot.autoconfigure.security.servlet` → **`org.springframework.boot.security.autoconfigure.web.servlet`**（`SpringSecurityConfig` 使用）
  - `RestTemplateBuilder`：`org.springframework.boot.web.client` → **`org.springframework.boot.restclient`**（`ConsumingRestService` 需明確引入 `spring-boot-starter-restclient`）
  - **H2 Console** 也被拆為獨立模組 **`spring-boot-h2console`** — `MyWeb/pom.xml` 已引入，才能讓 `spring.h2.console.enabled=true` 生效
- **Hibernate groupId 變更**：`hibernate-micrometer` 的 groupId 是 **`org.hibernate.orm`**（Hibernate 6+ 命名）；版本交由 Spring Boot BOM 管理，勿硬編碼
- **Lombok annotation processor 必須顯式宣告**（見上「Lombok 慣例」段落最後一點）
- **Spring Boot Admin (codecentric) 版本鏈**：SBA 4.1.x 對應 Boot 4.1；client（`MyWeb`）與 server（`AdminActuator`）兩邊 `spring-boot-admin.version` 必須一致
