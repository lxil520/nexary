<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type { PlatformServiceWatermark } from '../types/platform';

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { summary, resources, events, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const {
  snapshot,
  isLoading: platformLoading,
  errorMessage: platformErrorMessage,
  hasLoaded: platformLoaded,
  refreshPlatform,
} = usePlatformData();
const { enumLabel, formatTimestamp, locale, t } = useLocale();

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载治理工作台',
        errorTitle: '治理数据不可用',
        title: '今日值班态势',
        subtitle: '先看事故，再看影响，再决定进入资源、事件或 Trace。',
        health: '平台态势',
        incidents: '事故候选',
        affectedZones: '影响机房',
        hotServices: '高风险服务',
        localGuard: '本地治理信号',
        openCircuits: '打开熔断',
        rejected: '拒绝',
        stoppedTrace: '停止 Trace',
        failures: '失败',
        quarantine: '封禁候选',
        currentIncident: '首要事故',
        noIncident: '当前没有事故候选',
        evidence: '证据',
        primaryResource: '首要资源',
        enterResource: '查看资源',
        waterline: '水位',
        qps: 'QPS',
        p95: 'P95',
        errorRate: '错误率',
        noServices: '暂无服务水位',
        noLocal: '本地治理还没有事件',
        recentEvents: '最近事件',
        middleware: '中间件热点',
        zones: '机房水位',
        connectors: '接入工具',
        stateNeedsAction: '需要处理',
        stateWatch: '观察',
        stateHealthy: '正常',
      }
    : {
        loading: 'Loading governance workbench',
        errorTitle: 'Governance data is unavailable',
        title: 'Today Operations Posture',
        subtitle: 'Start with incidents, inspect impact, then drill into resources, events, or traces.',
        health: 'Platform State',
        incidents: 'Incident Candidates',
        affectedZones: 'Affected Zones',
        hotServices: 'High-Risk Services',
        localGuard: 'Local Guard Signals',
        openCircuits: 'Open Circuits',
        rejected: 'Rejected',
        stoppedTrace: 'Stopped Traces',
        failures: 'Failures',
        quarantine: 'Quarantine Candidates',
        currentIncident: 'Primary Incident',
        noIncident: 'No incident candidates',
        evidence: 'Evidence',
        primaryResource: 'Primary Resource',
        enterResource: 'Open Resource',
        waterline: 'Watermark',
        qps: 'QPS',
        p95: 'P95',
        errorRate: 'Error Rate',
        noServices: 'No service watermarks',
        noLocal: 'No local governance events yet',
        recentEvents: 'Recent Events',
        middleware: 'Middleware Hotspots',
        zones: 'Zone Watermarks',
        connectors: 'Connected Tools',
        stateNeedsAction: 'Needs Action',
        stateWatch: 'Watch',
        stateHealthy: 'Healthy',
      },
);

const overview = computed(() => snapshot.value?.overview ?? null);
const incidents = computed(() => snapshot.value?.incidents ?? []);
const primaryIncident = computed(() => incidents.value[0] ?? null);
const hotServices = computed(() =>
  [...(overview.value?.serviceWatermarks ?? [])]
    .sort((left, right) => stateWeight(right.state) - stateWeight(left.state) || right.watermarkPercent - left.watermarkPercent)
    .slice(0, 5),
);
const hotMiddleware = computed(() =>
  [...(overview.value?.middlewareWatermarks ?? [])]
    .sort((left, right) => stateWeight(right.state) - stateWeight(left.state) || right.usagePercent - left.usagePercent)
    .slice(0, 3),
);
const zoneWatermarks = computed(() => overview.value?.zoneWatermarks ?? []);
const recentEvents = computed(() => events.value.slice(0, 5));
const attentionResources = computed(() =>
  [...resources.value]
    .filter((resource) => {
      const runtime = resource.runtimeSnapshot;
      return (
        runtime?.circuitState === 'OPEN' ||
        (runtime?.totalRejections ?? 0) > 0 ||
        resource.lastTraceStopReason && resource.lastTraceStopReason !== 'NONE'
      );
    })
    .slice(0, 5),
);
const localSignals = computed(() => [
  { label: copy.value.openCircuits, value: summary.value?.openCircuitCount ?? 0, tone: 'critical' },
  { label: copy.value.rejected, value: summary.value?.rejectedCount ?? 0, tone: 'warning' },
  { label: copy.value.stoppedTrace, value: summary.value?.stoppedTraceCount ?? 0, tone: 'warning' },
  { label: copy.value.failures, value: summary.value?.failureCount ?? 0, tone: 'critical' },
  { label: copy.value.quarantine, value: summary.value?.quarantineCandidateCount ?? 0, tone: 'critical' },
]);
const healthLabel = computed(() => {
  const health = overview.value?.summary.health ?? 'HEALTHY';
  if (health === 'NEEDS_ACTION' || health === 'CRITICAL') {
    return copy.value.stateNeedsAction;
  }
  if (health === 'WATCH' || health === 'WARNING') {
    return copy.value.stateWatch;
  }
  return copy.value.stateHealthy;
});
const isInitialLoading = computed(() => (isLoading.value && !hasLoaded.value) || (platformLoading.value && !platformLoaded.value));
const mergedError = computed(() => errorMessage.value ?? platformErrorMessage.value);

