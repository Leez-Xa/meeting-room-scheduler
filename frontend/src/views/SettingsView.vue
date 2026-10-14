<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/client'

interface Rules {
  workdays: number[]; openTime: string; closeTime: string
  capacityWeight: number; deviceWeight: number; floorWeight: number
  floorPenalty: number; draftRetentionDays: number; reminderMinutes: number
}
interface Settings { revision: string; rules: Rules; updatedAt: string | null; updatedBy: string | null; migrationRequired: boolean }
const saved = ref<Settings>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const form = reactive<Rules>({ workdays: [], openTime: '', closeTime: '', capacityWeight: 50, deviceWeight: 30, floorWeight: 20, floorPenalty: 20, draftRetentionDays: 7, reminderMinutes: 15 })
const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
function payload(): Rules {
  return { ...form, workdays: [...form.workdays].sort(), capacityWeight: form.capacityWeight / 100, deviceWeight: form.deviceWeight / 100, floorWeight: form.floorWeight / 100 }
}
function normalized(rules: Rules) { return JSON.stringify({ ...rules, workdays: [...rules.workdays].sort(), openTime: rules.openTime.slice(0, 5), closeTime: rules.closeTime.slice(0, 5) }) }
const dirty = computed(() => !!saved.value && normalized(payload()) !== normalized(saved.value.rules))
const totalWeight = computed(() => Math.round((form.capacityWeight + form.deviceWeight + form.floorWeight) * 100) / 100)
const validation = computed(() => {
  if (!form.workdays.length) return '至少选择一个开放工作日'
  if (!form.openTime || !form.closeTime || form.openTime >= form.closeTime) return '请选择有效开放时间，开始须早于结束'
  if (Math.abs(totalWeight.value - 100) > 0.0001) return '三项推荐权重合计须为100%'
  const values = [form.capacityWeight, form.deviceWeight, form.floorWeight, form.floorPenalty, form.draftRetentionDays, form.reminderMinutes]
  if (values.some(value => value == null || !Number.isFinite(value))) return '请填写所有数字参数'
  if ([form.capacityWeight, form.deviceWeight, form.floorWeight, form.floorPenalty].some(value => value < 0 || value > 100)
    || form.draftRetentionDays < 1 || form.draftRetentionDays > 365 || form.reminderMinutes < 1 || form.reminderMinutes > 120) return '数字参数超出允许范围'
  return ''
})
function apply(settings: Settings) {
  saved.value = settings
  Object.assign(form, settings.rules, { workdays: [...settings.rules.workdays], openTime: settings.rules.openTime.slice(0, 5), closeTime: settings.rules.closeTime.slice(0, 5), capacityWeight: settings.rules.capacityWeight * 100, deviceWeight: settings.rules.deviceWeight * 100, floorWeight: settings.rules.floorWeight * 100 })
}
async function reload() {
  if (dirty.value) {
    try { await ElMessageBox.confirm('重新加载会放弃当前未保存的修改。', '重新加载参数', { confirmButtonText: '重新加载', cancelButtonText: '保留修改', type: 'warning' }) } catch { return }
  }
  loading.value = true; error.value = ''
  try { apply(await api<Settings>('/admin/settings')) }
  catch (e) { error.value = e instanceof Error ? e.message : '参数加载失败' }
  finally { loading.value = false }
}
async function save() {
  if (saving.value || loading.value || !saved.value || saved.value.migrationRequired || validation.value || !dirty.value) return
  saving.value = true; error.value = ''
  try {
    apply(await api<Settings>('/admin/settings', { method: 'PUT', body: JSON.stringify({ revision: saved.value.revision, rules: payload() }) }))
    ElMessage.success('系统参数已保存并生效')
  } catch (e) { error.value = e instanceof Error ? e.message : '保存失败，请重试' }
  finally { saving.value = false }
}
onBeforeRouteLeave(async () => {
  if (saving.value) return false
  if (!dirty.value) return true
  try { await ElMessageBox.confirm('参数尚未保存，确认离开？', '未保存的修改', { confirmButtonText: '离开', cancelButtonText: '继续编辑' }); return true } catch { return false }
})
onMounted(reload)
</script>

