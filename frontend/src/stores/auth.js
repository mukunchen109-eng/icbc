import { defineStore } from 'pinia'

export const reviewRoleCodes = ['INFO_MANAGER', 'DEPT_MANAGER']
export const isReviewUser = user => reviewRoleCodes.includes(user?.roleCode)
export const isAdmin = user => user?.roleCode === 'ADMIN'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: JSON.parse(localStorage.getItem('user') || 'null') }),
  actions: {
    login(user, token, expiresIn) {
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
      localStorage.setItem('token', token)
      localStorage.setItem('tokenExpiresAt', String(Date.now() + expiresIn * 1000))
    },
    logout() {
      this.user = null
      localStorage.removeItem('user')
      localStorage.removeItem('token')
      localStorage.removeItem('tokenExpiresAt')
    }
  }
})
