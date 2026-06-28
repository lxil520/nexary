<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type {
  PlatformDependencyEdge,
  PlatformHostSignal,
  PlatformIncidentCandidate,
  PlatformRequestFlow,
  PlatformServiceNode,
  PlatformSpan,
  PlatformTransactionMetric,
} from '../types/platform';

type SectionId =
  | 'overview'
  | 'topology'
  | 'request-flows'
  | 'incidents'
  | 'services'
  | 'hosts'
  | 'middleware'
  | 'resources'
  | 'integrations'
  | 'notifications'
  | 'policies';

type IncidentToneInput = Pick<PlatformIncidentCandidate, 'severity'>;
type FlowToneInput = Pick<PlatformRequestFlow, 'status'>;
type FlowMetricInput = Pick<PlatformRequestFlow, 'traceKey' | 'endpointKey' | 'durationMs'>;
type SpanMetricInput = Pick<PlatformSpan, 'startOffsetMs' | 'durationMs'>;

const props = defineProps<{ section: SectionId }>();

const { locale } = useLocale();
const { snapshot, isLoading, errorMessage, hasLoaded, refreshPlatform } = usePlatformData();

const selectedIncidentKey = ref<string | null>(null);
const selectedFlowKey = ref<string | null>(null);
const selectedServiceKey = ref<string | null>(null);
const selectedEdgeKey = ref<string | null>(null);
const selectedZone = ref('ALL');
const searchTerm = ref('');

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载治理平台',
        errorTitle: '治理平台数据不可用',
        emptyTitle: '还没有平台资产',
        emptyMessage: '启动治理平台样例，或向 /api/platform/resources 上报服务、依赖和接入器。',
        allZones: '全部机房',
        search: '搜索服务、资源、链路',
        overview: '总览',
        topology: '拓扑',
        requestFlows: '请求链路',
        incidents: '事故',
        services: '服务',
        hosts: '主机实例',
        middleware: '中间件',
        resources: '资源治理',
        integrations: '集成',
        notifications: '通知',
        policies: '策略计划',
        diagnosis: '诊断结论',
        incidentQueue: '事故队列',
        serviceWaterline: '服务水位',
        zoneWaterline: '机房水位',
        middlewareWaterline: '中间件水位',
        evidence: '证据',
        firstCheck: '优先检查',
        refs: '外部引用',
        spans: 'Span 瀑布',
        transactions: '交易统计',
        impact: '影响范围',
        dryRun: 'Dry-run',
        noDirectWrite: '只读 / 不写生产配置',
        zone: '机房',
        selectedEdge: '当前链路',
        source: '来源',
        target: '目标',
        resource: '资源',
        errors: '异常',
        total: '总量',
        failure: '失败',
        sample: '样本',
        qps: 'QPS',
        failureRate: '错误率',
        p95p99: 'P95 / P99',
        host: '主机',
        cpu: 'CPU',
        memory: '内存',
        swap: 'Swap',
        diskIo: '磁盘 IO',
        jitter: '抖动',
        loss: '丢包',
        threads: '线程',
        last: '最近异常',
        kind: '类型',
        usage: '水位',
        latency: '延迟',
        connectedServices: '调用方',
        localDiagnostics: '本地诊断',
        localResourceNote: 'Nexary 本地资源和故障 Trace 仍保留为 SDK 级诊断入口。',
        localIncidentNote: '平台事故使用脱敏资源键回链到本地证据，不写生产策略。',
        enabled: '启用',
        bound: '绑定',
        edges: '链路',
        warning: '警告',
        critical: '严重',
      }
    : {
        loading: 'Loading governance platform',
        errorTitle: 'Governance platform data is unavailable',
        emptyTitle: 'No platform assets yet',
        emptyMessage: 'Start the platform sample or post services, dependencies, and connectors to /api/platform/resources.',
        allZones: 'All zones',
        search: 'Search services, resources, flows',
        overview: 'Overview',
        topology: 'Topology',
        requestFlows: 'Request flows',
        incidents: 'Incidents',
        services: 'Services',
        hosts: 'Hosts',
        middleware: 'Middleware',
        resources: 'Resource governance',
        integrations: 'Integrations',
        notifications: 'Notifications',
        policies: 'Policy plans',
        diagnosis: 'Diagnosis',
        incidentQueue: 'Incident queue',
        serviceWaterline: 'Service waterline',
        zoneWaterline: 'Zone waterline',
        middlewareWaterline: 'Middleware waterline',
        evidence: 'Evidence',
        firstCheck: 'First check',
        refs: 'External refs',
        spans: 'Span waterfall',
        transactions: 'Transactions',
        impact: 'Impact',
        dryRun: 'Dry-run',
        noDirectWrite: 'Read-only / no production write',
        zone: 'Zone',
        selectedEdge: 'Selected edge',
        source: 'Source',
        target: 'Target',
        resource: 'Resource',
        errors: 'Errors',
        total: 'Total',
        failure: 'Failure',
        sample: 'Sample',
        qps: 'QPS',
        failureRate: 'Failure rate',
        p95p99: 'P95 / P99',
        host: 'Host',
        cpu: 'CPU',
        memory: 'Memory',
        swap: 'Swap',
        diskIo: 'Disk IO',
        jitter: 'Jitter',
        loss: 'Loss',
        threads: 'Threads',
        last: 'Last',
        kind: 'Kind',
        usage: 'Usage',
        latency: 'Latency',
        connectedServices: 'Services',
        localDiagnostics: 'Local diagnostics',
        localResourceNote: 'Nexary local resources and fault traces remain available for SDK-level diagnosis.',
        localIncidentNote: 'Platform incidents use sanitized resource keys to link back into local evidence.',
        enabled: 'enabled',
        bound: 'bound',
        edges: 'edges',
        warning: 'warning',
        critical: 'critical',
      },
);