function stateWeight(state: string): number {
  const normalized = state.toUpperCase();
  if (['CRITICAL', 'FAILED', 'OPEN', 'NEEDS_ACTION'].includes(normalized)) {
    return 3;
  }
  if (['WARNING', 'DEGRADED', 'HALF_OPEN', 'WATCH'].includes(normalized)) {
    return 2;
  }
  return 1;
}

function stateTone(state: string): string {
  const weight = stateWeight(state);
  if (weight >= 3) {
    return 'critical';
  }
  if (weight === 2) {
    return 'warning';
  }
  return 'healthy';
}

function serviceResourceKey(service: PlatformServiceWatermark): string {
  if (service.serviceKey === 'room-resource') {
    return 'downstream:room-resource:allocate';
  }
  if (service.serviceKey === 'open-api') {
    return 'gateway:open-api:route';
  }
  return service.serviceKey;
}

function formatPercent(value: number): string {
  return `${Math.round(value * 1000) / 10}%`;
}

function formatMetricPercent(value: number): string {
  return `${Math.round(value * 10) / 10}%`;
}

function refreshWorkbench(): void {
  void Promise.all([refreshAll(), refreshPlatform()]);
}

onMounted(() => {
  if (!hasLoaded.value || !platformLoaded.value) {
    refreshWorkbench();
  }
});
</script>

