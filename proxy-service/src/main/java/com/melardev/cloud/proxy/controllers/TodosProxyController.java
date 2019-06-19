package com.melardev.cloud.proxy.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping(value = "todos", produces = MediaType.APPLICATION_JSON_VALUE)
public class TodosProxyController {

    private final RestTemplate restTemplate;
    private final Random random;

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    ObjectMapper mapper;

    public TodosProxyController() {
        this.restTemplate = new RestTemplate();
        random = new Random();
    }

    public String getTodoBaseMicroServiceUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances("todo-service");
        if (instances != null && instances.size() > 0) {
            String url = instances.get(random.nextInt(instances.size())).getUri().toString();
            if (!url.endsWith("/"))
                url += "/";
            return url;
        }
        return null;
    }

    private static HttpEntity<String> getHeadersRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<String> getWriteRequestEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        return new HttpEntity<>(requestBody, headers);
    }

    private ResponseEntity<String> fetch(String url) {
        return fetch(url, getHeadersRequestEntity());
    }

    private ResponseEntity<String> fetch(String url, HttpEntity requestEntity) {
        if (url == null || url.startsWith("null"))
            return sendMicroServiceNotFoundResponse();
        return fetch(url, HttpMethod.GET, requestEntity);
    }

    private ResponseEntity<String> fetch(String url, HttpMethod httpMethod) {
        if (url == null || url.startsWith("null"))
            return sendMicroServiceNotFoundResponse();
        return fetch(url, httpMethod, getHeadersRequestEntity());
    }


    private ResponseEntity<String> fetch(String url, HttpMethod httpMethod, HttpEntity requestEntity) {
        if (url == null || url.startsWith("null"))
            return sendMicroServiceNotFoundResponse();

        try {
            ResponseEntity<String> response = restTemplate.exchange(url,
                    httpMethod, requestEntity, String.class);
            return response;
        } catch (RestClientException ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<String> index() {
        return fetch(getTodoBaseMicroServiceUrl() );
    }


    @GetMapping("/{id}")
    public ResponseEntity<String> get(@PathVariable("id") Long id) {
        return fetch(getTodoBaseMicroServiceUrl() + id);
    }

    @GetMapping("/pending")
    public ResponseEntity<String> getNotCompletedTodos() {
        return fetch(getTodoBaseMicroServiceUrl() + "pending");
    }

    @GetMapping("/completed")
    public ResponseEntity<String> getCompletedTodos() {
        return fetch(getTodoBaseMicroServiceUrl() + "completed");
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestBody String todo) {
        String url = getTodoBaseMicroServiceUrl() ;
        return fetch(url, HttpMethod.POST, getWriteRequestEntity(todo));
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable("id") Long id,
                                 @RequestBody String todo) {
        String url = getTodoBaseMicroServiceUrl() + "" + id;
        return fetch(url, HttpMethod.PUT, getWriteRequestEntity(todo));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable("id") Long id) {
        String url = getTodoBaseMicroServiceUrl()  + id;
        return fetch(url, HttpMethod.DELETE);
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAll() {
        String url = getTodoBaseMicroServiceUrl();
        return fetch(url, HttpMethod.DELETE);
    }

    @GetMapping(value = "/after/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getByDateAfter(@PathVariable("date") String date) {
        return fetch(getTodoBaseMicroServiceUrl() + "after/" + date);
    }

    @GetMapping(value = "/before/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getByDateBefore(@PathVariable("date") String date) {
        return fetch(getTodoBaseMicroServiceUrl() + "after/" + date);
    }

    private ResponseEntity<String> sendMicroServiceNotFoundResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("full_messages", Collections.singletonList("Targeted microservice not found"));
        try {
            return new ResponseEntity<>(mapper.writeValueAsString(response), HttpStatus.BAD_GATEWAY);
        } catch (JsonProcessingException e) {
            return new ResponseEntity<>("{\"success:\": false}", HttpStatus.BAD_GATEWAY);
        }
    }
}
