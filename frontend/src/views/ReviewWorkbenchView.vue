<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const canEdit = computed(() => auth.user?.roleCode === 'INFO_MANAGER')
const isFinalReviewer = computed(() => auth.user?.roleCode === 'DEPT_MANAGER')
const selectedReport = computed(() => ({
  taskId: route.params.taskId,
  reportId: Number(route.query.reportId),
  title: reportDetail.value?.reportTitle || route.query.title || '每日资讯摘要',
  date: reportDetail.value?.reportDate || route.query.date || '待确认',
  status: reportDetail.value?.status || ''
}))
const loading = ref(true)
const loadError = ref('')
const reportDetail = ref(null)
const articles = ref([])
const sources = ref([])
const issues = ref([])
const reviewRecords = ref([])
const reviewComment = ref('')
const submitting = ref(false)
const checking = ref(false)
const changeInfo = computed(() => `操作人：${auth.user?.username || '-'}；操作时间：${new Date().toLocaleString('zh-CN', { hour12: false })}`)
const draft = ref('')
const annotationText = ref('')
const selectedText = ref('')
const annotations = ref([])
const showAnnotation = ref(false)
const draftEditor = ref(null)
const sourcePanel = ref(null)
const hoverBubble = ref(null)
const dirty = ref(false)
const saving = ref(false)
const saveNotice = ref('')
const leaveDialogVisible = ref(false)
const lastDraftPayload = ref(null)
const selectedArticleSequence = ref(null)
let hoverTimer
let savedSelectionRange = null

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}
function renderDraft() {
  draft.value = articles.value.map(item => `<section class="entry" data-sequence="${item.sequenceNo}"><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.summaryContent)}</p></section>`).join('')
}
async function loadWorkspace() {
  loading.value = true
  loadError.value = ''
  let workspaceLoaded = false
  try {
    if (!selectedReport.value.reportId) throw new Error('缺少报告编号')
    const detailResponse = await http.get(`/reports/${selectedReport.value.reportId}/review-detail`)
    if (detailResponse.data?.code !== 200) throw new Error(detailResponse.data?.message || '报告详情查询失败')
    reportDetail.value = detailResponse.data.data
    const [articleResponse, issueResponse, recordResponse] = await Promise.all([
      http.get(`/reports/${selectedReport.value.reportId}/articles`),
      http.get(`/reports/${selectedReport.value.reportId}/issues`),
      http.get(`/review-tasks/${selectedReport.value.taskId}/records`)
    ])
    articles.value = articleResponse.data?.data || []
    issues.value = issueResponse.data?.data || []
    reviewRecords.value = recordResponse.data?.data || []
    const sourceResponse = await http.get(`/reports/${selectedReport.value.reportId}/sources`)
    sources.value = sourceResponse.data?.data || []
    renderDraft()
    workspaceLoaded = true
  } catch (requestError) {
    loadError.value = requestError.response?.data?.message || requestError.message || '审核工作台加载失败'
  } finally {
    loading.value = false
  }
  if (workspaceLoaded) {
    await nextTick()
    applyReviewRecords()
  }
}

