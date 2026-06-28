<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';

const { settings, lastRefreshAt, refreshAll } = useConsoleData();
const { snapshot, hasLoaded: platformLoaded, refreshPlatform } = usePlatformData();
const { locale, t } = useLocale();

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        title: '接入与通道',
        subtitle: '这里放治理平台的只读边界、接入器状态、策略草案和通知通道预留。',
        boundary: '运行边界',
        connectors: '工具接入',
        notifications: '通知通道',
        policyPlans: '策略草案',
        localApi: '本地 Console API',
        platformApi: '平台 API',
        noConnectors: '没有接入器',
        noRoutes: '没有通知通道',
        noPlans: '没有策略草案',
        state: '状态',
        dryRun: '演练',
        targetTeam: '目标团队',
        minSeverity: '触发级别',
        message: '最近消息',
        proposedAction: '建议动作',
        risk: '风险',
        evidence: '证据',
        route: '路由',
        readonly: '只读',
      }
    : {
        title: 'Integrations and Channels',
        subtitle: 'Read-only boundary, integration health, policy drafts, and notification channel placeholders live here.',
        boundary: 'Runtime Boundary',
        connectors: 'Tool Integrations',
        notifications: 'Notification Channels',
        policyPlans: 'Policy Drafts',
        localApi: 'Local Console API',
        platformApi: 'Platform API',
        noConnectors: 'No connectors',
        noRoutes: 'No notification routes',
        noPlans: 'No policy drafts',
        state: 'State',
        dryRun: 'Dry Run',
        targetTeam: 'Target Team',
        minSeverity: 'Trigger',
        message: 'Last Message',
        proposedAction: 'Proposed Action',
        risk: 'Risk',
        evidence: 'Evidence',
        route: 'Route',
        readonly: 'Read Only',
      },
);

const connectors = computed(() => snapshot.value?.connectors ?? []);
const notificationRoutes = computed(() => snapshot.value?.overview.notificationRoutes ?? []);
const policyPlans = computed(() => snapshot.value?.overview.policyPlans ?? []);

function refreshSettings(): void {
  void Promise.all([refreshAll(), refreshPlatform()]);
}

onMounted(() => {
  if (!platformLoaded.value) {
    void refreshPlatform();
  }
});
</script>

<template>
  <div class="ops-page">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.boundary }}</p>
        <h2>{{ copy.title }}</h2>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.connectors }}</span>
          <strong>{{ connectors.length }}</strong>
        </article>
        <article>
          <span>{{ copy.notifications }}</span>
          <strong>{{ notificationRoutes.length }}</strong>
        </article>
        <article>
          <span>{{ copy.policyPlans }}</span>
          <strong>{{ policyPlans.length }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.boundary }}</span>
            <h3>{{ copy.boundary }}</h3>
          </div>
          <StatusBadge :label="copy.readonly" state="CLOSED" />
        </header>
        <dl class="ops-definition-list">
          <div>
            <dt>{{ t('settings.apiBase') }}</dt>
            <dd class="table-monospace">{{ settings.apiBase }}</dd>
          </div>
          <div>
            <dt>{{ t('settings.dataMode') }}</dt>
            <dd>{{ settings.dataMode }}</dd>
          </div>
          <div>
            <dt>{{ t('settings.refreshInterval') }}</dt>
            <dd>{{ settings.refreshIntervalMs }} ms</dd>
          </div>
          <div>
            <dt>{{ t('settings.lastRefresh') }}</dt>
            <dd>{{ lastRefreshAt ?? t('app.notRefreshed') }}</dd>
          </div>
          <div>
            <dt>{{ t('settings.boundary') }}</dt>
            <dd>{{ t('settings.boundaryText') }}</dd>
          </div>
        </dl>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.connectors }}</span>
            <h3>{{ copy.connectors }}</h3>
          </div>
          <button class="button" type="button" @click="refreshSettings">{{ t('app.refresh') }}</button>
        </header>
        <EmptyState v-if="connectors.length === 0" :title="copy.noConnectors" :message="copy.subtitle" />
        <div v-else class="ops-connector-grid ops-connector-grid--settings">
          <article v-for="connector in connectors" :key="connector.connectorKey">
            <span>{{ connector.kind }}</span>
            <strong>{{ connector.displayName }}</strong>
            <small>{{ connector.connectorKey }}</small>
            <StatusBadge :label="connector.state" :state="connector.state" />
          </article>
        </div>
      </div>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.notifications }}</span>
            <h3>{{ copy.notifications }}</h3>
          </div>
          <span>{{ notificationRoutes.length }} {{ t('state.shown') }}</span>
        </header>
        <EmptyState v-if="notificationRoutes.length === 0" :title="copy.noRoutes" :message="copy.subtitle" />
        <div v-else class="ops-table-list">
          <article v-for="route in notificationRoutes" :key="route.routeKey">
            <div>
              <strong>{{ route.displayName }}</strong>
              <small>{{ route.channel }} / {{ copy.route }} {{ route.routeKey }}</small>
            </div>
            <span>{{ copy.targetTeam }} <b>{{ route.targetTeam }}</b></span>
            <span>{{ copy.minSeverity }} <b>{{ route.minSeverity }}</b></span>
            <span>{{ copy.dryRun }} <b>{{ route.dryRun ? 'ON' : 'OFF' }}</b></span>
            <StatusBadge :label="route.state" :state="route.state" />
          </article>
        </div>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.policyPlans }}</span>
            <h3>{{ copy.policyPlans }}</h3>
          </div>
          <span>{{ policyPlans.length }} {{ t('state.shown') }}</span>
        </header>
        <EmptyState v-if="policyPlans.length === 0" :title="copy.noPlans" :message="copy.subtitle" />
        <div v-else class="ops-table-list">
          <article v-for="plan in policyPlans" :key="plan.planKey">
            <div>
              <strong>{{ plan.title }}</strong>
              <small>{{ plan.serviceKey }} / {{ plan.resourceKey }}</small>
            </div>
            <span>{{ copy.risk }} <b>{{ plan.risk }}</b></span>
            <span>{{ copy.state }} <b>{{ plan.state }}</b></span>
            <span>{{ copy.evidence }} <b>{{ plan.evidenceCount }}</b></span>
          </article>
        </div>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.localApi }}</span>
          <h3>{{ copy.localApi }}</h3>
        </div>
      </header>
      <dl class="endpoint-list endpoint-list--api">
        <div>
          <dt><span>GET</span>{{ t('settings.summary') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.summary }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.resources') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.resources }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.resourceDetail') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.resourceDetail }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.events') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.events }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.traces') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.traces }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.traceDetail') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.traceDetail }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.faultSummary') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.faultSummary }}</dd>
        </div>
      </dl>
    </section>
  </div>
</template>
