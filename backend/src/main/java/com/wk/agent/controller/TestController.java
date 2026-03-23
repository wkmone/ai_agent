package com.wk.agent.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/info")
    public String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 网络连接测试 ===\n\n");
        
        // 测试本机网络
        try {
            InetAddress local = InetAddress.getLocalHost();
            sb.append("本机host: ").append(local.getHostName()).append("\n");
            sb.append("本机IP: ").append(local.getHostAddress()).append("\n");
        } catch (UnknownHostException e) {
            sb.append("获取本机信息失败: ").append(e.getMessage()).append("\n");
        }
        
        // 测试 Ollama 连接
        sb.append("\n=== 测试连接 Ollama ===\n");
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:11434/api/tags";
        
        try {
            String response = restTemplate.getForObject(url, String.class);
            sb.append("连接成功!\n");
            sb.append("Response: ").append(response);
        } catch (Exception e) {
            sb.append("连接失败!\n");
            sb.append("Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                sb.append("Cause: ").append(e.getCause().getMessage()).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        RestTemplate restTemplate = new RestTemplate();
        
        String url = "http://localhost:11434/v1/chat/completions";
        
        String requestBody = """
            {
                "model": "qwen3",
                "messages": [
                    {"role": "user", "content": "%s"}
                ],
                "stream": false
            }
            """.formatted(message);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody, headers);
        
        try {
            String response = restTemplate.postForObject(url, entity, String.class);
            return response;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
