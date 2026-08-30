<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import http from "../api/http";
import "../styles/user.css";
const roles = [
  { id: 1, name: "系统管理员" },
  { id: 2, name: "资讯管理员" },
  { id: 3, name: "部室负责人" },
];
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: "",
  roleId: "",
  status: "",
});
const records = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref(""),
  dialog = ref(false),
  detail = ref(false);
const form = reactive({
  id: null,
  username: "",
  password: "",
  roleId: 2,
  status: 1,
});
const editing = computed(() => form.id !== null),
  pages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)));
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const { data } = await http.get("/users", { params: query });
    records.value = data.data.records;
    total.value = data.data.total;
  } catch (e) {
    error.value = e.response?.data?.message || "用户数据加载失败";
  } finally {
    loading.value = false;
  }
}
function search() {
  query.pageNum = 1;
  load();
}
function resetFilters() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    username: "",
    roleId: "",
    status: "",
  });
  load();
}
function changePage(p) {
  if (p < 1 || p > pages.value) return;
  query.pageNum = p;
  load();
}
function openCreateDialog() {
  Object.assign(form, {
    id: null,
    username: "",
    password: "",
    roleId: 2,
    status: 1,
  });
  detail.value = false;
  dialog.value = true;
}
function closeDialog() {
  dialog.value = false;
  detail.value = false;
}
function closeDialogOnEscape(event) {
  if (event.key === "Escape" && dialog.value) closeDialog();
}
async function openDetail(id) {
  error.value = "";
  try {
    const { data } = await http.get(`/users/${id}`);
    Object.assign(form, { ...data.data, password: "" });
    detail.value = true;
    dialog.value = true;
  } catch (e) {
    error.value = e.response?.data?.message || "用户详情加载失败";
  }
}
async function save() {
  error.value = "";
  try {
    if (editing.value)
      await http.put(`/users/${form.id}`, {
        roleId: Number(form.roleId),
        status: Number(form.status),
      });
    else
      await http.post("/users", {
        username: form.username,
        password: form.password,
        roleId: Number(form.roleId),
      });
    closeDialog();
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || "保存失败";
  }
}
onMounted(() => {
  window.addEventListener("keydown", closeDialogOnEscape);
  load();
});
onBeforeUnmount(() =>
  window.removeEventListener("keydown", closeDialogOnEscape),
);
</script>
<template>
  <div class="user-heading">
    <div>
      <h2>用户权限管理</h2>
      <p class="muted">维护平台用户账号、启用状态和角色权限</p>
    </div>
    <button type="button" @click="openCreateDialog">+ 新增用户</button>
  </div>
  <div class="panel user-filter">
    <label
      >用户账号<input
        v-model="query.username"
        placeholder="请输入账号"
        @keyup.enter="search" /></label
    ><label
      >角色<select v-model="query.roleId">
        <option value="">全部角色</option>
        <option v-for="r in roles" :key="r.id" :value="r.id">
          {{ r.name }}
        </option>
      </select></label
    ><label
      >状态<select v-model="query.status">
        <option value="">全部状态</option>
        <option value="1">启用</option>
        <option value="0">停用</option>
      </select></label
    >
    <div class="filter-actions">
      <button type="button" @click="search">查询</button
      ><button type="button" class="secondary" @click="resetFilters">
        重置
      </button>
    </div>
  </div>
  <p v-if="error" class="form-error">{{ error }}</p>
  <div class="panel user-table-wrap">
    <table class="user-table">
      <thead>
        <tr>
          <th>用户ID</th>
          <th>登录账号</th>
          <th>角色</th>
          <th>状态</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="6" class="empty">正在加载...</td>
        </tr>
        <tr v-else-if="!records.length">
          <td colspan="6" class="empty">暂无符合条件的用户</td>
        </tr>
        <tr v-for="u in records" :key="u.id">
          <td>{{ u.id }}</td>
          <td class="account">{{ u.username }}</td>
          <td>
            <span class="role-tag">{{ u.roleName }}</span>
          </td>
          <td>
            <span :class="['status-tag', u.status ? 'enabled' : 'disabled']">{{
              u.status ? "启用" : "停用"
            }}</span>
          </td>
          <td>{{ u.createdAt }}</td>
          <td>
            <button class="link-button" @click="openDetail(u.id)">
              详情 / 编辑
            </button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="admin-pagination">
      <span>共 {{ total }} 条，第 {{ query.pageNum }} / {{ pages }} 页</span>
      <div class="admin-pagination-actions">
        <button
          type="button"
          :disabled="query.pageNum === 1"
          @click="changePage(query.pageNum - 1)"
        >
          上一页</button
        ><button
          type="button"
          :disabled="query.pageNum === pages"
          @click="changePage(query.pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
  <Teleport to="body"
    ><div v-if="dialog" class="modal-mask" @click.self="closeDialog">
      <form class="user-modal" @submit.prevent="save">
        <div class="modal-title">
          <div>
            <h3>
              {{ editing ? (detail ? "用户详情" : "编辑用户") : "新增用户" }}
            </h3>
            <p>
              {{
                editing ? "用户 ID：" + form.id : "创建平台登录账号并分配角色"
              }}
            </p>
          </div>
          <button
            type="button"
            class="close-button"
            aria-label="关闭"
            @click="closeDialog"
          >
            ×
          </button>
        </div>
        <label
          >登录账号<input
            v-model="form.username"
            :disabled="editing"
            required
            maxlength="64"
            placeholder="例如 info02" /></label
        ><label v-if="!editing"
          >初始密码<input
            v-model="form.password"
            type="password"
            minlength="3"
            maxlength="100"
            required
            placeholder="至少 3 位" /></label
        ><label
          >角色<select v-model="form.roleId" :disabled="detail">
            <option v-for="r in roles" :key="r.id" :value="r.id">
              {{ r.name }}
            </option>
          </select></label
        ><label v-if="editing"
          >账号状态<select v-model="form.status" :disabled="detail">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select></label
        ><label v-if="editing"
          >创建时间<input :value="form.createdAt" disabled
        /></label>
        <div class="modal-actions">
          <button type="button" class="secondary" @click="closeDialog">
            取消</button
          ><button v-if="detail" type="button" @click="detail = false">
            编辑用户</button
          ><button v-else type="submit">
            {{ editing ? "保存修改" : "创建用户" }}
          </button>
        </div>
      </form>
    </div></Teleport
  >
</template>
