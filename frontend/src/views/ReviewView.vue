<script setup>
import { computed, onMounted, onUnmounted, ref } from "vue";
import http from "../api/http";

const reports = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = 10;
const loading = ref(false);
const error = ref("");
const manageVisible = ref(false);
const manageSaving = ref(false);
const manageError = ref("");
const managedReport = ref(null);
const reviewers = ref([]);
const manageForm = ref({ stage: "INITIAL", reviewerId: null });
const pdfVisible = ref(false);
const pdfLoading = ref(false);
const pdfError = ref("");
const pdfTitle = ref("");
const pdfUrl = ref("");
const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize)),
);
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

function statusText(status) {
  return statusLabels[status] || status || "-";
}

async function loadReports(targetPage = pageNum.value) {
  loading.value = true;
  error.value = "";
  try {
    const { data } = await http.get("/reports/admin/review", {
      params: { pageNum: targetPage, pageSize },
    });
    if (data?.code !== 200)
      throw new Error(data?.message || "报告列表查询失败");
    reports.value = data.data?.records || [];
    total.value = Number(data.data?.total || 0);
    pageNum.value = Number(data.data?.pageNum || targetPage);
  } catch (exception) {
    error.value =
      exception.response?.data?.message ||
      exception.message ||
      "报告列表加载失败";
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
  loadReports(page);
}

function reportStage(status) {
  return String(status || "").startsWith("FINAL_") ? "FINAL" : "INITIAL";
}
async function loadReviewers() {
  manageError.value = "";
  try {
    const { data } = await http.get("/reports/admin/reviewers", {
      params: { stage: manageForm.value.stage },
    });
    reviewers.value = data?.data || [];
    if (
      !reviewers.value.some((item) => item.id === manageForm.value.reviewerId)
    ) {
      manageForm.value.reviewerId = reviewers.value[0]?.id || null;
    }
  } catch (exception) {
    reviewers.value = [];
    manageError.value = exception.response?.data?.message || "审核人员加载失败";
  }
}
async function openManage(report) {
  managedReport.value = report;
  manageForm.value = {
    stage:
      report.status === "FINAL_APPROVED" ? "FINAL" : reportStage(report.status),
    reviewerId: report.currentReviewerId,
  };
  manageVisible.value = true;
  await loadReviewers();
}
async function saveManagement() {
  if (!managedReport.value || !manageForm.value.reviewerId) {
    manageError.value = "请选择审核人员";
    return;
  }
  manageSaving.value = true;
  manageError.value = "";
  try {
    await http.put(
      `/reports/admin/${managedReport.value.id}/review-management`,
      manageForm.value,
    );
    manageVisible.value = false;
    await loadReports();
  } catch (exception) {
    manageError.value =
      exception.response?.data?.message || "报告审核信息修改失败";
  } finally {
    manageSaving.value = false;
  }
}
function closePdf() {
  pdfVisible.value = false;
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value);
  pdfUrl.value = "";
}
async function browseArchive(report) {
  closePdf();
  pdfVisible.value = true;
  pdfLoading.value = true;
  pdfError.value = "";
  pdfTitle.value = report.reportTitle || "归档报告";
  try {
    const response = await http.get(`/reports/admin/${report.id}/archive/pdf`, {
      responseType: "blob",
    });
    pdfUrl.value = URL.createObjectURL(
      new Blob([response.data], { type: "application/pdf" }),
    );
  } catch (exception) {
    pdfError.value =
      exception.response?.status === 404
        ? "未找到该报告的归档PDF"
        : "归档PDF读取失败";
  } finally {
    pdfLoading.value = false;
  }
}

onMounted(() => loadReports(1));
onUnmounted(closePdf);
</script>

