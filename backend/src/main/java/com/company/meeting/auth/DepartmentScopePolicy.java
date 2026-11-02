package com.company.meeting.auth;

/**
 * Centralizes the department data boundary used by meeting visibility and approval.
 * Department administrators are scoped to their own department only; the parent
 * relationship in dept is intentionally not treated as an implicit inheritance rule.
 *
 * 部门数据范围的统一判断入口。
 * 系统管理员覆盖全局，部门管理员只覆盖本部门，不隐式继承子部门权限。
 * 申请人、参会人可见性和自审限制仍由预约服务继续判断。
 */
public final class DepartmentScopePolicy {
    private DepartmentScopePolicy() {}

    /**
     * 只判断管理角色与同部门，不递归子部门。
     */
    public static boolean canAccessDepartment(UserPrincipal principal, Long targetDepartmentId) {
        if (principal == null || targetDepartmentId == null) return false;
        return "SYSTEM_ADMIN".equals(principal.role())
                || ("DEPT_ADMIN".equals(principal.role()) && targetDepartmentId.equals(principal.deptId()));
    }

    /**
     * 判断管理角色覆盖范围，自审及申请时角色另行校验。
     */
    public static boolean canApproveDepartment(UserPrincipal principal, Long targetDepartmentId) {
        return principal != null
                && ("SYSTEM_ADMIN".equals(principal.role()) || "DEPT_ADMIN".equals(principal.role()))
                && canAccessDepartment(principal, targetDepartmentId);
    }
}
