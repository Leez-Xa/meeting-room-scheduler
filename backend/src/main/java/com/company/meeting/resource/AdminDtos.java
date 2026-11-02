package com.company.meeting.resource;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 基础管理输入与分页模型，约束角色、设备类型和启用标记。
 * 资料修改与密码重置分离，防止普通更新隐式改密。
 */
public final class AdminDtos {
    private AdminDtos() {}
    /**
     * 账号维护输入，三类固定角色，password 仅新增使用。
     */
    public record AccountRequest(@NotBlank @Pattern(regexp="[a-zA-Z0-9_.-]{3,50}") String username,
            @NotBlank @Size(max=50) String realName, @NotNull Long deptId,
            @NotBlank @Pattern(regexp="SYSTEM_ADMIN|DEPT_ADMIN|EMPLOYEE") String role,
            @NotNull @Min(1) @Max(100) Integer floorNo, @NotNull @Min(0) @Max(1) Integer status,
            String password) {}
    /**
     * 部门输入，空 parentId 为根，服务检查环和停用约束。
     */
    public record DepartmentRequest(@NotBlank @Size(max=100) String deptName, Long parentId,
            @NotNull @Min(0) Integer sortNo, @NotNull @Min(0) @Max(1) Integer status) {}
    /**
     * 容量、楼层、启用标记均约束范围的房间输入。
     */
    public record RoomRequest(@NotBlank @Size(max=100) String roomName,
            @NotNull @Min(1) @Max(100) Integer floorNo, @NotNull @Min(1) @Max(10000) Integer capacity,
            @NotBlank @Size(max=255) String location, @NotNull @Min(0) @Max(1) Integer status) {}
    /**
     * 房间固定设备，支持投影、麦克风、视频会议及显示类型。
     */
    public record DeviceRequest(@NotNull Long roomId, @NotBlank @Size(max=100) String deviceName,
            @NotBlank @Pattern(regexp="PROJECTOR|MIC|VIDEO|DISPLAY") String deviceType,
            @NotNull @Min(1) @Max(100) Integer quantity, @NotNull @Min(0) @Max(1) Integer status) {}
    /**
     * 本人改密，先匹配旧密码。
     */
    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    /**
     * 管理员重置输入，与资料更新分离。
     */
    public record ResetPasswordRequest(@NotBlank String password) {}
    /**
     * 分页结果，page 从 1 开始，total 为全部符合条件记录数。
     */
    public record Page<T>(List<T> records, long total, int page, int pageSize) {}
}
