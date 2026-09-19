package com.company.myweb.config.security;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 主設定 — 定義 filter chain、路徑授權規則、登入方式、密碼編碼器
 * 認證邏輯（帳密比對）在同 package 的 UsernamePwdAuthenticationProvider
 */
@Configuration
public class SpringSecurityConfig {

    /**
     * SecurityFilterChain：HTTP request 進入時經過的一連串 Security filter
     * 這個 bean 定義了整個 app 的授權規則與登入行為
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    AuthenticationProvider myCustomProvider) throws Exception {

        // CSRF 保護：預設對所有 request 開啟，以下路徑豁免（因為非瀏覽器表單提交）
        //   - H2 console：內部用非標準表單，CSRF 會擋
        //   - REST API / Spring Data REST / Actuator：這些吃 JSON、由程式呼叫，無需 CSRF token
        http.csrf(csrf -> csrf
                .ignoringRequestMatchers(PathRequest.toH2Console())
                .ignoringRequestMatchers("/api/**")
                .ignoringRequestMatchers("/spring-data-api/**")
                .ignoringRequestMatchers("/myWeb/actuator/**"));

        // 路徑授權規則（由上而下比對，先命中先套用）
        //   authenticated() = 只要登入就過；hasRole("X") = 需要對應角色（自動加 ROLE_ 前綴）
        //   anyRequest().permitAll() = 未列出的路徑一律放行（含首頁、H2 console、靜態資源）
        http.authorizeHttpRequests(request -> request
                .requestMatchers("/dashboard").authenticated()
                .requestMatchers("/profilePage").authenticated()
                .requestMatchers("/updateProfile").authenticated()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasRole("ADMIN")
                .requestMatchers("/spring-data-api/**").hasRole("ADMIN")
                .requestMatchers("/myWeb/actuator/**").hasRole("ADMIN")
                .anyRequest().permitAll());

        // 啟用 Spring Security 表單登入功能；formLogin() 會自動加入 UsernamePasswordAuthenticationFilter，使用預設 POST /login 接收登入表單。
        // 預設欄位名稱是 username、password；本專案的 username 實際填入使用者 Email。
        // 登入驗證成功後，Spring Security 會把 Authentication 放入 SecurityContext，
        // 並保存到 HTTP Session，讓後續 request 能辨識目前登入者。
        // GET /login 顯示登入頁；成功 → /dashboard；失敗 → /login?error=true
        http.formLogin(loginConfig -> loginConfig
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
                .failureUrl("/login?error=true"));

        // 註冊自訂 provider（用 email 查 person 表 + BCrypt 比對密碼）
        http.authenticationProvider(myCustomProvider);

        // 登出流程改在 LoginController 內手動處理（走 GET 繞過預設 CSRF）
        // http.logout(...) 因此不在此設定

        // 允許 AdminActuator 與其他 REST Client 以 Basic Auth 呼叫需要 ROLE_ADMIN 的 Actuator 與 REST API。
        http.httpBasic(Customizer.withDefaults());

        // 關掉 X-Frame-Options → 允許 H2 console 用 <iframe> 顯示子視窗
        // 生產環境不該關（會有 clickjacking 風險），這裡純為 dev 便利
        http.headers(headersConfigurer -> headersConfigurer
                .frameOptions(frameOptionsConfig -> frameOptionsConfig.disable()));

        return http.build();
    }

    /**
     * BCrypt 密碼編碼器：註冊 + 登入時比對密碼都用這個 bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /* 保留範例：改用 InMemoryUserDetailsManager 記憶體帳號管理（不查 DB）
       目前用 UsernamePwdAuthenticationProvider 走 DB 認證，此段停用
    @Bean
    public InMemoryUserDetailsManager userDetailsManager() {
        UserDetails admin = User.withDefaultPasswordEncoder()
                .username("admin").password("123").roles("ADMIN").build();
        UserDetails student = User.withDefaultPasswordEncoder()
                .username("student").password("123").roles("STUDENT").build();
        return new InMemoryUserDetailsManager(admin, student);
    }*/
}
