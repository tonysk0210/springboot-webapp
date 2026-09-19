package com.company.ConsumingRestService.dto;

import lombok.Data;

/** RestTemplate 使用的資料傳輸物件。 */
@Data
public class Contact {

    private int contactId;
    private String name;
    private String mobile;
    private String email;
    private String subject;
    private String message;
    private String status;

}
