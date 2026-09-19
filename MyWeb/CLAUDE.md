# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 模組定位

`myweb` 是倉庫下三個獨立 Spring Boot 專案之一（其他兩個為 `AdminActuator`、`ConsumingRestService`，位於同層目錄）。三者 **沒有** 父層聚合 POM，各自建置與執行；MyWeb 的責任為：

- Thymeleaf 前端 + 表單登入
- 自訂 REST API（`/api/**`）與 Spring Data REST（`/spring-data-api/**`）
- Actuator client（向 `AdminActuator`（port 8083）註冊）

服務綁定 Spring Boot **4.1.0** + Java **25**，Port **8081**。

本機典型啟動順序：先啟動 `../AdminActuator`（8083）讓 MyWeb 能註冊上去，再啟動 MyWeb；若要測試 Feign client，才啟動 `../ConsumingRestService`（8082）。

## 常用指令

以下指令在 MyWeb 目錄下執行（此檔案所在目錄）：

```powershell
# 啟動應用程式（含 spring-boot-devtools 支援熱重載）
.\mvnw.cmd spring-boot:run

# 建置
.\mvnw.cmd clean package

# 執行全部測試
.\mvnw.cmd test

# 執行單一測試類別
.\mvnw.cmd test "-Dtest=SomeTestClass"

# 執行單一測試方法
.\mvnw.cmd test "-Dtest=SomeTestClass#someMethod"

# 只更新執行中應用的 static 資源與 templates（免重啟；配合 devtools 效果更好）
.\mvnw.cmd resources:resources
```

### 測試慣例
- 框架：JUnit 5 + Spring Boot Test + Spring Security Test
- 測試類命名 `*Tests`；測試方法以行為描述，例：`registerRejectsDuplicateEmail()`
- 修改 controller、security、validation、persistence 時應涵蓋成功與失敗路徑
- `src/test/java` 目錄目前為空（除了空的 `MyWebApplicationTests`），是接下來要補上的區塊

## 架構

### 進入點與關鍵註解
根 package 為 **`com.company.myweb`**（全小寫）。`MyWebApplication.java` 帶三個 class-level 註解：
- `@SpringBootApplication` — 由於 Entity 位於 `com.company.myweb.model`、Repository 位於 `com.company.myweb.repository`，均在預設 scan 範圍內，因此 **不需要** 顯式 `@EntityScan` / `@EnableJpaRepositories`。同理 AOP（`aspect/LoggerAspect`）由 Boot 的 AOP autoconfigure 啟用，**不需要** `@EnableAspectJAutoProxy`。該 aspect 兩個 advice 的切點範圍 **刻意不同**：`@Around`（執行時間 log）只涵蓋 `controller..*`、`rest..*`、`service..*`（具名 pointcut `businessLayer()`）；`@AfterThrowing`（例外 log）維持 `com.company.myweb..*` 全範圍。理由：actuator 走 Basic Auth 且無 session，AdminActuator 每次輪詢都重新驗證一次，`@Around` 全攔會把 `config.security` 與 `repository` 一併刷出來（單次輪詢約 7 行，且 `Person` 為 EAGER + `@Data` toString 會把 BCrypt hash 印進 log）；例外是低頻事件，全範圍不產生噪音。兩個切點都再 `&& notRestExceptionHandler()` 排除 `rest/GlobalExceptionRestController`（它繼承的 `ResponseEntityExceptionHandler.handleException(..)` 是 `public final`，CGLIB 無法 override → 只要任一 advice 匹配到就會噴 WARN，**兩邊都要排除才會消失**；排掉後該 bean 不再被 CGLIB 代理，`@RestControllerAdvice` 仍正常運作）。**新增 package 若也想被計時，要記得加進 `businessLayer()`**
- `@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")` — `auditor/AuditAwareImpl` 從 `SecurityContextHolder` 取得目前使用者名稱作為 `@CreatedBy` / `@LastModifiedBy` 欄位值；若無驗證則退回為 `"anonymousUser"`（**未登入註冊** 時仍能寫入 `person.created_by` 的關鍵）
- `@EnableConfigurationProperties(MyWebProperties.class)` — 顯式登記 `myweb.*` 屬性 bean，取代在 `MyWebProperties` 上加 `@Component`

