<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type { PlatformServiceNode } from '../types/platform';

const { locale } = useLocale();
const { snapshot, isLoading, errorMessage, hasLoaded, refreshPlatform } = usePlatformData();

const selectedZone = ref('ALL');
const selectedServiceKey = ref<string | null>(null);

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载平台拓扑',
        errorTitle: '平台数据不可用',
        emptyTitle: '还没有平台资产',
        emptyMessage: '启动治理平台样例，或向 /api/platform/resources 上报服务、依赖和接入器。',
        title: '服务拓扑与接入',
        subtitle: '这个页面只回答服务、机房、中间件和治理工具怎么串起来。',
        allZones: '全部机房',
        topology: '拓扑图',
        incidentQueue: '事故队列',
        connectors: '工具接入',
        dependencies: '依赖边',
        noIncidents: '没有事故候选',
        noConnectors: '没有接入器',
        noDependencies: '没有依赖',
        source: '来源',
        target: '目标',
        resource: '资源',
        kind: '类型',
        selected: '当前服务',
        warnings: '警告',
        critical: '严重',
        team: '团队',
        cluster: '集群',
        zone: '机房',
        connectedTools: '接入工具',
      }
    : {
        loading: 'Loading platform topology',
        errorTitle: 'Platform data is unavailable',
        emptyTitle: 'No platform assets yet',
        emptyMessage: 'Start the platform sample or post services, dependencies, and connectors to /api/platform/resources.',
        title: 'Service Topology and Integrations',
        subtitle: 'This page only answers how services, zones, middleware, and governance tools connect.',
        allZones: 'All Zones',
        topology: 'Topology',
        incidentQueue: 'Incident Queue',
        connectors: 'Tool Integrations',
        dependencies: 'Dependency Edges',
        noIncidents: 'No incident candidates',
        noConnectors: 'No connectors',
        noDependencies: 'No dependencies',
        source: 'Source',
        target: 'Target',
        resource: 'Resource',
        kind: 'Kind',
        selected: 'Selected Service',
        warnings: 'Warnings',
        critical: 'Critical',
        team: 'Team',
        cluster: 'Cluster',
        zone: 'Zone',
        connectedTools: 'Connected Tools',
      },
);

const services = computed(() => snapshot.value?.topology.services ?? []);
const dependencies = computed(() => snapshot.value?.topology.dependencies ?? []);
const incidents = computed(() => snapshot.value?.incidents ?? []);
const connectors = computed(() => snapshot.value?.connectors ?? []);
const zones = computed(() => ['ALL', ...Array.from(new Set(services.value.map((service) => service.zoneKey)))]);
const visibleServices = computed(() =>
  services.value.filter((service) => selectedZone.value === 'ALL' || service.zoneKey === selectedZone.value),
);
const selectedService = computed(() => {
  if (visibleServices.value.length === 0) {
    return null;
  }
  return visibleServices.value.find((service) => service.serviceKey === selectedServiceKey.value) ?? visibleServices.value[0];
});
const visibleDependencies = computed(() =>
  dependencies.value.filter((edge) => {
    if (selectedZone.value === 'ALL') {
      return true;
    }
    return visibleServices.value.some((service) => service.serviceKey === edge.sourceKey || service.serviceKey === edge.targetKey);
  }),
);
const connectedDependencies = computed(() => {
  const service = selectedService.value;
  if (!service) {
    return visibleDependencies.value.slice(0, 8);
  }
  return visibleDependencies.value.filter((edge) => edge.sourceKey === service.serviceKey || edge.targetKey === service.serviceKey).slice(0, 8);
});

function selectService(service: PlatformServiceNode): void {
  selectedServiceKey.value = service.serviceKey;
}

function serviceTone(service: PlatformServiceNode): string {
  if (service.criticalCount > 0) {
    return 'critical';
  }
  if (service.warningCount > 0) {
    return 'warning';
  }
  return 'healthy';
}

