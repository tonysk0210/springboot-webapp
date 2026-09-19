package com.company.myweb.controller.authenticated;

import com.company.myweb.model.Person;
import com.company.myweb.repository.PersonRepository;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class DashboardController {

    private final PersonRepository personRepository;

    @Autowired
    public DashboardController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication, HttpSession session) {
        // 1) 從 Authentication 取當前使用者 email（即 authentication.getName()），查 DB 拿 Person
        Person person = personRepository.readByEmail(authentication.getName());

        // 2) 把使用者資訊塞進 model 給前端顯示
        model.addAttribute("username", person.getName());
        model.addAttribute("role", authentication.getAuthorities().toString());
        if (person.getPlan() != null) model.addAttribute("plan", person.getPlan().getName());
        // 注意：若 Person.plan 是 LAZY，transaction 結束後存取會拋 LazyInitializationException

        // 3) 把 Person 存進內嵌 Tomcat 管理的伺服器端 HttpSession，供 ProfilePageController 等後續 handler 使用
        session.setAttribute("loggedInPerson", person);

        return "authenticated/dashboard";
    }
}