const services = computed(() => snapshot.value?.services ?? []);
const dependencies = computed(() => snapshot.value?.topology.dependencies ?? []);
const incidents = computed(() => snapshot.value?.incidents ?? []);
const connectors = computed(() => snapshot.value?.connectors ?? []);
const flows = computed(() => snapshot.value?.requestFlows ?? []);
const transactions = computed(() => snapshot.value?.transactions ?? []);
const hosts = computed(() => snapshot.value?.hosts ?? []);
const middleware = computed(() => snapshot.value?.overview.middlewareWatermarks ?? []);
const notificationRoutes = computed(() => snapshot.value?.overview.notificationRoutes ?? []);
const policyPlans = computed(() => snapshot.value?.overview.policyPlans ?? []);
const serviceWatermarks = computed(() => snapshot.value?.overview.serviceWatermarks ?? []);
const zoneWatermarks = computed(() => snapshot.value?.overview.zoneWatermarks ?? []);
const summary = computed(() => snapshot.value?.overview.summary ?? null);
const zones = computed(() => ['ALL', ...Array.from(new Set([...services.value.map((service) => service.zoneKey), ...hosts.value.map((host) => host.zoneKey)]))]);

const visibleServices = computed(() =>
  services.value.filter((service) =>
    matchesZone(service.zoneKey) && matchesText([service.name, service.serviceKey, service.clusterKey, service.teamKey]),
  ),
);
const visibleDependencies = computed(() =>
  dependencies.value.filter((edge) => {
    const relatedZone =
      serviceByKey(edge.sourceKey)?.zoneKey ??
      serviceByKey(edge.targetKey)?.zoneKey ??
      middleware.value.find((item) => item.middlewareKey === edge.targetKey)?.zoneKey ??
      'unknown';
    return matchesZone(relatedZone) && matchesText([edge.sourceKey, edge.targetKey, edge.resourceKey, edge.kind]);
  }),
);
const visibleFlows = computed(() =>
  flows.value.filter((flow) => matchesZone(flow.zoneKey) && matchesText([flow.traceKey, flow.entryServiceKey, flow.endpointKey, flow.primaryError, flow.summary])),
);
const visibleIncidents = computed(() =>
  incidents.value.filter((incident) =>
    matchesZone(incident.impactScope.zoneKey) && matchesText([incident.title, incident.primaryResourceKey, incident.impactScope.serviceKey]),
  ),
);
const selectedIncident = computed(
  () => visibleIncidents.value.find((incident) => incident.incidentKey === selectedIncidentKey.value) ?? visibleIncidents.value[0] ?? null,
);
const selectedFlow = computed(
  () => visibleFlows.value.find((flow) => flow.traceKey === selectedFlowKey.value) ?? visibleFlows.value[0] ?? null,
);
const selectedService = computed(
  () => visibleServices.value.find((service) => service.serviceKey === selectedServiceKey.value) ?? visibleServices.value[0] ?? null,
);
const selectedEdge = computed(
  () => visibleDependencies.value.find((edge) => edgeKey(edge) === selectedEdgeKey.value) ?? visibleDependencies.value[0] ?? null,
);
const criticalHosts = computed(() => hosts.value.filter((host) => host.state === 'CRITICAL'));
const selectedEdgeStatus = computed(() => (selectedEdge.value ? edgeStatusLabel(selectedEdge.value) : 'HEALTHY'));

