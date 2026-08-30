<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import http from "../api/http";

const router = useRouter();
const loading = ref(false);
const error = ref("");
const overview = ref(null);
const reportStatusLabels = {
  GENERATED: "已生成",
  INITIAL_PENDING: "待初审",
  INITIAL_REVIEWING: "初审中",
  INITIAL_REJECTED: "初审已退回",
  FINAL_PENDING: "待终审",
  FINAL_REVIEWING: "终审中",
  FINAL_APPROVED: "审核已通过",
  FINAL_ARCHIVED: "已归档",
  FINALIZED: "审核已通过",
  ARCHIVED: "已归档",
};
const commonStatusLabels = {
  SUCCESS: "成功",
  COMPLETED: "成功",
  FAILED: "失败",
  PENDING: "待处理",
  RUNNING: "进行中",
  PROCESSING: "进行中",
  SENDING: "发送中",
  PARTIAL_FAILED: "部分失败",
};
const moduleDefinitions = computed(() => [
  {
    key: "review",
    title: "人机审核",
    subtitle: "报告审核阶段分布",
    latestLabel: "最新一条报告状态",
    route: "/review",
    colors: ["#2784c6", "#7859c6", "#35a46f", "#7c8b99"],
    segments: [
      { key: "INITIAL", label: "初审" },
      { key: "FINAL", label: "终审" },
      { key: "APPROVED", label: "审核已通过" },
      { key: "ARCHIVED", label: "已归档" },
    ],
  },
  {
    key: "distribution",
    title: "精准分发",
    subtitle: "邮件收件人发送结果",
    latestLabel: "最新一条发送状态",
    route: "/distribution",
    colors: ["#35a46f", "#df5b64"],
    segments: [
      { key: "SUCCESS", label: "发送成功" },
      { key: "FAILED", label: "发送失败" },
    ],
  },
  {
    key: "tasks",
    title: "采集日志",
    subtitle: "资讯采集任务执行结果",
    latestLabel: "最新一条采集状态",
    route: "/task",
    colors: ["#2c8fc5", "#e56b55"],
    segments: [
      { key: "SUCCESS", label: "成功" },
      { key: "FAILED", label: "失败" },
    ],
  },
  {
    key: "users",
    title: "用户权限",
    subtitle: "平台用户角色分布",
    latestLabel: "用户概况",
    route: "/user",
    colors: ["#315c8b", "#2a9d8f", "#e39a3b"],
    segments: [
      { key: "ADMIN", label: "系统管理员" },
      { key: "INFO_MANAGER", label: "资讯管理员" },
      { key: "DEPT_MANAGER", label: "部室负责人" },
    ],
  },
]);

function moduleData(definition) {
  const source = overview.value?.[definition.key];
  return definition.segments.map((segment, index) => ({
    ...segment,
    value: Number(source?.counts?.[segment.key] || 0),
    color: definition.colors[index],
  }));
}
function totalOf(definition) {
  return moduleData(definition).reduce((sum, item) => sum + item.value, 0);
}
function donutStyle(definition) {
  const data = moduleData(definition);
  const total = totalOf(definition);
  if (!total) return { background: "#e8edf2" };
  let cursor = 0;
  const stops = data.map((item) => {
    const start = cursor;
    cursor += (item.value / total) * 100;
    return `${item.color} ${start}% ${cursor}%`;
  });
  return { background: `conic-gradient(${stops.join(",")})` };
}
function statusLabel(status) {
  return (
    reportStatusLabels[status] ||
    commonStatusLabels[status] ||
    status ||
    "暂无记录"
  );
}
function latestText(definition) {
  if (definition.key === "users") return `当前共 ${totalOf(definition)} 位用户`;
  const latest = overview.value?.[definition.key]?.latest;
  if (!latest) return "暂无最新记录";
  return `${latest.title || "最新记录"}：${statusLabel(latest.status)}`;
}
async function loadOverview() {
  loading.value = true;
  error.value = "";
  try {
    const { data } = await http.get("/admin/dashboard");
    if (data?.code !== 200)
      throw new Error(data?.message || "工作台数据加载失败");
    overview.value = data.data;
  } catch (exception) {
    error.value =
      exception.response?.data?.message ||
      exception.message ||
      "工作台数据加载失败";
  } finally {
    loading.value = false;
  }
}
onMounted(loadOverview);
</script>

