package com.odysseygen.service;

import com.odysseygen.dto.request.LoginRequest;
import com.odysseygen.dto.request.RegisterRequest;
import com.odysseygen.dto.request.UpdateUserInfoRequest;
import com.odysseygen.dto.response.LoginResponse;
import com.odysseygen.dto.response.UserInfoDTO;

public interface UserService {
    void register(RegisterRequest registerRequest);
    LoginResponse login(LoginRequest loginRequest);
    UserInfoDTO getById(Long userId);  // ✅ 改为 DTO
    void updateUserInfo(Long userId, UpdateUserInfoRequest request);
    boolean isProfileComplete(Long userId);
}