<template>
  <section class="settings-page">
    <div class="page-heading"><div><h1>系统参数</h1><p class="muted">统一预约规则与通知策略 · 北京时间</p></div><el-button :disabled="saving || loading" @click="reload">重新加载</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="settings-error" role="alert" />
    <el-alert v-if="saved?.migrationRequired" title="数据库尚未升级，当前使用系统初始规则。请先完成数据库升级，再编辑参数。" type="warning" show-icon :closable="false" class="settings-error" />
    <div v-loading="loading" class="settings-layout">
      <el-form v-if="saved" label-position="top" class="settings-form" :disabled="loading || saving || saved.migrationRequired" @submit.prevent="save">
        <section class="settings-section">
          <div class="section-heading"><h2>预约开放规则</h2><span class="settings-caption">01</span></div>
          <p class="field-note">周一至周五开放本周未来时段，周六、周日同时开放下周。会议不可跨天。</p>
          <el-form-item label="开放工作日" required><el-checkbox-group v-model="form.workdays" aria-label="开放工作日"><el-checkbox v-for="(day, index) in days" :key="day" :value="index + 1">{{ day }}</el-checkbox></el-checkbox-group></el-form-item>
          <div class="settings-fields"><el-form-item label="开放开始时间" required><el-time-picker v-model="form.openTime" format="HH:mm" value-format="HH:mm" :clearable="false" aria-label="开放开始时间" /></el-form-item><el-form-item label="开放结束时间" required><el-time-picker v-model="form.closeTime" format="HH:mm" value-format="HH:mm" :clearable="false" aria-label="开放结束时间" /></el-form-item></div>
          <p class="field-note">新规则如影响已有有效预约，将阻止保存，请先处理相关预约。</p>
        </section>
        <section class="settings-section">
          <div class="section-heading"><h2>会议室推荐</h2><span class="settings-caption">02</span></div>
          <p class="field-note">先过滤容量、设备、停用与时段冲突，再按以下权重评分。</p>
          <div class="settings-fields settings-weights"><el-form-item v-for="item in [{key:'capacityWeight',label:'人数匹配'},{key:'deviceWeight',label:'设备匹配'},{key:'floorWeight',label:'楼层距离'}] as const" :key="item.key" :label="`${item.label}（%）`" required><el-input-number v-model="form[item.key]" :min="0" :max="100" :precision="2" :aria-label="`${item.label}权重`" /></el-form-item></div>
          <p class="weight-total" :class="{invalid: totalWeight !== 100}" aria-live="polite">权重合计 <strong>{{ totalWeight }}%</strong><span>须为100%</span></p>
          <el-form-item label="每相差一层扣分（0–100）" required><el-input-number v-model="form.floorPenalty" :min="0" :max="100" :precision="0" aria-label="每层扣分" /></el-form-item>
        </section>
        <section class="settings-section">
          <div class="section-heading"><h2>提醒与草稿保留</h2><span class="settings-caption">03</span></div>
          <div class="settings-fields"><el-form-item label="会前提醒（提前分钟）" required><el-input-number v-model="form.reminderMinutes" :min="1" :max="120" :precision="0" aria-label="提醒提前分钟" /><p class="field-note">通知全部参会人；同场会议只提醒一次。</p></el-form-item><el-form-item label="草稿保留（天）" required><el-input-number v-model="form.draftRetentionDays" :min="1" :max="365" :precision="0" aria-label="草稿保留天数" /><p class="field-note">超过保留期未修改的草稿自动清理，不再出现在列表中。</p></el-form-item></div>
        </section>
        <div class="settings-actions"><div aria-live="polite"><p v-if="validation" class="settings-validation">{{ validation }}</p><span v-else class="field-note">{{ saved.migrationRequired ? '等待数据库升级' : dirty ? '有未保存的修改' : '当前参数已同步' }}</span></div><el-button type="primary" native-type="submit" :loading="saving" :disabled="!dirty || !!validation || loading || saved.migrationRequired">保存参数</el-button></div>
      </el-form>
      <el-empty v-else-if="!loading" description="参数暂时无法加载，请点击重新加载" :image-size="80" />
      <aside v-if="saved" class="settings-summary">
        <h2>规则预览</h2><p class="summary-label">开放时段</p><strong class="summary-hours">{{ form.openTime || '—' }} <span>–</span> {{ form.closeTime || '—' }}</strong><p>{{ [...form.workdays].sort().map(day => days[day - 1]).join(' / ') || '尚未选择工作日' }}</p>
        <div class="summary-divider"></div><p class="summary-label">推荐权重</p><div class="weight-bar" aria-hidden="true"><span :style="{flex: form.capacityWeight}"></span><span :style="{flex: form.deviceWeight}"></span><span :style="{flex: form.floorWeight}"></span></div><div class="summary-weights"><span>人数 {{ form.capacityWeight }}%</span><span>设备 {{ form.deviceWeight }}%</span><span>楼层 {{ form.floorWeight }}%</span></div>
        <div class="summary-divider"></div><p>会前 {{ form.reminderMinutes }} 分钟提醒</p><p>草稿 {{ form.draftRetentionDays }} 天未改后清理</p><p class="field-note">保存后即时生效，重启后保留。正在填写的预约以提交时的最新规则校验。</p>
        <div class="summary-divider"></div><p class="field-note">参数版本 {{ saved.revision }}<br />{{ saved.updatedBy && saved.updatedAt ? `更新于 ${saved.updatedAt.replace('T', ' ')}` : '使用系统初始参数' }}</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.settings-page { max-width: 1180px; }
