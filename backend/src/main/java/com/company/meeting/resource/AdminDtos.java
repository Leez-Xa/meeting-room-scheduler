package com.company.meeting.resource;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}
    public record AccountRequest(@NotBlank @Pattern(regexp="[a-zA-Z0-9_.-]{3,50}") String username,
            @NotBlank @Size(max=50) String realName, @NotNull Long deptId,
            @NotBlank @Pattern(regexp="SYSTEM_ADMIN|DEPT_ADMIN|EMPLOYEE") String role,
            @NotNull @Min(1) @Max(100) Integer floorNo, @NotNull @Min(0) @Max(1) Integer status,
            String password) {}
    public record DepartmentRequest(@NotBlank @Size(max=100) String deptName, Long parentId,
            @NotNull @Min(0) Integer sortNo, @NotNull @Min(0) @Max(1) Integer status) {}
    public record RoomRequest(@NotBlank @Size(max=100) String roomName,
            @NotNull @Min(1) @Max(100) Integer floorNo, @NotNull @Min(1) @Max(10000) Integer capacity,
            @NotBlank @Size(max=255) String location, @NotNull @Min(0) @Max(1) Integer status) {}
    public record DeviceRequest(@NotNull Long roomId, @NotBlank @Size(max=100) String deviceName,
            @NotBlank @Pattern(regexp="PROJECTOR|MIC|VIDEO|DISPLAY") String deviceType,
            @NotNull @Min(1) @Max(100) Integer quantity, @NotNull @Min(0) @Max(1) Integer status) {}
    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record ResetPasswordRequest(@NotBlank String password) {}
    public record Page<T>(List<T> records, long total, int page, int pageSize) {}
}
