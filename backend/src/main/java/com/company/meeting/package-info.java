/**
 * 企业会议室预约后端。
 * 先读 meeting 业务编排，再读 resource 房间锁和冲突 SQL，
 * 结合 auth 身份与部门范围、config 规则、notice/report 外围功能理解调用链。
 */
package com.company.meeting;
