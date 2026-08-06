package com.odysseygen.controller;

import com.odysseygen.common.Result;
import com.odysseygen.dto.request.LoginRequest;
import com.odysseygen.dto.request.RegisterRequest;
import com.odysseygen.dto.request.UpdateUserInfoRequest;
import com.odysseygen.dto.response.LoginResponse;
import com.odysseygen.dto.response.UserInfoDTO;
import com.odysseygen.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功", null);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }

    @PutMapping("/info")
    public Result<?> updateUserInfo(@RequestAttribute Long userId,
                                    @Valid @RequestBody UpdateUserInfoRequest request) {
        userService.updateUserInfo(userId, request);
        return Result.success("信息完善成功", null);
    }

    @GetMapping("/info")
    public Result<UserInfoDTO> getUserInfo(@RequestAttribute Long userId) {
        UserInfoDTO user = userService.getById(userId);
        return Result.success(user);
    }

    @GetMapping("/profile/complete")
    public Result<Boolean> isProfileComplete(@RequestAttribute Long userId) {
        boolean complete = userService.isProfileComplete(userId);
        return Result.success(complete);
    }

    @GetMapping("/verify")
    public Result<?> verifyToken(@RequestAttribute Long userId) {
        return Result.success("Token有效", null);
    }
}
