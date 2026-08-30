<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import http from "../api/http";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const canEdit = computed(() =>
  ["INFO_MANAGER", "DEPT_MANAGER"].includes(auth.user?.roleCode),
);
const isFinalReviewer = computed(() => auth.user?.roleCode === "DEPT_MANAGER");
const statusLabels = {
  GENERATED: "已生成",
  INITIAL_PENDING: "待初审",
  INITIAL_REVIEWING: "初审中",
  INITIAL_REJECTED: "初审已退回",
  FINAL_PENDING: "待终审",
  FINAL_REVIEWING: "终审中",
  FINAL_APPROVED: "终审已通过",
  FINAL_ARCHIVED: "已归档",
};
const categoryLabels = {
  FINANCE: "金融领域",
  MACRO: "宏观经济",
  BEIJING_POLICY: "首都政策",
};
function categoryLabel(category) {
  return categoryLabels[category] || category || "未分类";
}
const statusText = computed(
  () =>
    statusLabels[reportDetail.value?.status] ||
    reportDetail.value?.status ||
    "待确认",
);
const selectedTextPreview = computed(() => {
  const text = selectedText.value;
  return text.length <= 10 ? text : `${text.slice(0, 5)}…${text.slice(-4)}`;
});
const selectedReport = computed(() => ({
  taskId: route.params.taskId,
  reportId: Number(route.query.reportId),
  title: reportDetail.value?.reportTitle || route.query.title || "每日资讯摘要",
  date: reportDetail.value?.reportDate || route.query.date || "待确认",
  status: reportDetail.value?.status || "",
}));
const loading = ref(true);
const loadError = ref("");
const reportDetail = ref(null);
const articles = ref([]);
const sources = ref([]);
const issues = ref([]);
const reviewRecords = ref([]);
const pendingOperations = ref([]);
const sensitiveWords = ["绝对安全"];
const localSensitiveMatches = ref([]);
const localDataMatches = ref([]);
const reviewComment = ref("");
const submitting = ref(false);
const draft = ref("");
const annotationText = ref("");
const selectedText = ref("");
const annotations = ref([]);
const showAnnotation = ref(false);
const draftEditor = ref(null);
const sourcePanel = ref(null);
const hoverBubble = ref(null);
const dirty = ref(false);
const saving = ref(false);
const saveNotice = ref("");
const leaveDialogVisible = ref(false);
const decisionDialogVisible = ref(false);
const decisionDialog = ref({ title: "", message: "", tone: "success" });
const lastDraftPayload = ref(null);
const selectedArticleSequence = ref(null);
const replacementMode = ref(false);
const replacementDialogVisible = ref(false);
const replacementLoading = ref(false);
const replacementSubmitting = ref(false);
const replacementError = ref("");
const replacementTarget = ref(null);
const replacementCandidates = ref([]);
const selectedReplacementArticleId = ref(null);
const selectedReplacement = computed(
  () =>
    replacementCandidates.value.find(
      (item) => item.articleId === selectedReplacementArticleId.value,
    ) || null,
);
let hoverTimer;
let savedSelectionRange = null;

function escapeHtml(value) {
  return String(value ?? "").replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
}
function renderDraft() {
  draft.value = articles.value
    .map(
      (item) =>
        `<section class="entry${replacementMode.value ? " replacement-mode-entry" : ""}" data-sequence="${item.sequenceNo}">${replacementMode.value ? '<button type="button" class="entry-replace-button" data-replace-entry="true" contenteditable="false">替换</button>' : ""}<div class="entry-title-row"><h3>${escapeHtml(item.title)}</h3><span class="article-category" contenteditable="false">${escapeHtml(categoryLabel(item.category))}</span></div><p>${escapeHtml(item.summaryContent)}</p></section>`,
    )
    .join("");
}
async function loadWorkspace() {
  loading.value = true;
  loadError.value = "";
  let workspaceLoaded = false;
  try {
    if (!selectedReport.value.reportId) throw new Error("缺少报告编号");
    const detailResponse = await http.get(
      `/reports/${selectedReport.value.reportId}/review-detail`,
    );
    if (detailResponse.data?.code !== 200)
      throw new Error(detailResponse.data?.message || "报告详情查询失败");
    reportDetail.value = detailResponse.data.data;
    const [articleResponse, issueResponse, recordResponse] = await Promise.all([
      http.get(`/reports/${selectedReport.value.reportId}/articles`),
      http.get(`/reports/${selectedReport.value.reportId}/issues`),
      http.get(`/review-tasks/${selectedReport.value.taskId}/records`),
    ]);
    articles.value = articleResponse.data?.data || [];
    issues.value = issueResponse.data?.data || [];
    reviewRecords.value = recordResponse.data?.data || [];
    const sourceResponse = await http.get(
      `/reports/${selectedReport.value.reportId}/sources`,
    );
    sources.value = sourceResponse.data?.data || [];
    renderDraft();
    workspaceLoaded = true;
  } catch (requestError) {
    loadError.value =
      requestError.response?.data?.message ||
      requestError.message ||
      "审核工作台加载失败";
  } finally {
    loading.value = false;
  }
  if (workspaceLoaded) {
    await nextTick();
    applyReviewRecords();
  }
}

