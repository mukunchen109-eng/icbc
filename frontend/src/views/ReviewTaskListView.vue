<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const records = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: auth.user?.roleCode === 'DEPT_MANAGER' ? '' : 'PENDING' })
const isInitial = computed(() => auth.user?.roleCode === 'INFO_MANAGER')
const stage = computed(() => isInitial.value ? 'INITIAL' : 'FINAL')
const stageName = computed(() => isInitial.value ? '初审' : '终审')
const pages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const statusText = { PENDING: '待审核', ANNOTATING: '批阅中', REVIEWING: '审核中', APPROVED: '待发送', REJECTED: '已退回', COMPLETED: '已完成', ARCHIVED: '已归档' }
function taskStatusText(status) { return statusText[status] || status }
function taskSubmittedAt(task) { return task.submittedAt || task.submitted_at || '-' }
const mailDialog = ref(false)
const selectedReport = ref(null)
const mailSuccess = ref('')
const mailError = ref('')
const sendingMail = ref(false)
const loadingRecipients = ref(false)
const addingRecipient = ref(false)
const showRecipientForm = ref(false)
const recipients = reactive([])
const newRecipient = reactive({ name: '', email: '' })
const selectedRecipientCount = computed(() => recipients.filter(item => item.selected).length)
const demoTasks = {
  INITIAL: {
    id: 90001, reportId: 10001, versionId: 20001, versionNo: 1,
    reviewStage: 'INITIAL', status: 'PENDING', reportDate: '2026-08-25',
    reportTitle: '每日资讯摘要 - 20260825（初审测试）', submittedAt: '2026-08-25 09:35:00', demo: true
  },
  DEPT: [
    { id: 4, reportId: 4, versionId: 21, versionNo: 1, reviewStage: 'FINAL', status: 'PENDING', reportDate: '2026-08-23', reportTitle: '每日资讯摘要-20260823（终审页面测试-待审核）', submittedAt: '2026-08-25 13:05:00', databaseFallback: true },
    { id: 5, reportId: 5, versionId: 22, versionNo: 1, reviewStage: 'FINAL', status: 'REVIEWING', reportDate: '2026-08-22', reportTitle: '每日资讯摘要-20260822（终审页面测试-审核中）', submittedAt: '2026-08-25 12:10:00', databaseFallback: true },
    { id: 6, reportId: 6, versionId: 23, versionNo: 1, reviewStage: 'FINAL', status: 'APPROVED', reportDate: '2026-08-21', reportTitle: '每日资讯摘要-20260821（终审页面测试-待发送）', submittedAt: '2026-08-25 11:15:00', completedAt: '2026-08-25 11:45:00', databaseFallback: true },
    { id: 7, reportId: 7, versionId: 24, versionNo: 1, reviewStage: 'FINAL', status: 'REJECTED', reportDate: '2026-08-20', reportTitle: '每日资讯摘要-20260820（终审页面测试-已退回）', submittedAt: '2026-08-25 10:20:00', completedAt: '2026-08-25 10:50:00', databaseFallback: true },
    { id: 8, reportId: 8, versionId: 26, versionNo: 1, reviewStage: 'FINAL', status: 'ARCHIVED', reportDate: '2026-08-19', reportTitle: '每日资讯摘要-20260819（终审页面测试-已归档）', submittedAt: '2026-08-25 09:25:00', completedAt: '2026-08-25 10:00:00', databaseFallback: true }
  ]
}

function matchingDemoTasks() {
  const source = isInitial.value ? [demoTasks.INITIAL] : demoTasks.DEPT
  return query.status ? source.filter(item => item.status === query.status) : source
}

function useDemoTasks() {
  records.value = matchingDemoTasks()
  total.value = records.value.length
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get('/review-tasks/my', { params: { stage: stage.value, ...query } })
    if (data.code !== 200) throw new Error(data.message || '查询失败')
    records.value = data.data?.records || []
    total.value = Number(data.data?.total || 0)
    if (!records.value.length) useDemoTasks()
  } catch (requestError) {
    useDemoTasks()
    error.value = ''
  } finally {
    loading.value = false
  }
}