watch(() => props.section, () => {
  searchTerm.value = '';
});

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshPlatform();
  }
});

function matchesZone(zoneKey: string): boolean {
  return selectedZone.value === 'ALL' || zoneKey === selectedZone.value;
}

function matchesText(values: Array<string | null | undefined>): boolean {
  const needle = searchTerm.value.trim().toLowerCase();
  if (!needle) {
    return true;
  }
  return values.some((value) => (value ?? '').toLowerCase().includes(needle));
}

function serviceByKey(serviceKey: string): PlatformServiceNode | null {
  return services.value.find((service) => service.serviceKey === serviceKey) ?? null;
}

function edgeKey(edge: PlatformDependencyEdge): string {
  return `${edge.sourceKey}->${edge.targetKey}:${edge.resourceKey}`;
}

function edgeTone(edge: PlatformDependencyEdge): string {
  if (edge.criticalCount > 0) {
    return 'critical';
  }
  if (edge.warningCount > 0) {
    return 'warning';
  }
  if (edge.attributes.packetLossPercent && Number(edge.attributes.packetLossPercent) > 0) {
    return 'jitter';
  }
  return 'healthy';
}

function stateTone(state: string | null | undefined): string {
  const value = (state ?? '').toUpperCase();
  if (['CRITICAL', 'FAILED', 'ERROR', 'OPEN', 'NEEDS_ACTION'].includes(value)) {
    return 'critical';
  }
  if (['WARNING', 'DEGRADED', 'WATCH', 'SLOW', 'DISABLED'].includes(value)) {
    return 'warning';
  }
  return 'healthy';
}

function incidentTone(incident: IncidentToneInput): string {
  return incident.severity === 'CRITICAL' ? 'critical' : incident.severity === 'WARNING' ? 'warning' : 'healthy';
}

function flowTone(flow: FlowToneInput): string {
  return stateTone(flow.status === 'OK' ? 'HEALTHY' : flow.status);
}

function edgeStatusLabel(edge: PlatformDependencyEdge): string {
  const tone = edgeTone(edge);
  if (tone === 'jitter') {
    return 'NETWORK_JITTER';
  }
  return tone.toUpperCase();
}

function serviceStatusLabel(service: PlatformServiceNode | null): string {
  if (!service) {
    return 'HEALTHY';
  }
  if (service.criticalCount > 0) {
    return 'CRITICAL';
  }
  if (service.warningCount > 0) {
    return 'WARNING';
  }
  return 'HEALTHY';
}

function spanStyle(span: SpanMetricInput, flow: FlowMetricInput): Record<string, string> {
  const total = Math.max(flow.durationMs, 1);
  const offset = Math.min((span.startOffsetMs / total) * 100, 82);
  const width = Math.max(6, Math.min((span.durationMs / total) * 100, 100));
  return {
    '--offset': `${offset}%`,
    '--width': `${width}%`,
  };
}

function percent(value: number): string {
  return `${(value * 100).toFixed(value >= 0.1 ? 1 : 2)}%`;
}

function wholePercent(value: number): string {
  return `${Math.round(value)}%`;
}

function ms(value: number): string {
  return `${Math.round(value)}ms`;
}

function numberAttr(attributes: Record<string, string> | undefined, key: string, fallback = 0): number {
  const rawValue = attributes?.[key];
  if (rawValue === undefined || rawValue === '') {
    return fallback;
  }
  const parsed = Number(rawValue);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function txForFlow(flow: FlowMetricInput | null): PlatformTransactionMetric | null {
  if (!flow) {
    return transactions.value[0] ?? null;
  }
  return transactions.value.find((metric) => metric.sampleTraceKey === flow.traceKey || metric.endpointKey === flow.endpointKey) ?? null;
}

function txForEdge(edge: PlatformDependencyEdge | null): PlatformTransactionMetric | null {
  if (!edge) {
    return null;
  }
  const exact = transactions.value.find(
    (metric) =>
      metric.serviceKey === edge.sourceKey &&
      (metric.endpointKey === edge.resourceKey || edge.resourceKey.includes(metric.endpointKey) || metric.endpointKey.includes(edge.targetKey)),
  );
  if (exact) {
    return exact;
  }
  return transactions.value
    .filter((metric) => metric.serviceKey === edge.sourceKey)
    .sort((left, right) => right.failureRate - left.failureRate || right.p95Ms - left.p95Ms)[0] ?? null;
}

function edgeQps(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return tx.qps.toFixed(2);
  }
  return numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'qps').toFixed(0);
}

