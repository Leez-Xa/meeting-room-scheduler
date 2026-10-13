<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, showError } from '../api/client'
import type { Notice, NoticePage } from '../types'

const loading = ref(false)
const filter = ref('all')
const page = ref(1)
const pageSize = ref(15)
const total = ref(0)
const unread = ref(0)
const notices = ref<Notice[]>([])

function signalCountChanged() { window.dispatchEvent(new Event('notice-count-changed')) }
function displayTime(value: string) { return value?.replace('T', ' ').slice(0, 16) ?? '' }
async function load() {
  loading.value = true
  try {
    const result = await api<NoticePage>(`/notices?unreadOnly=${filter.value === 'unread'}&page=${page.value}&pageSize=${pageSize.value}`)
    notices.value = result.records
    total.value = Number(result.total)
    unread.value = Number(result.unread)
  } catch (error) { showError(error) } finally { loading.value = false }
}
async function markRead(item: Notice) {
  if (item.read) return
  try {
    await api(`/notices/${item.id}/read`, { method: 'POST' })
    item.read = true
    unread.value = Math.max(0, unread.value - 1)
    signalCountChanged()
  } catch (error) { showError(error) }
}
async function markAllRead() {
  if (!unread.value) return
  try {
    await api('/notices/read-all', { method: 'POST' })
    notices.value.forEach(item => { item.read = true })
    unread.value = 0
    signalCountChanged()
  } catch (error) { showError(error) }
}
function changeFilter(value: string) { filter.value = value; page.value = 1; void load() }
function changePage(value: number) { page.value = value; void load() }
onMounted(load)
</script>

<template>
  <div class="page-heading">
    <div><div class="eyebrow">INBOX</div><h1>消息中心</h1><p class="muted">预约审批、取消和会议提醒会显示在这里。</p></div>
    <el-button plain :disabled="!unread" @click="markAllRead">全部标为已读</el-button>
  </div>
  <el-card class="notice-panel" shadow="never">
    <el-tabs :model-value="filter" @tab-change="changeFilter">
      <el-tab-pane label="全部消息" name="all" />
      <el-tab-pane :label="`未读消息 ${unread ? `(${unread})` : ''}`" name="unread" />
    </el-tabs>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="!notices.length" description="暂时没有消息" />
    <div v-else class="notice-list">
      <button v-for="item in notices" :key="item.id" class="notice-item" :class="{ unread: !item.read }" @click="markRead(item)">
        <span class="notice-dot" aria-hidden="true"></span>
        <span class="notice-copy"><strong>{{ item.title }}</strong><span>{{ item.content }}</span><small>{{ displayTime(item.createTime) }}</small></span>
        <el-tag v-if="!item.read" size="small" type="success">未读</el-tag>
      </button>
    </div>
    <div v-if="total > pageSize" class="notice-pagination"><el-pagination background layout="prev, pager, next" :current-page="page" :page-size="pageSize" :total="total" @current-change="changePage" /></div>
  </el-card>
</template>