<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type { PlatformSeverity } from '../types/platform';

type SeverityFilter = 'ALL' | PlatformSeverity;
type WorkbenchTab = 'incidents' | 'topology' | 'services';

interface IncidentKeyTarget {
  readonly incidentKey: string;
}

interface IncidentSearchTarget {
  readonly title: string;
  readonly primaryResourceKey: string;
  readonly impactScope: {
    readonly serviceKey: string;
    readonly clusterKey: string;
    readonly zoneKey: string;
  };
  readonly suggestedCheck: {
    readonly resourceKey: string;
  } | null;
  readonly evidence: readonly {
    readonly resourceKey: string;
    readonly signalType: string;
    readonly referenceType: string;
    readonly referenceKey: string;
  }[];
}

interface ServiceSearchTarget {
  readonly serviceKey: string;
  readonly name: string;
  readonly teamKey: string;
  readonly environmentKey: string;
  readonly clusterKey: string;
  readonly zoneKey: string;
}

const { locale } = useLocale();
const { snapshot, isLoading, errorMessage, hasLoaded, serviceCount, incidentCount, dependencyCount, connectorCount, refreshPlatform } =
  usePlatformData();

const severityFilter = ref<SeverityFilter>('ALL');
const serviceQuery = ref('');
const selectedIncidentKey = ref<string | null>(null);
const activeTab = ref<WorkbenchTab>('incidents');

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载平台视图',
        errorTitle: '平台数据不可用',
        emptyTitle: '还没有平台资产',
        emptyMessage: '启动 governance platform sample，或向 /api/platform/resources 上报资源后再刷新。',
        scope: '平台模式',
        title: '治理运维工作台',
        subtitle: '先看事故、服务和证据。只读，不写策略。',
        health: '当前健康',
        needsAction: '需要处理',
        watch: '观察',
        clear: '正常',
        incidentQueue: '事故队列',
        topology: '拓扑影响',
        serviceHealth: '服务健康',
        evidence: '证据链',
        suggested: '建议检查',
        primaryResource: '首要资源',
        impactedScope: '影响范围',
        reference: '引用',
        connectors: '接入状态',
        services: '服务',
        dependencies: '依赖',
        incidents: '事故',
        affectedServices: '受影响服务',
        critical: '严重',
        warning: '警告',
        info: '信息',
        all: '全部',
        searchPlaceholder: '搜索服务、集群、机房或资源',
        noIncidents: '当前没有事故候选',
        noEvidence: '还没有证据',
        noConnectors: '没有连接器状态',
        noServices: '没有匹配的服务',
        noSelection: '选择一条事故查看证据',
        cluster: '集群',
        zone: '机房',
        team: '团队',
        warnings: '警告',
        criticals: '严重',
        resource: '资源',
        outcome: '结果',
        duration: '耗时',
        lastSeen: '最近',
        started: '开始',
        tabIncidents: '事故',
        tabTopology: '拓扑',
        tabServices: '服务',
        connectorReadonly: '只读接入',
        refreshHint: '异常证据来自 SDK、网关、Sentinel、实例健康和重试停止信号。',
      }
    : {
        loading: 'Loading platform view',
        errorTitle: 'Platform data is unavailable',
        emptyTitle: 'No platform assets yet',
        emptyMessage: 'Start the governance platform sample or post resources to /api/platform/resources, then refresh.',
        scope: 'Platform mode',
        title: 'Governance Operations Workbench',
        subtitle: 'Start with incidents, services, and evidence. Read-only only.',
        health: 'Current health',
        needsAction: 'Needs action',
        watch: 'Watch',
        clear: 'Clear',
        incidentQueue: 'Incident Queue',
        topology: 'Topology Impact',
        serviceHealth: 'Service Health',
        evidence: 'Evidence Chain',
        suggested: 'Suggested Check',
        primaryResource: 'Primary Resource',
        impactedScope: 'Impact Scope',
        reference: 'Reference',
        connectors: 'Integrations',
        services: 'Services',
        dependencies: 'Dependencies',
        incidents: 'Incidents',
        affectedServices: 'Affected Services',
        critical: 'Critical',
        warning: 'Warning',
        info: 'Info',
        all: 'All',
        searchPlaceholder: 'Search service, cluster, zone, or resource',
        noIncidents: 'No incident candidates',
        noEvidence: 'No evidence yet',
        noConnectors: 'No connector status',
        noServices: 'No matching services',
        noSelection: 'Select an incident to inspect evidence',
        cluster: 'Cluster',
        zone: 'Zone',
        team: 'Team',
        warnings: 'Warnings',
        criticals: 'Critical',
        resource: 'Resource',
        outcome: 'Outcome',
        duration: 'Duration',
        lastSeen: 'Last seen',
        started: 'Started',
        tabIncidents: 'Incidents',
        tabTopology: 'Topology',
        tabServices: 'Services',
        connectorReadonly: 'Read-only',
        refreshHint: 'Evidence comes from SDK, Gateway, Sentinel, instance health, and retry-stop signals.',
      },
);