function edgeFailure(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return percent(tx.failureRate);
  }
  return percent(numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'errorRate'));
}

function edgeP95(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return ms(tx.p95Ms);
  }
  return ms(numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'p95Ms'));
}

function edgeP99(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return ms(tx.p99Ms);
  }
  return ms(numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'p99Ms'));
}

function hostForService(serviceKey: string): PlatformHostSignal[] {
  return hosts.value.filter((host) => host.serviceKey === serviceKey);
}
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="copy.loading" />
  <ErrorState v-else-if="errorMessage" :title="copy.errorTitle" :message="errorMessage" @retry="refreshPlatform" />
  <EmptyState v-else-if="hasLoaded && services.length === 0" :title="copy.emptyTitle" :message="copy.emptyMessage" />
  <div v-else class="ops-workbench">
    <section class="ops-section-bar" aria-label="Page filters">
      <label>
        <span>{{ copy.zone }}</span>
        <select v-model="selectedZone">
          <option v-for="zone in zones" :key="zone" :value="zone">{{ zone === 'ALL' ? copy.allZones : zone }}</option>
        </select>
      </label>
      <label class="ops-search">
        <span>{{ copy.search }}</span>
        <input v-model="searchTerm" type="search" :placeholder="copy.search" />
      </label>
    </section>

    <section v-if="section === 'overview'" class="ops-summary-strip">
      <article :data-tone="stateTone(summary?.health)">
        <span>Health</span>
        <strong>{{ summary?.health ?? 'HEALTHY' }}</strong>
      </article>
      <article data-tone="critical">
        <span>{{ copy.incidents }}</span>
        <strong>{{ summary?.criticalIncidents ?? 0 }} / {{ summary?.warningIncidents ?? 0 }}</strong>
      </article>
      <article>
        <span>{{ copy.services }}</span>
        <strong>{{ summary?.serviceCount ?? services.length }}</strong>
      </article>
      <article>
        <span>{{ copy.middleware }}</span>
        <strong>{{ summary?.middlewareCount ?? middleware.length }}</strong>
      </article>
      <article>
        <span>{{ copy.requestFlows }}</span>
        <strong>{{ flows.length }}</strong>
      </article>
      <article>
        <span>{{ copy.integrations }}</span>
        <strong>{{ summary?.connectorCount ?? connectors.length }}</strong>
      </article>
    </section>

    <template v-if="section === 'overview'">
      <section class="ops-overview-grid">
        <div class="ops-panel ops-panel--queue">
          <header>
            <span>{{ copy.incidentQueue }}</span>
            <strong>{{ visibleIncidents.length }}</strong>
          </header>
          <button
            v-for="incident in visibleIncidents"
            :key="incident.incidentKey"
            type="button"
            class="ops-list-button"
            :class="{ 'is-active': selectedIncident?.incidentKey === incident.incidentKey }"
            :data-tone="incidentTone(incident)"
            @click="selectedIncidentKey = incident.incidentKey"
          >
            <StatusBadge :label="incident.severity" :state="incident.severity" />
            <strong>{{ incident.title }}</strong>
            <small>{{ incident.impactScope.serviceKey }} / {{ incident.impactScope.zoneKey }}</small>
          </button>
        </div>

        <div class="ops-panel ops-topology-canvas">
          <header>
            <span>{{ copy.topology }}</span>
            <strong>{{ visibleDependencies.length }} {{ copy.edges }}</strong>
          </header>
          <div class="topology-board">
            <button
              v-for="edge in visibleDependencies.slice(0, 8)"
              :key="edgeKey(edge)"
              type="button"
              class="edge-card"
              :data-tone="edgeTone(edge)"
              @click="selectedEdgeKey = edgeKey(edge)"
            >
              <span>{{ edge.sourceKey }}</span>
              <i></i>
              <span>{{ edge.targetKey }}</span>
              <small>{{ edge.kind }} / {{ copy.qps }} {{ edgeQps(edge) }} / {{ copy.failureRate }} {{ edgeFailure(edge) }} / P95 {{ edgeP95(edge) }}</small>
            </button>
          </div>
        </div>

        <aside class="ops-panel ops-diagnosis">
          <header>
            <span>{{ copy.diagnosis }}</span>
            <StatusBadge :label="selectedIncident?.severity ?? 'INFO'" :state="selectedIncident?.severity ?? 'INFO'" />
          </header>
          <h2>{{ selectedIncident?.title ?? copy.diagnosis }}</h2>
          <p>{{ selectedIncident?.suggestedCheck?.message ?? copy.noDirectWrite }}</p>
          <dl v-if="selectedIncident">
            <div>
              <dt>{{ copy.impact }}</dt>
              <dd>{{ selectedIncident.impactScope.serviceKey }} / {{ selectedIncident.impactScope.clusterKey }} / {{ selectedIncident.impactScope.zoneKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.firstCheck }}</dt>
              <dd>{{ selectedIncident.primaryResourceKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.evidence }}</dt>
              <dd>{{ selectedIncident.evidenceCount }}</dd>
            </div>
          </dl>
        </aside>
      </section>

      <section class="ops-four-grid">
        <div class="ops-panel">
          <header><span>{{ copy.serviceWaterline }}</span><strong>{{ serviceWatermarks.length }}</strong></header>
          <div class="mini-table">
            <button v-for="service in serviceWatermarks.slice(0, 6)" :key="service.serviceKey" type="button" @click="selectedServiceKey = service.serviceKey">
              <span>{{ service.name }}</span>
              <b>{{ percent(service.errorRate) }}</b>
              <em>{{ ms(service.p95Ms) }}</em>
            </button>
          </div>
        </div>
        <div class="ops-panel">
          <header><span>{{ copy.zoneWaterline }}</span><strong>{{ zoneWatermarks.length }}</strong></header>
          <div class="mini-table">
            <button v-for="zone in zoneWatermarks" :key="zone.zoneKey" type="button" @click="selectedZone = zone.zoneKey">
              <span>{{ zone.zoneKey }}</span>
              <b>{{ wholePercent(zone.memoryPercent) }}</b>
              <em>{{ ms(zone.networkJitterMs) }}</em>
            </button>
          </div>
        </div>
        <div class="ops-panel">
          <header><span>{{ copy.middlewareWaterline }}</span><strong>{{ middleware.length }}</strong></header>
          <div class="mini-table">
            <button v-for="item in middleware" :key="item.middlewareKey" type="button">
              <span>{{ item.name }}</span>
              <b>{{ wholePercent(item.usagePercent) }}</b>
              <em>{{ ms(item.latencyMs) }}</em>
            </button>
          </div>
        </div>
        <div class="ops-panel">
          <header><span>{{ copy.hosts }}</span><strong>{{ criticalHosts.length }}</strong></header>
          <div class="mini-table">
            <button v-for="host in hosts.slice(0, 6)" :key="host.hostKey" type="button">
              <span>{{ host.hostKey }}</span>
              <b>{{ wholePercent(host.swapPercent) }}</b>
              <em>{{ host.lastError }}</em>
            </button>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="section === 'topology'">
      <section class="ops-topology-layout">
        <div class="ops-panel ops-topology-canvas ops-topology-canvas--large">
          <header>
            <span>{{ copy.topology }}</span>
            <strong>{{ visibleServices.length }} {{ copy.services }} / {{ visibleDependencies.length }} {{ copy.edges }}</strong>
          </header>
          <div class="topology-board topology-board--large">
            <button
              v-for="edge in visibleDependencies"
              :key="edgeKey(edge)"
              type="button"
              class="edge-card"
              :data-tone="edgeTone(edge)"
              :class="{ 'is-active': selectedEdge && edgeKey(edge) === edgeKey(selectedEdge) }"
              @click="selectedEdgeKey = edgeKey(edge)"
            >
              <span>{{ edge.sourceKey }}</span>
              <i></i>
              <span>{{ edge.targetKey }}</span>
              <small>{{ edge.kind }} / {{ copy.qps }} {{ edgeQps(edge) }} / {{ copy.failureRate }} {{ edgeFailure(edge) }} / {{ copy.p95p99 }} {{ edgeP95(edge) }} / {{ edgeP99(edge) }}</small>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel">
          <header><span>{{ copy.selectedEdge }}</span><StatusBadge :label="selectedEdgeStatus" :state="selectedEdgeStatus" /></header>
          <dl v-if="selectedEdge">
            <div><dt>{{ copy.source }}</dt><dd>{{ selectedEdge.sourceKey }}</dd></div>
            <div><dt>{{ copy.target }}</dt><dd>{{ selectedEdge.targetKey }}</dd></div>
            <div><dt>{{ copy.resource }}</dt><dd>{{ selectedEdge.resourceKey }}</dd></div>
            <div><dt>{{ copy.qps }}</dt><dd>{{ edgeQps(selectedEdge) }}</dd></div>
            <div><dt>{{ copy.failureRate }}</dt><dd>{{ edgeFailure(selectedEdge) }}</dd></div>
            <div><dt>{{ copy.p95p99 }}</dt><dd>{{ edgeP95(selectedEdge) }} / {{ edgeP99(selectedEdge) }}</dd></div>
            <div><dt>{{ copy.errors }}</dt><dd>{{ selectedEdge.warningCount }} {{ copy.warning }} / {{ selectedEdge.criticalCount }} {{ copy.critical }}</dd></div>
          </dl>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'request-flows'">
      <section class="ops-flow-layout">
        <aside class="ops-panel ops-flow-list">
          <header><span>{{ copy.requestFlows }}</span><strong>{{ visibleFlows.length }}</strong></header>
          <button
            v-for="flow in visibleFlows"
            :key="flow.traceKey"
            type="button"
            class="ops-list-button"
            :class="{ 'is-active': selectedFlow?.traceKey === flow.traceKey }"
            :data-tone="flowTone(flow)"
            @click="selectedFlowKey = flow.traceKey"
          >
            <strong>{{ flow.endpointKey }}</strong>
            <small>{{ flow.entryServiceKey }} / {{ flow.primaryError }} / {{ ms(flow.durationMs) }}</small>
          </button>
        </aside>
        <main class="ops-panel span-panel">
          <header>
            <span>{{ copy.spans }}</span>
            <strong>{{ selectedFlow?.traceKey ?? '-' }}</strong>
          </header>
          <div v-if="selectedFlow" class="span-waterfall">
            <article v-for="span in selectedFlow.spans" :key="span.spanId" :data-tone="stateTone(span.status)">
              <div>
                <strong>{{ span.operation }}</strong>
                <small>{{ span.serviceKey }} / {{ span.component }} / {{ span.errorType }}</small>
              </div>
              <span class="duration-line" :style="spanStyle(span, selectedFlow)"></span>
              <b>{{ ms(span.durationMs) }}</b>
            </article>
          </div>
        </main>
        <aside class="ops-panel detail-panel">
          <header><span>{{ copy.transactions }}</span><StatusBadge :label="selectedFlow?.status ?? 'OK'" :state="selectedFlow?.status ?? 'OK'" /></header>
          <dl v-if="txForFlow(selectedFlow)">
            <div><dt>{{ copy.total }}</dt><dd>{{ txForFlow(selectedFlow)?.total }}</dd></div>
            <div><dt>{{ copy.failure }}</dt><dd>{{ txForFlow(selectedFlow)?.failure }} / {{ percent(txForFlow(selectedFlow)?.failureRate ?? 0) }}</dd></div>
            <div><dt>{{ copy.p95p99 }}</dt><dd>{{ ms(txForFlow(selectedFlow)?.p95Ms ?? 0) }} / {{ ms(txForFlow(selectedFlow)?.p99Ms ?? 0) }}</dd></div>
            <div><dt>{{ copy.sample }}</dt><dd>{{ txForFlow(selectedFlow)?.sampleTraceKey || selectedFlow?.traceKey }}</dd></div>
          </dl>
          <h3>{{ copy.refs }}</h3>
          <div class="ref-list">
            <span v-for="ref in selectedFlow?.evidenceRefs ?? []" :key="`${ref.type}-${ref.refKey}`">{{ ref.type }} / {{ ref.refKey }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'incidents'">
      <section class="ops-two-column">
        <div class="ops-panel">
          <header><span>{{ copy.incidents }}</span><strong>{{ visibleIncidents.length }}</strong></header>
          <button
            v-for="incident in visibleIncidents"
            :key="incident.incidentKey"
            type="button"
            class="ops-list-button"
            :data-tone="incidentTone(incident)"
            :class="{ 'is-active': selectedIncident?.incidentKey === incident.incidentKey }"
            @click="selectedIncidentKey = incident.incidentKey"
          >
            <StatusBadge :label="incident.severity" :state="incident.severity" />
            <strong>{{ incident.title }}</strong>
            <small>{{ incident.primaryResourceKey }} / {{ incident.evidenceCount }} {{ copy.evidence }}</small>
          </button>
        </div>
        <aside class="ops-panel detail-panel">
          <header><span>{{ copy.evidence }}</span><StatusBadge :label="selectedIncident?.severity ?? 'INFO'" :state="selectedIncident?.severity ?? 'INFO'" /></header>
          <h2>{{ selectedIncident?.title ?? '-' }}</h2>
          <dl v-if="selectedIncident">
            <div><dt>{{ copy.impact }}</dt><dd>{{ selectedIncident.impactScope.serviceKey }} / {{ selectedIncident.impactScope.zoneKey }}</dd></div>
            <div><dt>{{ copy.firstCheck }}</dt><dd>{{ selectedIncident.suggestedCheck?.resourceKey ?? selectedIncident.primaryResourceKey }}</dd></div>
          </dl>
          <ol class="timeline-list">
            <li v-for="item in selectedIncident?.evidence ?? []" :key="`${item.timestamp}-${item.resourceKey}-${item.signalType}`">
              <strong>{{ item.signalType }}</strong>
              <span>{{ item.resourceKey }} / {{ item.outcome }} / {{ item.durationBucket }}</span>
              <small>{{ item.referenceType }} / {{ item.referenceKey }}</small>
            </li>
          </ol>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'services'">
      <section class="ops-two-column ops-two-column--wide">
        <div class="ops-panel">
          <header><span>{{ copy.services }}</span><strong>{{ visibleServices.length }}</strong></header>
          <div class="ops-table">
            <button v-for="service in visibleServices" :key="service.serviceKey" type="button" :class="{ 'is-active': selectedService?.serviceKey === service.serviceKey }" @click="selectedServiceKey = service.serviceKey">
              <span><strong>{{ service.name }}</strong><small>{{ service.clusterKey }} / {{ service.zoneKey }}</small></span>
              <b>{{ service.warningCount }} / {{ service.criticalCount }}</b>
              <em>{{ service.attributes.qps ?? '-' }} qps</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel">
          <header><span>{{ selectedService?.serviceKey ?? '-' }}</span><StatusBadge :label="serviceStatusLabel(selectedService)" :state="serviceStatusLabel(selectedService)" /></header>
          <dl v-if="selectedService">
            <div><dt>{{ copy.qps }}</dt><dd>{{ selectedService.attributes.qps ?? '-' }}</dd></div>
            <div><dt>{{ copy.failureRate }}</dt><dd>{{ selectedService.attributes.errorRate ?? '-' }}</dd></div>
            <div><dt>{{ copy.p95p99 }}</dt><dd>{{ selectedService.attributes.p95Ms ?? '-' }} / {{ selectedService.attributes.p99Ms ?? '-' }}ms</dd></div>
            <div><dt>{{ copy.hosts }}</dt><dd>{{ hostForService(selectedService.serviceKey).length }}</dd></div>
          </dl>
          <h3>{{ copy.topology }}</h3>
          <div class="ref-list">
            <span v-for="edge in dependencies.filter((edge) => edge.sourceKey === selectedService?.serviceKey)" :key="edgeKey(edge)">{{ edge.kind }} / {{ edge.targetKey }} / {{ edge.resourceKey }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'hosts'">
      <section class="ops-panel">
        <header><span>{{ copy.hosts }}</span><strong>{{ hosts.length }}</strong></header>
        <div class="matrix-table">
          <div class="matrix-head"><span>{{ copy.host }}</span><span>{{ copy.cpu }}</span><span>{{ copy.memory }}</span><span>{{ copy.swap }}</span><span>{{ copy.diskIo }}</span><span>{{ copy.jitter }}</span><span>{{ copy.loss }}</span><span>{{ copy.threads }}</span><span>{{ copy.last }}</span></div>
          <div v-for="host in hosts" :key="host.hostKey" class="matrix-row" :data-tone="stateTone(host.state)">
            <span><strong>{{ host.hostKey }}</strong><small>{{ host.serviceKey }} / {{ host.zoneKey }}</small></span>
            <b>{{ wholePercent(host.cpuPercent) }}</b>
            <b>{{ wholePercent(host.memoryPercent) }}</b>
            <b>{{ wholePercent(host.swapPercent) }}</b>
            <b>{{ wholePercent(host.diskIoPercent) }}</b>
            <b>{{ ms(host.networkJitterMs) }}</b>
            <b>{{ wholePercent(host.packetLossPercent) }}</b>
            <b>{{ host.jvmThreadCount }}</b>
            <em>{{ host.lastError }}</em>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="section === 'middleware'">
      <section class="ops-panel">
        <header><span>{{ copy.middleware }}</span><strong>{{ middleware.length }}</strong></header>
        <div class="ops-card-grid">
          <article v-for="item in middleware" :key="item.middlewareKey" :data-tone="stateTone(item.state)">
            <header><strong>{{ item.name }}</strong><StatusBadge :label="item.state" :state="item.state" /></header>
            <dl>
              <div><dt>{{ copy.kind }}</dt><dd>{{ item.kind }}</dd></div>
              <div><dt>{{ copy.zone }}</dt><dd>{{ item.zoneKey }}</dd></div>
              <div><dt>{{ copy.usage }}</dt><dd>{{ wholePercent(item.usagePercent) }}</dd></div>
              <div><dt>{{ copy.latency }}</dt><dd>{{ ms(item.latencyMs) }}</dd></div>
              <div><dt>{{ copy.failureRate }}</dt><dd>{{ percent(item.errorRate) }}</dd></div>
              <div><dt>{{ copy.connectedServices }}</dt><dd>{{ item.connectedServices }}</dd></div>
            </dl>
          </article>
        </div>
      </section>
    </template>

    <template v-else-if="section === 'resources'">
      <section class="ops-two-column">
        <div class="ops-panel">
          <header><span>{{ copy.resources }}</span><strong>{{ snapshot?.signals.length ?? 0 }}</strong></header>
          <div class="ops-table">
            <button v-for="signal in snapshot?.signals ?? []" :key="`${signal.timestamp}-${signal.resourceKey}-${signal.signalType}`" type="button">
              <span><strong>{{ signal.resourceKey }}</strong><small>{{ signal.serviceKey }} / {{ signal.signalType }}</small></span>
              <b>{{ signal.outcome }}</b>
              <em>{{ signal.durationBucket }}</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel">
          <header><span>{{ copy.localDiagnostics }}</span><StatusBadge label="READ_ONLY" state="INFO" /></header>
          <p>{{ copy.noDirectWrite }}</p>
          <div class="ref-list">
            <span>{{ copy.localResourceNote }}</span>
            <span>{{ copy.localIncidentNote }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'integrations'">
      <section class="ops-panel">
        <header><span>{{ copy.integrations }}</span><strong>{{ connectors.length }}</strong></header>
        <div class="ops-card-grid">
          <article v-for="connector in connectors" :key="connector.connectorKey" :data-tone="stateTone(connector.state)">
            <header><strong>{{ connector.displayName }}</strong><StatusBadge :label="connector.state" :state="connector.state" /></header>
            <p>{{ connector.kind }} / {{ connector.connectorKey }}</p>
            <small>{{ connector.lastMessage }}</small>
          </article>
        </div>
      </section>
    </template>

    <template v-else-if="section === 'notifications'">
      <section class="ops-panel">
        <header><span>{{ copy.notifications }}</span><strong>{{ notificationRoutes.length }}</strong></header>
        <div class="ops-table">
          <button v-for="route in notificationRoutes" :key="route.routeKey" type="button">
            <span><strong>{{ route.displayName }}</strong><small>{{ route.channel }} / {{ route.targetTeam }}</small></span>
            <b>{{ route.minSeverity }}</b>
            <em>{{ route.dryRun ? copy.dryRun : copy.enabled }} / {{ route.boundIncidentCount }} {{ copy.bound }}</em>
          </button>
        </div>
      </section>
    </template>

    <template v-else-if="section === 'policies'">
      <section class="ops-panel">
        <header><span>{{ copy.policies }}</span><strong>{{ policyPlans.length }}</strong></header>
        <div class="ops-table ops-table--plans">
          <button v-for="plan in policyPlans" :key="plan.planKey" type="button">
            <span><strong>{{ plan.title }}</strong><small>{{ plan.serviceKey }} / {{ plan.resourceKey }}</small></span>
            <b>{{ plan.risk }}</b>
            <em>{{ plan.state }} / {{ plan.proposedAction }}</em>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>
