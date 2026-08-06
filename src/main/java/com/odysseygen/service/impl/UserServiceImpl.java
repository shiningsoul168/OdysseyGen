package com.odysseygen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.odysseygen.common.BusinessException;
import com.odysseygen.common.ResultCode;
import com.odysseygen.dto.request.LoginRequest;
import com.odysseygen.dto.request.RegisterRequest;
import com.odysseygen.dto.request.UpdateUserInfoRequest;
import com.odysseygen.dto.response.LoginResponse;
import com.odysseygen.dto.response.UserInfoDTO;
import com.odysseygen.entity.UserInfo;
import com.odysseygen.mapper.UserInfoMapper;
import com.odysseygen.service.UserService;
import com.odysseygen.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserInfoMapper userInfoMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        // 1. 检查用户名是否存在
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUsername, request.getUsername());
        if (userInfoMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.USER_EXIST);
        }

        // 2. ✅ 检查邮箱是否已被注册
        LambdaQueryWrapper<UserInfo> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(UserInfo::getEmail, request.getEmail());
        if (userInfoMapper.selectCount(emailWrapper) > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        // 3. 创建用户
        UserInfo user = new UserInfo();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userInfoMapper.insert(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getUsername, request.getUsername());
        UserInfo user = userInfoMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        // 3. ✅ 校验账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用");
        }

        // 4. 生成 Token
        String token = jwtUtil.generateToken(user.getUserId(), user.getUsername());

        return new LoginResponse(token, user.getUserId(), user.getUsername(), user.getAvatar());
    }

    @Override
    public UserInfoDTO getById(Long userId) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return convertToDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        user.setMajor(request.getMajor());
        user.setGpa(request.getGpa());
        user.setSchoolLevel(request.getSchoolLevel());
        user.setEnglishLevel(request.getEnglishLevel());
        user.setGraduationYear(request.getGraduationYear());

        if (request.getPersonalityTags() != null) {
            try {
                user.setPersonalityTags(objectMapper.writeValueAsString(request.getPersonalityTags()));
            } catch (Exception e) {
                log.warn("性格标签序列化失败", e);
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        userInfoMapper.updateById(user);
    }

    @Override
    public boolean isProfileComplete(Long userId) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        return user.getMajor() != null && !user.getMajor().isEmpty();
    }

    private UserInfoDTO convertToDTO(UserInfo user) {
        UserInfoDTO dto = new UserInfoDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }
}