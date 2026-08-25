<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const canEdit = computed(() => auth.user?.roleCode === 'INFO_MANAGER')
const isFinalReviewer = computed(() => auth.user?.roleCode === 'DEPT_MANAGER')
const sensitiveWords = ['绝对安全', '保证收益', '内幕消息', '暴涨', '稳赚不赔', '重大利好']
const dataChecks = [
  { field: '成交额环比', draft: '7.2%', source: '6.8%', detail: '草稿“成交额环比”为 7.2%，原始资讯为 6.8%。' },
  { field: '发布日期', draft: '8 月 24 日', source: '8 月 23 日', detail: '草稿引用日期与原始资讯发布日期不一致。' }
]
const changeInfo = '修改人：资讯管理员 info01；修改时间：2026-08-24 16:25'
const draft = ref(`<section class="entry"><h3>01　货币市场与流动性</h3><p>【每日金融】央行公开市场操作保持流动性合理充裕，银行间市场资金面平稳。<span class="annotation-mark" data-annotation-id="1">市场参与者应持续关注</span>资金价格变化。</p></section><section class="entry"><h3>02　金融板块表现</h3><p>【每日金融】金融板块交投活跃，成交额较上一交易日增长<span class="data-alert" data-tooltip="成交额环比：草稿 7.2%，原文 6.8%">7.2%</span>。机构普遍认为后续市场<span class="sensitive-alert" data-tooltip="敏感词：绝对安全">绝对安全</span>。</p></section><section class="entry"><h3>03　宏观经济观察</h3><p>【每日经济】宏观指标显示经济修复态势延续，市场持续关注需求恢复、行业景气度与外部环境变化。</p></section><section class="entry"><h3>04　政策动态</h3><p>【政策参考】有关部门发布重点领域政策解读，明确支持方向并要求相关单位结合实际做好落实。发布日期为<span class="data-alert" data-tooltip="发布日期：草稿 8 月 24 日，原文 8 月 23 日">8 月 24 日</span>。</p></section><section class="entry"><h3>05　风险提示</h3><p>【风险提示】需关注外围市场波动、重点行业估值变化以及突发事件对市场情绪的影响，避免使用<span class="sensitive-alert" data-tooltip="敏感词：重大利好">重大利好</span>等表述。</p></section><section class="entry"><h3>06　编辑结论</h3><p>综合各项资讯，建议按照审校意见调整数据和表述，并在终审确认后向指定范围分发。</p></section>`)
const annotationText = ref('')
const selectedText = ref('')
const annotations = ref([{ id: 1, text: '市场参与者应持续关注', note: '建议补充关注的具体指标。', replies: [], resolved: false }])
const showAnnotation = ref(false)
const draftEditor = ref(null)
const sourcePanel = ref(null)
const syncLock = ref(false)
const hoverBubble = ref(null)
let hoverTimer

