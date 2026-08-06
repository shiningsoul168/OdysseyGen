package com.odysseygen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.odysseygen.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