function getArticleSource(articleId) {
  return (
    sources.value.find(
      (source) => Number(source.articleId) === Number(articleId),
    ) || null
  );
}
function focusSourceForDraftEntry(event) {
  const section = event.target.closest?.(".entry");
  if (!section || !sourcePanel.value) return;
  const article = articles.value.find(
    (item) => item.sequenceNo === Number(section.dataset.sequence),
  );
  const source = getArticleSource(article?.id);
  if (!source) return;
  const target = sourcePanel.value.querySelector(
    `[data-daily-seq="${source.dailySeq}"]`,
  );
  if (!target) return;
  sourcePanel.value.scrollTo({
    top: target.offsetTop - sourcePanel.value.offsetTop,
    behavior: "smooth",
  });
  target.classList.add("source-focused");
  window.setTimeout(() => target.classList.remove("source-focused"), 900);
}
function handleDraftClick(event) {
  const replaceButton = event.target.closest?.('[data-replace-entry="true"]');
  if (replaceButton) {
    event.preventDefault();
    event.stopPropagation();
    const section = replaceButton.closest(".entry");
    const article = articles.value.find(
      (item) => item.sequenceNo === Number(section?.dataset.sequence),
    );
    if (article) openReplacementDialog(article);
    return;
  }
  focusSourceForDraftEntry(event);
}
let sensitiveHighlightFrame = 0;
function refreshSensitiveHighlights() {
  if (!draftEditor.value) return;
  const ranges = [];
  const matches = [];
  const walker = document.createTreeWalker(
    draftEditor.value,
    NodeFilter.SHOW_TEXT,
  );
  let node;
  while ((node = walker.nextNode())) {
    for (const word of sensitiveWords) {
      let fromIndex = 0;
      let index;
      while ((index = node.nodeValue.indexOf(word, fromIndex)) >= 0) {
        const range = document.createRange();
        range.setStart(node, index);
        range.setEnd(node, index + word.length);
        ranges.push(range);
        matches.push({ word, occurrence: matches.length + 1 });
        fromIndex = index + word.length;
      }
    }
  }
  localSensitiveMatches.value = matches;
  if (window.CSS?.highlights && typeof window.Highlight !== "undefined") {
    CSS.highlights.set("sensitive-word", new Highlight(...ranges));
  }
}
function scheduleSensitiveHighlights() {
  cancelAnimationFrame(sensitiveHighlightFrame);
  sensitiveHighlightFrame = requestAnimationFrame(refreshSensitiveHighlights);
}
const numberPattern = /\d[\d,，]*(?:\.\d+)?(?:[%％])?/g;
function normalizeNumber(value) {
  return String(value || "")
    .replace(/[，,]/g, "")
    .replaceAll("％", "%");
}
function createTextRange(element, start, end) {
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  let node;
  let offset = 0;
  let startNode;
  let endNode;
  let startOffset;
  let endOffset;
  while ((node = walker.nextNode())) {
    const nodeEnd = offset + node.nodeValue.length;
    if (!startNode && start >= offset && start < nodeEnd) {
      startNode = node;
      startOffset = start - offset;
    }
    if (startNode && end > offset && end <= nodeEnd) {
      endNode = node;
      endOffset = end - offset;
      break;
    }
    offset = nodeEnd;
  }
  if (!startNode || !endNode) return null;
  const range = document.createRange();
  range.setStart(startNode, startOffset);
  range.setEnd(endNode, endOffset);
  return range;
}
function refreshDataHighlights() {
  if (!draftEditor.value) return;
  const ranges = [];
  const matches = [];
  for (const section of draftEditor.value.querySelectorAll(".entry")) {
    const sequenceNo = Number(section.dataset.sequence);
    const article = articles.value.find(
      (item) => item.sequenceNo === sequenceNo,
    );
    const source = getArticleSource(article?.id);
    const paragraph = section.querySelector("p");
    if (!article || !source || !paragraph) continue;
    const summary = paragraph.textContent || "";
    const sourceText = normalizeNumber(
      source.originalContent || source.content,
    );
    const sourceNumbers = new Set(
      Array.from(sourceText.matchAll(numberPattern), (match) =>
        normalizeNumber(match[0]),
      ),
    );
    for (const match of summary.matchAll(numberPattern)) {
      const currentNumber = normalizeNumber(match[0]);
      if (sourceNumbers.has(currentNumber)) continue;
      const startOffset = match.index;
      const endOffset = startOffset + match[0].length;
      const range = createTextRange(paragraph, startOffset, endOffset);
      if (!range) continue;
      ranges.push(range);
      matches.push({
        sequenceNo,
        articleId: article.id,
        matchedText: match[0],
        startOffset,
        endOffset,
        message: `数据不一致：报告中的“${match[0]}”未在对应原始资讯中找到`,
      });
    }
  }
  localDataMatches.value = matches;
  if (window.CSS?.highlights && typeof window.Highlight !== "undefined") {
    CSS.highlights.set("data-inconsistency", new Highlight(...ranges));
  }
}
function findDataMatchAtPoint(event) {
  const caret = document.caretRangeFromPoint?.(event.clientX, event.clientY);
  if (!caret) return null;
  const element =
    caret.startContainer.nodeType === Node.TEXT_NODE
      ? caret.startContainer.parentElement
      : caret.startContainer;
  const paragraph = element?.closest?.(".entry p");
  const section = paragraph?.closest(".entry");
  if (!paragraph || !section) return null;
  const before = document.createRange();
  before.selectNodeContents(paragraph);
  try {
    before.setEnd(caret.startContainer, caret.startOffset);
  } catch {
    return null;
  }
  const offset = before.toString().length;
  const sequenceNo = Number(section.dataset.sequence);
  return localDataMatches.value.find(
    (item) =>
      item.sequenceNo === sequenceNo &&
      offset >= item.startOffset &&
      offset < item.endOffset,
  );
}
function persistDraft() {
  scheduleSensitiveHighlights();
  refreshDataHighlights();
}
function markDirty() {
  dirty.value = true;
  saveNotice.value = "";
}
function handleDraftInput() {
  persistDraft();
  markDirty();
}
function preventDraftLineBreak(event) {
  if (
    event.key === "Enter" ||
    event.inputType === "insertParagraph" ||
    event.inputType === "insertLineBreak"
  ) {
    event.preventDefault();
  }
}
function captureSelection() {
  const selection = window.getSelection();
  selectedText.value = selection?.toString().trim() || "";
  savedSelectionRange =
    selection?.rangeCount && !selection.isCollapsed
      ? selection.getRangeAt(0).cloneRange()
      : null;
  const node = selection?.anchorNode;
  const element = node?.nodeType === Node.TEXT_NODE ? node.parentElement : node;
  selectedArticleSequence.value =
    Number(element?.closest?.(".entry")?.dataset.sequence) || null;
}
function wrapSelection(className, tooltip, annotationId = null) {
  const range = savedSelectionRange?.cloneRange();
  if (!range || !draftEditor.value?.contains(range.commonAncestorContainer))
    return false;
  const marker = document.createElement("span");
  marker.className = className;
  if (tooltip) marker.dataset.tooltip = tooltip;
  if (annotationId) marker.dataset.annotationId = annotationId;
  try {
    range.surroundContents(marker);
  } catch {
    return false;
  }
  persistDraft();
  markDirty();
  return true;
}
function wrapStoredText(
  section,
  text,
  className,
  tooltip,
  annotationId = null,
) {
  if (!section || !text) return false;
  const walker = document.createTreeWalker(section, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    if (node.parentElement?.closest(".change-mark,.annotation-mark")) continue;
    const index = node.nodeValue.indexOf(text);
    if (index < 0) continue;
    const range = document.createRange();
    range.setStart(node, index);
    range.setEnd(node, index + text.length);
    const marker = document.createElement("span");
    marker.className = className;
    if (tooltip) marker.dataset.tooltip = tooltip;
    if (annotationId) marker.dataset.annotationId = annotationId;
    range.surroundContents(marker);
    return true;
  }
  return false;
}
function applyReviewRecords() {
  annotations.value = [];
  for (const record of reviewRecords.value) {
    const section = draftEditor.value?.querySelector(
      `.entry[data-sequence="${record.sequenceNo}"]`,
    );
    if (record.actionType === "MARK_MODIFY") {
      wrapStoredText(
        section,
        record.selectedText,
        "change-mark",
        `标记修改；${record.createdAt}`,
      );
    } else if (
      record.actionType === "ANNOTATE" ||
      record.actionType === "COMMENT"
    ) {
      const annotation = {
        id: record.id,
        text: record.selectedText,
        note: record.commentText,
        operatorUsername: record.operatorUsername || "-",
        replies: [],
        resolved: false,
      };
      annotations.value.push(annotation);
      wrapStoredText(
        section,
        record.selectedText,
        "annotation-mark",
        "",
        record.id,
      );
    }
  }
  persistDraft();
}
function stageOperation(operation) {
  pendingOperations.value.push(operation);
  markDirty();
}
function saveModifyMark() {
  if (
    !selectedText.value ||
    !selectedArticleSequence.value ||
    !savedSelectionRange
  ) {
    saveNotice.value = "请先在报告草稿中选择需要标记的文字";
    return;
  }
  const article = articles.value.find(
    (item) => item.sequenceNo === selectedArticleSequence.value,
  );
  if (!article) {
    saveNotice.value = "未找到所选文字对应的报告条目";
    return;
  }
  const text = selectedText.value;
  if (!wrapSelection("change-mark", "标记修改；尚未保存")) {
    saveNotice.value = "页面选区已经失效，请重新选择文字";
    return;
  }
  stageOperation({
    type: "MARK_MODIFY",
    articleId: article.id,
    selectedText: text,
  });
  saveNotice.value = "标记修改已暂存，保存草稿后写入审核记录";
}
function markModify() {
  saveModifyMark();
}
function openAnnotation() {
  captureSelection();
  if (selectedText.value) showAnnotation.value = true;
}
async function addAnnotation() {
  if (!annotationText.value.trim() || !selectedText.value) return;
  const article = articles.value.find(
    (item) => item.sequenceNo === selectedArticleSequence.value,
  );
  if (!article) {
    saveNotice.value = "未找到批注对应的报告条目";
    return;
  }
  const recordId = `temp-${crypto.randomUUID()}`;
  const text = selectedText.value;
  const commentText = annotationText.value.trim();
  const annotation = {
    id: recordId,
    articleId: article.id,
    text,
    note: commentText,
    operatorUsername: auth.user?.username || "-",
    replies: [],
    resolved: false,
  };
  if (!wrapSelection("annotation-mark", "", annotation.id)) {
    saveNotice.value = "页面选区已经失效，请重新选择文字";
    return;
  }
  stageOperation({
    type: "COMMENT",
    articleId: article.id,
    selectedText: text,
    commentText,
  });
  annotations.value.unshift(annotation);
  annotationText.value = "";
  selectedText.value = "";
  showAnnotation.value = false;
  saveNotice.value = "批注已暂存，保存草稿后写入审核记录";
}
function showAnnotationBubble(event) {
  const target = event.target.closest?.(".annotation-mark");
  if (target) {
    clearTimeout(hoverTimer);
    const annotation = annotations.value.find(
      (item) => String(item.id) === target.dataset.annotationId,
    );
    if (!annotation) return;
    const rect = target.getBoundingClientRect();
    hoverBubble.value = {
      kind: "annotation",
      title: "批注信息",
      text: `${annotation.operatorUsername}：${annotation.note}`,
      left: Math.min(rect.left, window.innerWidth - 310),
      top: rect.bottom + 8,
      width: 290,
    };
    return;
  }
  const issue = findDataMatchAtPoint(event);
  if (!issue) {
    hideAnnotationBubble();
    return;
  }
  clearTimeout(hoverTimer);
  hoverBubble.value = {
    kind: "data",
    title: "数据不一致",
    text: issue.message,
    left: Math.min(event.clientX + 10, window.innerWidth - 310),
    top: event.clientY + 18,
    width: 290,
  };
}
function hideAnnotationBubble() {
  clearTimeout(hoverTimer);
  hoverTimer = setTimeout(() => {
    hoverBubble.value = null;
  }, 40);
}
function reply(annotation) {
  const content = window.prompt("输入回复内容");
  if (content?.trim()) {
    annotation.replies.push(content.trim());
    markDirty();
  }
}
function resolve(annotation) {
  annotation.resolved = !annotation.resolved;
  markDirty();
}
async function saveDraft() {
  persistDraft();
  saving.value = true;
  saveNotice.value = "";
  try {
    const sections = [...(draftEditor.value?.querySelectorAll(".entry") || [])];
    const changes = sections
      .map((section) => ({
        sequenceNo: Number(section.dataset.sequence),
        title: section.querySelector("h3")?.innerText.trim() || "",
        summaryContent: section.querySelector("p")?.innerText.trim() || "",
      }))
      .filter((change) => {
        const source = articles.value.find(
          (item) => item.sequenceNo === change.sequenceNo,
        );
        return (
          source &&
          (source.title !== change.title ||
            source.summaryContent !== change.summaryContent)
        );
      });
    const operations = pendingOperations.value.map(
      ({ previewTitle, previewSummary, ...operation }) => operation,
    );
    for (const change of changes) {
      const current = articles.value.find(
        (item) => item.sequenceNo === change.sequenceNo,
      );
      const replacement = pendingOperations.value.find(
        (item) => item.type === "REPLACE" && item.articleId === current?.id,
      );
      if (
        replacement &&
        replacement.previewTitle === change.title &&
        replacement.previewSummary === change.summaryContent
      )
        continue;
      operations.push({
        type: "MODIFY",
        articleId: current.id,
        title: change.title,
        summaryContent: change.summaryContent,
        reason: "审核工作台人工修改",
      });
    }
    if (!operations.length) {
      dirty.value = false;
      saveNotice.value = "没有需要保存的修改";
      return true;
    }
    await http.post(`/review-tasks/${selectedReport.value.taskId}/draft`, {
      operations,
    });
    lastDraftPayload.value = {
      taskId: selectedReport.value.taskId,
      operations,
    };
    pendingOperations.value = [];
    await loadWorkspace();
    dirty.value = false;
    saveNotice.value = `已保存 ${operations.length} 项草稿操作`;
    return true;
  } catch (requestError) {
    saveNotice.value = requestError.response?.data?.message || "草稿保存失败";
    return false;
  } finally {
    saving.value = false;
  }
}
async function resolveIssueItem(issue) {
  if (
    pendingOperations.value.some(
      (item) => item.type === "RESOLVE_ISSUE" && item.issueId === issue.id,
    )
  )
    return;
  issue.resolved = 1;
  stageOperation({ type: "RESOLVE_ISSUE", issueId: issue.id });
  saveNotice.value = "问题处理状态已暂存，保存草稿后写入数据库";
}
function toggleReplacementMode() {
  replacementMode.value = !replacementMode.value;
  if (!replacementMode.value) closeReplacementDialog();
  for (const section of draftEditor.value?.querySelectorAll(".entry") || []) {
    section.classList.toggle("replacement-mode-entry", replacementMode.value);
    const existing = section.querySelector('[data-replace-entry="true"]');
    if (replacementMode.value && !existing) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "entry-replace-button";
      button.dataset.replaceEntry = "true";
      button.contentEditable = "false";
      button.textContent = "替换";
      section.prepend(button);
    } else if (!replacementMode.value) {
      existing?.remove();
    }
  }
}
async function openReplacementDialog(article) {
  if (dirty.value) {
    saveNotice.value = "当前草稿有未保存修改，请先保存草稿再替换资讯";
    return;
  }
  replacementTarget.value = article;
  replacementCandidates.value = [];
  selectedReplacementArticleId.value = null;
  replacementError.value = "";
  replacementDialogVisible.value = true;
  replacementLoading.value = true;
  try {
    const { data } = await http.get(
      `/review-tasks/${selectedReport.value.taskId}/replacement-articles`,
      {
        params: {},
      },
    );
    replacementCandidates.value = data?.data || [];
  } catch (requestError) {
    replacementError.value =
      requestError.response?.data?.message || "备选资讯加载失败";
  } finally {
    replacementLoading.value = false;
  }
}
function closeReplacementDialog() {
  if (replacementSubmitting.value) return;
  replacementDialogVisible.value = false;
  replacementTarget.value = null;
  replacementCandidates.value = [];
  selectedReplacementArticleId.value = null;
  replacementError.value = "";
}
function toggleReplacementCandidate(articleId) {
  selectedReplacementArticleId.value =
    selectedReplacementArticleId.value === articleId ? null : articleId;
}
async function confirmReplacement() {
  const current = replacementTarget.value;
  const candidate = selectedReplacement.value;
  if (!current || !candidate || replacementSubmitting.value) return;
  replacementSubmitting.value = true;
  replacementError.value = "";
  try {
    await http.post(
      `/review-tasks/${selectedReport.value.taskId}/articles/${current.id}/replace`,
      {
        newNewsId: candidate.newsId,
        reason: candidate.reason?.trim() || "替换为备选条目",
      },
    );
    replacementDialogVisible.value = false;
    replacementTarget.value = null;
    replacementCandidates.value = [];
    selectedReplacementArticleId.value = null;
    await loadWorkspace();
    saveNotice.value = "资讯条目替换成功";
  } catch (requestError) {
    replacementError.value =
      requestError.response?.data?.message || "替换资讯失败";
  } finally {
    replacementSubmitting.value = false;
  }
}
async function submitDecision(decision) {
  if (dirty.value && canEdit.value) {
    const saved = await saveDraft();
    if (!saved) return;
  }
  submitting.value = true;
  try {
    const { data } = await http.post(
      `/review-tasks/${selectedReport.value.taskId}/submit`,
      {
        decision,
        comment: reviewComment.value,
      },
    );
    if (data?.code !== 200) throw new Error(data?.message || "审核提交失败");
    if (decision === "REJECT") {
      decisionDialog.value = {
        title: "退回修改成功",
        message: "报告已退回资讯管理员修改，相关审核意见和操作记录已完整保留。",
        tone: "warning",
      };
    } else if (isFinalReviewer.value) {
      decisionDialog.value = {
        title: "终审确认成功",
        message: "报告已通过终审并进入待发送状态，可继续进行精准分发。",
        tone: "success",
      };
    } else {
      decisionDialog.value = {
        title: "提交终审成功",
        message: "报告已通过初审并生成终审任务，等待部室负责人审核。",
        tone: "success",
      };
    }
    decisionDialogVisible.value = true;
  } catch (requestError) {
    saveNotice.value =
      requestError.response?.data?.message ||
      requestError.message ||
      "审核提交失败";
  } finally {
    submitting.value = false;
  }
}
function submitInitialReview() {
  submitDecision("APPROVE");
}
function confirmFinalReview() {
  submitDecision("APPROVE");
}
function returnForRevision() {
  submitDecision("REJECT");
}
function logout() {
  auth.logout();
  router.push("/login");
}
function backToTasks() {
  router.push("/review-tasks");
}
function closeDecisionDialog() {
  decisionDialogVisible.value = false;
  backToTasks();
}
function requestBackToTasks() {
  if (canEdit.value && dirty.value) leaveDialogVisible.value = true;
  else backToTasks();
}
async function saveAndBack() {
  const saved = await saveDraft();
  if (!saved) return;
  leaveDialogVisible.value = false;
  backToTasks();
}
function discardAndBack() {
  dirty.value = false;
  leaveDialogVisible.value = false;
  backToTasks();
}