function syncScroll(side) {
  if (syncLock.value) return
  syncLock.value = true
  const from = side === 'left' ? draftEditor.value : sourcePanel.value
  const to = side === 'left' ? sourcePanel.value : draftEditor.value
  if (from && to) to.scrollTop = from.scrollTop
  requestAnimationFrame(() => { syncLock.value = false })
}
function persistDraft() { draft.value = draftEditor.value?.innerHTML || draft.value }
function handleDraftInput() {
  const node = window.getSelection()?.anchorNode
  const element = node?.nodeType === Node.TEXT_NODE ? node.parentElement : node
  const paragraph = element?.closest?.('.entry p')
  if (paragraph && !paragraph.dataset.changed) {
    paragraph.dataset.changed = 'true'
    paragraph.classList.add('change-mark')
    paragraph.dataset.tooltip = changeInfo
  }
  persistDraft()
}
function captureSelection() { selectedText.value = window.getSelection()?.toString().trim() || '' }
function wrapSelection(className, tooltip, annotationId = null) {
  const selection = window.getSelection()
  if (!selection?.rangeCount || selection.isCollapsed) return false
  const range = selection.getRangeAt(0)
  const marker = document.createElement('span')
  marker.className = className
  marker.dataset.tooltip = tooltip
  if (annotationId) marker.dataset.annotationId = annotationId
  try { range.surroundContents(marker) } catch { return false }
  selection.removeAllRanges(); selection.addRange(range)
  persistDraft()
  return true
}
function markRed() { if (wrapSelection('manual-red', `人工标红；${changeInfo}`)) captureSelection() }
function markModify() { if (wrapSelection('change-mark', changeInfo)) captureSelection() }
function openAnnotation() { captureSelection(); if (selectedText.value) showAnnotation.value = true }
function addAnnotation() {
  if (!annotationText.value.trim() || !selectedText.value) return
  const annotation = { id: Date.now(), text: selectedText.value, note: annotationText.value, replies: [], resolved: false }
  wrapSelection('annotation-mark', `批注：${annotationText.value}`, annotation.id)
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
function reply(annotation) { const content = window.prompt('输入回复内容'); if (content?.trim()) annotation.replies.push(content.trim()) }
function resolve(annotation) { annotation.resolved = !annotation.resolved }
function submitInitialReview() { alert('已提交终审（演示）') }
function confirmFinalReview() { alert('已确认终审通过（演示）') }
function returnForRevision() { alert('已退回修改（演示）') }
function logout() { auth.logout(); router.push('/login') }
</script>

<template>
  <div class="review-shell">
    <header class="review-header"><div><strong>金融智讯</strong><span>人机协同审核工作台</span></div><div class="review-user"><span class="role-badge">{{ auth.user?.roleName }}</span><span>{{ auth.user?.username }}</span><button class="text-button" @click="logout">退出</button></div></header>
    <section class="review-context"><div><span class="eyebrow">待审核报告</span><h1>每日资讯摘要 · 2026-08-24</h1><p>报告编号：FI-20260824-001　·　当前环节：{{ isFinalReviewer ? '终审' : '初审' }}　·　{{ canEdit ? '可编辑并留痕' : '只读查看修改痕迹与批注' }}</p></div><div class="context-actions"><button v-if="canEdit" class="outline-button" @click="persistDraft">保存草稿</button><button v-if="canEdit" @click="submitInitialReview">提交终审</button><template v-else><button class="outline-button" @click="returnForRevision">退回</button><button @click="confirmFinalReview">确认终审</button></template></div></section>
    <div class="review-legend"><span class="legend-sensitive">敏感词</span><span class="legend-data">数据不一致</span><span class="legend-change">修改/批注</span></div>
    <main class="review-workspace">
      <article class="document-panel draft-panel"><div class="document-title"><div><h2>报告草稿</h2></div><span class="status-dot">{{ canEdit ? '初审中' : '待终审' }}</span></div>
        <div v-if="canEdit" class="review-toolbar"><button class="tool-button red-tool" @mousedown.prevent @click="markRed">标红</button><button class="tool-button" @mousedown.prevent @click="markModify">标记修改</button><button class="tool-button" @mousedown.prevent @click="openAnnotation">添加批注</button></div>
        <div ref="draftEditor" class="draft-editor" :contenteditable="canEdit" @input="handleDraftInput" @mouseup="captureSelection" @scroll="syncScroll('left')" @mouseover="showAnnotationBubble" @mouseleave="hideAnnotationBubble" v-html="draft"></div>
        <div v-if="showAnnotation" class="annotation-composer"><span>批注对象：{{ selectedText }}</span><input v-model="annotationText" placeholder="输入批注内容"><button @click="addAnnotation">添加</button></div>
        <div class="issue-summary"><div><b>敏感词库</b><span v-for="word in sensitiveWords" :key="word" class="sensitive-chip">{{ word }}</span></div><div><b>数据核验</b><span v-for="item in dataChecks" :key="item.field" class="data-chip" :title="item.detail">{{ item.field }}：{{ item.draft }} / {{ item.source }}</span></div></div>
      </article>
      <article class="document-panel source-panel"><div class="document-title"><div><h2>原始资讯全文</h2></div><span class="source-count">6 条资讯</span></div>
        <div ref="sourcePanel" class="source-content" @scroll="syncScroll('right')">
          <section><div class="source-meta"><b>01</b><span>每日金融 · 09:12</span></div><h3>央行开展公开市场操作，维护流动性合理充裕</h3><p>为维护月末流动性合理充裕，中国人民银行以利率招标方式开展公开市场操作。市场资金面总体平稳，机构对后续政策节奏保持关注。</p></section>
          <section><div class="source-meta"><b>02</b><span>每日金融 · 10:35</span></div><h3>金融板块交投活跃，市场情绪保持谨慎</h3><p>盘面显示金融相关板块成交有所增加，成交额较上一交易日增长<span class="source-data" data-tooltip="成交额环比：草稿 7.2%，原文 6.8%">6.8%</span>。投资者仍重点关注宏观数据、政策预期以及外围市场变化。</p></section>
          <section><div class="source-meta"><b>03</b><span>每日经济 · 14:20</span></div><h3>宏观指标显示经济修复态势延续</h3><p>最新发布的相关指标反映经济运行总体平稳。分析人士表示，后续仍需关注需求恢复、行业景气度与外部环境变化。</p></section>
          <section><div class="source-meta"><b>04</b><span>政策参考 · 16:10</span></div><h3>有关部门发布重点领域政策解读</h3><p>政策文件于<span class="source-data" data-tooltip="发布日期：草稿 8 月 24 日，原文 8 月 23 日">8 月 23 日</span>发布，明确了重点领域的支持方向，并要求各相关单位结合实际做好落实。</p></section>
          <section><div class="source-meta"><b>05</b><span>风险监测 · 16:35</span></div><h3>市场风险提示及舆情观察</h3><p>有关市场传言包含“<span class="sensitive-alert" data-tooltip="敏感词：重大利好">重大利好</span>”“<span class="sensitive-alert" data-tooltip="敏感词：保证收益">保证收益</span>”等表述，需避免在正式报告中直接引用。</p></section>
          <section><div class="source-meta"><b>06</b><span>编辑说明 · 17:00</span></div><h3>资讯编辑与分发要求</h3><p>各条资讯需经事实核验后进入报告，引用数据、日期与机构名称应与原文保持一致，并记录所有审核修改。</p></section>
        </div>
      </article>
    </main>
    <div v-if="hoverBubble" class="annotation-bubble" :style="{ left: `${hoverBubble.left}px`, top: `${hoverBubble.top}px` }" @mouseenter="keepAnnotationBubble" @mouseleave="hideAnnotationBubble"><b>批注</b><p>{{ hoverBubble.item.note }}</p><small>{{ hoverBubble.item.replies.length ? `已有 ${hoverBubble.item.replies.length} 条回复` : '暂无回复' }}</small><div><button @click="reply(hoverBubble.item)">回复</button><button @click="resolve(hoverBubble.item)">{{ hoverBubble.item.resolved ? '取消解决' : '解决' }}</button></div></div>
    <footer v-if="isFinalReviewer" class="review-footer"><label>审核意见<input placeholder="填写修改意见或审核说明（选填）"></label><button class="danger-button" @click="returnForRevision">退回修改</button></footer>
  </div>
</template>
