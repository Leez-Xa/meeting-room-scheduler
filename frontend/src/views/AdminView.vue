<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { api, showError } from '../api/client'
import type { Role } from '../types'

type Kind = 'users' | 'departments' | 'rooms' | 'devices'
interface Row { id: string; status: number; [key: string]: unknown }
interface Page { records: Row[]; total: string | number; page: number; pageSize: number }
const kind = ref<Kind>('users')
const records = ref<Row[]>([])
const departmentOptions = ref<Row[]>([])
const roomOptions = ref<Row[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const saving = ref(false)
const editor = ref(false)
const roleLabels: Record<Role,string> = { SYSTEM_ADMIN: '系统管理员', DEPT_ADMIN: '部门管理员', EMPLOYEE: '普通员工' }
const deviceLabels: Record<string,string> = { PROJECTOR: '投影仪', MIC: '麦克风', VIDEO: '视频会议设备', DISPLAY: '显示器' }
const kindLabels: Record<Kind,string> = { users: '账号', departments: '部门', rooms: '会议室', devices: '设备' }
const blank = () => ({ id: '', username: '', realName: '', password: '', deptId: '', role: 'EMPLOYEE' as Role, floorNo: 1, status: 1, deptName: '', parentId: '', sortNo: 0, roomName: '', capacity: 8, location: '', roomId: '', deviceName: '', deviceType: 'PROJECTOR', quantity: 1 })
const form = reactive(blank())
const columns = computed(() => ({
  users: [{prop:'username',label:'账号'},{prop:'realName',label:'姓名'},{prop:'deptId',label:'部门'},{prop:'role',label:'角色'},{prop:'floorNo',label:'办公楼层'}],
  departments: [{prop:'deptName',label:'部门名称'},{prop:'parentId',label:'上级部门'},{prop:'sortNo',label:'排序'}],
  rooms: [{prop:'roomName',label:'会议室'},{prop:'floorNo',label:'楼层'},{prop:'capacity',label:'容量'},{prop:'location',label:'位置'}],
  devices: [{prop:'deviceName',label:'设备名称'},{prop:'deviceType',label:'类型'},{prop:'quantity',label:'数量'},{prop:'roomId',label:'所属会议室'}],
})[kind.value])
function cell(row: Row, key: string) {
  if (key==='role') return roleLabels[row[key] as Role]
  if (key==='deviceType') return deviceLabels[String(row[key])]
  if (key==='deptId' || key==='parentId') return departmentOptions.value.find(d=>d.id===row[key])?.deptName || '—'
  if (key==='roomId') return roomOptions.value.find(r=>r.id===row[key])?.roomName || '—'
  return row[key] ?? '—'
}
async function loadOptions() {
  const [departments, rooms] = await Promise.all([api<Page>('/admin/departments?pageSize=100'),api<Page>('/admin/rooms?pageSize=100')])
  departmentOptions.value = departments.records
  roomOptions.value = rooms.records
}
async function load() {
  loading.value = true
  try {
    const result = await api<Page>(`/admin/${kind.value}?page=${page.value}&pageSize=20`)
    records.value = result.records
    total.value = Number(result.total)
    await loadOptions()
  } catch (e) { showError(e) } finally { loading.value = false }
}
function edit(row?: Row) { Object.assign(form,blank(),row||{}); form.password=''; form.parentId = String(row?.parentId || ''); editor.value=true }
function payload() {
  switch(kind.value) {
    case 'users': return { username:form.username, realName:form.realName, deptId:form.deptId, role:form.role, floorNo:form.floorNo, status:form.status, ...(!form.id?{password:form.password}:{}) }
    case 'departments': return { deptName:form.deptName, parentId:form.parentId||null, sortNo:form.sortNo, status:form.status }
    case 'rooms': return { roomName:form.roomName, floorNo:form.floorNo, capacity:form.capacity, location:form.location, status:form.status }
    case 'devices': return { deviceName:form.deviceName, deviceType:form.deviceType, roomId:form.roomId, quantity:form.quantity, status:form.status }
  }
}
async function save() {
  saving.value=true
  try { await api(`/admin/${kind.value}${form.id?`/${form.id}`:''}`,{method:form.id?'PUT':'POST',body:JSON.stringify(payload())}); ElMessage.success('已保存'); editor.value=false; await load() } catch(e) { showError(e) } finally { saving.value=false }
}
async function reset(row: Row) {
  try { const {value}=await ElMessageBox.prompt(`重置 ${row.username} 的密码。原会话将失效。`,'重置密码',{inputType:'password',inputPlaceholder:'至少8字符',confirmButtonText:'重置',cancelButtonText:'取消'}); await api(`/admin/users/${row.id}/reset-password`,{method:'POST',body:JSON.stringify({password:value})}); ElMessage.success('密码已重置') } catch(e) { if(e instanceof Error) showError(e) }
}
watch(kind,()=>{ page.value=1; load() })
onMounted(load)
</script>
<template><section>
<div class="page-heading"><div><h1>基础数据</h1><p class="muted">账号、部门、会议室与固定设备</p></div><el-button type="primary" :icon="Plus" @click="edit()">新增{{ kindLabels[kind] }}</el-button></div>
<el-tabs v-model="kind"><el-tab-pane label="账号" name="users" /><el-tab-pane label="部门" name="departments" /><el-tab-pane label="会议室" name="rooms" /><el-tab-pane label="固定设备" name="devices" /></el-tabs>
<div class="admin-toolbar"><span class="muted">共 {{ total }} 条</span><el-button :icon="Refresh" circle aria-label="刷新基础数据" @click="load" /></div>
<el-table :data="records" v-loading="loading" empty-text="暂无数据" class="data-table"><el-table-column v-for="column in columns" :key="column.prop" :label="column.label" min-width="130"><template #default="{row}">{{ cell(row,column.prop) }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status===1?'success':'info'">{{ row.status===1?'启用':'停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="180"><template #default="{row}"><el-button text type="primary" @click="edit(row)">编辑</el-button><el-button v-if="kind==='users'" text @click="reset(row)">重置密码</el-button></template></el-table-column></el-table>
<el-pagination :current-page="page" :page-size="20" :total="total" layout="prev, pager, next" style="margin-top:20px" @current-change="(value:number)=>{page=value;load()}" />
<el-dialog v-model="editor" :title="`${form.id?'编辑':'新增'}${kindLabels[kind]}`" width="min(600px,94vw)">
<el-form label-position="top" @submit.prevent="save">
<template v-if="kind==='users'"><div class="form-columns"><el-form-item label="账号" required><el-input v-model="form.username" maxlength="50" autocomplete="off" /></el-form-item><el-form-item label="姓名" required><el-input v-model="form.realName" maxlength="50" /></el-form-item></div>
<el-form-item label="所属部门" required><el-select v-model="form.deptId" style="width:100%"><el-option v-for="dept in departmentOptions" :key="dept.id" :value="dept.id" :label="String(dept.deptName)" :disabled="dept.status!==1" /></el-select></el-form-item><el-form-item label="角色" required><el-select v-model="form.role" style="width:100%"><el-option v-for="(label,role) in roleLabels" :key="role" :value="role" :label="label" /></el-select></el-form-item><el-form-item v-if="!form.id" label="初始密码" required><el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少8字符" /></el-form-item><el-form-item label="办公楼层" required><el-input-number v-model="form.floorNo" :min="1" :max="100" /></el-form-item></template>
<template v-if="kind==='departments'"><el-form-item label="部门名称" required><el-input v-model="form.deptName" maxlength="100" /></el-form-item><el-form-item label="上级部门"><el-select v-model="form.parentId" clearable style="width:100%"><el-option v-for="dept in departmentOptions.filter(d=>d.id!==form.id)" :key="dept.id" :value="dept.id" :label="String(dept.deptName)" :disabled="dept.status!==1" /></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item></template>
<template v-if="kind==='rooms'"><el-form-item label="会议室名称" required><el-input v-model="form.roomName" maxlength="100" /></el-form-item><div class="form-columns"><el-form-item label="楼层" required><el-input-number v-model="form.floorNo" :min="1" :max="100" /></el-form-item><el-form-item label="容量" required><el-input-number v-model="form.capacity" :min="1" :max="10000" /></el-form-item></div><el-form-item label="位置" required><el-input v-model="form.location" maxlength="255" /></el-form-item></template>
<template v-if="kind==='devices'"><el-form-item label="设备名称" required><el-input v-model="form.deviceName" maxlength="100" /></el-form-item><el-form-item label="所属会议室" required><el-select v-model="form.roomId" style="width:100%"><el-option v-for="room in roomOptions" :key="room.id" :value="room.id" :label="String(room.roomName)" /></el-select></el-form-item><div class="form-columns"><el-form-item label="类型" required><el-select v-model="form.deviceType"><el-option v-for="(label,type) in deviceLabels" :key="type" :value="type" :label="label" /></el-select></el-form-item><el-form-item label="数量" required><el-input-number v-model="form.quantity" :min="1" :max="100" /></el-form-item></div></template>
<el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
<el-alert v-if="kind==='rooms'||kind==='devices'" title="有有效预约的资源须先处理相关预约，再修改或停用。" type="info" :closable="false" />
</el-form><template #footer><el-button @click="editor=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
</section></template>