<template>
  <div class="dashboard-page">
    <section class="dashboard-heading">
      <div>
        <h2>工作台</h2>
        <p class="muted">金融资讯处理全流程运行概览</p>
      </div>
      <button type="button" :disabled="loading" @click="loadOverview">
        {{ loading ? "刷新中…" : "刷新数据" }}
      </button>
    </section>
    <p v-if="error" class="dashboard-error">{{ error }}</p>

    <section
      class="generation-banner"
      :class="`generation-${String(overview?.generation?.status || 'loading').toLowerCase()}`"
    >
      <div class="generation-icon"><span></span></div>
      <div class="generation-copy">
        <small>今日智能报告生成状态</small>
        <h3>
          {{
            loading && !overview
              ? "正在读取今日任务状态…"
              : overview?.generation?.title || "暂无状态"
          }}
        </h3>
        <p>
          {{
            overview?.generation?.detail || "根据今日采集任务与报告生成结果判断"
          }}
        </p>
      </div>
      <span
        v-if="overview?.generation?.reportStatus"
        class="generation-report-status"
        >{{ statusLabel(overview.generation.reportStatus) }}</span
      >
    </section>

    <section class="dashboard-grid">
      <article
        v-for="definition in moduleDefinitions"
        :key="definition.key"
        class="dashboard-card"
      >
        <header class="dashboard-card-heading">
          <div>
            <h3>{{ definition.title }}</h3>
            <p>{{ definition.subtitle }}</p>
          </div>
          <button
            type="button"
            class="detail-button"
            @click="router.push(definition.route)"
          >
            查看详情 <span>→</span>
          </button>
        </header>
        <div class="chart-layout">
          <div
            class="donut"
            :style="donutStyle(definition)"
            role="img"
            :aria-label="`${definition.title}状态环形图`"
          >
            <div>
              <strong>{{ totalOf(definition) }}</strong
              ><span>{{
                definition.key === "users" ? "用户总数" : "记录总数"
              }}</span>
            </div>
          </div>
          <ul class="chart-legend">
            <li v-for="item in moduleData(definition)" :key="item.key">
              <i :style="{ background: item.color }"></i
              ><span>{{ item.label }}</span
              ><strong>{{ item.value }}</strong>
            </li>
          </ul>
        </div>
        <footer class="latest-status">
          <span>{{ definition.latestLabel }}</span>
          <b :title="latestText(definition)">{{ latestText(definition) }}</b>
        </footer>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 590px;
  flex-direction: column;
  overflow: hidden;
}
.dashboard-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex: none;
  margin-bottom: 12px;
}
.dashboard-heading p {
  margin: 0;
}
.dashboard-heading button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.dashboard-error {
  flex: none;
  margin: 0 0 10px;
  padding: 8px 13px;
  border-radius: 8px;
  background: #fff0f0;
  color: #b8323f;
}
.generation-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 78px;
  flex: none;
  padding: 12px 18px;
  border: 1px solid #d7e4ee;
  border-radius: 11px;
  background: linear-gradient(112deg, #f7fbff, #eef6fb);
  box-shadow: 0 3px 12px rgba(31, 66, 94, 0.07);
}
.generation-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 14px;
  background: #1764a5;
  box-shadow: 0 6px 14px rgba(23, 100, 165, 0.2);
}
.generation-icon span {
  width: 21px;
  height: 25px;
  border: 2px solid #fff;
  border-radius: 4px;
  position: relative;
}
.generation-icon span::after {
  content: "";
  position: absolute;
  left: 4px;
  right: 4px;
  top: 6px;
  height: 2px;
  background: #fff;
  box-shadow: 0 6px #fff;
}
.generation-copy {
  min-width: 0;
}
.generation-copy small {
  color: #557590;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.6px;
}
.generation-copy h3 {
  margin: 3px 0;
  color: #153b5a;
  font-size: 17px;
}
.generation-copy p {
  margin: 0;
  color: #667e91;
  font-size: 12px;
}
.generation-report-status {
  margin-left: auto;
  padding: 6px 11px;
  border-radius: 18px;
  background: #dff3e7;
  color: #237349;
  font-size: 12px;
  font-weight: 700;
}
.generation-failed {
  border-color: #efc9cc;
  background: linear-gradient(112deg, #fffafa, #fff1f2);
}
.generation-failed .generation-icon {
  background: #c94652;
}
.generation-not_started .generation-icon,
.generation-loading .generation-icon {
  background: #71869a;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-height: 0;
  flex: 1;
  margin-top: 12px;
}
.dashboard-card {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 14px 17px 12px;
  border: 1px solid #dce5ed;
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(31, 66, 94, 0.06);
}
.dashboard-card-heading {
  display: flex;
  height: auto;
  padding: 0;
  align-items: flex-start;
  justify-content: space-between;
  background: none;
  box-shadow: none;
}
.dashboard-card-heading h3 {
  margin: 0;
  color: #183952;
  font-size: 16px;
}
.dashboard-card-heading p {
  margin: 3px 0 0;
  color: #7a8b9a;
  font-size: 11px;
}
.detail-button {
  padding: 5px 9px;
  border: 1px solid #c9dbe8;
  background: #f8fbfd;
  color: #1764a5;
  font-size: 11px;
}
.detail-button span {
  margin-left: 3px;
}
.chart-layout {
  display: grid;
  grid-template-columns: 116px minmax(150px, 1fr);
  align-items: center;
  gap: 16px;
  min-height: 0;
  flex: 1;
  padding: 6px 5px;
}
.donut {
  display: grid;
  place-items: center;
  width: 108px;
  height: 108px;
  border-radius: 50%;
  position: relative;
  transform: rotate(-90deg);
}
.donut::after {
  content: "";
  position: absolute;
  inset: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: inset 0 0 0 1px #edf1f4;
}
.donut > div {
  z-index: 1;
  display: grid;
  text-align: center;
  transform: rotate(90deg);
}
.donut strong {
  color: #173b57;
  font-size: 22px;
}
.donut span {
  margin-top: 1px;
  color: #82909d;
  font-size: 10px;
}
.chart-legend {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.chart-legend li {
  display: grid;
  grid-template-columns: 9px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  color: #526779;
  font-size: 12px;
}
.chart-legend i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.chart-legend strong {
  color: #1d3e58;
  font-size: 13px;
}
.latest-status {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  height: auto;
  padding: 8px 10px;
  border-radius: 7px;
  background: #f5f8fa;
  box-shadow: none;
}
.latest-status span {
  color: #7c8d9b;
  font-size: 11px;
}
.latest-status b {
  overflow: hidden;
  color: #334f65;
  font-size: 11px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 820px) {
  .dashboard-page {
    height: auto;
    min-height: 0;
    overflow: visible;
  }
  .dashboard-grid {
    grid-template-columns: 1fr;
    grid-template-rows: none;
  }
  .generation-banner {
    align-items: flex-start;
  }
  .generation-report-status {
    margin-left: 0;
  }
  .chart-layout {
    grid-template-columns: 130px 1fr;
  }
}
@media (max-width: 520px) {
  .dashboard-heading {
    align-items: flex-start;
    gap: 14px;
  }
  .generation-banner {
    flex-wrap: wrap;
  }
  .chart-layout {
    grid-template-columns: 1fr;
  }
  .donut {
    margin: auto;
  }
  .dashboard-card-heading {
    gap: 12px;
  }
  .latest-status {
    grid-template-columns: 1fr;
  }
  .latest-status b {
    text-align: left;
  }
}
.dashboard-page {
  min-height: 0;
}
.generation-copy small {
  font-size: 12px;
}
.generation-copy p {
  font-size: 13px;
}
.dashboard-card-heading p {
  font-size: 12px;
}
.chart-layout {
  grid-template-columns: minmax(0, 1fr) 152px minmax(190px, 1fr);
  column-gap: 28px;
  padding: 3px 0;
}
.donut {
  grid-column: 2;
  width: 152px;
  height: 152px;
}
.donut::after {
  inset: 24px;
}
.donut strong {
  font-size: 27px;
}
.donut span {
  font-size: 11px;
}
.chart-legend {
  grid-column: 3;
  justify-self: end;
  width: max-content;
  min-width: 150px;
  gap: 9px;
}
.chart-legend li {
  grid-template-columns: 9px auto auto;
  justify-content: end;
  column-gap: 8px;
  font-size: 13px;
}
.chart-legend strong {
  min-width: 24px;
  font-size: 14px;
  text-align: right;
}
.latest-status {
  width: min(460px, calc(100% - 64px));
  align-self: center;
  grid-template-columns: auto minmax(0, 1fr);
  padding: 8px 12px;
}
.latest-status span,
.latest-status b {
  font-size: 12px;
}
.latest-status b {
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
  line-height: 1.4;
}
@media (max-width: 1050px) {
  .chart-layout {
    grid-template-columns: minmax(0, 1fr) 140px minmax(160px, 1fr);
    column-gap: 20px;
  }
  .donut {
    width: 140px;
    height: 140px;
  }
  .donut::after {
    inset: 22px;
  }
  .latest-status {
    width: calc(100% - 36px);
  }
}
@media (max-width: 820px) {
  .chart-layout {
    grid-template-columns: 160px 1fr;
  }
  .donut {
    grid-column: 1;
    width: 152px;
    height: 152px;
  }
  .chart-legend {
    grid-column: 2;
    justify-self: end;
  }
  .latest-status {
    width: calc(100% - 48px);
  }
}
@media (max-width: 520px) {
  .chart-layout {
    grid-template-columns: 1fr;
    row-gap: 12px;
  }
  .donut,
  .chart-legend {
    grid-column: 1;
  }
  .chart-legend {
    justify-self: center;
  }
  .latest-status {
    width: calc(100% - 20px);
    grid-template-columns: 1fr;
  }
  .latest-status b {
    text-align: left;
  }
}
.dashboard-card-heading h3 {
  font-size: 18px;
  line-height: 1.25;
}
.donut {
  align-self: start;
  transform: translateY(-39px) rotate(-90deg);
}
.chart-legend {
  justify-self: start;
  width: auto;
  min-width: 160px;
}
.chart-legend li {
  grid-template-columns: 9px auto 28px;
  justify-content: start;
}
.chart-legend strong {
  text-align: left;
}
@media (max-width: 820px) {
  .donut {
    transform: rotate(-90deg);
  }
  .chart-legend {
    justify-self: start;
  }
}
@media (max-width: 520px) {
  .chart-legend {
    justify-self: center;
  }
}
.chart-layout {
  grid-template-columns: minmax(170px, 1fr) 228px minmax(180px, 1fr);
  column-gap: 20px;
}
.donut {
  grid-column: 2;
  align-self: center;
  width: 228px;
  height: 228px;
  transform: rotate(-90deg);
}
.donut::after {
  inset: 36px;
}
.donut strong {
  font-size: 32px;
}
.donut span {
  font-size: 12px;
}
.chart-legend {
  grid-column: 3;
  justify-self: end;
  width: 170px;
  min-width: 170px;
  margin-right: 2px;
}
.chart-legend li {
  grid-template-columns: 9px 105px 36px;
  justify-content: start;
  width: 170px;
}
.chart-legend li span {
  text-align: left;
}
.chart-legend strong {
  min-width: 0;
  text-align: right;
}
@media (max-width: 1200px) {
  .chart-layout {
    grid-template-columns: minmax(130px, 1fr) 190px minmax(170px, 1fr);
  }
  .donut {
    width: 190px;
    height: 190px;
  }
  .donut::after {
    inset: 30px;
  }
}
@media (max-width: 820px) {
  .chart-layout {
    grid-template-columns: 180px 1fr;
  }
  .donut {
    grid-column: 1;
    width: 170px;
    height: 170px;
  }
  .donut::after {
    inset: 27px;
  }
  .chart-legend {
    grid-column: 2;
    justify-self: end;
  }
}
@media (max-width: 520px) {
  .chart-layout {
    grid-template-columns: 1fr;
  }
  .donut,
  .chart-legend {
    grid-column: 1;
  }
  .chart-legend {
    justify-self: center;
  }
}
.chart-layout {
  position: relative;
  grid-template-columns: 1fr;
}
.donut {
  grid-column: 1;
  justify-self: center;
}
.chart-legend {
  position: absolute;
  right: 2px;
  top: 50%;
  grid-column: auto;
  transform: translateY(-50%);
}
@media (max-width: 820px) {
  .chart-layout {
    grid-template-columns: 180px 1fr;
  }
  .donut {
    grid-column: 1;
    justify-self: start;
  }
  .chart-legend {
    position: static;
    grid-column: 2;
    transform: none;
  }
}
@media (max-width: 520px) {
  .chart-layout {
    grid-template-columns: 1fr;
  }
  .donut,
  .chart-legend {
    grid-column: 1;
    justify-self: center;
  }
  .chart-legend {
    position: static;
  }
}
.donut {
  width: 171px;
  height: 171px;
}
.donut::after {
  inset: 27px;
}
.donut strong {
  font-size: 28px;
}
.chart-legend {
  width: 145px;
  min-width: 145px;
}
.chart-legend li {
  grid-template-columns: 9px 88px 30px;
  width: 145px;
  column-gap: 6px;
}
.chart-legend strong {
  min-width: 0;
}
@media (max-width: 820px) {
  .donut {
    width: 160px;
    height: 160px;
  }
  .donut::after {
    inset: 25px;
  }
}
@media (max-width: 520px) {
  .donut {
    width: 150px;
    height: 150px;
  }
  .donut::after {
    inset: 24px;
  }
}
</style>
