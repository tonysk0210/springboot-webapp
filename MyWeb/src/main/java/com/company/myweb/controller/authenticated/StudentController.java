package com.company.myweb.controller.authenticated;

import com.company.myweb.model.Course;
import com.company.myweb.model.Person;
import com.company.myweb.repository.CourseRepository;
import com.company.myweb.repository.PersonRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final CourseRepository coursesRepository;
    private final PersonRepository personRepository;

    @Autowired
    public StudentController(CourseRepository coursesRepository, PersonRepository personRepository) {
        this.coursesRepository = coursesRepository;
        this.personRepository = personRepository;
    }

    /**
     * 顯示登入使用者已選修的所有課程 — 從 session 取 loggedInPerson 塞給模板
     */
    @GetMapping("/viewEnrolledCourses")
    public String viewEnrolledCourses(HttpSession session, Model model) {
        Person person = (Person) session.getAttribute("loggedInPerson");
        model.addAttribute("person", person);
        return "authenticated/studentOnly/viewEnrolledCourses";
    }

    /**
     * 顯示所有可選課程 + 標記已選修的（讓 checkbox 停用）
     * 送 courseList 給模板做全部清單；送 alreadyRegisteredCourses 給模板做 disable 判斷
     */
    @GetMapping("/signUpCourses")
    public String signUpCourses(Model model, HttpSession session) {
        List<Course> courseList = coursesRepository.findAll();
        model.addAttribute("courseList", courseList);
        Person person = (Person) session.getAttribute("loggedInPerson");
        model.addAttribute("alreadyRegisteredCourses", person.getCourses());
        return "/authenticated/studentOnly/signUpCourses";
    }

    /**
     * 接收使用者勾選的課程 ID 清單並加到其 courses set，最後更新 session
     * 沒選任何一門 → 回原頁並顯示錯誤
     */
    @PostMapping("/purchaseSelectedCourse")
    public String purchaseSelectedCourses(@RequestParam(value = "selectedCourses", required = false) List<Integer> listOfCourseIds, HttpSession session, RedirectAttributes redirectAttributes) {
        if (listOfCourseIds == null || listOfCourseIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "請至少選擇一門課程");
            return "redirect:/student/signUpCourses";
        }
        // 1) 從 session 取當前使用者
        Person person = (Person) session.getAttribute("loggedInPerson");

        // 2) 把選到的每門課程加到 person.courses（owning side of 多對多，負責寫 person_courses 中介表）
        for (Integer courseId : listOfCourseIds) {
            person.getCourses().add(coursesRepository.findById(courseId).get());
        }
        // 3) 儲存 person → JPA 依 owning side 更新中介表
        Person updatedPerson = personRepository.save(person);

        // 4) 更新 session 內的 loggedInPerson，保持資料一致
        session.setAttribute("loggedInPerson", updatedPerson);

        // 5) 帶 ?success 導回頁面顯示成功訊息
        return "redirect:/student/signUpCourses?success";
    }

}
