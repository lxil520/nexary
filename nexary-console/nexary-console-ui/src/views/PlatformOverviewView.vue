<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type { PlatformServiceNode } from '../types/platform';

const { locale } = useLocale();
const { snapshot, isLoading, errorMessage, hasLoaded, serviceCount, incidentCount, dependencyCount, connectorCount, refreshPlatform } =
  usePlatformData();

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载平台视图',
        errorTitle: '平台数据不可用',
        emptyTitle: '还没有平台资产',
        emptyMessage: '启动 governance platform sample，或向 /api/platform/resources 上报资源后再刷新。',
        scope: '平台模式',
        title: '治理集成工作台',
        subtitle: '汇聚服务组、集群、机房、中间件、治理事件和连接器状态。',
        services: '服务组',
        dependencies: '依赖',
        incidents: '事故候选',
        connectors: '连接器',
        priority: '优先处理',
        topology: '拓扑关系',
        serviceHealth: '服务健康',
        integrations: '接入状态',
        evidence: '证据',
        suggested: '建议检查',
        primaryResource: '首要资源',
        evidenceCount: '证据',
        impactedResources: '影响资源',
        timeline: '证据时间线',
        reference: '引用',
        window: '窗口',
        noIncidents: '当前没有事故候选',
        noConnectors: '没有连接器状态',
        warnings: '警告',
        critical: '严重',
        cluster: '集群',
        zone: '机房',
        team: '团队',
      }
    : {
        loading: 'Loading platform view',
        errorTitle: 'Platform data is unavailable',
        emptyTitle: 'No platform assets yet',
        emptyMessage: 'Start the governance platform sample or post resources to /api/platform/resources, then refresh.',
        scope: 'Platform mode',
        title: 'Governance Operations Workbench',
        subtitle: 'Aggregates service groups, clusters, zones, middleware, governance signals, and connector status.',
        services: 'Services',
        dependencies: 'Dependencies',
        incidents: 'Incident candidates',
        connectors: 'Connectors',
        priority: 'Priority Queue',
        topology: 'Topology',
        serviceHealth: 'Service Health',
        integrations: 'Integrations',
        evidence: 'Evidence',
        suggested: 'Suggested check',
        primaryResource: 'Primary resource',
        evidenceCount: 'Evidence',
        impactedResources: 'Impacted resources',
        timeline: 'Evidence timeline',
        reference: 'Reference',
        window: 'Window',
        noIncidents: 'No incident candidates',
        noConnectors: 'No connector status',
        warnings: 'Warnings',
        critical: 'Critical',
        cluster: 'Cluster',
        zone: 'Zone',
        team: 'Team',
      },
);

const priorityServices = computed<PlatformServiceNode[]>(() => {
  return [...(snapshot.value?.services ?? [])]
    .sort((left, right) => right.criticalCount - left.criticalCount || right.warningCount - left.warningCount)
    .slice(0, 6);
});

const priorityIncidents = computed(() => (snapshot.value?.incidents ?? []).slice(0, 5));
const connectorStatuses = computed(() => snapshot.value?.connectors ?? []);

