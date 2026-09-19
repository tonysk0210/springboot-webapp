package com.company.myweb.service;

import com.company.myweb.config.MyWebProperties;
import com.company.myweb.constant.ProjectConstant;
import com.company.myweb.model.Contact;
import com.company.myweb.repository.ContactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import static com.company.myweb.constant.ProjectConstant.ANSI_GREEN;
import static com.company.myweb.constant.ProjectConstant.ANSI_RESET;

@Slf4j
@Service
public class ContactService {
    private final ContactRepository contactRepository;
    private final MyWebProperties myWebProperties;

    @Autowired
    public ContactService(ContactRepository contactRepository, MyWebProperties myWebProperties) {
        this.contactRepository = contactRepository;
        this.myWebProperties = myWebProperties;
    }

    public Contact saveContact(Contact contact) {
        // 表單只帶 name、mobile、email、subject、message；contactId、稽核欄位（createdAt、createdBy、updatedAt、updatedBy）與 status 之後才設
        log.info(ANSI_GREEN + "從表單接收的 Contact：" + contact.toString() + ANSI_RESET);

        contact.setStatus(ProjectConstant.STATUS_OPEN);        // 1) 預設狀態設為 OPEN
        Contact savedContact = contactRepository.save(contact); // 2) 存 DB（JPA save 幾乎不會回 null，INSERT/UPDATE 皆然）

        // 存完後 contactId、createdAt、createdBy、updatedBy 皆已由 JPA + AuditorAware 自動填入
        log.info(ANSI_GREEN + "存檔後的 contact：" + contact + ANSI_RESET);
        log.info(ANSI_GREEN + "存檔後的 savedContact：" + savedContact + ANSI_RESET);
        log.info(ANSI_GREEN + "contact 與 savedContact 是同一物件嗎？" + (contact == savedContact) + ANSI_RESET); // true — JPA 的 save() 會回傳同一物件

        return savedContact;
    }

    /* ─── 分頁相關 ─── */

    /**
     * 依 currentPageNum、sortField、sortDir 建立 Pageable，然後查 OPEN 狀態的 Contact 回 Page<Contact>
     */
    public Page<Contact> findContactWithOpenStatus(int currentPageNum, String sortField, String sortDir) {
        int sizePerPage = myWebProperties.getPaginationPageSize();
        // 1) 建立 Pageable
        Pageable pageable = PageRequest.of(
                currentPageNum - 1,                                                     // 1. 頁碼由 0 開始，扣掉使用者輸入的 1
                sizePerPage,                                                                        // 2. 每頁筆數
                sortDir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,                   // 3. 排序方向
                sortField                                                                // 4. 排序欄位
        );
        // 2) 執行 JPQL @Query 查詢並回 Page
        return contactRepository.findByStatusWithPageableAtQuery(ProjectConstant.STATUS_OPEN, pageable);
    }

    /**
     * 把 Contact 狀態從 OPEN 改為 CLOSED
     * 因為走 @Modifying @Query（JPQL bulk update）→ 繞過 JPA lifecycle → AuditorAware 不會自動填 updatedBy
     * 所以必須手動把 authentication.getName() 傳進去，讓 SQL 內明寫更新
     */
    public boolean updateContactStatus(int id, Authentication authentication) {
        return contactRepository.updateStatusById(ProjectConstant.STATUS_CLOSED, authentication.getName(), id) > 0;
    }
}
