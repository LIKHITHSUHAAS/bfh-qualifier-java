package com.example.bfhqualifierjava;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
public class App implements CommandLineRunner {

    @Bean
    RestTemplate restTemplate() { return new RestTemplate(); }

    private final RestTemplate rt;
    public App(RestTemplate rt) { this.rt = rt; }

    @Value("${bfh.name}")  String name;
    @Value("${bfh.regNo}") String regNo;
    @Value("${bfh.email}") String email;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            // 1) Generate webhook + token
            String genUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("name", name);
            body.put("regNo", regNo);
            body.put("email", email);

            ResponseEntity<Map> resp =
                    rt.postForEntity(genUrl, new HttpEntity<>(body, h), Map.class);
            Map<?,?> res = resp.getBody();
            System.out.println("GenerateWebhook response: " + res);

            String accessToken = (String) Optional.ofNullable(res.get("accessToken")).orElse(res.get("token"));
            String webhook     = (String) Optional.ofNullable(res.get("webhook")).orElse(res.get("webhookUrl"));

            // 2) Final SQL query
            String finalQuery =
                    "SELECT e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME, " +
                    "COUNT(e2.EMP_ID) AS YOUNGER_EMPLOYEES_COUNT " +
                    "FROM EMPLOYEE e1 " +
                    "JOIN DEPARTMENT d ON e1.DEPARTMENT = d.DEPARTMENT_ID " +
                    "LEFT JOIN EMPLOYEE e2 " +
                    "ON e1.DEPARTMENT = e2.DEPARTMENT AND e2.DOB > e1.DOB " +
                    "GROUP BY e1.EMP_ID, e1.FIRST_NAME, e1.LAST_NAME, d.DEPARTMENT_NAME " +
                    "ORDER BY e1.EMP_ID DESC;";

            // 3) Submit answer
            HttpHeaders h2 = new HttpHeaders();
            h2.setContentType(MediaType.APPLICATION_JSON);
            h2.set("Authorization", accessToken);

            Map<String, String> ans = Map.of("finalQuery", finalQuery);

            String submitUrl = (webhook != null && !webhook.isBlank())
                    ? webhook
                    : "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA";

            ResponseEntity<String> submitResp =
                    rt.postForEntity(submitUrl, new HttpEntity<>(ans, h2), String.class);

            System.out.println("Submit response: " + submitResp.getStatusCode() +
                               " body=" + submitResp.getBody());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
