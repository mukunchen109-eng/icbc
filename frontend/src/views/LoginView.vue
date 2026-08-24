<script setup>
import { ref } from 'vue'; import { useRouter } from 'vue-router'; import http from '../api/http'; import { useAuthStore } from '../stores/auth'
const username=ref('admin'), password=ref('123456'), error=ref(''), router=useRouter(), auth=useAuthStore()
async function login(){ try { const {data}=await http.post('/auth/login',{username:username.value,password:password.value}); auth.login(data.data.user,data.data.token); router.push('/dashboard') } catch { error.value='登录失败，请检查后端服务' } }
</script>
<template><div class="login"><form @submit.prevent="login"><h1>金融智讯</h1><p>金融资讯智能化处理系统</p><input v-model="username" placeholder="用户名"><input v-model="password" type="password" placeholder="密码"><em v-if="error">{{ error }}</em><button>登录</button><small>演示账号：admin / 123456</small></form></div></template>