function stateClass(state: string): string {
  return `is-${state.toLowerCase()}`;
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
  <EmptyState
    v-else-if="hasLoaded && !snapshot"
    :title="copy.emptyTitle"
    :message="copy.emptyMessage"
  />
  <section v-else-if="snapshot" class="platform-page">
    <section class="platform-hero">
      <div>
        <p class="eyebrow">{{ copy.scope }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="platform-hero__stats">
        <article>
          <span>{{ copy.services }}</span>
          <strong>{{ serviceCount }}</strong>
        </article>
        <article>
          <span>{{ copy.dependencies }}</span>
          <strong>{{ dependencyCount }}</strong>
        </article>
        <article>
          <span>{{ copy.incidents }}</span>
          <strong>{{ incidentCount }}</strong>
        </article>
        <article>
          <span>{{ copy.connectors }}</span>
          <strong>{{ connectorCount }}</strong>
        </article>
      </div>
    </section>

    <div class="platform-grid">
      <section class="platform-panel platform-panel--wide">
        <header>
          <div>
            <p class="eyebrow">{{ copy.priority }}</p>
            <h3>{{ copy.incidents }}</h3>
          </div>
          <span>{{ priorityIncidents.length }}</span>
        </header>
        <div v-if="priorityIncidents.length === 0" class="platform-empty">{{ copy.noIncidents }}</div>
        <article
          v-for="incident in priorityIncidents"
          v-else
          :key="incident.incidentKey"
          class="incident-row"
          :class="stateClass(incident.severity)"
        >
          <div>
            <strong>{{ incident.title }}</strong>
            <span>{{ incident.impactScope.serviceKey }} · {{ incident.impactScope.clusterKey }} · {{ incident.impactScope.zoneKey }}</span>
          </div>
          <dl class="incident-row__meta">
            <div>
              <dt>{{ copy.primaryResource }}</dt>
              <dd>{{ incident.primaryResourceKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.evidenceCount }}</dt>
              <dd>{{ incident.evidenceCount }}</dd>
            </div>
            <div>
              <dt>{{ copy.impactedResources }}</dt>
              <dd>{{ incident.impactedResourceCount }}</dd>
            </div>
          </dl>
          <div class="incident-row__evidence">
            <p>{{ copy.timeline }}</p>
            <span
              v-for="item in incident.evidence.slice(0, 3)"
              :key="`${incident.incidentKey}-${item.resourceKey}-${item.signalType}-${item.timestamp}`"
            >
              {{ item.signalType }} / {{ item.resourceKey }} / {{ item.durationBucket }}
              <small>{{ copy.reference }} {{ item.referenceType }}:{{ item.referenceKey }}</small>
            </span>
          </div>
          <div v-if="incident.suggestedCheck" class="incident-row__suggested">
            <p>{{ copy.suggested }}</p>
            <span>{{ incident.suggestedCheck.resourceKey }}</span>
            <small>{{ incident.suggestedCheck.message }}</small>
          </div>
        </article>
      </section>

      <section class="platform-panel">
        <header>
          <div>
            <p class="eyebrow">{{ copy.topology }}</p>
            <h3>{{ copy.dependencies }}</h3>
          </div>
          <span>{{ snapshot.topology.dependencies.length }}</span>
        </header>
        <div class="topology-list">
          <div v-for="edge in snapshot.topology.dependencies.slice(0, 9)" :key="`${edge.sourceKey}-${edge.targetKey}-${edge.resourceKey}`">
            <strong>{{ edge.sourceKey }}</strong>
            <span>{{ edge.kind }} · {{ edge.warningCount }} / {{ edge.criticalCount }}</span>
            <strong>{{ edge.targetKey }}</strong>
          </div>
        </div>
      </section>
    </div>

    <div class="platform-grid platform-grid--lower">
      <section class="platform-panel platform-panel--wide">
        <header>
          <div>
            <p class="eyebrow">{{ copy.serviceHealth }}</p>
            <h3>{{ copy.services }}</h3>
          </div>
          <span>{{ priorityServices.length }}</span>
        </header>
        <div class="platform-table" role="table">
          <div class="platform-table__head" role="row">
            <span>{{ copy.services }}</span>
            <span>{{ copy.cluster }}</span>
            <span>{{ copy.zone }}</span>
            <span>{{ copy.warnings }}</span>
            <span>{{ copy.critical }}</span>
          </div>
          <div v-for="service in priorityServices" :key="service.serviceKey" class="platform-table__row" role="row">
            <span>
              <strong>{{ service.name }}</strong>
              <small>{{ service.serviceKey }} · {{ copy.team }} {{ service.teamKey }}</small>
            </span>
            <span>{{ service.clusterKey }}</span>
            <span>{{ service.zoneKey }}</span>
            <span>{{ service.warningCount }}</span>
            <span>{{ service.criticalCount }}</span>
          </div>
        </div>
      </section>

      <section class="platform-panel platform-panel--wide">
        <header>
          <div>
            <p class="eyebrow">{{ copy.evidence }}</p>
            <h3>{{ copy.timeline }}</h3>
          </div>
          <span>{{ snapshot.signals.length }}</span>
        </header>
        <div class="evidence-timeline">
          <article v-for="signal in snapshot.signals.slice(0, 8)" :key="`${signal.resourceKey}-${signal.signalType}-${signal.timestamp}`">
            <div>
              <strong>{{ signal.signalType }}</strong>
              <span>{{ signal.serviceKey }} · {{ signal.clusterKey }} · {{ signal.zoneKey }}</span>
            </div>
            <p>{{ signal.resourceKey }} / {{ signal.outcome }} / {{ signal.durationBucket }}</p>
          </article>
        </div>
      </section>

      <section class="platform-panel">
        <header>
          <div>
            <p class="eyebrow">{{ copy.integrations }}</p>
            <h3>{{ copy.connectors }}</h3>
          </div>
          <span>{{ connectorStatuses.length }}</span>
        </header>
        <div v-if="connectorStatuses.length === 0" class="platform-empty">{{ copy.noConnectors }}</div>
        <div v-else class="connector-list">
          <div v-for="connector in connectorStatuses" :key="connector.connectorKey" :class="stateClass(connector.state)">
            <span>{{ connector.displayName }}</span>
            <strong>{{ connector.state }}</strong>
            <small>{{ connector.lastMessage }}</small>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>
