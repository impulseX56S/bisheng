package com.example.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.security.entity.AppInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用信息 Mapper 接口
 * MyBatis-Plus 持久层
 *
 * @author Security Architect
 */
@Mapper
public interface AppInfoMapper extends BaseMapper<AppInfo> {

    /**
     * 根据 AppID 查询应用信息
     *
     * @param appId 应用 ID
     * @return 应用信息
     */
    @Select("SELECT * FROM app_info WHERE app_id = #{appId}")
    AppInfo selectByAppId(@Param("appId") String appId);
}
