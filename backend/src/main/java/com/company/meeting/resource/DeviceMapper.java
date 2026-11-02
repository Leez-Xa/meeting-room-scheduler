package com.company.meeting.resource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/**
 * device 表的 MyBatis-Plus 基础映射。
 * 复用 BaseMapper，逻辑删除与列映射来自 AdminEntities；
 * 业务权限与跨记录约束由 AdminService 校验。
 */
@Mapper public interface DeviceMapper extends BaseMapper<AdminEntities.Device> {}