onMounted(loadWorkspace);
onUnmounted(() => {
  cancelAnimationFrame(sensitiveHighlightFrame);
  window.CSS?.highlights?.delete("sensitive-word");
  window.CSS?.highlights?.delete("data-inconsistency");
});
</script>

<template>
  <div class="review-shell">
    <header class="review-header">
      <div><strong>金融智讯</strong><span>人机协同审核工作台</span></div>
      <div class="review-user">
        <span class="role-badge">{{ auth.user?.roleName }}</span
        ><span>{{ auth.user?.username }}</span
        ><button class="text-button" @click="logout">退出</button>
      </div>
    </header>
    <section class="review-context">
      <div>
        <span class="eyebrow"
          >待审核报告 · 报告 #{{ selectedReport.reportId }}</span
        >
        <h1>{{ selectedReport.title }}</h1>
        <p>报告日期：{{ selectedReport.date }}　·　状态：{{ statusText }}</p>
      </div>
      <div class="context-actions">
        <span v-if="saveNotice" class="draft-save-notice">{{ saveNotice }}</span
        ><button
          v-if="canEdit"
          class="outline-button"
          :disabled="saving || loading"
          @click="saveDraft"
        >
          {{ saving ? "保存中…" : "保存草稿" }}</button
        ><button
          v-if="!isFinalReviewer"
          :disabled="submitting || loading"
          @click="submitInitialReview"
        >
          {{ submitting ? "提交中…" : "提交终审" }}</button
        ><button
          v-else
          :disabled="submitting || loading"
          @click="confirmFinalReview"
        >
          {{ submitting ? "提交中…" : "确认终审" }}</button
        ><button class="outline-button" @click="requestBackToTasks">
          返回列表
        </button>
      </div>
    </section>
    <div class="review-legend">
      <span class="legend-sensitive">敏感词</span
      ><span class="legend-data">数据不一致</span
      ><span class="legend-change">修改/批注</span>
    </div>
    <div v-if="loadError" class="task-error">
      <b>审核数据加载失败</b><span>{{ loadError }}</span
      ><button @click="loadWorkspace">重新加载</button>
    </div>
    <div v-else-if="loading" class="task-empty">
      <span class="task-spinner"></span><b>正在加载报告与原始资讯…</b>
    </div>
    <main v-else class="review-workspace">
      <article class="document-panel draft-panel">
        <div class="document-title">
          <div><h2>报告草稿</h2></div>
          <span class="status-dot">{{
            isFinalReviewer ? "终审中" : "初审中"
          }}</span>
        </div>
        <div v-if="canEdit" class="review-toolbar">
          <button class="tool-button" @mousedown.prevent @click="markModify">
            标记修改</button
          ><button
            class="tool-button"
            @mousedown.prevent
            @click="openAnnotation"
          >
            添加批注</button
          ><button
            class="tool-button"
            :class="{ 'replacement-active': replacementMode }"
            @click="toggleReplacementMode"
          >
            {{ replacementMode ? "取消替换" : "替换资讯" }}
          </button>
        </div>
        <div
          ref="draftEditor"
          class="draft-editor"
          :contenteditable="canEdit"
          @keydown="preventDraftLineBreak"
          @beforeinput="preventDraftLineBreak"
          @input="handleDraftInput"
          @mouseup="captureSelection"
          @click="handleDraftClick"
          @mousemove="showAnnotationBubble"
          @mouseleave="hideAnnotationBubble"
          v-html="draft"
        ></div>
        <div v-if="showAnnotation" class="annotation-composer">
          <span :title="selectedText">添加批注：{{ selectedTextPreview }}</span
          ><input v-model="annotationText" placeholder="输入批注内容" /><button
            @click="addAnnotation"
          >
            添加
          </button>
        </div>
        <div class="issue-summary">
          <div>
            <b>敏感内容</b
            ><span
              v-for="item in localSensitiveMatches"
              :key="`${item.word}-${item.occurrence}`"
              class="sensitive-chip"
              title="草稿中检测到的敏感词"
              >{{ item.word }}</span
            ><span v-if="!localSensitiveMatches.length">未发现敏感词</span>
          </div>
          <div>
            <b>数据核验</b
            ><span
              v-for="(item, index) in localDataMatches"
              :key="`local-${item.articleId}-${item.startOffset}-${index}`"
              class="data-chip"
              :title="item.message"
              >第 {{ item.sequenceNo }} 条：{{ item.matchedText }}</span
            ><button
              v-for="item in issues.filter(
                (issue) =>
                  issue.issueType !== 'SENSITIVE_CONTENT' && !issue.resolved,
              )"
              :key="item.id"
              class="data-chip"
              :disabled="!canEdit"
              :title="canEdit ? `${item.message}；点击标记处理` : item.message"
              @click="resolveIssueItem(item)"
            >
              {{ item.issueType }}：{{ item.matchedText }}
            </button
            ><span
              v-if="
                !localDataMatches.length &&
                !issues.some(
                  (issue) =>
                    issue.issueType !== 'SENSITIVE_CONTENT' && !issue.resolved,
                )
              "
              >未发现数据不一致</span
            >
          </div>
        </div>
      </article>
      <article class="document-panel source-panel">
        <div class="document-title">
          <div><h2>原始资讯全文</h2></div>
          <span class="source-count">{{ sources.length }} 条资讯</span>
        </div>
        <div ref="sourcePanel" class="source-content">
          <section
            v-for="source in sources"
            :key="source.articleId"
            :data-daily-seq="source.dailySeq"
          >
            <h3>{{ source.title }}</h3>
            <p>{{ source.originalContent || source.content }}</p>
          </section>
          <section v-if="!sources.length">
            <p>当前报告暂无已选原始资讯。</p>
          </section>
        </div>
      </article>
    </main>
    <div
      v-if="hoverBubble"
      class="annotation-bubble"
      :style="{
        left: `${hoverBubble.left}px`,
        top: `${hoverBubble.top}px`,
        width: `${hoverBubble.width}px`,
      }"
    >
      <b>{{ hoverBubble.title }}</b>
      <p>{{ hoverBubble.text }}</p>
    </div>
    <Teleport to="body"
      ><div
        v-if="replacementDialogVisible"
        class="replacement-mask"
        @click.self="closeReplacementDialog"
      >
        <section
          class="replacement-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="replacement-dialog-title"
        >
          <header>
            <div>
              <h2 id="replacement-dialog-title">选择备选资讯</h2>
              <p>当前条目：{{ replacementTarget?.title }}</p>
            </div>
            <button
              class="replacement-close"
              aria-label="关闭"
              :disabled="replacementSubmitting"
              @click="closeReplacementDialog"
            >
              ×
            </button>
          </header>
          <div v-if="replacementLoading" class="replacement-state">
            <span class="task-spinner"></span>正在加载备选资讯…
          </div>
          <div
            v-else-if="replacementError && !replacementCandidates.length"
            class="replacement-state replacement-error"
          >
            {{ replacementError }}
          </div>
          <div
            v-else-if="!replacementCandidates.length"
            class="replacement-state"
          >
            当前报告没有可替换的备选资讯
          </div>
          <div v-else class="replacement-list">
            <article
              v-for="candidate in replacementCandidates"
              :key="candidate.articleId"
              :class="[
                'replacement-item',
                {
                  selected:
                    selectedReplacementArticleId === candidate.articleId,
                },
              ]"
              @click="toggleReplacementCandidate(candidate.articleId)"
            >
              <button
                type="button"
                class="replacement-selector"
                :aria-pressed="
                  selectedReplacementArticleId === candidate.articleId
                "
                :aria-label="`选择 ${candidate.title}`"
                @click.stop="toggleReplacementCandidate(candidate.articleId)"
              >
                <span></span>
              </button>
              <div>
                <div class="replacement-title-row">
                  <h3>{{ candidate.title }}</h3>
                  <span class="article-category">{{
                    categoryLabel(candidate.category)
                  }}</span>
                </div>
                <p class="replacement-reason">
                  理由：{{ candidate.reason || "暂无推荐理由" }}
                </p>
                <p
                  v-if="selectedReplacementArticleId === candidate.articleId"
                  class="replacement-content"
                >
                  {{ candidate.summaryContent || "暂无条目内容" }}
                </p>
              </div>
            </article>
          </div>
          <p
            v-if="replacementError && replacementCandidates.length"
            class="replacement-inline-error"
          >
            {{ replacementError }}
          </p>
          <footer>
            <button
              class="outline-button"
              :disabled="replacementSubmitting"
              @click="closeReplacementDialog"
            >
              取消</button
            ><button
              :disabled="!selectedReplacement || replacementSubmitting"
              @click="confirmReplacement"
            >
              {{ replacementSubmitting ? "替换中…" : "确定替换" }}
            </button>
          </footer>
        </section>
      </div></Teleport
    >
    <footer class="review-footer">
      <label
        >{{
          isFinalReviewer ? "部门负责人审查意见" : "部门负责人审查意见（只读）"
        }}<input
          v-if="isFinalReviewer"
          v-model="reviewComment"
          placeholder="填写终审意见或退回原因（选填）" /><input
          v-else
          :value="
            reportDetail?.departmentReviewComment || '暂无部门负责人审查意见'
          "
          readonly /></label
      ><button
        v-if="isFinalReviewer"
        class="danger-button"
        :disabled="submitting"
        @click="returnForRevision"
      >
        退回修改
      </button>
    </footer>
    <Teleport to="body"
      ><div v-if="decisionDialogVisible" class="review-decision-mask">
        <section
          class="review-decision-dialog"
          :class="`decision-${decisionDialog.tone}`"
          role="dialog"
          aria-modal="true"
          aria-labelledby="decision-dialog-title"
        >
          <div class="decision-icon" aria-hidden="true"><span></span></div>
          <div class="decision-content">
            <small>审核流程已更新</small>
            <h3 id="decision-dialog-title">{{ decisionDialog.title }}</h3>
            <p>{{ decisionDialog.message }}</p>
          </div>
          <footer>
            <button type="button" @click="closeDecisionDialog">
              返回任务列表
            </button>
          </footer>
        </section>
      </div></Teleport
    >
    <Teleport to="body"
      ><div
        v-if="leaveDialogVisible"
        class="review-leave-mask"
        @click.self="leaveDialogVisible = false"
      >
        <section
          class="review-leave-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="leave-dialog-title"
        >
          <h3 id="leave-dialog-title">尚未保存草稿</h3>
          <p>当前报告有未保存的修改，返回列表前是否保存草稿？</p>
          <div class="review-leave-actions">
            <button class="outline-button" @click="leaveDialogVisible = false">
              取消</button
            ><button class="outline-button" @click="discardAndBack">
              不保存返回</button
            ><button :disabled="saving" @click="saveAndBack">
              {{ saving ? "保存中…" : "保存并返回" }}
            </button>
          </div>
        </section>
      </div></Teleport
    >
  </div>
</template>
