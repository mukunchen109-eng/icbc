<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import http from "../api/http";
import { isReviewUser, useAuthStore } from "../stores/auth";

const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);
const router = useRouter();
const auth = useAuthStore();

async function login() {
  error.value = "";
  if (!username.value.trim() || !password.value) {
    error.value = "请输入用户名和密码";
    return;
  }

  loading.value = true;
  try {
    const { data } = await http.post("/auth/login", {
      username: username.value.trim(),
      password: password.value,
    });
    if (data.code !== 200 || !data.data?.token || !data.data?.user) {
      error.value = data.message || "登录失败";
      return;
    }

    const { token, expiresIn, user } = data.data;
    auth.login(user, token, expiresIn);
    router.push(isReviewUser(user) ? "/review-tasks" : "/dashboard");
  } catch (requestError) {
    error.value =
      requestError.response?.data?.message || "网络异常，请稍后重试";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login">
    <form @submit.prevent="login">
      <h1>金融智讯</h1>
      <p>金融资讯智能化处理系统</p>
      <input
        v-model="username"
        autocomplete="username"
        placeholder="用户名"
      /><input
        v-model="password"
        autocomplete="current-password"
        type="password"
        placeholder="密码"
      /><em v-if="error">{{ error }}</em
      ><button :disabled="loading">{{ loading ? "登录中…" : "登录" }}</button>
    </form>
  </div>
</template>
