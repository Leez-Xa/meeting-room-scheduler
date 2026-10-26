import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import AppLayout from '../layouts/AppLayout.vue'
import OverviewView from '../views/OverviewView.vue'
import RoomsView from '../views/RoomsView.vue'
import BookingView from '../views/BookingView.vue'
import MeetingsView from '../views/MeetingsView.vue'
import ApprovalView from '../views/ApprovalView.vue'
import AuditView from '../views/AuditView.vue'
import AdminView from '../views/AdminView.vue'
import AccountView from '../views/AccountView.vue'
import NoticesView from '../views/NoticesView.vue'
import ReportsView from '../views/ReportsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', component: AppLayout, children: [
      { path: '', component: OverviewView, meta: { title: '工作台' } },
      { path: 'rooms', component: RoomsView, meta: { title: '会议室与排期' } },
      { path: 'booking', component: BookingView, meta: { title: '发起预约' } },
      { path: 'meetings', component: MeetingsView, meta: { title: '我的会议' } },
      { path: 'approval', component: ApprovalView, meta: { title: '待审批会议' } },
      { path: 'audit', component: AuditView, meta: { title: '审计日志' } },
      { path: 'admin', component: AdminView, meta: { title: '基础数据' } },
      { path: 'settings', component: () => import('../views/SettingsView.vue'), meta: { title: '系统参数' } },
      { path: 'account', component: AccountView, meta: { title: '账号设置' } },
      { path: 'notices', component: NoticesView, meta: { title: '消息中心' } },
      { path: 'reports', component: ReportsView, meta: { title: '会议报表' } },
    ] },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized) await auth.initialize()
  if (!auth.user && to.path !== '/login') return '/login'
  if (auth.user && to.path === '/login') return '/'
  if (['/admin','/audit','/settings'].includes(to.path) && !auth.isAdmin) return '/'
  if (to.path === '/approval' && !auth.canApprove) return '/'
  if (to.path === '/reports' && !auth.canApprove) return '/'
})
export default router
