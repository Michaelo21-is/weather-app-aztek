package com.moj.weather_app.Controller;

import com.moj.weather_app.Dto.SignInDto;
import com.moj.weather_app.Dto.SignUpDto;
import com.moj.weather_app.Service.AuthCookieService;
import com.moj.weather_app.Service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService authCookieService;
    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
    }
    @PostMapping("/sign-up")
    public void signUp(@RequestBody SignUpDto signUpDto, HttpServletResponse response){
        authCookieService.addTokenCookies(response, authService.signUp(signUpDto));
    }

    @PostMapping("/sign-in")
    public void signIn(@RequestBody SignInDto signInDto, HttpServletResponse response){
        authCookieService.addTokenCookies(response, authService.signIn(signInDto));
    }

    @PostMapping("/refresh")
    public void refresh(HttpServletRequest request, HttpServletResponse response){
        authCookieService.addTokenCookies(response, authService.refresh(authCookieService.readRefreshToken(request)));
    }

    @PostMapping("/sign-out")
    public void signOut(HttpServletRequest request, HttpServletResponse response){
        authService.signOut(authCookieService.readRefreshToken(request));
        authCookieService.clearTokenCookies(response);
    }
}
