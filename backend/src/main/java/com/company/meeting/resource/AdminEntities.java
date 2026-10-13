package com.company.meeting.resource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

public final class AdminEntities {
    private AdminEntities() {}
    public static class Base {
        @TableId(type=IdType.AUTO) public Long id;
        public Long createBy;
        public Long updateBy;
        public LocalDateTime createTime;
        public LocalDateTime updateTime;
        @TableLogic public Integer deleted;
    }
    @TableName("dept") public static class Department extends Base {
        public String deptName;
        @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
        public Long parentId;
        public Integer sortNo;
        public Integer status;
    }
    @TableName("sys_user") public static class Account extends Base {
        public String username;
        @com.fasterxml.jackson.annotation.JsonIgnore public String password;
        public String realName;
        public Long deptId;
        public String role;
        public Integer floorNo;
        public Integer status;
    }
    @TableName("meeting_room") public static class Room extends Base {
        public String roomName;
        public Integer floorNo;
        public Integer capacity;
        public String location;
        public Integer status;
    }
    @TableName("device") public static class Device extends Base {
        public Long roomId;
        public String deviceName;
        public String deviceType;
        public Integer quantity;
        public Integer status;
    }
}
