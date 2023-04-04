package com.wish17.practicegithubAction.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Hello {

    @GetMapping("/")
    public String hello () {
        return "wish 자동배포 테스트 성공";
    }
}
