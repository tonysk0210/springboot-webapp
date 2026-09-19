package com.company.ConsumingRestService.controller;

import com.company.ConsumingRestService.dto.Contact;
import com.company.ConsumingRestService.dto.Response;
import com.company.ConsumingRestService.proxy.OpenFeignRestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class ContactRestController {

    private final OpenFeignRestClient openFeignRestClient;
    private final RestTemplate restTemplate;
    private final WebClient webClient;

    @Autowired
    public ContactRestController(OpenFeignRestClient openFeignRestClient, RestTemplate restTemplate, WebClient webClient) {
        this.openFeignRestClient = openFeignRestClient;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
    }

    /**
     * 使用 FeignClient 取得指定狀態的聯絡訊息，例如 status=OPEN。
     */
    // 透過 FeignClient 呼叫
    @GetMapping("/getMessages")
    public List<Contact> getMessages(@RequestParam String status) {
        return openFeignRestClient.getContactMessageByStatusAPI(status);
    }

    /**
     * 透過 RestTemplate 儲存聯絡訊息。
     * 呼叫：POST http://localhost:8082/saveMessages
     * {
     * "name" : "RestTemplate",
     * "mobile" : "1234567890",
     * "email" : "resttemplate@gmail.com",
     * "subject" : "resttemplate",
     * "message" : "resttemplate message",
     * "status" : "OPEN"
     * }
     */
    // 透過 RestTemplate 呼叫
    @PostMapping("/saveMessages")
    public ResponseEntity<Response> saveMessages(@RequestBody Contact contact) {
        String uri = "http://localhost:8081/api/contact/saveContactMessage";
        // 1) 建立 HttpHeaders，並加入必要的 invocationFrom
        HttpHeaders headers = new HttpHeaders();
        headers.add("invocationFrom", "RestTemplate");
        // 2) 使用 HttpEntity 包裝請求內容與標頭
        HttpEntity<Contact> httpEntity = new HttpEntity<>(contact, headers);
        // 3) 執行 HTTP 呼叫
        ResponseEntity<Response> responseEntity = restTemplate.exchange(uri, HttpMethod.POST, httpEntity, Response.class);
        return responseEntity;
    }

    /**
     * 透過 WebClient 儲存聯絡訊息。
     * 呼叫：POST http://localhost:8082/saveMessagesWebClient
     * {
     * "name" : "WebClient",
     * "mobile" : "1234567890",
     * "email" : "webclient@gmail.com",
     * "subject" : "webclient",
     * "message" : "webclient message",
     * "status" : "OPEN"
     * }
     */
    // 透過 WebClient 呼叫
    @PostMapping("/saveMessagesWebClient")
    public Mono<ResponseEntity<Response>> saveMessagesWebClient(@RequestBody Contact contact) {
        String uri = "http://localhost:8081/api/contact/saveContactMessage";
        return webClient.post().uri(uri)
                .header("invocationFrom", "WebClient") // 1) 加入自訂標頭
                .body(Mono.just(contact), Contact.class) // 2) 設定請求內容
                .retrieve()
                // 3) 用 toEntity 而非 bodyToMono：連同上游的 status code 與 header 一起取回。
                //    bodyToMono 只拿 body，狀態碼會被丟掉 → 這一層會退回 Spring 預設的 200，
                //    與 /saveMessages（RestTemplate 版回傳 ResponseEntity）行為不一致。
                .toEntity(Response.class);
    }
}
