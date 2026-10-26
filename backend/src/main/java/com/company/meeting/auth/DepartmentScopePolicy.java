package com.company.meeting.auth;

/**
 * Centralizes the department data boundary used by meeting visibility and approval.
 * Department administrators are scoped to their own department only; the parent
 * relationship in dept is intentionally not treated as an implicit inheritance rule.
 */
public final class DepartmentScopePolicy {
    private DepartmentScopePolicy() {}

    public static boolean canAccessDepartment(UserPrincipal principal, Long targetDepartmentId) {
        if (principal == null || targetDepartmentId == null) return false;
        return "SYSTEM_ADMIN".equals(principal.role())
                || ("DEPT_ADMIN".equals(principal.role()) && targetDepartmentId.equals(principal.deptId()));
    }

    public static boolean canApproveDepartment(UserPrincipal principal, Long targetDepartmentId) {
        return principal != null
                && ("SYSTEM_ADMIN".equals(principal.role()) || "DEPT_ADMIN".equals(principal.role()))
                && canAccessDepartment(principal, targetDepartmentId);
    }
}
