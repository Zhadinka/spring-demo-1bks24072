package com.example.demo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

@RestController
public class HelloController {

    @Value("${greeting.text}")
    private String greetingText;

    @GetMapping("/hello")
    public String hello() {
        return greetingText;
    }

    @GetMapping("/numbers")
    public List<Integer> numbers() {
        return IntStream.rangeClosed(1, 10).boxed().toList();
    }
}
