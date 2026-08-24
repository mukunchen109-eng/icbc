import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
const routes = [{ path: '/login', component: () => import('../views/LoginView.vue') }, { path: '/', component: MainLayout, children: [
  { path: '', redirect: '/dashboard' }, { path: 'dashboard', component: () => import('../views/DashboardView.vue') }, { path: 'collection', component: () => import('../views/CollectionView.vue') }, { path: 'preprocess', component: () => import('../views/PreprocessView.vue') }, { path: 'report', component: () => import('../views/ReportView.vue') }, { path: 'review', component: () => import('../views/ReviewView.vue') }, { path: 'distribution', component: () => import('../views/DistributionView.vue') }, { path: 'task', component: () => import('../views/TaskView.vue') }, { path: 'user', component: () => import('../views/UserView.vue') }
] }]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(to => to.path === '/login' || localStorage.getItem('token') ? true : '/login')
export default router