function focusSourceForDraftEntry(event) {
  const section = event.target.closest?.('.entry')
  if (!section || !sourcePanel.value) return
  const article = articles.value.find(item => item.sequenceNo === Number(section.dataset.sequence))
  const source = sources.value.find(item => item.articleId === article?.id || item.newsId === article?.newsId)
  if (!source) return
  const target = sourcePanel.value.querySelector(`[data-daily-seq="${source.dailySeq}"]`)
  if (!target) return
  sourcePanel.value.scrollTo({ top: target.offsetTop - sourcePanel.value.offsetTop, behavior: 'smooth' })
  target.classList.add('source-focused')
  window.setTimeout(() => target.classList.remove('source-focused'), 900)
}
function persistDraft() { draft.value = draftEditor.value?.innerHTML || draft.value }
function markDirty() { dirty.value = true; saveNotice.value = '' }
function handleDraftInput() {
  const node = window.getSelection()?.anchorNode
  const element = node?.nodeType === Node.TEXT_NODE ? node.parentElement : node
  const paragraph = element?.closest?.('.entry p')
  if (paragraph && !paragraph.dataset.changed) {
    paragraph.dataset.changed = 'true'
    paragraph.classList.add('change-mark')
    paragraph.dataset.tooltip = changeInfo.value
  }
  persistDraft()
  markDirty()
}
function captureSelection() {
  const selection = window.getSelection()
  selectedText.value = selection?.toString().trim() || ''
  savedSelectionRange = selection?.rangeCount && !selection.isCollapsed
    ? selection.getRangeAt(0).cloneRange() : null
  const node = selection?.anchorNode
  const element = node?.nodeType === Node.TEXT_NODE ? node.parentElement : node
  selectedArticleSequence.value = Number(element?.closest?.('.entry')?.dataset.sequence) || null
}
function wrapSelection(className, tooltip, annotationId = null) {
  const range = savedSelectionRange?.cloneRange()
  if (!range || !draftEditor.value?.contains(range.commonAncestorContainer)) return false
  const marker = document.createElement('span')
  marker.className = className
  if (tooltip) marker.dataset.tooltip = tooltip
  if (annotationId) marker.dataset.annotationId = annotationId
  try { range.surroundContents(marker) } catch { return false }
  persistDraft()
  markDirty()
  return true
}
function wrapStoredText(section, text, className, tooltip, annotationId = null) {
  if (!section || !text) return false
  const walker = document.createTreeWalker(section, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    if (node.parentElement?.closest('.manual-red,.change-mark,.annotation-mark')) continue
    const index = node.nodeValue.indexOf(text)
    if (index < 0) continue
    const range = document.createRange()
    range.setStart(node, index); range.setEnd(node, index + text.length)
    const marker = document.createElement('span')
    marker.className = className
    if (tooltip) marker.dataset.tooltip = tooltip
    if (annotationId) marker.dataset.annotationId = annotationId
    range.surroundContents(marker)
    return true
  }
  return false
}
function applyReviewRecords() {
  annotations.value = []
  for (const record of reviewRecords.value) {
    const section = draftEditor.value?.querySelector(`.entry[data-sequence="${record.sequenceNo}"]`)
    if (record.actionType === 'MARK_RED') {
      wrapStoredText(section, record.selectedText, 'manual-red', `人工标红；${record.createdAt}`)
    } else if (record.actionType === 'MARK_MODIFY') {
      wrapStoredText(section, record.selectedText, 'change-mark', `标记修改；${record.createdAt}`)
    } else if (record.actionType === 'ANNOTATE' || record.actionType === 'COMMENT') {
      const annotation = { id: record.id, text: record.selectedText, note: record.commentText, replies: [], resolved: false }
      annotations.value.push(annotation)
      wrapStoredText(section, record.selectedText, 'annotation-mark', '', record.id)
    }
  }
  persistDraft()
}
async function saveMark(markType, className, label) {
  if (!selectedText.value || !selectedArticleSequence.value || !savedSelectionRange) {
    saveNotice.value = '请先在报告草稿中选择需要标记的文字'
    return
  }
  const article = articles.value.find(item => item.sequenceNo === selectedArticleSequence.value)
  if (!article) { saveNotice.value = '未找到所选文字对应的报告条目'; return }
  try {
    const { data } = await http.post(`/review-tasks/${selectedReport.value.taskId}/marks`, {
      articleId: article.id, markType, selectedText: selectedText.value
    })
    if (!wrapSelection(className, `${label}；${changeInfo.value}`, data.data?.recordId)) {
      saveNotice.value = '标记已留痕，但页面选区已经失效，请刷新查看'
    } else saveNotice.value = `${label}已保存到审核记录`
  } catch (requestError) { saveNotice.value = requestError.response?.data?.message || `${label}失败` }
}
function markRed() { saveMark('RED', 'manual-red', '标红') }
function markModify() { saveMark('MODIFY', 'change-mark', '标记修改') }
function openAnnotation() { captureSelection(); if (selectedText.value) showAnnotation.value = true }
async function addAnnotation() {
  if (!annotationText.value.trim() || !selectedText.value) return
  const article = articles.value.find(item => item.sequenceNo === selectedArticleSequence.value)
  if (!article) { saveNotice.value = '未找到批注对应的报告条目'; return }
  let recordId
  try {
    const { data } = await http.post(`/review-tasks/${selectedReport.value.taskId}/comments`, {
      articleId: article.id, selectedText: selectedText.value,
      commentText: annotationText.value.trim()
    })
    recordId = data.data?.recordId
  } catch (requestError) {
    saveNotice.value = requestError.response?.data?.message || '批注保存失败'
    return
  }
  const annotation = { id: recordId, text: selectedText.value, note: annotationText.value, replies: [], resolved: false }
  wrapSelection('annotation-mark', '', annotation.id)
  annotations.value.unshift(annotation)
  annotationText.value = ''; selectedText.value = ''; showAnnotation.value = false
}
function showAnnotationBubble(event) {
  const target = event.target.closest?.('.annotation-mark')
  if (!target) return
  clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => {
    const item = annotations.value.find(annotation => String(annotation.id) === target.dataset.annotationId)
    if (!item) return
    const rect = target.getBoundingClientRect()
    hoverBubble.value = { item, left: Math.min(rect.left, window.innerWidth - 310), top: rect.bottom + 8 }
  }, 200)
}
function hideAnnotationBubble() { hoverTimer = setTimeout(() => { hoverBubble.value = null }, 120) }
function keepAnnotationBubble() { clearTimeout(hoverTimer) }
function reply(annotation) { const content = window.prompt('输入回复内容'); if (content?.trim()) { annotation.replies.push(content.trim()); markDirty() } }
function resolve(annotation) { annotation.resolved = !annotation.resolved; markDirty() }
async function saveDraft() {
  persistDraft()
  saving.value = true
  saveNotice.value = ''
  try {
    const sections = [...(draftEditor.value?.querySelectorAll('.entry') || [])]
    const changes = sections.map(section => ({
      sequenceNo: Number(section.dataset.sequence),
      title: section.querySelector('h3')?.innerText.trim() || '',
      summaryContent: section.querySelector('p')?.innerText.trim() || ''
    })).filter(change => {
      const source = articles.value.find(item => item.sequenceNo === change.sequenceNo)
      return source && (source.title !== change.title || source.summaryContent !== change.summaryContent)
    })
    for (const change of changes) {
      const current = articles.value.find(item => item.sequenceNo === change.sequenceNo)
      await http.put(`/review-tasks/${selectedReport.value.taskId}/articles/${current.id}`, {
        title: change.title, summaryContent: change.summaryContent,
        reason: '审核工作台人工修改'
      })
      const refreshed = await http.get(`/reports/${selectedReport.value.reportId}/articles`)
      articles.value = refreshed.data?.data || []
    }
    lastDraftPayload.value = { taskId: selectedReport.value.taskId, changes }
    if (changes.length) await loadWorkspace()
    else {
      renderDraft()
      await nextTick()
      applyReviewRecords()
    }
    dirty.value = false
    saveNotice.value = changes.length ? `已保存 ${changes.length} 条修改，状态已更新为审核中` : '没有需要保存的修改'
    return true
  } catch (requestError) {
    saveNotice.value = requestError.response?.data?.message || '草稿保存失败'
    return false
  } finally { saving.value = false }
}
async function runCheck() {
  checking.value = true
  try {
    await http.post(`/reports/${selectedReport.value.reportId}/check`)
    const { data } = await http.get(`/reports/${selectedReport.value.reportId}/issues`)
    issues.value = data?.data || []
    saveNotice.value = `检测完成，发现 ${issues.value.length} 项问题`
  } catch (requestError) {
    saveNotice.value = requestError.response?.data?.message || '检测失败'
  } finally { checking.value = false }
}
async function resolveIssueItem(issue) {
  try {
    await http.put(`/review-issues/${issue.id}/resolve`)
    issue.resolved = 1
    saveNotice.value = '问题已标记为已处理'
  } catch (requestError) { saveNotice.value = requestError.response?.data?.message || '问题处理失败' }
}
async function replaceSelectedArticle() {
  const current = articles.value.find(item => item.sequenceNo === selectedArticleSequence.value)
  if (!current) { saveNotice.value = '请先在需要替换的报告条目中选择文字'; return }
  try {
    const { data } = await http.get(`/review-tasks/${selectedReport.value.taskId}/replacement-articles`, {
      params: { category: current.category || '' }
    })
    const candidates = data?.data || []
    if (!candidates.length) { saveNotice.value = '没有可替换的资讯'; return }
    const message = candidates.slice(0, 10).map(item => `${item.newsId}：${item.title}`).join('\n')
    const selectedId = window.prompt(`输入替换资讯ID：\n${message}`)
    if (!selectedId) return
    const reason = window.prompt('填写替换原因')
    if (!reason?.trim()) return
    const response = await http.post(`/review-tasks/${selectedReport.value.taskId}/articles/${current.id}/replace`, {
      newNewsId: Number(selectedId), reason: reason.trim()
    })
    await loadWorkspace()
    saveNotice.value = '资讯已替换，状态已更新为审核中'
  } catch (requestError) { saveNotice.value = requestError.response?.data?.message || '替换资讯失败' }
}
async function submitDecision(decision) {
  if (dirty.value && canEdit.value) {
    const saved = await saveDraft()
    if (!saved) return
  }
  submitting.value = true
  try {
    const { data } = await http.post(`/review-tasks/${selectedReport.value.taskId}/submit`, {
      decision, comment: reviewComment.value
    })
    if (data?.code !== 200) throw new Error(data?.message || '审核提交失败')
    window.alert(decision === 'APPROVE'
      ? (isFinalReviewer.value ? '终审已通过，报告进入待发送状态' : '初审已通过，已生成终审任务')
      : '报告已退回')
    backToTasks()
  } catch (requestError) {
    saveNotice.value = requestError.response?.data?.message || requestError.message || '审核提交失败'
  } finally { submitting.value = false }
}
function submitInitialReview() { submitDecision('APPROVE') }
function confirmFinalReview() { submitDecision('APPROVE') }
function returnForRevision() { submitDecision('REJECT') }
function logout() { auth.logout(); router.push('/login') }
function backToTasks() { router.push('/review-tasks') }
function requestBackToTasks() { if (canEdit.value && dirty.value) leaveDialogVisible.value = true; else backToTasks() }
async function saveAndBack() { await saveDraft(); leaveDialogVisible.value = false; backToTasks() }
function discardAndBack() { dirty.value = false; leaveDialogVisible.value = false; backToTasks() }