function changeStatus() { query.pageNum = 1; load() }
function changePage(page) { if (page < 1 || page > pages.value) return; query.pageNum = page; load() }
function openTask(task) {
  router.push({ name: 'review-workbench', params: { taskId: task.id }, query: {
    reportId: task.reportId, title: task.reportTitle, date: task.reportDate,
    versionId: task.versionId, versionNo: task.versionNo || '', demo: task.demo || task.databaseFallback ? '1' : ''
  } })
}
async function openMailDialog(report) {
  selectedReport.value = report
  mailSuccess.value = ''
  mailError.value = ''
  showRecipientForm.value = false
  mailDialog.value = true
  loadingRecipients.value = true
  recipients.splice(0)
  try {
    const { data } = await http.get('/mail-recipients')
    if (data?.code !== 200) throw new Error(data?.message || '查询接收人员失败')
    recipients.push(...(data.data || []).map(item => ({ ...item, selected: false })))
    if (!recipients.length) mailError.value = '当前没有启用的接收人员'
  } catch (requestError) {
    mailError.value = requestError.response?.data?.message || requestError.message || '接收人员加载失败'
  } finally {
    loadingRecipients.value = false
  }
}
function closeMailDialog() { if (!sendingMail.value) mailDialog.value = false }
async function addRecipient() {
  mailError.value = ''
  const name = newRecipient.name.trim()
  const email = newRecipient.email.trim()
  if (!name || !/^\S+@\S+\.\S+$/.test(email)) {
    mailError.value = '请填写收件人姓名和正确的邮箱地址'
    return
  }
  addingRecipient.value = true
  try {
    const { data } = await http.post('/mail-recipients', { name, email })
    if (data?.code !== 200) throw new Error(data?.message || '新增收件人失败')
    recipients.push({ ...data.data, selected: true })
    Object.assign(newRecipient, { name: '', email: '' })
    showRecipientForm.value = false
    mailSuccess.value = '收件人已保存到数据库并自动选中'
  } catch (requestError) {
    mailError.value = requestError.response?.data?.message || requestError.message || '新增收件人失败'
  } finally {
    addingRecipient.value = false
  }
}
async function sendReport() {
  const selected = recipients.filter(item => item.selected)
  if (!selected.length) { mailError.value = '请至少选择一名接收人员'; return }
  const payload = {
    reportId: selectedReport.value.reportId,
    versionId: selectedReport.value.versionId,
    subject: selectedReport.value.reportTitle,
    mailBody: `您好，附件为 ${selectedReport.value.reportDate} 每日资讯摘要，请查收。`,
    recipients: selected.map(({ name, email }) => ({ name, email }))
  }
  mailError.value = ''
  mailSuccess.value = ''
  sendingMail.value = true
  try {
    const createResponse = await http.post('/mail-tasks', payload)
    if (createResponse.data?.code !== 200) throw new Error(createResponse.data?.message || '创建发送任务失败')
    const mailTaskId = createResponse.data?.data?.id
    if (!mailTaskId) throw new Error('后端未返回邮件任务编号')

    const sendResponse = await http.post(`/mail-tasks/${mailTaskId}/send`)
    if (sendResponse.data?.code !== 200) throw new Error(sendResponse.data?.message || '发送邮件失败')
    const result = sendResponse.data?.data || {}
    if (Number(result.failedCount || 0) > 0) {
      mailError.value = `发送完成：成功 ${result.successCount || 0} 封，失败 ${result.failedCount} 封。失败记录可稍后重试。`
    } else {
      mailSuccess.value = `处理完成，共记录 ${result.successCount ?? payload.recipients.length} 名收件人。报告已归档。`
      selectedReport.value.status = 'ARCHIVED'
      await load()
    }
  } catch (requestError) {
    mailError.value = requestError.response?.data?.message || requestError.message || '发送失败，请稍后重试'
  } finally {
    sendingMail.value = false
  }
}
function logout() { auth.logout(); router.push('/login') }

onMounted(load)
</script>

