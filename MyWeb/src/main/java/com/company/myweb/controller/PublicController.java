package com.company.myweb.controller;

import com.company.myweb.exception.EmailAlreadyExistsException;
import com.company.myweb.model.Person;
import com.company.myweb.service.PersonService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/public")
public class PublicController {

    private final PersonService personService;

    @Autowired
    public PublicController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("person", new Person());
        return "register";
    }

    @PostMapping("/createUser")
    public String createUser(@Valid @ModelAttribute("person") Person person, BindingResult result) {
        if (result.hasErrors()) return "register"; // 1) Bean Validation 檢查失敗回原表單

        try {
            personService.savePerson(person); // 2) 嘗試存檔；PersonService 遇重複 email 會拋 EmailAlreadyExistsException
        } catch (EmailAlreadyExistsException ex) {
            // 3) 把 exception 訊息綁定到 "email" 欄位的錯誤上
            result.rejectValue(
                    "email",           // Person 上要標記錯誤的欄位名
                    null,                    // message code（若要 i18n 可在 messages.properties 定義）
                    ex.getMessage()          // fallback 訊息（從 exception 取）
            );
            // 4) 回註冊頁；person 與 BindingResult 已在 model 內，Thymeleaf 會顯示錯誤
            return "register";
        }

        return "redirect:/login?register=true";
    }
}
