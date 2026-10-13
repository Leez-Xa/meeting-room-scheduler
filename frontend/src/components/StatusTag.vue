<script setup lang="ts">
import type { MeetingStatus } from '../types'
defineProps<{ status: MeetingStatus }>()
const labels: Record<MeetingStatus, string> = { DRAFT: '草稿', PENDING: '待审批', APPROVED: '预约成功', IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELED: '已取消', REJECTED: '已拒绝', EXPIRED: '已过期' }
</script>
<template><span class="status-tag" :class="status.toLowerCase()"><i></i>{{ labels[status] }}</span></template>
