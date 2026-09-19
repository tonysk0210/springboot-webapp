package com.company.myweb.config.security;

import com.company.myweb.model.Person;
import com.company.myweb.model.Role;
import com.company.myweb.repository.PersonRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.company.myweb.constant.ProjectConstant.ANSI_GREEN;

/**
 * 使用者提交 POST /login 後的完整流程：
 *   1. Spring Security 透過 AuthenticationProvider 驗證使用者
 *   2. 產生 UsernamePasswordAuthenticationToken（含 username + roles/authorities）
 *   3. 存進 SecurityContext 與 session
 *   4. 導向設定的 success URL（見 SpringSecurityConfig.formLogin）
 *   5. 之後每個 request 都用這個 context 決定授權
 */
@Slf4j
@Component
public class UsernamePwdAuthenticationProvider implements AuthenticationProvider {

    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UsernamePwdAuthenticationProvider(PersonRepository personRepository, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Spring Security 可能同時註冊多個 AuthenticationProvider。表單登入流程：
     *   1. AuthenticationManager 嘗試驗證
     *   2. 依序呼叫每個 provider 的 supports()
     *   3. 傳入 authentication request 的 class（例如 UsernamePasswordAuthenticationToken.class）
     *   4. supports() 回 true → 呼叫該 provider 的 authenticate()
     *   5. supports() 用於「挑對的 provider」處理當下的 authentication 型別
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

    /**
     * 建立「已認證」的 UsernamePasswordAuthenticationToken 並回傳。
     * 已認證的 token 代表 Spring Security 認可這個使用者
     * credentials（密碼）驗證後不再需要，設 null 提高安全性
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        // 1) 從表單取出使用者輸入的 email 與密碼
        String enteredEmail = authentication.getName();
        String enteredPassword = authentication.getCredentials().toString();
        // 2) 依 email 查 Person（Person.roles 是 EAGER，一併載入）
        Person person = personRepository.readByEmail(enteredEmail);
        log.info(ANSI_GREEN + "Current method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " | Person: " + person);

        // 3) 認證邏輯：使用者存在 + BCrypt 比對輸入密碼與 DB 內加密密碼相符
        if (person != null && passwordEncoder.matches(enteredPassword, person.getPassword()))
            // 4) 建立已認證的 Authentication（principal + authorities，credentials 清 null）
            return new UsernamePasswordAuthenticationToken(
                    enteredEmail,
                    null,
                    getGrantedAuthorities(person.getRoles()));

        else throw new BadCredentialsException("帳號或密碼錯誤");
        // Spring Security 的 AuthenticationFailureHandler 接住此 exception → 導向 /login?error=true（見 SpringSecurityConfig）
    }

    /**
     * 把 Role 物件轉為 Spring Security 的 GrantedAuthority 清單
     * Spring Security 用 GrantedAuthority 判斷資源存取權限；可支援多角色
     */
    private List<GrantedAuthority> getGrantedAuthorities(Role roles) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        // .hasRole("ADMIN") 內部會檢查 List<GrantedAuthority> 是否包含 "ROLE_ADMIN"，故此處加 ROLE_ 前綴
        grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + roles.getRoleName()));
        return grantedAuthorities;
    }
}
