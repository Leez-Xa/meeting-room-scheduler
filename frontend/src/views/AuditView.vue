<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, showError } from '../api/client'
import type { Audit } from '../types'
const logs = ref<Audit[]>([])
onMounted(async () => { try { logs.value = await api('/audit') } catch (e) { showError(e) } })
</script>
<template><section><div class="page-heading"><div><h1>审计日志</h1><p class="muted">最近 100 条操作记录</p></div></div><el-table :data="logs" empty-text="暂无审计记录" class="data-table"><el-table-column label="时间" width="190"><template #default="{row}">{{ row.operateTime.replace('T', ' ').slice(0,19) }}</template></el-table-column><el-table-column prop="operatorName" label="操作人" width="160" /><el-table-column prop="operateType" label="操作类型" width="200" /><el-table-column prop="content" label="内容" min-width="250" /></el-table></section></template>