.settings-error { margin-bottom: 24px; }
.settings-layout { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 40px; min-height: 200px; align-items: start; }
.settings-section { padding: 0 0 22px; margin-bottom: 24px; border-bottom: 1px solid #dce5df; }
.settings-section .section-heading { margin-bottom: 10px; }
.settings-caption { color: #687f70; font: 18px Georgia, serif; }
.settings-section > .field-note { margin: 0 0 18px; }
.settings-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.settings-fields .el-time-picker, .settings-fields :deep(.el-date-editor) { width: 100%; }
.settings-fields .field-note { flex-basis: 100%; margin: 8px 0 0; }
.settings-weights { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.settings-weights .el-input-number { width: 100%; }
.weight-total { display: flex; gap: 12px; align-items: center; margin: -4px 0 22px; font-size: 12px; color: #39634b; }
.weight-total span { margin-left: auto; color: #5b7062; }
.invalid, .settings-validation { color: #a33636; }
.settings-actions { display: flex; justify-content: space-between; gap: 20px; align-items: center; }
.settings-validation { margin: 0; font-size: 12px; }
.settings-summary { position: sticky; top: 24px; padding: 26px 22px; background: #e8f0e9; border-top: 3px solid #388365; border-radius: 3px; }
.settings-summary p { font-size: 12px; line-height: 1.8; color: #435f4e; overflow-wrap: anywhere; }
.settings-summary h2 { margin: 0 0 26px; }
.summary-label { margin-bottom: 8px; }
.summary-hours { font-size: 24px; font-weight: 500; color: #28593e; white-space: nowrap; }
.summary-hours span { color: #637c69; padding: 0 4px; }
.summary-divider { border-top: 1px solid #cbdacd; margin: 22px 0; }
.weight-bar { height: 6px; display: flex; gap: 2px; overflow: hidden; margin: 12px 0; border-radius: 3px; }
.weight-bar span:nth-child(1) { background: #327d5b; }.weight-bar span:nth-child(2) { background: #73967c; }.weight-bar span:nth-child(3) { background: #adbba8; }
.summary-weights { display: flex; flex-wrap: wrap; gap: 10px; font-size: 11px; color: #435f4e; }
.settings-page :deep(.field-note) { color: #526858; }
@media (max-width: 1100px) { .settings-layout { grid-template-columns: minmax(0, 1fr) 240px; gap: 24px; }.settings-weights { grid-template-columns: 1fr; gap: 0; }.settings-weights .el-input-number { width: 180px; } }
@media (max-width: 760px) { .settings-layout { grid-template-columns: 1fr; }.settings-summary { position: static; }.settings-fields { grid-template-columns: 1fr; gap: 0; }.settings-page .page-heading { gap: 12px; }.settings-summary { padding: 22px; }.settings-actions { align-items: flex-start; } }
</style>
