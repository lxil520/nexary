<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import ResourceFilterBar from '../components/ResourceFilterBar.vue';
import ResourceTable from '../components/ResourceTable.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { uniqueSorted, useFilteredResources } from '../composables/useLocalFilters';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type { ResourceFilters } from '../types/console';
import type { PlatformServiceWatermark } from '../types/platform';

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { resources, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { snapshot, isLoading: platformLoading, hasLoaded: platformLoaded, refreshPlatform } = usePlatformData();
const { locale, t } = useLocale();
const filters = ref<ResourceFilters>({
  keyword: '',
  engine: 'ALL',
  kind: 'ALL',
  circuitState: 'ALL',
  provider: 'ALL',
  trafficClass: 'ALL',
  priority: 'ALL',
});

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载资源水位',
        serviceBoard: '服务水位',
        serviceBoardNote: '按团队、集群和机房看服务是否接近风险线。',
        middlewareBoard: '中间件与机房',
        localBoard: '本地治理资源',
        noService: '暂无服务水位',
        noMiddleware: '暂无中间件水位',
        noZone: '暂无机房水位',
        zoneBoard: '机房网络水位',
        waterline: '水位',
        instances: '实例',
        qps: 'QPS',
        p95: 'P95',
        p99: 'P99',
        errorRate: '错误率',
        gateway: '网关',
        sentinel: 'Sentinel',
        cpu: 'CPU',
        memory: '内存',
        latency: '延迟',
        usage: '使用率',
        connected: '连接服务',
      }
    : {
        loading: 'Loading resource watermarks',
        serviceBoard: 'Service Watermarks',
        serviceBoardNote: 'Review services by team, cluster, and zone before drilling into local resources.',
        middlewareBoard: 'Middleware and Zones',
        localBoard: 'Local Governed Resources',
        noService: 'No service watermarks',
        noMiddleware: 'No middleware watermarks',
        noZone: 'No zone watermarks',
        zoneBoard: 'Zone Network Watermarks',
        waterline: 'Watermark',
        instances: 'Instances',
        qps: 'QPS',
        p95: 'P95',
        p99: 'P99',
        errorRate: 'Error Rate',
        gateway: 'Gateway',
        sentinel: 'Sentinel',
        cpu: 'CPU',
        memory: 'Memory',
        latency: 'Latency',
        usage: 'Usage',
        connected: 'Connected Services',
      },
);

const filteredResources = useFilteredResources(resources, filters);
const engineOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.engine ?? 'LOCAL')));
const kindOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.kind)));
const circuitOptions = computed(() =>
  uniqueSorted(resources.value.map((resource) => resource.runtimeSnapshot?.circuitState ?? 'NO_STATE')),
);
const providerOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.provider)));
const trafficOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.trafficClass ?? 'online')));
const priorityOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.priority)));
const serviceWatermarks = computed(() =>
  [...(snapshot.value?.overview.serviceWatermarks ?? [])].sort(
    (left, right) => stateWeight(right.state) - stateWeight(left.state) || right.watermarkPercent - left.watermarkPercent,
  ),
);
const middlewareWatermarks = computed(() =>
  [...(snapshot.value?.overview.middlewareWatermarks ?? [])].sort(
    (left, right) => stateWeight(right.state) - stateWeight(left.state) || right.usagePercent - left.usagePercent,
  ),
);
const zoneWatermarks = computed(() => snapshot.value?.overview.zoneWatermarks ?? []);
const isInitialLoading = computed(() => (isLoading.value && !hasLoaded.value) || (platformLoading.value && !platformLoaded.value));

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

function refreshResources(): void {
  void Promise.all([refreshAll(), refreshPlatform()]);
}

onMounted(() => {
  if (!hasLoaded.value || !platformLoaded.value) {
    refreshResources();
  }
});
</script>

