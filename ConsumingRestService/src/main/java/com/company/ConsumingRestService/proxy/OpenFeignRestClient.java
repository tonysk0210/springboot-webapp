package com.company.ConsumingRestService.proxy;

import com.company.ConsumingRestService.config.ProjectConfiguration;
import com.company.ConsumingRestService.dto.Contact;
import feign.Headers;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 定義呼叫 REST API 的抽象方法；實際實作由 Feign 在執行期間產生。
 * 1. 定義 @FeignClient 介面
 * 2. 設定 ProjectConfiguration
 * 3. 注入 OpenFeignRestClient 以呼叫 REST API
 */
@FeignClient(name = "contact-service", url = "http://localhost:8081/api/contact", configuration = ProjectConfiguration.class)
public interface OpenFeignRestClient {

    @GetMapping(value = "/getContactMessageByStatus")
    @Headers(value = "Content-Type: application/json") // 指定 Content-Type 為 application/json，代表回傳的資料格式為 JSON
    public List<Contact> getContactMessageByStatusAPI(@RequestParam String status);
}