<template>
  <div class="task-list-shell">
    <header class="task-list-header">
      <div><strong>金融智讯</strong><span>审核任务中心</span></div>
      <div class="task-list-user"><span class="role-badge">{{ auth.user?.roleName }}</span><span>{{ auth.user?.username }}</span><button class="text-button" @click="logout">退出</button></div>
    </header>
    <main class="task-list-main">
      <section class="task-list-intro">
        <div><span class="task-eyebrow">{{ stageName }}工作台</span><h1>{{ isInitial ? '我的待审核报告' : '我的终审与待发送报告' }}</h1><p>{{ isInitial ? '核验报告内容、政治方向和数据一致性，处理完成后提交终审。' : '复核报告并完成定稿；对已完成终审的报告选择接收人员并创建发送任务。' }}</p></div>
        <div class="task-count"><b>{{ total }}</b><span>项任务</span></div>
      </section>
      <section class="task-list-panel">
        <div class="task-list-toolbar"><div><h2>{{ isInitial ? '审核任务' : '终审与发送任务' }}</h2><span>{{ isInitial ? '仅显示分配给当前账号的初审任务' : '处理终审任务，并发送已经完成终审的报告' }}</span></div><label>任务状态<select v-model="query.status" @change="changeStatus"><template v-if="isInitial"><option value="PENDING">待审核</option><option value="ANNOTATING">批阅中</option><option value="REVIEWING">审核中</option><option value="REJECTED">已退回</option><option value="APPROVED">待发送</option><option value="">全部状态</option></template><template v-else><option value="">全部任务</option><option value="PENDING">待审核</option><option value="REVIEWING">审核中</option><option value="APPROVED">待发送</option><option value="REJECTED">已退回</option><option value="ARCHIVED">已归档</option></template></select></label><button class="outline-button" :disabled="loading" @click="load">刷新</button></div>
        <div v-if="error" class="task-error"><b>暂时无法加载任务</b><span>{{ error }}</span><button @click="load">重新加载</button></div>
        <div v-else-if="loading" class="task-empty"><span class="task-spinner"></span><b>正在加载审核任务…</b></div>
        <div v-else-if="!records.length" class="task-empty"><span class="task-empty-icon">✓</span><b>当前没有{{ query.status ? taskStatusText(query.status) : '' }}任务</b><p>新的{{ stageName }}任务到达后会显示在这里。</p></div>
        <div v-else class="task-table-wrap"><table class="task-table"><thead><tr><th>报告信息</th><th>报告日期</th><th>审核阶段</th><th>任务状态</th><th class="submitted-time-column">提交时间</th><th></th></tr></thead><tbody><tr v-for="task in records" :key="task.id"><td><b>{{ task.reportTitle }} <span v-if="task.demo" class="demo-chip">测试数据</span><span v-else-if="task.databaseFallback" class="demo-chip">数据库测试记录</span></b><small>任务 #{{ task.id }} · 报告 #{{ task.reportId }} · 版本 V{{ task.versionNo || task.versionId }}</small></td><td>{{ task.reportDate }}</td><td><span class="stage-chip">{{ task.reviewStage === 'FINAL' ? '终审' : '初审' }}</span></td><td><span :class="['task-status', task.status?.toLowerCase()]">{{ taskStatusText(task.status) }}</span></td><td class="submitted-time-column">{{ taskSubmittedAt(task) }}</td><td><button v-if="!isInitial && task.status === 'APPROVED'" class="send-report-button" @click="openMailDialog(task)">发送报告</button><button v-else-if="!isInitial && task.status === 'REJECTED'" class="closed-task-button" disabled>已退回</button><button v-else-if="!isInitial && task.status === 'ARCHIVED'" class="closed-task-button" disabled>已归档</button><button v-else @click="openTask(task)">进入审核</button></td></tr></tbody></table></div>
        <div v-if="records.length" class="task-pagination"><span>共 {{ total }} 条</span><button class="outline-button" :disabled="query.pageNum === 1" @click="changePage(query.pageNum - 1)">上一页</button><b>{{ query.pageNum }} / {{ pages }}</b><button class="outline-button" :disabled="query.pageNum === pages" @click="changePage(query.pageNum + 1)">下一页</button></div>
      </section>
    </main>
    <Teleport to="body"><div v-if="mailDialog" class="mail-modal-mask" @click.self="closeMailDialog"><section class="mail-modal"><header><div><h2>发送报告</h2></div><button class="mail-close" aria-label="关闭" :disabled="sendingMail" @click="closeMailDialog">×</button></header><div class="mail-report-card"><div><small>待发送报告</small><h3>{{ selectedReport?.reportTitle }}</h3><p>报告日期：{{ selectedReport?.reportDate }}　·　最终版本：V{{ selectedReport?.versionNo }}</p></div><span>终审完成</span><dl><div><dt>报告编号</dt><dd>#{{ selectedReport?.reportId }}</dd></div><div><dt>最终版本 ID</dt><dd>{{ selectedReport?.versionId }}</dd></div><div><dt>处理方式</dt><dd>记录发送结果，不连接真实邮箱</dd></div><div><dt>完成时间</dt><dd>{{ selectedReport?.completedAt || '-' }}</dd></div></dl></div><div class="mail-recipient-head"><div><h3>接收人员</h3><p>来自收件人通讯录 · 已选择 {{ selectedRecipientCount }} 人</p></div><div class="mail-recipient-actions"><label v-if="recipients.length"><input type="checkbox" :checked="selectedRecipientCount === recipients.length" @change="recipients.forEach(item => item.selected = $event.target.checked)"> 全选</label><button class="outline-button" @click="showRecipientForm = !showRecipientForm">{{ showRecipientForm ? '取消新增' : '+ 新增收件人' }}</button></div></div><form v-if="showRecipientForm" class="recipient-add recipient-add-collapsed" @submit.prevent="addRecipient"><div><input v-model="newRecipient.name" placeholder="收件人姓名"><input v-model="newRecipient.email" type="email" placeholder="收件邮箱"><button type="submit" class="outline-button" :disabled="addingRecipient">{{ addingRecipient ? '保存中…' : '保存' }}</button></div></form><div v-if="loadingRecipients" class="recipient-list">正在加载接收人员…</div><div v-else class="recipient-list"><label v-for="person in recipients" :key="person.id" class="recipient-row recipient-row-refined"><input v-model="person.selected" type="checkbox"><span class="recipient-avatar">{{ person.name.slice(0, 1) }}</span><span><b>{{ person.name }}</b><small>{{ person.email }}</small></span></label></div><p v-if="mailError" class="mail-message error">{{ mailError }}</p><p v-if="mailSuccess" class="mail-message success">{{ mailSuccess }}</p><footer><button class="outline-button" :disabled="sendingMail" @click="closeMailDialog">取消</button><button :disabled="!selectedRecipientCount || sendingMail || loadingRecipients" @click="sendReport">{{ sendingMail ? '正在处理…' : '确认发送' }}</button></footer></section></div></Teleport>
  </div>
</template>
