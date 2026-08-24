<script setup>
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore(); const router = useRouter()
const menus = [['/dashboard','工作台'],['/collection','数据采集'],['/preprocess','数据预处理'],['/report','智能报告'],['/review','人机审核'],['/distribution','精准分发'],['/task','任务与日志'],['/user','用户权限']]
function logout(){ auth.logout(); router.push('/login') }
</script>
<template><div class="shell"><aside><h1>金融智讯</h1><p>智能化处理平台</p><nav><RouterLink v-for="[to,label] in menus" :key="to" :to="to">{{ label }}</RouterLink></nav></aside><main><header><span>金融资讯智能化处理系统</span><div>{{ auth.user?.displayName || auth.user?.username }} <button @click="logout">退出</button></div></header><section class="page"><RouterView/></section></main></div></template>