<template>
  <LoadingBlock v-if="isInitialLoading" :label="copy.loading" />
  <ErrorState
    v-else-if="errorMessage"
    :title="t('resources.errorTitle')"
    :message="errorMessage"
    @retry="refreshResources"
  />
  <div v-else class="ops-page">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.serviceBoard }}</p>
        <h2>{{ copy.serviceBoard }}</h2>
        <p>{{ copy.serviceBoardNote }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.serviceBoard }}</span>
          <strong>{{ serviceWatermarks.length }}</strong>
        </article>
        <article>
          <span>{{ copy.middlewareBoard }}</span>
          <strong>{{ middlewareWatermarks.length }}</strong>
        </article>
        <article>
          <span>{{ copy.zoneBoard }}</span>
          <strong>{{ zoneWatermarks.length }}</strong>
        </article>
        <article>
          <span>{{ copy.localBoard }}</span>
          <strong>{{ resources.length }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.serviceBoard }}</span>
          <h3>{{ copy.serviceBoard }}</h3>
          <p>{{ copy.serviceBoardNote }}</p>
        </div>
        <span>{{ serviceWatermarks.length }} {{ t('state.shown') }}</span>
      </header>
      <EmptyState v-if="serviceWatermarks.length === 0" :title="copy.noService" :message="t('resources.noResourcesMessage')" />
      <div v-else class="ops-service-matrix">
        <button
          v-for="service in serviceWatermarks"
          :key="service.serviceKey"
          type="button"
          class="ops-service-card"
          :data-tone="stateTone(service.state)"
          @click="emit('selectResource', serviceResourceKey(service))"
        >
          <span class="ops-service-card__top">
            <strong>{{ service.name }}</strong>
            <StatusBadge :label="service.state" :state="service.state" />
          </span>
          <small>{{ service.teamKey }} / {{ service.clusterKey }} / {{ service.zoneKey }}</small>
          <span class="ops-meter" :data-tone="stateTone(service.state)">
            <i :style="{ width: `${Math.min(service.watermarkPercent, 100)}%` }"></i>
          </span>
          <span class="ops-kv-row">
            <span><b>{{ copy.waterline }}</b><em>{{ formatMetricPercent(service.watermarkPercent) }}</em></span>
            <span><b>{{ copy.instances }}</b><em>{{ service.instanceCount }}</em></span>
            <span><b>{{ copy.qps }}</b><em>{{ service.qps }}</em></span>
            <span><b>{{ copy.p95 }}</b><em>{{ service.p95Ms }}ms</em></span>
            <span><b>{{ copy.p99 }}</b><em>{{ service.p99Ms }}ms</em></span>
            <span><b>{{ copy.errorRate }}</b><em>{{ formatPercent(service.errorRate) }}</em></span>
            <span><b>{{ copy.gateway }}</b><em>{{ service.gatewayState }}</em></span>
            <span><b>{{ copy.sentinel }}</b><em>{{ service.sentinelState }}</em></span>
          </span>
        </button>
      </div>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.middlewareBoard }}</span>
            <h3>{{ copy.middlewareBoard }}</h3>
          </div>
        </header>
        <EmptyState v-if="middlewareWatermarks.length === 0" :title="copy.noMiddleware" :message="t('state.noResourcesMessage')" />
        <div v-else class="ops-table-list">
          <article v-for="item in middlewareWatermarks" :key="item.middlewareKey" :data-tone="stateTone(item.state)">
            <div>
              <strong>{{ item.name }}</strong>
              <small>{{ item.kind }} / {{ item.zoneKey }}</small>
            </div>
            <span>{{ copy.usage }} <b>{{ formatMetricPercent(item.usagePercent) }}</b></span>
            <span>{{ copy.latency }} <b>{{ item.latencyMs }}ms</b></span>
            <span>{{ copy.connected }} <b>{{ item.connectedServices }}</b></span>
          </article>
        </div>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.zoneBoard }}</span>
            <h3>{{ copy.zoneBoard }}</h3>
          </div>
        </header>
        <EmptyState v-if="zoneWatermarks.length === 0" :title="copy.noZone" :message="t('state.noResourcesMessage')" />
        <div v-else class="ops-zone-strip ops-zone-strip--stacked">
          <article v-for="zone in zoneWatermarks" :key="zone.zoneKey" :data-tone="stateTone(zone.state)">
            <strong>{{ zone.zoneKey }}</strong>
            <span>{{ copy.cpu }} {{ formatMetricPercent(zone.cpuPercent) }} / {{ copy.memory }} {{ formatMetricPercent(zone.memoryPercent) }}</span>
            <small>jitter {{ zone.networkJitterMs }}ms / loss {{ formatMetricPercent(zone.packetLossPercent) }} / http {{ formatPercent(zone.httpFailureRate) }}</small>
          </article>
        </div>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.localBoard }}</span>
          <h3>{{ copy.localBoard }}</h3>
        </div>
        <span>{{ filteredResources.length }} {{ t('state.shown') }}</span>
      </header>
      <ResourceFilterBar
        :filters="filters"
        :engine-options="engineOptions"
        :kind-options="kindOptions"
        :circuit-options="circuitOptions"
        :provider-options="providerOptions"
        :traffic-options="trafficOptions"
        :priority-options="priorityOptions"
        @update="filters = $event"
      />
      <EmptyState
        v-if="hasLoaded && resources.length === 0"
        :title="t('resources.noResources')"
        :message="t('resources.noResourcesMessage')"
      />
      <EmptyState
        v-else-if="filteredResources.length === 0"
        :title="t('resources.noMatch')"
        :message="t('resources.noMatchMessage')"
      />
      <ResourceTable v-else :resources="filteredResources" @select="emit('selectResource', $event)" />
    </section>
  </div>
</template>
