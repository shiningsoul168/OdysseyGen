package com.odysseygen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.odysseygen.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
