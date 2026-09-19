package com.company.myweb.service;

import com.company.myweb.constant.ProjectConstant;
import com.company.myweb.exception.EmailAlreadyExistsException;
import com.company.myweb.model.Person;
import com.company.myweb.model.Role;
import com.company.myweb.repository.PersonRepository;
import com.company.myweb.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.company.myweb.constant.ProjectConstant.ANSI_GREEN;
import static com.company.myweb.constant.ProjectConstant.ANSI_RESET;

@Slf4j
@Service
public class PersonService {
    private PersonRepository personRepository;
    private RoleRepository rolesRepository;
    private PasswordEncoder passwordEncoder;

    @Autowired
    public PersonService(PersonRepository personRepository, RoleRepository rolesRepository, PasswordEncoder passwordEncoder) {
        this.personRepository = personRepository;
        this.rolesRepository = rolesRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Person savePerson(Person person) {

        // 1) 檢查 email 是否已被使用；已存在則拋 EmailAlreadyExistsException（由 controller 攔並顯示錯誤訊息）
        if (personRepository.existsByEmail(person.getEmail())) {
            throw new EmailAlreadyExistsException("⚠\uFE0F 此 Email（" + person.getEmail() + "）已被其他帳號使用");
        }

        // 至此 name、mobile、email、password 都是使用者透過註冊表單填的原始值
        Role roles = rolesRepository.getByRoleName(ProjectConstant.STUDENT_ROLE);
        person.setRoles(roles);                                         // 2) 指派 STUDENT 角色
        person.setPassword(passwordEncoder.encode(person.getPassword())); // 3) 密碼 BCrypt 加密

        // 註冊當下使用者尚未登入 → SecurityContext 內沒 Authentication → AuditorAware 會回 "anonymousUser"
        // 但我們希望 createdBy 記錄成使用者自己的 email，故：
        //   - Person 上 shadow field 遮蔽父類 BaseEntity.createdBy（見 Person.java 註釋）
        //   - 這裡手動 setCreatedBy(email)，Hibernate 寫 DB 時拿子類 field 的值 → email 落地 DB
        person.setCreatedBy(person.getEmail()); // 4) 手動填 createdBy = 使用者 email

        log.info(ANSI_GREEN + "存檔前的 Person：" + person + ANSI_RESET);
        Person savedPerson = personRepository.save(person); // 5) 存檔（AuditorAware 自動填 createdAt、updatedAt；personId 由 IDENTITY 產生）
        log.info(ANSI_GREEN + "存檔後的 Person：" + savedPerson + ANSI_RESET);

        return savedPerson;
    }
}
