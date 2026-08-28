<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const tasks = ref([])
const loading = ref(false)
const error = ref('')

async function loadTasks() {
  loading.value = true
  error.value = ''

  try {
    const response = await http.get('/tasks')
    tasks.value = response.data.data
  } catch (exception) {
    error.value =
      exception.response?.data?.message || '任务记录加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)
</script>

<template>
  <div class="task-heading">
    <div>
      <h2>任务与日志</h2>
      <p class="muted">查看每日资讯采集任务的执行状态和日志摘要</p>
    </div>

    <button type="button" :disabled="loading" @click="loadTasks">
      {{ loading ? '刷新中...' : '刷新' }}
    </button>
  </div>

  <p v-if="error" class="error-message">
    {{ error }}
  </p>

  <div class="panel table-wrap">
    <table>
      <thead>
        <tr>
          <th>任务日期</th>
          <th>触发方式</th>
          <th>状态</th>
          <th>处理数量</th>
          <th>重试次数</th>
          <th>日志摘要</th>
          <th>开始时间</th>
          <th>结束时间</th>
        </tr>
      </thead>

      <tbody>
        <tr v-if="loading">
          <td colspan="8" class="empty">正在加载...</td>
        </tr>

        <tr v-else-if="tasks.length === 0">
          <td colspan="8" class="empty">暂无任务记录</td>
        </tr>

        <tr v-for="task in tasks" :key="task.id">
          <td>{{ task.targetDate }}</td>
          <td>{{ task.triggerType }}</td>
          <td>{{ task.status }}</td>
          <td>{{ task.processedCount }}</td>
          <td>{{ task.retryCount }}</td>
          <td>{{ task.message || '-' }}</td>
          <td>{{ task.startedAt || '-' }}</td>
          <td>{{ task.finishedAt || '-' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.task-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-heading p {
  margin: 0;
}

.table-wrap {
  overflow-x: auto;
  padding: 0;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 14px 16px;
  text-align: left;
  border-bottom: 1px solid #e7edf3;
  font-size: 14px;
}

th {
  background: #f7f9fc;
  color: #52677e;
  font-size: 13px;
}

.error-message {
  padding: 10px 14px;
  border-radius: 6px;
  background: #fff0f0;
  color: #b8323f;
}

.empty {
  padding: 36px;
  text-align: center;
  color: #7a8997;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>