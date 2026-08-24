<template><article class="card"><span>{{ label }}</span><strong>{{ value }}</strong><small>{{ hint }}</small></article></template>
<script setup>defineProps({label:String,value:[String,Number],hint:String})</script>
