import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { isAdmin, isReviewUser } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/review-tasks', name: 'review-tasks', component: () => import('../views/ReviewTaskListView.vue') },
  { path: '/review-tasks/:taskId', name: 'review-workbench', component: () => import('../views/ReviewWorkbenchView.vue') },
  { path: '/', component: MainLayout, children: [
  { path: '', redirect: '/dashboard' }, { path: 'dashboard', component: () => import('../views/DashboardView.vue') }, { path: 'collection', component: () => import('../views/CollectionView.vue') }, { path: 'preprocess', component: () => import('../views/PreprocessView.vue') }, { path: 'report', component: () => import('../views/ReportView.vue') }, { path: 'review', component: () => import('../views/ReviewView.vue') }, { path: 'distribution', component: () => import('../views/DistributionView.vue') }, { path: 'task', component: () => import('../views/TaskView.vue') }, { path: 'user', component: () => import('../views/UserView.vue'), meta: { adminOnly: true } }
] }]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(to => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (!token) return to.path === '/login' ? true : '/login'
  if (to.path === '/login') return isReviewUser(user) ? '/review-tasks' : '/dashboard'
  if (isReviewUser(user) && !to.path.startsWith('/review-tasks')) return '/review-tasks'
  if (to.meta.adminOnly && !isAdmin(user)) return '/dashboard'
  return true
})
export default router
