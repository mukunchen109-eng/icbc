<script setup>
import { computed, onMounted, ref } from "vue";
import http from "../api/http";

const records = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 10;
const loading = ref(false);
const error = ref("");
const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize)),
);
const statusLabels = {
  PENDING: "待发送",
  SENDING: "发送中",
  SUCCESS: "发送成功",
  FAILED: "发送失败",
  COMPLETED: "已完成",
  PARTIAL_FAILED: "部分失败",
};

function statusText(status) {
  return statusLabels[status] || status || "-";
}
async function loadRecords(targetPage = pageNum.value) {
  loading.value = true;
  error.value = "";
  try {
    const { data } = await http.get("/admin/mail-logs", {
      params: { pageNum: targetPage, pageSize },
    });
    if (data?.code !== 200)
      throw new Error(data?.message || "邮件记录查询失败");
    records.value = data.data?.records || [];
    total.value = Number(data.data?.total || 0);
    pageNum.value = Number(data.data?.pageNum || targetPage);
  } catch (exception) {
    error.value =
      exception.response?.data?.message ||
      exception.message ||
      "邮件记录加载失败";
  } finally {
    loading.value = false;
  }
}
function changePage(page) {
  if (
    loading.value ||
    page < 1 ||
    page > totalPages.value ||
    page === pageNum.value
  )
    return;
  loadRecords(page);
}

onMounted(() => loadRecords(1));
</script>

<template>
  <div class="mail-record-heading">
    <div>
      <h2>精准分发</h2>
      <p class="muted">查看所有邮件收件人的发送记录</p>
    </div>
    <button type="button" :disabled="loading" @click="loadRecords()">
      {{ loading ? "刷新中…" : "刷新" }}
    </button>
  </div>
  <p v-if="error" class="mail-record-error">{{ error }}</p>
  <div class="panel mail-table-wrap">
    <table>
      <thead>
        <tr>
          <th>收件人姓名</th>
          <th>邮箱</th>
          <th>报告标题</th>
          <th>发送状态</th>
          <th>发送时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="5" class="mail-empty">正在加载…</td>
        </tr>
        <tr v-else-if="!records.length">
          <td colspan="5" class="mail-empty">暂无邮件发送记录</td>
        </tr>
        <tr v-for="record in records" v-else :key="record.id">
          <td>{{ record.recipientName || "-" }}</td>
          <td>{{ record.recipientEmail || "-" }}</td>
          <td>{{ record.subject || "-" }}</td>
          <td>
            <span
              :class="[
                'mail-status',
                `status-${String(record.mailStatus || '').toLowerCase()}`,
              ]"
              >{{ statusText(record.mailStatus) }}</span
            >
          </td>
          <td>{{ record.sentAt || "-" }}</td>
        </tr>
      </tbody>
    </table>
    <div class="admin-pagination">
      <span>共 {{ total }} 条，第 {{ pageNum }} / {{ totalPages }} 页</span>
      <div class="admin-pagination-actions">
        <button
          type="button"
          :disabled="loading || pageNum <= 1"
          @click="changePage(pageNum - 1)"
        >
          上一页</button
        ><button
          type="button"
          :disabled="loading || pageNum >= totalPages"
          @click="changePage(pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mail-record-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mail-record-heading p {
  margin: 0;
}
.mail-record-error {
  padding: 10px 14px;
  border-radius: 6px;
  background: #fff0f0;
  color: #b8323f;
}
.mail-table-wrap {
  padding: 0;
  overflow: hidden;
}
.mail-table-wrap table {
  width: 100%;
  border-collapse: collapse;
}
.mail-table-wrap th,
.mail-table-wrap td {
  padding: 15px 18px;
  text-align: left;
  border-bottom: 1px solid #e7edf3;
  font-size: 14px;
}
.mail-table-wrap th {
  background: #f7f9fc;
  color: #52677e;
  font-size: 13px;
}
.mail-empty {
  padding: 40px !important;
  text-align: center !important;
  color: #7a8997;
}
.mail-status {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 12px;
  background: #edf2f6;
  color: #53697b;
  font-size: 12px;
}
.status-success {
  background: #e6f6ec;
  color: #237345;
}
.status-failed {
  background: #ffe8e8;
  color: #b62e39;
}
.status-pending,
.status-sending {
  background: #fff3d7;
  color: #8a6200;
}
.mail-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  color: #65788a;
  font-size: 13px;
}
.mail-pagination div {
  display: flex;
  gap: 8px;
}
.mail-pagination button {
  padding: 7px 12px;
}
.mail-pagination button:disabled,
.mail-record-heading button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