### 資料層
- 使用內嵌 **H2** 記憶體資料庫（`jdbc:h2:mem:mydb`），Console 位於 `http://localhost:8081/h2-console`。⚠️ **devtools 熱重載會讓資料歸零** —— 關閉舊 ApplicationContext 時 Spring 的 `inMemoryDatabaseShutdownExecutor` 會關掉內嵌 DB，重啟後 `schema.sql` + `data.sql` 重跑，AUTO_INCREMENT 也重置。開發時請預期「改 Java 程式碼 → 手動建立的測試資料消失」。註：連線字串原本帶 `DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`，實測在目前配置下無可觀察效果（HikariCP 預設 `minimumIdle = maximumPoolSize = 10`，連線數不會歸零）故已移除；日後補整合測試或把 `minimum-idle` 調成 0 時可能需要加回 `DB_CLOSE_DELAY=-1`
- **`schema.sql` 是 schema 的唯一真相** — `spring.jpa.hibernate.ddl-auto=validate`，Hibernate 只在啟動時對照 `src/main/resources/sql/schema.sql` **驗證** JPA Entity（型別、欄位缺失就啟動失敗），**不會** 依 Entity 修改 DB。新增/變更欄位時：改 `schema.sql` DDL **並** 同步 `model/` 底下 Entity 標註，兩邊不一致啟動就會炸
- 初始資料（含兩個 BCrypt 加密的預設帳號：`admin@gmail.com` / `admin` 與 `student@gmail.com` / `student`）放在 `src/main/resources/sql/data.sql`；路徑透過 `spring.sql.init.{schema,data}-locations` 明確指定
- JPA Entity：`Person`、`Role`、`Address`、`Plan`、`Course`、`Contact`（**單數**）。外鍵全部集中在 `person` 表上（`role_id` NOT NULL、`address_id` / `plan_id` 可為 NULL），並透過 `person_courses` 中介表（複合主鍵，DB 層擋重複選課）對 `Course` 做多對多。`Person` 的四個關聯**全部 `FetchType.EAGER`**，查一次 `Person` 會連帶發出 3~5 筆 SQL
- ⚠️ **唯一的 `LAZY` 是 `Plan.persons`**，而 `viewPlanDetail.html` 在**模板渲染階段**才存取它 → 這頁依賴 `spring.jpa.open-in-view=true`（目前未設定，走預設 true）。**不要為了消掉啟動 WARN 就把 open-in-view 關掉**，會讓該頁噴 `LazyInitializationException`；真要關必須先把 `AdminController.viewPlanDetail` 改成 fetch join / `@EntityGraph`
- ⚠️ `Person.courses` 與 `Course.persons` **雙向都是 EAGER**，載入一個 Person 會連鎖撈出「同課程的其他學生及其 address/plan/role」。資料量變大時先把 `Course.persons` 改 `LAZY` 切斷傳染鏈
- 稽核欄位來自 `model/BaseEntity`；`Person` **刻意覆蓋** `createdBy` 欄位，以避免未登入註冊時 insert 失敗
- ⚠️ `spring.jpa.properties.jakarta.persistence.validation.mode=none` **不可拿掉**：註冊流程先通過 MVC 驗證、再把密碼 BCrypt 加密，此時 `password` 已與 `confirmPassword` 不同；若讓 Hibernate 在存檔前再驗一次，`Person` 上的 `@FieldValueMatchValidator` 會誤判成「兩次密碼不一致」而讓註冊失敗。**前綴必須是 `jakarta.*`**（`javax.*` 是舊名，會發 HHH90000021 警告，將來被移除就會靜默失效 → 註冊壞掉）
- ⚠️ `@Modifying` 的 bulk UPDATE/DELETE（例：`ContactRepository.updateStatusById`）**不經過 Entity、不觸發 `AuditingEntityListener`** → 稽核欄位要手動寫進 JPQL，呼叫端也得自己把 `authentication.getName()` 傳進來
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

### 對外提供的端點
- 網頁 UI：`/`、`/home`、`/about`、`/contact`、`/news`、`/login`；註冊在 **`/public/register`**（表單 POST 到 `/public/createUser`）— `PublicController` 有 class 層 `@RequestMapping("/public")`
- 自訂 REST：`/api/contact/*`（見 `ContactRestController`）
- Spring Data REST 自動端點：`/spring-data-api/**`（HAL Explorer 位於 `/spring-data-api/`）
- Actuator：`/myWeb/actuator/**`（**13 個端點全開** — `management.endpoints.web.exposure.include=*`，生產環境應改白名單）。`config/MyWebActuatorInfoContributor` 實作 `InfoContributor`，把自訂資料掛在 `/info` 的 `myWeb-info` key 下（同時顯示在 Boot Admin 的「資訊」卡片）。`/myWeb/actuator/loggers/{name}` 可**線上調 log level 免重啟**，是查 SQL（`org.hibernate.SQL`）與追認證流程（`com.company.myweb.config.security`）的主要手段；`/myWeb/actuator/logfile` 則靠 `logging.file.name` 才會註冊
- Boot Admin client **目前為開啟**（`spring.boot.admin.client.enabled=true`）→ 啟動 MyWeb 前請先起 `../AdminActuator`（8083），否則 console 會刷連線失敗。註冊時使用 instance metadata 內的 `admin@gmail.com` / `admin` 做 Basic Auth 讓 Admin server 回呼。實測輪詢頻率：`/health` 約每 20 秒、`/info` 約每 60 秒；Admin UI 開著時還會多抓 `/metrics/**`（約每分鐘一波 20+ 個請求）。**actuator 走 Basic Auth 且無 session，每個請求都會完整跑一次 `UsernamePwdAuthenticationProvider`（含 BCrypt 與 3 筆 SQL）** — 這是 log 容易被洗版的根源

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