function connectorTone(state: string): string {
  if (state === 'FAILED') {
    return 'critical';
  }
  if (state === 'DEGRADED' || state === 'DISABLED') {
    return 'warning';
  }
  return 'healthy';
}

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshPlatform();
  }
});
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="copy.loading" />
  <ErrorState v-else-if="errorMessage" :title="copy.errorTitle" :message="errorMessage" @retry="refreshPlatform" />
  <EmptyState v-else-if="hasLoaded && services.length === 0" :title="copy.emptyTitle" :message="copy.emptyMessage" />
  <div v-else class="ops-page ops-topology">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.topology }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.topology }}</span>
          <strong>{{ services.length }}</strong>
        </article>
        <article>
          <span>{{ copy.dependencies }}</span>
          <strong>{{ dependencies.length }}</strong>
        </article>
        <article>
          <span>{{ copy.connectedTools }}</span>
          <strong>{{ connectors.length }}</strong>
        </article>
        <article>
          <span>{{ copy.incidentQueue }}</span>
          <strong>{{ incidents.length }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-toolbar">
      <button
        v-for="zone in zones"
        :key="zone"
        type="button"
        :class="{ 'is-active': selectedZone === zone }"
        @click="selectedZone = zone"
      >
        {{ zone === 'ALL' ? copy.allZones : zone }}
      </button>
    </section>

    <section class="ops-topology-grid">
      <div class="ops-panel ops-topology-map">
        <header>
          <div>
            <span>{{ copy.topology }}</span>
            <h3>{{ copy.topology }}</h3>
          </div>
          <span>{{ visibleServices.length }} {{ copy.topology }}</span>
        </header>
        <div class="ops-node-map">
          <button
            v-for="service in visibleServices"
            :key="service.serviceKey"
            type="button"
            class="ops-node"
            :class="{ 'is-active': selectedService?.serviceKey === service.serviceKey }"
            :data-tone="serviceTone(service)"
            @click="selectService(service)"
          >
            <span class="ops-node__icon">{{ service.name.slice(0, 2).toUpperCase() }}</span>
            <strong>{{ service.name }}</strong>
            <small>{{ service.clusterKey }} / {{ service.zoneKey }}</small>
            <em>{{ service.warningCount }} {{ copy.warnings }} / {{ service.criticalCount }} {{ copy.critical }}</em>
          </button>
        </div>
      </div>

      <aside class="ops-panel">
        <header>
          <div>
            <span>{{ copy.selected }}</span>
            <h3>{{ selectedService?.name ?? copy.selected }}</h3>
          </div>
          <StatusBadge :label="selectedService ? serviceTone(selectedService) : 'NONE'" :state="selectedService ? serviceTone(selectedService) : 'NONE'" />
        </header>
        <dl v-if="selectedService" class="ops-definition-list">
          <div>
            <dt>{{ copy.team }}</dt>
            <dd>{{ selectedService.teamKey }}</dd>
          </div>
          <div>
            <dt>{{ copy.cluster }}</dt>
            <dd>{{ selectedService.clusterKey }}</dd>
          </div>
          <div>
            <dt>{{ copy.zone }}</dt>
            <dd>{{ selectedService.zoneKey }}</dd>
          </div>
        </dl>
        <EmptyState v-else :title="copy.emptyTitle" :message="copy.emptyMessage" />
      </aside>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.dependencies }}</span>
            <h3>{{ copy.dependencies }}</h3>
          </div>
          <span>{{ connectedDependencies.length }} {{ copy.dependencies }}</span>
        </header>
        <EmptyState v-if="connectedDependencies.length === 0" :title="copy.noDependencies" :message="copy.emptyMessage" />
        <div v-else class="ops-edge-list">
          <article v-for="edge in connectedDependencies" :key="`${edge.sourceKey}-${edge.targetKey}-${edge.resourceKey}`">
            <span>
              <strong>{{ edge.sourceKey }}</strong>
              <small>{{ copy.source }}</small>
            </span>
            <i></i>
            <span>
              <strong>{{ edge.targetKey }}</strong>
              <small>{{ copy.target }}</small>
            </span>
            <em>{{ edge.kind }} / {{ edge.resourceKey }}</em>
          </article>
        </div>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.connectors }}</span>
            <h3>{{ copy.connectors }}</h3>
          </div>
          <span>{{ connectors.length }} {{ copy.connectors }}</span>
        </header>
        <EmptyState v-if="connectors.length === 0" :title="copy.noConnectors" :message="copy.emptyMessage" />
        <div v-else class="ops-connector-grid">
          <article v-for="connector in connectors" :key="connector.connectorKey" :data-tone="connectorTone(connector.state)">
            <span>{{ connector.kind }}</span>
            <strong>{{ connector.displayName }}</strong>
            <small>{{ connector.lastMessage }}</small>
            <StatusBadge :label="connector.state" :state="connector.state" />
          </article>
        </div>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.incidentQueue }}</span>
          <h3>{{ copy.incidentQueue }}</h3>
        </div>
        <span>{{ incidents.length }} {{ copy.incidentQueue }}</span>
      </header>
      <EmptyState v-if="incidents.length === 0" :title="copy.noIncidents" :message="copy.emptyMessage" />
      <div v-else class="ops-incident-list ops-incident-list--compact">
        <article v-for="incident in incidents" :key="incident.incidentKey">
          <div>
            <StatusBadge :label="incident.severity" :state="incident.severity" />
            <strong>{{ incident.title }}</strong>
            <small>{{ incident.impactScope.serviceKey }} / {{ incident.impactScope.clusterKey }} / {{ incident.impactScope.zoneKey }}</small>
          </div>
          <dl>
            <div>
              <dt>{{ copy.resource }}</dt>
              <dd>{{ incident.primaryResourceKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.critical }}</dt>
              <dd>{{ incident.evidenceCount }}</dd>
            </div>
          </dl>
        </article>
      </div>
    </section>
  </div>
</template>
