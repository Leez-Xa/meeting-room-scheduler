<script setup lang="ts">
import type { Meeting } from '../types'
import StatusTag from './StatusTag.vue'
defineProps<{ meeting: Meeting | null }>()
const devices: Record<string, string> = { PROJECTOR: '投影仪', MIC: '麦克风', VIDEO: '视频会议设备', DISPLAY: '显示器' }
</script>
<template>
  <div v-if="meeting" class="meeting-detail">
    <h2>{{ meeting.subject }}</h2><StatusTag :status="meeting.status" />
    <el-descriptions :column="1" border class="detail-grid">
      <el-descriptions-item label="会议室">{{ meeting.roomName || '尚未选择' }}</el-descriptions-item>
      <el-descriptions-item label="时间">{{ meeting.startTime.replace('T', ' ') }} 至 {{ meeting.endTime.slice(11, 16) }}</el-descriptions-item>
      <el-descriptions-item v-if="meeting.seriesId" label="周期场次">第 {{ meeting.seriesIndex }} / {{ meeting.seriesTotal }} 场</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ meeting.applicantName }} · {{ meeting.deptName }}</el-descriptions-item>
      <el-descriptions-item label="参会人">{{ meeting.attendees.map(a => a.realName).join('、') }}（{{ meeting.attendNum }}人）</el-descriptions-item>
      <el-descriptions-item label="设备需求">{{ meeting.devices.map(d => `${devices[d.deviceType] || d.deviceType} × ${d.quantity}`).join('、') || '无' }}</el-descriptions-item>
      <el-descriptions-item v-if="meeting.auditComment" label="审批意见">{{ meeting.auditComment }}</el-descriptions-item>
      <el-descriptions-item v-if="meeting.cancelReason" label="取消原因">{{ meeting.cancelReason }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>
