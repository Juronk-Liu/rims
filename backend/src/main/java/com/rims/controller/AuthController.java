package com.rims.controller;

import com.rims.common.Dicts;
import com.rims.common.Result;
import com.rims.dto.Dtos;
import com.rims.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Dtos.LoginResponse> login(@Valid @RequestBody Dtos.LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @GetMapping("/me")
    public Result<Dtos.UserVO> me() {
        return Result.ok(authService.me());
    }

    @GetMapping("/users")
    public Result<List<Dtos.UserVO>> users() {
        return Result.ok(authService.listUsers());
    }

    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.ok(Dicts.all());
    }
}
