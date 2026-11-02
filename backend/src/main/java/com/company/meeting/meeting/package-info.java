/**
 * 核心预约模块。Controller 接 HTTP，Dtos 定边界，Service 管业务和事务，
 * Repository 表达 SQL，Status 定义生命周期。
 * 建议按 saveDraft → submit/submitRecurring → approve/approveSeries → cancel/cancelSeries 阅读，
 * 再看 recommend、schedule 和 advanceStates。待审批占资源，草稿不占；人员冲突只确认。
 */
package com.company.meeting.meeting;
