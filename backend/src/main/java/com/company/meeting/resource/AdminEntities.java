package com.company.meeting.resource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 基础管理实体。Base 统一主键、审计与逻辑删除。
 * 实体用于持久化，接口输入使用独立 DTO；密码编码禁止 JSON 输出。
 */
public final class AdminEntities {
    private AdminEntities() {}
    /** 基础管理共用的主键、审计字段和逻辑删除。 */
    public static class Base {
        /** 自增 BIGINT，JSON 按字符串传输。 */
        @TableId(type=IdType.AUTO) public Long id;
        public Long createBy;
        public Long updateBy;
        public LocalDateTime createTime;
        public LocalDateTime updateTime;
        /** 0 有效、1 逻辑删除，MyBatis-Plus 基础查询过滤。 */
        @TableLogic public Integer deleted;
    }
    /** 部门层级不隐式赋予向下审批权限。 */
    @TableName("dept") public static class Department extends Base {
        public String deptName;
        // 允许 parentId 写 null，才能把子部门调整为根。
        @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
        public Long parentId;
        public Integer sortNo;
        public Integer status;
    }
    /** 内部账号，密码为 BCrypt 编码并禁止 JSON 输出。 */
    @TableName("sys_user") public static class Account extends Base {
        public String username;
        @com.fasterxml.jackson.annotation.JsonIgnore public String password;
        public String realName;
        public Long deptId;
        public String role;
        public Integer floorNo;
        public Integer status;
    }
    /** 房间启用标记与预约生命周期状态不同。 */
    @TableName("meeting_room") public static class Room extends Base {
        public String roomName;
        public Integer floorNo;
        public Integer capacity;
        public String location;
        public Integer status;
    }
    /** 固定设备归属 roomId，quantity 可代表同类多件。 */
    @TableName("device") public static class Device extends Base {
        public Long roomId;
        public String deviceName;
        public String deviceType;
        public Integer quantity;
        public Integer status;
    }
}
