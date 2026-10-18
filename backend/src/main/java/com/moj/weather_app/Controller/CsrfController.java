package com.moj.weather_app.Controller;

import com.moj.weather_app.Dto.CsrfTokenDto;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CsrfController {

    /**
     * Returns the CSRF token to send in the given header on every POST/PUT/PATCH/DELETE request.
     * Reading the token also makes Spring Security set the matching XSRF-TOKEN cookie.
     */
    @GetMapping("/csrf")
    public CsrfTokenDto csrf(CsrfToken csrfToken) {
        return new CsrfTokenDto(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