<template>
  <LoadingBlock v-if="isInitialLoading" :label="copy.loading" />
  <ErrorState
    v-else-if="mergedError"
    :title="copy.errorTitle"
    :message="mergedError"
    @retry="refreshWorkbench"
  />
  <div v-else class="ops-page ops-home">
    <section class="ops-hero" :data-state="stateTone(overview?.summary.health ?? 'HEALTHY')">
      <div>
        <p class="eyebrow">{{ copy.health }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.health }}</span>
          <strong>{{ healthLabel }}</strong>
        </article>
        <article>
          <span>{{ copy.incidents }}</span>
          <strong>{{ overview?.summary.criticalIncidents ?? incidents.length }}</strong>
        </article>
        <article>
          <span>{{ copy.affectedZones }}</span>
          <strong>{{ overview?.summary.zoneCount ?? zoneWatermarks.length }}</strong>
        </article>
        <article>
          <span>{{ copy.connectors }}</span>
          <strong>{{ overview?.summary.connectorCount ?? snapshot?.connectors.length ?? 0 }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-home-grid">
      <div class="ops-panel ops-panel--incident">
        <header>
          <div>
            <span>{{ copy.currentIncident }}</span>
            <h3>{{ primaryIncident?.title ?? copy.noIncident }}</h3>
          </div>
          <StatusBadge
            :label="primaryIncident?.severity ?? 'HEALTHY'"
            :state="primaryIncident?.severity ?? 'HEALTHY'"
          />
        </header>
        <div v-if="primaryIncident" class="ops-incident-summary">
          <dl>
            <div>
              <dt>{{ copy.primaryResource }}</dt>
              <dd>{{ primaryIncident.primaryResourceKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.evidence }}</dt>
              <dd>{{ primaryIncident.evidenceCount }}</dd>
            </div>
            <div>
              <dt>{{ copy.affectedZones }}</dt>
              <dd>{{ primaryIncident.impactScope.zoneKey }}</dd>
            </div>
          </dl>
          <button
            v-if="primaryIncident.suggestedCheck"
            class="button button--primary"
            type="button"
            @click="emit('selectResource', primaryIncident.suggestedCheck.resourceKey)"
          >
            {{ copy.enterResource }}
          </button>
        </div>
        <EmptyState v-else :title="copy.noIncident" :message="t('state.noEventsMessage')" />
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.localGuard }}</span>
            <h3>{{ copy.localGuard }}</h3>
          </div>
        </header>
        <div class="ops-signal-grid">
          <article v-for="signal in localSignals" :key="signal.label" :data-tone="signal.tone">
            <span>{{ signal.label }}</span>
            <strong>{{ signal.value }}</strong>
          </article>
        </div>
      </div>
    </section>

    <section class="ops-work-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.waterline }}</span>
            <h3>{{ copy.hotServices }}</h3>
          </div>
          <span>{{ hotServices.length }} {{ t('state.shown') }}</span>
        </header>
        <EmptyState v-if="hotServices.length === 0" :title="copy.noServices" :message="t('resources.noResourcesMessage')" />
        <div v-else class="ops-watermark-list">
          <button
            v-for="service in hotServices"
            :key="service.serviceKey"
            type="button"
            class="ops-watermark-row"
            @click="emit('selectResource', serviceResourceKey(service))"
          >
            <span>
              <strong>{{ service.name }}</strong>
              <small>{{ service.clusterKey }} / {{ service.zoneKey }}</small>
            </span>
            <span class="ops-meter" :data-tone="stateTone(service.state)">
              <i :style="{ width: `${Math.min(service.watermarkPercent, 100)}%` }"></i>
            </span>
            <span class="ops-row-stats">
              <b>{{ service.watermarkPercent }}%</b>
              <small>{{ copy.qps }} {{ service.qps }} / {{ copy.p95 }} {{ service.p95Ms }}ms / {{ copy.errorRate }} {{ formatPercent(service.errorRate) }}</small>
            </span>
          </button>
        </div>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.zones }}</span>
            <h3>{{ copy.zones }}</h3>
          </div>
        </header>
        <div class="ops-zone-strip">
          <article v-for="zone in zoneWatermarks" :key="zone.zoneKey" :data-tone="stateTone(zone.state)">
            <strong>{{ zone.zoneKey }}</strong>
            <span>CPU {{ formatMetricPercent(zone.cpuPercent) }} / MEM {{ formatMetricPercent(zone.memoryPercent) }}</span>
            <small>jitter {{ zone.networkJitterMs }}ms / loss {{ formatMetricPercent(zone.packetLossPercent) }}</small>
          </article>
        </div>
        <div class="ops-mini-list">
          <article v-for="middleware in hotMiddleware" :key="middleware.middlewareKey">
            <strong>{{ middleware.name }}</strong>
            <span>{{ middleware.kind }} / {{ middleware.zoneKey }}</span>
            <b>{{ formatMetricPercent(middleware.usagePercent) }}</b>
          </article>
        </div>
      </div>
    </section>

    <section class="ops-work-grid ops-work-grid--lower">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.recentEvents }}</span>
            <h3>{{ copy.recentEvents }}</h3>
          </div>
        </header>
        <EmptyState v-if="recentEvents.length === 0" :title="copy.noLocal" :message="t('state.noEventsMessage')" />
        <ol v-else class="ops-timeline">
          <li v-for="event in recentEvents" :key="`${event.timestamp}-${event.resourceKey}`">
            <time>{{ formatTimestamp(event.timestamp) }}</time>
            <strong>{{ event.resourceKey }}</strong>
            <span>{{ enumLabel(event.outcome) }} / {{ enumLabel(event.tracePrimaryStopReason ?? event.rejectionReason) }}</span>
          </li>
        </ol>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ t('overview.attention') }}</span>
            <h3>{{ t('overview.attention') }}</h3>
          </div>
        </header>
        <EmptyState v-if="attentionResources.length === 0" :title="t('state.noResources')" :message="t('state.noResourcesMessage')" />
        <div v-else class="ops-resource-stack">
          <button
            v-for="resource in attentionResources"
            :key="resource.resourceKey"
            type="button"
            @click="emit('selectResource', resource.resourceKey)"
          >
            <span>
              <strong>{{ resource.name }}</strong>
              <small>{{ resource.resourceKey }}</small>
            </span>
            <StatusBadge
              :label="resource.runtimeSnapshot?.circuitState ?? resource.lastTraceStopReason ?? 'NONE'"
              :state="resource.runtimeSnapshot?.circuitState ?? resource.lastTraceStopReason ?? 'NONE'"
            />
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