<template>
  <div class="review-list-heading">
    <div>
      <h2>人机审核</h2>
      <p class="muted">查看系统中全部报告的审核状态和当前审核人员</p>
    </div>
    <button type="button" :disabled="loading" @click="loadReports()">
      {{ loading ? "刷新中…" : "刷新" }}
    </button>
  </div>
  <p v-if="error" class="review-list-error">{{ error }}</p>
  <div class="panel review-table-wrap">
    <table>
      <thead>
        <tr>
          <th>报告题目</th>
          <th>报告状态</th>
          <th>当前审核人员</th>
          <th>最新更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="5" class="review-empty">正在加载…</td>
        </tr>
        <tr v-else-if="!reports.length">
          <td colspan="5" class="review-empty">暂无报告</td>
        </tr>
        <tr v-for="report in reports" v-else :key="report.id">
          <td>{{ report.reportTitle || "-" }}</td>
          <td>
            <span class="review-status">{{ statusText(report.status) }}</span>
          </td>
          <td>{{ report.currentReviewer || "-" }}</td>
          <td>{{ report.updatedAt || "-" }}</td>
          <td>
            <button
              v-if="report.status === 'FINAL_ARCHIVED'"
              type="button"
              class="row-action"
              @click="browseArchive(report)"
            >
              浏览</button
            ><button
              v-else
              type="button"
              class="row-action"
              @click="openManage(report)"
            >
              管理
            </button>
          </td>
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
  <Teleport to="body"
    ><div
      v-if="manageVisible"
      class="admin-modal-mask"
      @click.self="manageVisible = false"
    >
      <section class="admin-modal" role="dialog" aria-modal="true">
        <header>
          <div>
            <h3>
              {{
                managedReport?.status === "FINAL_APPROVED"
                  ? "打开锁定并退回终审"
                  : "管理报告审核"
              }}
            </h3>
            <p>{{ managedReport?.reportTitle }}</p>
          </div>
          <button
            type="button"
            class="modal-close"
            :disabled="manageSaving"
            @click="manageVisible = false"
          >
            ×
          </button>
        </header>
        <label
          >审核阶段<select
            v-model="manageForm.stage"
            :disabled="managedReport?.status === 'FINAL_APPROVED'"
            @change="loadReviewers"
          >
            <option value="INITIAL">初审</option>
            <option value="FINAL">终审</option>
          </select></label
        ><label
          >负责人员<select v-model="manageForm.reviewerId">
            <option
              v-for="reviewer in reviewers"
              :key="reviewer.id"
              :value="reviewer.id"
            >
              {{ reviewer.username }}
            </option>
          </select></label
        >
        <p v-if="managedReport?.status === 'FINAL_APPROVED'" class="modal-hint">
          保存后将解除报告锁定，状态改为待终审。
        </p>
        <p v-if="manageError" class="modal-error">{{ manageError }}</p>
        <footer>
          <button
            type="button"
            class="modal-secondary"
            :disabled="manageSaving"
            @click="manageVisible = false"
          >
            取消</button
          ><button
            type="button"
            :disabled="manageSaving || !manageForm.reviewerId"
            @click="saveManagement"
          >
            {{ manageSaving ? "保存中…" : "确定修改" }}
          </button>
        </footer>
      </section>
    </div></Teleport
  >
  <Teleport to="body"
    ><div
      v-if="pdfVisible"
      class="admin-modal-mask pdf-mask"
      @click.self="closePdf"
    >
      <section class="pdf-modal" role="dialog" aria-modal="true">
        <header>
          <h3>{{ pdfTitle }}</h3>
          <button type="button" class="modal-close" @click="closePdf">×</button>
        </header>
        <div v-if="pdfLoading" class="pdf-state">正在读取归档PDF…</div>
        <div v-else-if="pdfError" class="pdf-state modal-error">
          {{ pdfError }}
        </div>
        <iframe v-else-if="pdfUrl" :src="pdfUrl" title="归档报告PDF"></iframe>
      </section></div
  ></Teleport>
</template>

<style scoped>
.review-list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.review-list-heading p {
  margin: 0;
}
.review-list-error {
  padding: 10px 14px;
  border-radius: 6px;
  background: #fff0f0;
  color: #b8323f;
}
.review-table-wrap {
  padding: 0;
  overflow: hidden;
}
.review-table-wrap table {
  width: 100%;
  border-collapse: collapse;
}
.review-table-wrap th,
.review-table-wrap td {
  padding: 15px 18px;
  text-align: left;
  border-bottom: 1px solid #e7edf3;
  font-size: 14px;
}
.review-table-wrap th {
  background: #f7f9fc;
  color: #52677e;
  font-size: 13px;
}
.review-empty {
  padding: 40px !important;
  text-align: center !important;
  color: #7a8997;
}
.review-status {
  display: inline-block;
  padding: 4px 9px;
  border-radius: 12px;
  background: #e8f2fc;
  color: #14588f;
  font-size: 12px;
}
.row-action {
  padding: 6px 12px;
}
.review-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  color: #65788a;
  font-size: 13px;
}
.review-pagination div {
  display: flex;
  gap: 8px;
}
.review-pagination button {
  padding: 7px 12px;
}
.review-pagination button:disabled,
.review-list-heading button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.admin-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(8, 28, 46, 0.55);
}
.admin-modal {
  width: min(460px, 100%);
  padding: 22px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.24);
}
.admin-modal header,
.pdf-modal header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.admin-modal h3,
.pdf-modal h3 {
  margin: 0;
  color: #173751;
}
.admin-modal header p {
  margin: 6px 0 18px;
  color: #6b7e8f;
}
.admin-modal label {
  display: grid;
  gap: 7px;
  margin-top: 14px;
  color: #40586d;
  font-size: 13px;
}
.admin-modal select {
  padding: 10px;
  border: 1px solid #cfdae4;
  border-radius: 6px;
  background: #fff;
}
.admin-modal select:disabled {
  background: #eef2f5;
  color: #66798a;
}
.modal-hint {
  margin: 12px 0 0;
  color: #9a6500;
  font-size: 13px;
}
.admin-modal footer {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 22px;
}
.modal-close {
  padding: 0;
  background: transparent;
  color: #607789;
  font-size: 25px;
  line-height: 1;
}
.modal-secondary {
  background: #fff;
  color: #345870;
  border: 1px solid #ccd9e3;
}
.modal-error {
  color: #bd303a;
}
.pdf-mask {
  padding: 3vh 3vw;
}
.pdf-modal {
  display: flex;
  flex-direction: column;
  width: 94vw;
  height: 94vh;
  padding: 16px;
  background: #fff;
  border-radius: 10px;
}
.pdf-modal header {
  padding-bottom: 12px;
}
.pdf-modal iframe {
  flex: 1;
  width: 100%;
  border: 1px solid #dce5ed;
}
.pdf-state {
  display: grid;
  flex: 1;
  place-items: center;
  color: #687b8c;
}
</style>
