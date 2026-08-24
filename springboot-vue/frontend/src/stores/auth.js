import { defineStore } from 'pinia'
export const useAuthStore = defineStore('auth', { state: () => ({ user: JSON.parse(localStorage.getItem('user') || 'null') }), actions: { login(user, token) { this.user = user; localStorage.setItem('user', JSON.stringify(user)); localStorage.setItem('token', token) }, logout() { this.user = null; localStorage.clear() } } })