log 同時落地為檔案（`logging.file.name=MyWeb/logs/myweb.log`，已加入 `.gitignore`）。路徑是相對於**工作目錄 = repo 根**；`mvnw spring-boot:run` 預設工作目錄是 `MyWeb/`，所以 `pom.xml` 的 `spring-boot-maven-plugin` 加了 `<workingDirectory>${project.basedir}/..</workingDirectory>` 對齊 — **改動任一邊，log 就會跑到別的地方**。這個屬性是 `/myWeb/actuator/logfile` 端點的**註冊前提** — 沒設就沒有該端點，Spring Boot Admin 的 Logfile 頁籤也會是空的。`logging.pattern.file` 刻意**不帶** `%green()` 等 ANSI 轉換器，否則顏色碼會寫進檔案變成亂碼；輪替由 `logging.logback.rollingpolicy.*` 控制（單檔 10MB／保留 7 天／總量 100MB）。

**不要加回 `spring.jpa.show-sql=true`** — 它直接 `System.out.println`，不經 Logback，所以無法用 log level 控制、不進 log 檔、Admin 的 Logfile 頁籤看不到，而且會被 actuator 輪詢洗版（每次驗證帶 3 筆查詢）。要看 SQL 請改用 `logging.level.org.hibernate.SQL=DEBUG`（想看 `?` 的實際參數值再加 `logging.level.org.hibernate.orm.jdbc.bind=TRACE`），可在 Boot Admin 的 Loggers 頁籤線上開關、免重啟。

### Lombok 慣例
專案 pom 內含 **Lombok**（`optional=true`），全 codebase 已廣泛使用，請 **遵循既有寫法**，勿手寫 constructor / getter / setter / logger：
- `@Slf4j` 取代手寫 `LoggerFactory.getLogger(...)`
- `@RequiredArgsConstructor` 產生 final 欄位的 constructor injection（相容 Spring 4.3+ 單一 constructor 自動注入，`@Autowired` 可省）
- Entity / DTO 常用 `@Data` + `@NoArgsConstructor`；繼承 `BaseEntity` 時搭配 `@ToString(callSuper = true)`
- **Java 23+ 停用了預設的 classpath 隱式 annotation processing** — `pom.xml` 已顯式配置 `maven-compiler-plugin` 的 `<annotationProcessorPaths>` 宣告 Lombok；勿隨意移除，否則 `@Slf4j`、`@Data` 等不會生效

## Boot 4 / Java 25 建置注意事項

以下是升級到 Spring Boot 4.1 + Java 25 過程中踩過、非 obvious 的點：

- **`spring-boot-starter-web` 已 deprecated**，改用 **`spring-boot-starter-webmvc`**（舊名仍可 delegate，但新程式碼與升級請用新名字讓 MVC vs WebFlux 意圖明確）
- **Boot 4 拆分了 autoconfigure 模組**，多個常用類別被搬到 feature-specific 套件：
  - `@EntityScan`：舊 `org.springframework.boot.autoconfigure.domain` → 新 **`org.springframework.boot.persistence.autoconfigure`**（本專案 Entity 已在預設 scan 範圍所以未用到，但若日後把 model 移出 root package 需要顯式匯入時要走新路徑）
  - `PathRequest`（servlet）：舊 `org.springframework.boot.autoconfigure.security.servlet` → 新 **`org.springframework.boot.security.autoconfigure.web.servlet`**（`SpringSecurityConfig` 使用）
- **Hibernate groupId 變更**：`hibernate-micrometer` 的 groupId 是 **`org.hibernate.orm`**；版本交由 Spring Boot BOM 管理，勿硬編碼
- **Spring Boot Admin 版本鏈**：`spring-boot-admin.version` 屬性必須與 `AdminActuator/pom.xml` 內相同（client ↔ server）