const severityRank: Record<PlatformSeverity, number> = {
  CRITICAL: 3,
  WARNING: 2,
  INFO: 1,
};

const incidentsByPriority = computed(() => {
  return [...(snapshot.value?.incidents ?? [])].sort(
    (left, right) =>
      severityRank[right.severity] - severityRank[left.severity] ||
      (right.evidenceCount ?? 0) - (left.evidenceCount ?? 0) ||
      compareNullableDate(right.lastSeenAt, left.lastSeenAt),
  );
});

const filteredIncidents = computed(() => {
  const query = normalizedQuery.value;
  return incidentsByPriority.value.filter((incident) => {
    if (severityFilter.value !== 'ALL' && incident.severity !== severityFilter.value) {
      return false;
    }
    if (!query) {
      return true;
    }
    return searchableIncidentText(incident).includes(query);
  });
});

const selectedIncident = computed(() => {
  const incidents = filteredIncidents.value;
  if (incidents.length === 0) {
    return null;
  }
  return incidents.find((incident) => incident.incidentKey === selectedIncidentKey.value) ?? incidents[0];
});

const selectedEvidence = computed(() => selectedIncident.value?.evidence ?? []);

const normalizedQuery = computed(() => serviceQuery.value.trim().toLowerCase());

const filteredServices = computed(() => {
  const query = normalizedQuery.value;
  return [...(snapshot.value?.services ?? [])]
    .filter((service) => !query || searchableServiceText(service).includes(query))
    .sort((left, right) => right.criticalCount - left.criticalCount || right.warningCount - left.warningCount || left.name.localeCompare(right.name))
    .slice(0, 12);
});

const affectedServiceCount = computed(() => {
  const serviceKeys = new Set((snapshot.value?.incidents ?? []).map((incident) => incident.impactScope.serviceKey));
  return serviceKeys.size;
});

const criticalIncidentCount = computed(() => (snapshot.value?.incidents ?? []).filter((incident) => incident.severity === 'CRITICAL').length);
const warningIncidentCount = computed(() => (snapshot.value?.incidents ?? []).filter((incident) => incident.severity === 'WARNING').length);

const healthLabel = computed(() => {
  if (criticalIncidentCount.value > 0) {
    return copy.value.needsAction;
  }
  if (warningIncidentCount.value > 0) {
    return copy.value.watch;
  }
  return copy.value.clear;
});

const healthTone = computed(() => {
  if (criticalIncidentCount.value > 0) {
    return 'critical';
  }
  if (warningIncidentCount.value > 0) {
    return 'warning';
  }
  return 'healthy';
});

const topDependencies = computed(() => {
  return [...(snapshot.value?.topology.dependencies ?? [])]
    .sort((left, right) => right.criticalCount - left.criticalCount || right.warningCount - left.warningCount)
    .slice(0, 8);
});

const connectorStatuses = computed(() => snapshot.value?.connectors ?? []);
const recentSignals = computed(() => [...(snapshot.value?.signals ?? [])].slice(0, 8));

watch(filteredIncidents, (incidents) => {
  if (incidents.length === 0) {
    selectedIncidentKey.value = null;
    return;
  }
  if (!selectedIncidentKey.value || !incidents.some((incident) => incident.incidentKey === selectedIncidentKey.value)) {
    selectedIncidentKey.value = incidents[0].incidentKey;
  }
});

