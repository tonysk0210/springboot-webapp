package com.company.ConsumingRestService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** RestTemplate 使用的回應資料物件。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response {
    private String statusCode;
    private String statusMessage;
}
