package com.moj.weather_app.Controller;

import com.moj.weather_app.Service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }
    @GetMapping("/get-nickname")
    public String getNickname(@AuthenticationPrincipal UUID userId){
        return userService.getNickname(userId);
    }
}
