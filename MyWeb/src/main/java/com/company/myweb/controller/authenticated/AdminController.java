package com.company.myweb.controller.authenticated;

import com.company.myweb.model.Contact;
import com.company.myweb.model.Course;
import com.company.myweb.model.Plan;
import com.company.myweb.model.Person;
import com.company.myweb.repository.CourseRepository;
import com.company.myweb.repository.PlanRepository;
import com.company.myweb.repository.PersonRepository;
import com.company.myweb.service.ContactService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static com.company.myweb.constant.ProjectConstant.ANSI_GREEN;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final ContactService contactService;
    private final PlanRepository planRepository;
    private final PersonRepository personRepository;
    private final CourseRepository coursesRepository;

    // 聯絡訊息（舊版無分頁的寫法保留參考）
//    @GetMapping("/contactMessage")
//    public String displayMessage(Model model) {
//        List<Contact> contacts = contactService.findContactWithOpenStatus();
//        model.addAttribute("contactsWithOpenStatus", contacts);
//        return "contactMessage";
//    }

    /*************************************************** 聯絡訊息（Contact Message）相關 handler ***************************************************/

    /**
     * 分頁顯示 OPEN 狀態的聯絡訊息
     * 依 path variable currentPageNum + query param sortField / sortDir 建立 Page<Contact>
     * 分頁功能每次翻頁 / 排序都會回到這個 handler
     */
    @GetMapping("/viewContactMessage/page/{currentPageNum}")
    public String viewContactMessage(Model model,
                                     @PathVariable("currentPageNum") int currentPageNum,
                                     @RequestParam("sortField") String sortField,
                                     @RequestParam("sortDir") String sortDir) {
        // 1) 建立 Page<Contact>：由 service 依 page index、每頁筆數、排序條件產生
        Page<Contact> pageOfContacts = contactService.findContactWithOpenStatus(currentPageNum, sortField, sortDir);

        // 2) 取出當頁的資料 list 送給模板顯示
        List<Contact> listOfContactsPerPage = pageOfContacts.getContent();
        model.addAttribute("listOfContactsPerPage", listOfContactsPerPage);

        // 3) 傳分頁 / 排序相關的資訊給模板，用來渲染分頁列與排序圖示
        model.addAttribute("currentPageNum", currentPageNum);
        model.addAttribute("totalPage", pageOfContacts.getTotalPages());
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reversedSortDir", sortDir.equals("asc") ? "desc" : "asc");

        return "authenticated/adminOnly/contactMessage";
    }

    /**
     * 把指定 contactId 的訊息狀態由 OPEN 改為 CLOSED
     * 處理完後帶著原本的頁碼 / 排序條件導回原頁
     */
    @GetMapping("/closeMessage")
    public String closeMessage(@RequestParam int contactId,
                               @RequestParam("currentPageNum") int currentPageNum,
                               @RequestParam("sortField") String sortField,
                               @RequestParam("sortDir") String sortDir, Authentication authentication) {
        // 1) 把訊息狀態改為 CLOSED（走 @Modifying JPQL，需手動塞 updatedBy）
        boolean statusChangedToClosed = contactService.updateContactStatus(contactId, authentication);
        // 留在原本的分頁與排序狀態
        return "redirect:/admin/viewContactMessage/page/" + currentPageNum
                + "?sortField=" + sortField
                + "&sortDir=" + sortDir;
    }

    /*************************************************** 方案（Plan）相關 handler ***************************************************/

    /**
     * @ModelAttribute 標記的方法會在此 controller 內「每個 handler 執行前」自動呼叫，
     * 回傳值會塞到所有 view 的 model — 這裡用來把最新的方案清單顯示在 planPage 上
     */
    @ModelAttribute("planList")
    public List<Plan> displayAllPlans() {
        return planRepository.findAll();
    }

    /**
     * 顯示新增方案頁面 — 送一個空 Plan 給前端表單綁定
     */
    @GetMapping("/planPage")
    public String planPage(Model model) {
        model.addAttribute("plan", new Plan());
        return "authenticated/adminOnly/planPage";
    }

    /**
     * 驗證後新增方案
     * Spring 自動把表單欄位綁到非原始型別參數（Plan），驗證失敗回原頁；成功則存檔並 redirect
     */
    @PostMapping("/addNewPlan")
    public String addNewPlan(@Valid Plan plan, BindingResult result) {
        if (result.hasErrors()) return "authenticated/adminOnly/planPage";
        planRepository.save(plan);
        return "redirect:/admin/planPage";
    }

    /**
     * 刪除方案
     * 必須先解除每個關聯 Person 的外鍵（把 person.plan 設 null）再刪，
     * 否則 DB 層會因外鍵約束違反而失敗
     */
    @GetMapping("/deletePlan")
    public String deletePlan(@RequestParam int planId) {
        // 1) 依 planId 取要刪的方案
        Optional<Plan> planToBeDeleted = planRepository.findById(planId);

        // 2) 解除每個關聯 Person 對此 Plan 的外鍵
        for (Person person : planToBeDeleted.get().getPersons()) {
            person.setPlan(null);
            personRepository.save(person); // 可省略 — Person 是 owning side，plan set null 後 save 即刷 DB
        }
        // 3) 刪除 Plan 並回 planPage
        planRepository.deleteById(planId);
        return "redirect:/admin/planPage";
    }

    /**
     * 顯示特定方案的詳情頁：
     *   1) 顯示已註冊此方案的使用者清單
     *   2) 提供依 email 增加/移除使用者的操作
     */
    @GetMapping("/viewPlanDetail")
    public String viewPlanDetail(@RequestParam int planId, Model model, HttpSession session) {
        // 1) 依 planId 取方案
        Optional<Plan> thePlanOptional = planRepository.findById(planId);

        // 2) 送方案物件給模板，顯示已註冊的使用者
        model.addAttribute("thePlan", thePlanOptional.get());

        // 3) 送一個空 Person 給模板表單，用來輸入要新增的使用者 email
        model.addAttribute("person", new Person());

        // 4) 把方案存進 session，讓 /addStudent 知道要加到哪個方案
        session.setAttribute("thePlanInSession", thePlanOptional.get());

        return "authenticated/adminOnly/viewPlanDetail";
    }

    /**
     * 依 email 新增使用者到當前方案（若 email 不存在則導回並顯示錯誤）
     */
    @PostMapping("/addStudent")
    public String addStudent(Person person, HttpSession session) {

        // 1) 依 admin 輸入的 email 查 Person
        Person thePerson = personRepository.readByEmail(person.getEmail()); // 找不到回 null

        // 2) 從 session 取當前方案
        Plan thePlanInSession = (Plan) session.getAttribute("thePlanInSession");

        if (thePerson == null)
            return "redirect:/admin/viewPlanDetail?planId=" + thePlanInSession.getPlanId() + "&error=personNotFound";

        // 3) 從 owning side（Person.plan）設外鍵
        thePerson.setPlan(thePlanInSession);

        // 這行是為了保持記憶體內 Plan 物件的一致性（Hibernate 不強制）
        thePlanInSession.getPersons().add(thePerson);

        // 4) 存 Person → owning side 的 save 才會真正寫 DB
        Person updatedPerson = personRepository.save(thePerson);
        log.info(ANSI_GREEN + Thread.currentThread().getStackTrace()[1].getMethodName() + " updatedPerson: " + updatedPerson);
        log.info(ANSI_GREEN + Thread.currentThread().getStackTrace()[1].getMethodName() + " thePlanInSession: " + thePlanInSession + " | getPersons(): " + thePlanInSession.getPersons());

        // 5) 導回詳情頁顯示最新的使用者清單
        return "redirect:/admin/viewPlanDetail?planId=" + thePlanInSession.getPlanId();
    }

    /**
     * 從方案內移除使用者（把 person.plan 設 null）
     */
    @GetMapping("/removeStudent")
    public String removeStudent(@RequestParam int personId) {

        // 1) 依 personId 取要移除的使用者
        Optional<Person> thePersonToBeRemovedOptional = personRepository.findById(personId);
        Person thePersonToBeRemoved = thePersonToBeRemovedOptional.get();

        // 2) 取該使用者對應的方案
        Plan thisPlanToBeRemovedFrom = thePersonToBeRemoved.getPlan();

        // 3) 解除關聯：把 person.plan 設 null
        thePersonToBeRemoved.setPlan(null);

        // 保險：從 Plan.persons 集合也同步移除（Hibernate 可能會自己處理，但不保證）
        thisPlanToBeRemovedFrom.getPersons().remove(thePersonToBeRemoved);

        // 4) 走 owning side（Person）save 更新 DB
        personRepository.save(thePersonToBeRemoved);
        log.info(ANSI_GREEN + Thread.currentThread().getStackTrace()[1].getMethodName() + " thePersonToBeRemoved: " + thePersonToBeRemoved);
        log.info(ANSI_GREEN + Thread.currentThread().getStackTrace()[1].getMethodName() + " thisPlanToBeRemovedFrom: " + thisPlanToBeRemovedFrom + " | getPersons(): " + thisPlanToBeRemovedFrom.getPersons());

        // 5) 導回詳情頁顯示最新的使用者清單
        return "redirect:/admin/viewPlanDetail?planId=" + thisPlanToBeRemovedFrom.getPlanId();
    }

    /*************************************************** 課程（Course）相關 handler ***************************************************/

    /**
     * @ModelAttribute：每個此 controller 內的 handler 執行前都會呼叫，回傳的清單塞進所有 view 的 model
     * 這裡取所有 Course（依 courseId 升冪）供模板顯示
     */
    @ModelAttribute("courseList")
    public List<Course> displayAllCourses() {
        return coursesRepository.findAll(Sort.by(Sort.Direction.ASC, "courseId")); // JPA 動態排序
    }

    /**
     * 顯示新增課程頁面 — 送一個空 Course 給前端表單綁定
     */
    @GetMapping("/coursePage")
    public String coursePage(Model model) {
        model.addAttribute("course", new Course());
        return "authenticated/adminOnly/coursePage";
    }

    /**
     * 驗證後新增課程 — 驗證失敗回原頁；成功則存檔並 redirect
     */
    @PostMapping("/addNewCourse")
    public String addNewCourse(@Valid @ModelAttribute("course") Course course, BindingResult result) {
        if (result.hasErrors()) return "authenticated/adminOnly/coursePage";
        coursesRepository.save(course);
        return "redirect:/admin/coursePage";
    }

    /**
     * 刪除課程 — 必須先移除每個已選修此課程的 Person 對它的關聯（走 owning side Person.courses）
     */
    @GetMapping("/deleteCourse")
    public String deleteCourse(@RequestParam int courseId) {
        // 1) 依 courseId 取要刪的課程
        Optional<Course> courseToBeDeletedOptional = coursesRepository.findById(courseId);
        Course courseToBeDeleted = courseToBeDeletedOptional.get();
        // 2) 從每個已選修者的 courses 集合移除此課程（owning side）
        for (Person person : courseToBeDeleted.getPersons()) {
            person.getCourses().remove(courseToBeDeleted);
            personRepository.save(person); // 可省略 — owning side save 就會寫中介表
        }
        // 3) 移除關聯完成後才刪除課程
        coursesRepository.deleteById(courseId);
        return "redirect:/admin/coursePage";
    }

    /**
     * 顯示課程詳情頁：
     *   - 送課程物件給模板顯示已選修的學生
     *   - 送空 Person 給模板表單，用來輸入要新增選修的學生 email
     *   - 課程物件存 session，讓後續 add/remove handler 知道操作對象
     */
    @GetMapping("/viewCourseDetail")
    public String viewCourseDetail(@RequestParam int courseId, Model model, HttpSession session) {
        // 1) 依 courseId 取課程
        Optional<Course> courseDetailOptional = coursesRepository.findById(courseId);
        Course courseDetail = courseDetailOptional.get();
        // 2) 送 course 與空 person 給模板
        model.addAttribute("course", courseDetail);
        model.addAttribute("person", new Person());
        // 3) 存 course 進 session 供 add/remove handler 使用
        session.setAttribute("courseInSession", courseDetail);
        return "authenticated/adminOnly/viewCourseDetail";
    }

    /**
     * 依 email 把使用者加入當前課程；若 email 不存在或該 person 已選修此課，回原頁並顯示錯誤
     */
    @PostMapping("/addCourseStudent")
    public String addCourseStudent(Person person, HttpSession session) {
        // 1) 從 session 取當前課程
        Course courseInSession = (Course) session.getAttribute("courseInSession");
        // 2) 依 admin 輸入的 email 查 Person
        Person thePerson = personRepository.readByEmail(person.getEmail());
        // 3) 找不到 → 回原頁顯示錯誤
        if (thePerson == null)
            return "redirect:/admin/viewCourseDetail?courseId=" + courseInSession.getCourseId() + "&error=personNotFound";
        // 4) 若該 person 已選修此課 → 回原頁顯示錯誤
        if (courseInSession.getPersons().contains(thePerson)) {
            return "redirect:/admin/viewCourseDetail?courseId=" + courseInSession.getCourseId() + "&error=alreadyEnrolled";
        }
        // 5) 從 owning side（Person.courses）建立關聯並存檔（會寫 person_courses 中介表）
        thePerson.getCourses().add(courseInSession);
        courseInSession.getPersons().add(thePerson); // 保險同步；owning side save 才是真正寫 DB
        personRepository.save(thePerson);
        return "redirect:/admin/viewCourseDetail?courseId=" + courseInSession.getCourseId();
    }

    /**
     * 從課程移除指定學生 — 從 person.courses 拿掉此課程，走 owning side 存檔
     */
    @GetMapping("/deleteCourseStudent")
    public String deleteCourseStudent(@RequestParam int personId, HttpSession session) {
        // 1) 依 personId 取要移除的 Person
        Optional<Person> personTobeRemovedOptional = personRepository.findById(personId);
        Person personTobeRemoved = personTobeRemovedOptional.get();
        // 2) 從 session 取當前課程
        Course courseInSession = (Course) session.getAttribute("courseInSession");
        // 3) 從 person.courses 拿掉此課程，走 owning side save
        personTobeRemoved.getCourses().remove(courseInSession);
        personRepository.save(personTobeRemoved);
        return "redirect:/admin/viewCourseDetail?courseId=" + courseInSession.getCourseId();
    }

}
