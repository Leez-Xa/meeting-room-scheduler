package com.company.meeting.meeting;

/**
 * 预约八状态，与 schema.sql 约束一致。
 * {@code DRAFT} 草稿不占资源；{@code PENDING} 待审批、{@code APPROVED} 预约成功、
 * {@code IN_PROGRESS} 进行中占资源；{@code COMPLETED} 已结束、{@code CANCELED} 已取消、
 * {@code REJECTED} 已拒绝、{@code EXPIRED} 已过期不占资源。
 * 枚举只定义值，不授权转换；转换必须经过相应业务操作或系统任务。
 */
public enum MeetingStatus {
    DRAFT, PENDING, APPROVED, IN_PROGRESS, COMPLETED, CANCELED, REJECTED, EXPIRED
}