function selectIncident(incident: IncidentKeyTarget): void {
  selectedIncidentKey.value = incident.incidentKey;
}

function severityClass(severity: PlatformSeverity): string {
  return `is-${severity.toLowerCase()}`;
}

function stateClass(state: string): string {
  return `is-${state.toLowerCase()}`;
}

function formatDate(value: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').replace(/\.\d{3}Z$/, 'Z');
}

function searchableIncidentText(incident: IncidentSearchTarget): string {
  return [
    incident.title,
    incident.primaryResourceKey,
    incident.impactScope.serviceKey,
    incident.impactScope.clusterKey,
    incident.impactScope.zoneKey,
    incident.suggestedCheck?.resourceKey ?? '',
    ...incident.evidence.map((item) => `${item.resourceKey} ${item.signalType} ${item.referenceType} ${item.referenceKey}`),
  ]
    .join(' ')
    .toLowerCase();
}

function searchableServiceText(service: ServiceSearchTarget): string {
  return [service.serviceKey, service.name, service.teamKey, service.environmentKey, service.clusterKey, service.zoneKey]
    .join(' ')
    .toLowerCase();
}

function compareNullableDate(left: string | null, right: string | null): number {
  const leftValue = left ? Date.parse(left) : 0;
  const rightValue = right ? Date.parse(right) : 0;
  return leftValue - rightValue;
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
  <section v-else-if="snapshot" class="ops-workbench" :data-health="healthTone">
    <section class="ops-command">
      <div class="ops-command__intro">
        <p class="eyebrow">{{ copy.scope }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="ops-health">
        <span>{{ copy.health }}</span>
        <strong>{{ healthLabel }}</strong>
      </div>
      <div class="ops-kpis" aria-label="Platform summary">
        <article>
          <span>{{ copy.incidents }}</span>
          <strong>{{ incidentCount }}</strong>
        </article>
        <article>
          <span>{{ copy.affectedServices }}</span>
          <strong>{{ affectedServiceCount }}</strong>
        </article>
        <article>
          <span>{{ copy.services }}</span>
          <strong>{{ serviceCount }}</strong>
        </article>
        <article>
          <span>{{ copy.dependencies }}</span>
          <strong>{{ dependencyCount }}</strong>
        </article>
        <article>
          <span>{{ copy.connectors }}</span>
          <strong>{{ connectorCount }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-controls" aria-label="Platform filters">
      <div class="severity-segment">
        <button type="button" :class="{ 'is-active': severityFilter === 'ALL' }" @click="severityFilter = 'ALL'">
          {{ copy.all }}
        </button>
        <button type="button" :class="{ 'is-active': severityFilter === 'CRITICAL' }" @click="severityFilter = 'CRITICAL'">
          {{ copy.critical }}
        </button>
        <button type="button" :class="{ 'is-active': severityFilter === 'WARNING' }" @click="severityFilter = 'WARNING'">
          {{ copy.warning }}
        </button>
        <button type="button" :class="{ 'is-active': severityFilter === 'INFO' }" @click="severityFilter = 'INFO'">
          {{ copy.info }}
        </button>
      </div>
      <label class="ops-search">
        <span>{{ copy.searchPlaceholder }}</span>
        <input v-model="serviceQuery" type="search" :placeholder="copy.searchPlaceholder" />
      </label>
      <p>{{ copy.refreshHint }}</p>
    </section>

    <div class="mobile-workbench-tabs">
      <button type="button" :class="{ 'is-active': activeTab === 'incidents' }" @click="activeTab = 'incidents'">
        {{ copy.tabIncidents }}
      </button>
      <button type="button" :class="{ 'is-active': activeTab === 'topology' }" @click="activeTab = 'topology'">
        {{ copy.tabTopology }}
      </button>
      <button type="button" :class="{ 'is-active': activeTab === 'services' }" @click="activeTab = 'services'">
        {{ copy.tabServices }}
      </button>
    </div>

    <section class="ops-grid">
      <aside class="ops-panel incident-queue" :class="{ 'is-mobile-hidden': activeTab !== 'incidents' }">
        <header>
          <div>
            <p class="eyebrow">{{ copy.incidentQueue }}</p>
            <h3>{{ filteredIncidents.length }}</h3>
          </div>
          <span>{{ copy.critical }} {{ criticalIncidentCount }} / {{ copy.warning }} {{ warningIncidentCount }}</span>
        </header>
        <div v-if="filteredIncidents.length === 0" class="ops-empty">{{ copy.noIncidents }}</div>
        <button
          v-for="incident in filteredIncidents"
          v-else
          :key="incident.incidentKey"
          type="button"
          class="incident-card"
          :class="[severityClass(incident.severity), { 'is-selected': selectedIncident?.incidentKey === incident.incidentKey }]"
          @click="selectIncident(incident)"
        >
          <span class="incident-card__severity">{{ incident.severity }}</span>
          <strong>{{ incident.title }}</strong>
          <span>{{ incident.impactScope.serviceKey }} / {{ incident.impactScope.clusterKey }} / {{ incident.impactScope.zoneKey }}</span>
          <dl>
            <div>
              <dt>{{ copy.evidence }}</dt>
              <dd>{{ incident.evidenceCount }}</dd>
            </div>
            <div>
              <dt>{{ copy.primaryResource }}</dt>
              <dd>{{ incident.primaryResourceKey }}</dd>
            </div>
          </dl>
        </button>
      </aside>

      <main class="ops-center" :class="{ 'is-mobile-hidden': activeTab !== 'topology' }">
        <section class="ops-panel topology-board">
          <header>
            <div>
              <p class="eyebrow">{{ copy.topology }}</p>
              <h3>{{ copy.dependencies }}</h3>
            </div>
            <span>{{ topDependencies.length }}</span>
          </header>
          <div class="topology-board__canvas">
            <article
              v-for="edge in topDependencies"
              :key="`${edge.sourceKey}-${edge.targetKey}-${edge.resourceKey}`"
              class="dependency-edge"
              :class="{ 'is-critical': edge.criticalCount > 0, 'is-warning': edge.warningCount > 0 && edge.criticalCount === 0 }"
            >
              <div>
                <strong>{{ edge.sourceKey }}</strong>
                <span>{{ edge.kind }}</span>
              </div>
              <div class="dependency-edge__line">
                <span>{{ edge.warningCount }} / {{ edge.criticalCount }}</span>
              </div>
              <div>
                <strong>{{ edge.targetKey }}</strong>
                <span>{{ edge.resourceKey }}</span>
              </div>
            </article>
          </div>
        </section>

        <section class="ops-panel service-board" :class="{ 'is-mobile-hidden': activeTab !== 'services' }">
          <header>
            <div>
              <p class="eyebrow">{{ copy.serviceHealth }}</p>
              <h3>{{ copy.services }}</h3>
            </div>
            <span>{{ filteredServices.length }}</span>
          </header>
          <div v-if="filteredServices.length === 0" class="ops-empty">{{ copy.noServices }}</div>
          <div v-else class="service-table" role="table">
            <div class="service-table__head" role="row">
              <span>{{ copy.services }}</span>
              <span>{{ copy.cluster }}</span>
              <span>{{ copy.zone }}</span>
              <span>{{ copy.warnings }}</span>
              <span>{{ copy.criticals }}</span>
            </div>
            <div v-for="service in filteredServices" :key="service.serviceKey" class="service-table__row" role="row">
              <span>
                <strong>{{ service.name }}</strong>
                <small>{{ service.serviceKey }} / {{ copy.team }} {{ service.teamKey }}</small>
              </span>
              <span>{{ service.clusterKey }}</span>
              <span>{{ service.zoneKey }}</span>
              <span>{{ service.warningCount }}</span>
              <span>{{ service.criticalCount }}</span>
            </div>
          </div>
        </section>
      </main>

      <aside class="ops-panel evidence-drawer">
        <header>
          <div>
            <p class="eyebrow">{{ copy.evidence }}</p>
            <h3>{{ selectedIncident ? selectedIncident.title : copy.noSelection }}</h3>
          </div>
          <span v-if="selectedIncident" :class="['drawer-severity', severityClass(selectedIncident.severity)]">{{ selectedIncident.severity }}</span>
        </header>

        <div v-if="!selectedIncident" class="ops-empty">{{ copy.noSelection }}</div>
        <template v-else>
          <section class="drawer-summary">
            <dl>
              <div>
                <dt>{{ copy.impactedScope }}</dt>
                <dd>{{ selectedIncident.impactScope.serviceKey }} / {{ selectedIncident.impactScope.clusterKey }} / {{ selectedIncident.impactScope.zoneKey }}</dd>
              </div>
              <div>
                <dt>{{ copy.primaryResource }}</dt>
                <dd>{{ selectedIncident.primaryResourceKey }}</dd>
              </div>
              <div>
                <dt>{{ copy.started }}</dt>
                <dd>{{ formatDate(selectedIncident.startedAt) }}</dd>
              </div>
              <div>
                <dt>{{ copy.lastSeen }}</dt>
                <dd>{{ formatDate(selectedIncident.lastSeenAt) }}</dd>
              </div>
            </dl>
          </section>

          <section v-if="selectedIncident.suggestedCheck" class="suggested-check">
            <p>{{ copy.suggested }}</p>
            <strong>{{ selectedIncident.suggestedCheck.resourceKey }}</strong>
            <span>{{ selectedIncident.suggestedCheck.message }}</span>
          </section>

          <div v-if="selectedEvidence.length === 0" class="ops-empty">{{ copy.noEvidence }}</div>
          <ol v-else class="evidence-chain">
            <li v-for="item in selectedEvidence" :key="`${item.resourceKey}-${item.signalType}-${item.timestamp}`" :class="severityClass(item.severity)">
              <div>
                <strong>{{ item.signalType }}</strong>
                <span>{{ formatDate(item.timestamp) }}</span>
              </div>
              <p>{{ item.message || `${item.outcome} / ${item.durationBucket}` }}</p>
              <dl>
                <div>
                  <dt>{{ copy.resource }}</dt>
                  <dd>{{ item.resourceKey }}</dd>
                </div>
                <div>
                  <dt>{{ copy.outcome }}</dt>
                  <dd>{{ item.outcome }}</dd>
                </div>
                <div>
                  <dt>{{ copy.duration }}</dt>
                  <dd>{{ item.durationBucket }}</dd>
                </div>
                <div>
                  <dt>{{ copy.reference }}</dt>
                  <dd>{{ item.referenceType }} / {{ item.referenceKey }}</dd>
                </div>
              </dl>
            </li>
          </ol>
        </template>
      </aside>
    </section>

    <section class="ops-footer-grid">
      <section class="ops-panel signal-strip">
        <header>
          <div>
            <p class="eyebrow">{{ copy.evidence }}</p>
            <h3>{{ copy.lastSeen }}</h3>
          </div>
          <span>{{ recentSignals.length }}</span>
        </header>
        <article v-for="signal in recentSignals" :key="`${signal.resourceKey}-${signal.signalType}-${signal.timestamp}`">
          <strong>{{ signal.signalType }}</strong>
          <span>{{ signal.serviceKey }} / {{ signal.resourceKey }} / {{ signal.durationBucket }}</span>
        </article>
      </section>

      <section class="ops-panel connector-board">
        <header>
          <div>
            <p class="eyebrow">{{ copy.connectorReadonly }}</p>
            <h3>{{ copy.connectors }}</h3>
          </div>
          <span>{{ connectorStatuses.length }}</span>
        </header>
        <div v-if="connectorStatuses.length === 0" class="ops-empty">{{ copy.noConnectors }}</div>
        <article v-for="connector in connectorStatuses" v-else :key="connector.connectorKey" :class="stateClass(connector.state)">
          <div>
            <strong>{{ connector.displayName }}</strong>
            <span>{{ connector.kind }} / {{ connector.connectorKey }}</span>
          </div>
          <small>{{ connector.state }}</small>
          <p>{{ connector.lastMessage }}</p>
        </article>
      </section>
    </section>
  </section>
</template>