onMounted(loadWorkspace)
</script>

<template>
  <div class="review-shell">
    <header class="review-header"><div><strong>金融智讯</strong><span>人机协同审核工作台</span></div><div class="review-user"><span class="role-badge">{{ auth.user?.roleName }}</span><span>{{ auth.user?.username }}</span><button class="text-button" @click="logout">退出</button></div></header>
    <section class="review-context"><div><span class="eyebrow">待审核报告 · 报告 #{{ selectedReport.reportId }}</span><h1>{{ selectedReport.title }}</h1><p>报告日期：{{ selectedReport.date }}　·　状态：{{ reportDetail?.status }}　·　当前环节：{{ isFinalReviewer ? '终审只读复核' : '初审编辑' }}　·　完整留痕</p></div><div class="context-actions"><span v-if="saveNotice" class="draft-save-notice">{{ saveNotice }}</span><button v-if="canEdit" class="outline-button" :disabled="saving || loading" @click="saveDraft">{{ saving ? '保存中…' : '保存草稿' }}</button><button v-if="!isFinalReviewer" :disabled="submitting || loading" @click="submitInitialReview">{{ submitting ? '提交中…' : '提交终审' }}</button><template v-else><button class="outline-button" :disabled="submitting || loading" @click="returnForRevision">退回</button><button :disabled="submitting || loading" @click="confirmFinalReview">{{ submitting ? '提交中…' : '确认终审' }}</button></template><button class="outline-button" @click="requestBackToTasks">返回列表</button></div></section>
    <div class="review-legend"><span class="legend-sensitive">敏感词</span><span class="legend-data">数据不一致</span><span class="legend-change">修改/批注</span></div>
    <div v-if="loadError" class="task-error"><b>审核数据加载失败</b><span>{{ loadError }}</span><button @click="loadWorkspace">重新加载</button></div>
    <div v-else-if="loading" class="task-empty"><span class="task-spinner"></span><b>正在加载报告与原始资讯…</b></div>
    <main v-else class="review-workspace">
      <article class="document-panel draft-panel"><div class="document-title"><div><h2>报告草稿</h2></div><span class="status-dot">{{ isFinalReviewer ? '终审中' : '初审中' }}</span></div>
        <div v-if="canEdit" class="review-toolbar"><button class="tool-button red-tool" @mousedown.prevent @click="markRed">标红</button><button class="tool-button" @mousedown.prevent @click="markModify">标记修改</button><button class="tool-button" @mousedown.prevent @click="openAnnotation">添加批注</button><button class="tool-button" @click="replaceSelectedArticle">替换资讯</button><button class="tool-button" :disabled="checking" @click="runCheck">{{ checking ? '检测中…' : '重新检测' }}</button></div>
        <div ref="draftEditor" class="draft-editor" :contenteditable="canEdit" @input="handleDraftInput" @mouseup="captureSelection" @click="focusSourceForDraftEntry" @mouseover="showAnnotationBubble" @mouseleave="hideAnnotationBubble" v-html="draft"></div>
        <div v-if="showAnnotation" class="annotation-composer"><span>批注对象：{{ selectedText }}</span><input v-model="annotationText" placeholder="输入批注内容"><button @click="addAnnotation">添加</button></div>
        <div class="issue-summary"><div><b>敏感内容</b><button v-for="item in issues.filter(issue => issue.issueType === 'SENSITIVE_CONTENT' && !issue.resolved)" :key="item.id" class="sensitive-chip" :disabled="!canEdit" :title="canEdit ? `${item.message}；点击标记处理` : item.message" @click="resolveIssueItem(item)">{{ item.matchedText }}</button></div><div><b>数据核验</b><button v-for="item in issues.filter(issue => issue.issueType !== 'SENSITIVE_CONTENT' && !issue.resolved)" :key="item.id" class="data-chip" :disabled="!canEdit" :title="canEdit ? `${item.message}；点击标记处理` : item.message" @click="resolveIssueItem(item)">{{ item.issueType }}：{{ item.matchedText }}</button></div></div>
      </article>
      <article class="document-panel source-panel"><div class="document-title"><div><h2>原始资讯全文</h2></div><span class="source-count">{{ sources.length }} 条资讯</span></div>
        <div ref="sourcePanel" class="source-content"><section v-for="source in sources" :key="source.newsId" :data-daily-seq="source.dailySeq"><div class="source-meta"><b>{{ String(source.dailySeq).padStart(2, '0') }}</b><span>{{ source.industry || '资讯' }} · {{ source.newsDate }}</span></div><h3>{{ source.title }}</h3><p>{{ source.originalContent || source.content }}</p></section><section v-if="!sources.length"><p>报告日期当天暂无原始资讯。</p></section></div>
      </article>
    </main>
    <div v-if="hoverBubble" class="annotation-bubble" :style="{ left: `${hoverBubble.left}px`, top: `${hoverBubble.top}px` }" @mouseenter="keepAnnotationBubble" @mouseleave="hideAnnotationBubble"><b>批注</b><p>{{ hoverBubble.item.note }}</p><small>{{ hoverBubble.item.replies.length ? `已有 ${hoverBubble.item.replies.length} 条回复` : '暂无回复' }}</small><div v-if="canEdit"><button @click="reply(hoverBubble.item)">回复</button><button @click="resolve(hoverBubble.item)">{{ hoverBubble.item.resolved ? '取消解决' : '解决' }}</button></div></div>
    <footer class="review-footer"><label>{{ isFinalReviewer ? '部门负责人审查意见' : '部门负责人审查意见（只读）' }}<input v-if="isFinalReviewer" v-model="reviewComment" placeholder="填写终审意见或退回原因（选填）"><input v-else :value="reportDetail?.departmentReviewComment || '暂无部门负责人审查意见'" readonly></label><button v-if="isFinalReviewer" class="danger-button" :disabled="submitting" @click="returnForRevision">退回修改</button></footer>
    <Teleport to="body"><div v-if="leaveDialogVisible" class="review-leave-mask" @click.self="leaveDialogVisible = false"><section class="review-leave-dialog" role="dialog" aria-modal="true" aria-labelledby="leave-dialog-title"><h3 id="leave-dialog-title">尚未保存草稿</h3><p>当前报告有未保存的修改，返回列表前是否保存草稿？</p><div class="review-leave-actions"><button class="outline-button" @click="leaveDialogVisible = false">取消</button><button class="outline-button" @click="discardAndBack">不保存返回</button><button :disabled="saving" @click="saveAndBack">{{ saving ? '保存中…' : '保存并返回' }}</button></div></section></div></Teleport>
  </div>
</template>
