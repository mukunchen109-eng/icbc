<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const canEdit = computed(() => auth.user?.roleCode === 'INFO_MANAGER')
const isFinalReviewer = computed(() => auth.user?.roleCode === 'DEPT_MANAGER')
const statusLabels = {
  GENERATED: '已生成', INITIAL_PENDING: '待初审', INITIAL_REVIEWING: '初审中',
  INITIAL_REJECTED: '初审已退回', FINAL_PENDING: '待终审',
  FINAL_REVIEWING: '终审中', FINAL_APPROVED: '终审已通过', FINAL_ARCHIVED: '已归档'
}
const statusText = computed(() => statusLabels[reportDetail.value?.status] || reportDetail.value?.status || '待确认')
const selectedTextPreview = computed(() => {
  const text = selectedText.value
  return text.length <= 10 ? text : `${text.slice(0, 5)}…${text.slice(-4)}`
})
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
const pendingOperations = ref([])
const sensitiveWords = ['绝对安全']
const localSensitiveMatches = ref([])
const reviewComment = ref('')
const submitting = ref(false)
const checking = ref(false)
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
let sensitiveHighlightFrame = 0
function refreshSensitiveHighlights() {
  if (!draftEditor.value) return
  const ranges = []
  const matches = []
  const walker = document.createTreeWalker(draftEditor.value, NodeFilter.SHOW_TEXT)
  let node
  while ((node = walker.nextNode())) {
    for (const word of sensitiveWords) {
      let fromIndex = 0
      let index
      while ((index = node.nodeValue.indexOf(word, fromIndex)) >= 0) {
        const range = document.createRange()
        range.setStart(node, index)
        range.setEnd(node, index + word.length)
        ranges.push(range)
        matches.push({ word, occurrence: matches.length + 1 })
        fromIndex = index + word.length
      }
    }
  }
  localSensitiveMatches.value = matches
  if (window.CSS?.highlights && typeof window.Highlight !== 'undefined') {
    CSS.highlights.set('sensitive-word', new Highlight(...ranges))
  }
}
function scheduleSensitiveHighlights() {
  cancelAnimationFrame(sensitiveHighlightFrame)
  sensitiveHighlightFrame = requestAnimationFrame(refreshSensitiveHighlights)
}
function persistDraft() {
  scheduleSensitiveHighlights()
}
function markDirty() { dirty.value = true; saveNotice.value = '' }
function handleDraftInput() {
  persistDraft()
  markDirty()
}
function preventDraftLineBreak(event) {
  if (event.key === 'Enter' || event.inputType === 'insertParagraph' || event.inputType === 'insertLineBreak') {
    event.preventDefault()
  }
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
    if (node.parentElement?.closest('.change-mark,.annotation-mark')) continue
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
    if (record.actionType === 'MARK_MODIFY') {
      wrapStoredText(section, record.selectedText, 'change-mark', `标记修改；${record.createdAt}`)
    } else if (record.actionType === 'ANNOTATE' || record.actionType === 'COMMENT') {
      const annotation = { id: record.id, text: record.selectedText, note: record.commentText, operatorUsername: record.operatorUsername || '-', replies: [], resolved: false }
      annotations.value.push(annotation)
      wrapStoredText(section, record.selectedText, 'annotation-mark', '', record.id)
    }
  }
  persistDraft()
}
function stageOperation(operation) {
  pendingOperations.value.push(operation)
  markDirty()
}
function saveModifyMark() {
  if (!selectedText.value || !selectedArticleSequence.value || !savedSelectionRange) {
    saveNotice.value = '请先在报告草稿中选择需要标记的文字'
    return
  }
  const article = articles.value.find(item => item.sequenceNo === selectedArticleSequence.value)
  if (!article) { saveNotice.value = '未找到所选文字对应的报告条目'; return }
  const text = selectedText.value
  if (!wrapSelection('change-mark', '标记修改；尚未保存')) {
    saveNotice.value = '页面选区已经失效，请重新选择文字'
    return
  }
  stageOperation({
    type: 'MARK_MODIFY',
    articleId: article.id,
    selectedText: text
  })
  saveNotice.value = '标记修改已暂存，保存草稿后写入审核记录'
}
function markModify() { saveModifyMark() }
function openAnnotation() { captureSelection(); if (selectedText.value) showAnnotation.value = true }
async function addAnnotation() {
  if (!annotationText.value.trim() || !selectedText.value) return
  const article = articles.value.find(item => item.sequenceNo === selectedArticleSequence.value)
  if (!article) { saveNotice.value = '未找到批注对应的报告条目'; return }
  const recordId = `temp-${crypto.randomUUID()}`
  const text = selectedText.value
  const commentText = annotationText.value.trim()
  const annotation = { id: recordId, articleId: article.id, text, note: commentText, operatorUsername: auth.user?.username || '-', replies: [], resolved: false }
  if (!wrapSelection('annotation-mark', '', annotation.id)) {
    saveNotice.value = '页面选区已经失效，请重新选择文字'
    return
  }
  stageOperation({
    type: 'COMMENT',
    articleId: article.id,
    selectedText: text,
    commentText
  })
  annotations.value.unshift(annotation)
  annotationText.value = ''; selectedText.value = ''; showAnnotation.value = false
  saveNotice.value = '批注已暂存，保存草稿后写入审核记录'
}
function showAnnotationBubble(event) {
  const target = event.target.closest?.('.annotation-mark')
  if (!target) { hideAnnotationBubble(); return }
  clearTimeout(hoverTimer)
  const annotation = annotations.value.find(item => String(item.id) === target.dataset.annotationId)
  if (!annotation) return
  const rect = target.getBoundingClientRect()
  hoverBubble.value = {
    kind: 'annotation', title: '批注信息',
    text: `${annotation.operatorUsername}：${annotation.note}`,
    left: Math.min(rect.left, window.innerWidth - 310), top: rect.bottom + 8, width: 290
  }
}
function hideAnnotationBubble() { clearTimeout(hoverTimer); hoverTimer = setTimeout(() => { hoverBubble.value = null }, 40) }
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
    const operations = pendingOperations.value.map(({ previewTitle, previewSummary, ...operation }) => operation)
    for (const change of changes) {
      const current = articles.value.find(item => item.sequenceNo === change.sequenceNo)
      const replacement = pendingOperations.value.find(item => item.type === 'REPLACE' && item.articleId === current?.id)
      if (replacement && replacement.previewTitle === change.title && replacement.previewSummary === change.summaryContent) continue
      operations.push({
        type: 'MODIFY', articleId: current.id,
        title: change.title, summaryContent: change.summaryContent,
        reason: '审核工作台人工修改'
      })
    }
    if (!operations.length) {
      dirty.value = false
      saveNotice.value = '没有需要保存的修改'
      return true
    }
    await http.post(`/review-tasks/${selectedReport.value.taskId}/draft`, { operations })
    lastDraftPayload.value = { taskId: selectedReport.value.taskId, operations }
    pendingOperations.value = []
    await loadWorkspace()
    dirty.value = false
    saveNotice.value = `已保存 ${operations.length} 项草稿操作，状态已更新为审核中`
    return true
  } catch (requestError) {
    saveNotice.value = requestError.response?.data?.message || '草稿保存失败'
    return false
  } finally { saving.value = false }
}
async function runCheck() {
  if (dirty.value) {
    saveNotice.value = '请先保存草稿，再重新执行敏感词和数据一致性检测'
    return
  }
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
  if (pendingOperations.value.some(item => item.type === 'RESOLVE_ISSUE' && item.issueId === issue.id)) return
  issue.resolved = 1
  stageOperation({ type: 'RESOLVE_ISSUE', issueId: issue.id })
  saveNotice.value = '问题处理状态已暂存，保存草稿后写入数据库'
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
    const candidate = candidates.find(item => item.newsId === Number(selectedId))
    if (!candidate) { saveNotice.value = '输入的替换资讯ID不在候选列表中'; return }
    const section = draftEditor.value?.querySelector(`.entry[data-sequence="${current.sequenceNo}"]`)
    if (!section) { saveNotice.value = '未找到需要替换的报告条目'; return }
    section.querySelector('h3').innerText = candidate.title
    section.querySelector('p').innerText = candidate.summaryContent || ''
    pendingOperations.value = pendingOperations.value.filter(item =>
      item.articleId !== current.id || !['REPLACE', 'MARK_MODIFY', 'COMMENT'].includes(item.type))
    annotations.value = annotations.value.filter(item =>
      !String(item.id).startsWith('temp-') || item.articleId !== current.id)
    stageOperation({
      type: 'REPLACE', articleId: current.id, newNewsId: candidate.newsId,
      reason: reason.trim(), previewTitle: candidate.title,
      previewSummary: candidate.summaryContent || ''
    })
    persistDraft()
    saveNotice.value = '替换结果已暂存，保存草稿后写入数据库'
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
async function saveAndBack() { const saved = await saveDraft(); if (!saved) return; leaveDialogVisible.value = false; backToTasks() }
function discardAndBack() { dirty.value = false; leaveDialogVisible.value = false; backToTasks() }

onMounted(loadWorkspace)
onUnmounted(() => {
  cancelAnimationFrame(sensitiveHighlightFrame)
  window.CSS?.highlights?.delete('sensitive-word')
})
</script>

<template>
  <div class="review-shell">
    <header class="review-header"><div><strong>金融智讯</strong><span>人机协同审核工作台</span></div><div class="review-user"><span class="role-badge">{{ auth.user?.roleName }}</span><span>{{ auth.user?.username }}</span><button class="text-button" @click="logout">退出</button></div></header>
    <section class="review-context"><div><span class="eyebrow">待审核报告 · 报告 #{{ selectedReport.reportId }}</span><h1>{{ selectedReport.title }}</h1><p>报告日期：{{ selectedReport.date }}　·　状态：{{ statusText }}　·　当前环节：{{ isFinalReviewer ? '终审只读复核' : '初审编辑' }}　·　完整留痕</p></div><div class="context-actions"><span v-if="saveNotice" class="draft-save-notice">{{ saveNotice }}</span><button v-if="canEdit" class="outline-button" :disabled="saving || loading" @click="saveDraft">{{ saving ? '保存中…' : '保存草稿' }}</button><button v-if="!isFinalReviewer" :disabled="submitting || loading" @click="submitInitialReview">{{ submitting ? '提交中…' : '提交终审' }}</button><template v-else><button class="outline-button" :disabled="submitting || loading" @click="returnForRevision">退回</button><button :disabled="submitting || loading" @click="confirmFinalReview">{{ submitting ? '提交中…' : '确认终审' }}</button></template><button class="outline-button" @click="requestBackToTasks">返回列表</button></div></section>
    <div class="review-legend"><span class="legend-sensitive">敏感词</span><span class="legend-data">数据不一致</span><span class="legend-change">修改/批注</span></div>
    <div v-if="loadError" class="task-error"><b>审核数据加载失败</b><span>{{ loadError }}</span><button @click="loadWorkspace">重新加载</button></div>
    <div v-else-if="loading" class="task-empty"><span class="task-spinner"></span><b>正在加载报告与原始资讯…</b></div>
    <main v-else class="review-workspace">
      <article class="document-panel draft-panel"><div class="document-title"><div><h2>报告草稿</h2></div><span class="status-dot">{{ isFinalReviewer ? '终审中' : '初审中' }}</span></div>
        <div v-if="canEdit" class="review-toolbar"><button class="tool-button" @mousedown.prevent @click="markModify">标记修改</button><button class="tool-button" @mousedown.prevent @click="openAnnotation">添加批注</button><button class="tool-button" @click="replaceSelectedArticle">替换资讯</button><button class="tool-button" :disabled="checking" @click="runCheck">{{ checking ? '检测中…' : '重新检测' }}</button></div>
        <div ref="draftEditor" class="draft-editor" :contenteditable="canEdit" @keydown="preventDraftLineBreak" @beforeinput="preventDraftLineBreak" @input="handleDraftInput" @mouseup="captureSelection" @click="focusSourceForDraftEntry" @mouseover="showAnnotationBubble" @mouseout="hideAnnotationBubble" v-html="draft"></div>
        <div v-if="showAnnotation" class="annotation-composer"><span :title="selectedText">添加批注：{{ selectedTextPreview }}</span><input v-model="annotationText" placeholder="输入批注内容"><button @click="addAnnotation">添加</button></div>
        <div class="issue-summary"><div><b>敏感内容</b><span v-for="item in localSensitiveMatches" :key="`${item.word}-${item.occurrence}`" class="sensitive-chip" title="草稿中检测到的敏感词">{{ item.word }}</span><span v-if="!localSensitiveMatches.length">未发现敏感词</span></div><div><b>数据核验</b><button v-for="item in issues.filter(issue => issue.issueType !== 'SENSITIVE_CONTENT' && !issue.resolved)" :key="item.id" class="data-chip" :disabled="!canEdit" :title="canEdit ? `${item.message}；点击标记处理` : item.message" @click="resolveIssueItem(item)">{{ item.issueType }}：{{ item.matchedText }}</button></div></div>
      </article>
      <article class="document-panel source-panel"><div class="document-title"><div><h2>原始资讯全文</h2></div><span class="source-count">{{ sources.length }} 条资讯</span></div>
        <div ref="sourcePanel" class="source-content"><section v-for="source in sources" :key="source.newsId" :data-daily-seq="source.dailySeq"><div class="source-meta"><b>{{ String(source.dailySeq).padStart(2, '0') }}</b><span>{{ source.industry || '资讯' }} · {{ source.newsDate }}</span></div><h3>{{ source.title }}</h3><p>{{ source.originalContent || source.content }}</p></section><section v-if="!sources.length"><p>报告日期当天暂无原始资讯。</p></section></div>
      </article>
    </main>
    <div v-if="hoverBubble" class="annotation-bubble" :style="{ left: `${hoverBubble.left}px`, top: `${hoverBubble.top}px`, width: `${hoverBubble.width}px` }"><b>{{ hoverBubble.title }}</b><p>{{ hoverBubble.text }}</p></div>
    <footer class="review-footer"><label>{{ isFinalReviewer ? '部门负责人审查意见' : '部门负责人审查意见（只读）' }}<input v-if="isFinalReviewer" v-model="reviewComment" placeholder="填写终审意见或退回原因（选填）"><input v-else :value="reportDetail?.departmentReviewComment || '暂无部门负责人审查意见'" readonly></label><button v-if="isFinalReviewer" class="danger-button" :disabled="submitting" @click="returnForRevision">退回修改</button></footer>
    <Teleport to="body"><div v-if="leaveDialogVisible" class="review-leave-mask" @click.self="leaveDialogVisible = false"><section class="review-leave-dialog" role="dialog" aria-modal="true" aria-labelledby="leave-dialog-title"><h3 id="leave-dialog-title">尚未保存草稿</h3><p>当前报告有未保存的修改，返回列表前是否保存草稿？</p><div class="review-leave-actions"><button class="outline-button" @click="leaveDialogVisible = false">取消</button><button class="outline-button" @click="discardAndBack">不保存返回</button><button :disabled="saving" @click="saveAndBack">{{ saving ? '保存中…' : '保存并返回' }}</button></div></section></div></Teleport>
  </div>
</template>
