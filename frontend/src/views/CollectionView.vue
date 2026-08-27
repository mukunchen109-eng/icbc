<script setup>import http from '../api/http'; const run=async()=>{await http.post('/collections/run');alert('采集任务已提交')}</script>
<template><h2>数据采集</h2><div class="panel"><h3>数据源</h3><p>预留 IPA 平台定时采集与 Excel 文件导入两种方式。</p><button @click="run">立即执行采集</button></div></template>
