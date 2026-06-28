<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import EventTable from '../components/EventTable.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';

const props = defineProps<{
  resourceKey: string;
}>();

const { events, isLoading, errorMessage, hasLoaded, refreshAll, loadResource, resourceByKey } = useConsoleData();
const { enumLabel, locale, t } = useLocale();
const resource = computed(() => resourceByKey(props.resourceKey));
const runtime = computed(() => resource.value?.runtimeSnapshot ?? null);
const relatedEvents = computed(() => events.value.filter((event) => event.resourceKey === props.resourceKey).slice(0, 10));
const instanceSnapshots = computed(() => resource.value?.instanceHealthSnapshots ?? []);
const circuitState = computed(() => runtime.value?.circuitState ?? 'NO_STATE');
const traceStopReason = computed(() => resource.value?.lastTraceStopReason ?? 'NONE');
const traceOutcome = computed(() => resource.value?.lastTraceOutcome ?? runtime.value?.lastOutcome ?? 'NONE');
const callSummary = computed(() => {
  const snapshot = runtime.value;
  return `${snapshot?.windowCalls ?? 0} / ${snapshot?.windowFailures ?? 0} / ${snapshot?.windowSlowCalls ?? 0}`;
});

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        hero: '资源排障详情',
        heroNote: '先确认运行窗口和策略，再看实例健康与最近事件。',
        runtime: '运行窗口',
        policy: '策略快照',
        calls: '调用 / 失败 / 慢调用',
        rejections: '拒绝',
        circuit: '熔断',
        traceStop: 'Trace 停止',
        traceOutcome: 'Trace 结果',
      }
    : {
        hero: 'Resource Troubleshooting Detail',
        heroNote: 'Confirm runtime and policy first, then inspect instance health and recent events.',
        runtime: 'Runtime Window',
        policy: 'Policy Snapshot',
        calls: 'Calls / Fail / Slow',
        rejections: 'Rejected',
        circuit: 'Circuit',
        traceStop: 'Trace Stop',
        traceOutcome: 'Trace Outcome',
      },
);

function valueOrZero(value: number | undefined): number {
  return value ?? 0;
}

function thresholdLabel(value: number | null): string {
  return value == null ? t('policy.disabled') : `${value}%`;
}

function durationLabel(value: string | null): string {
  return value ?? t('policy.notSet');
}

async function ensureResourceLoaded(): Promise<void> {
  if (!hasLoaded.value) {
    await refreshAll();
  }
  if (!resource.value && props.resourceKey) {
    await loadResource(props.resourceKey);
  }
}

onMounted(() => {
  void ensureResourceLoaded();
});

watch(
  () => props.resourceKey,
  () => {
    void ensureResourceLoaded();
  },
);
</script>

<template>
  <LoadingBlock v-if="isLoading && !resource" :label="t('detail.loading')" />
  <ErrorState
    v-else-if="errorMessage"
    :title="t('detail.errorTitle')"
    :message="errorMessage"
    @retry="ensureResourceLoaded"
  />
  <EmptyState
    v-else-if="!resource"
    :title="t('detail.notFound')"
    :message="t('detail.notFoundMessage')"
  />
  <div v-else class="ops-page">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.hero }}</p>
        <h2>{{ resource.name }}</h2>
        <p class="resource-key">{{ resource.resourceKey }}</p>
        <p>{{ enumLabel(resource.engine ?? 'LOCAL') }} / {{ enumLabel(resource.kind) }} / {{ resource.provider }} / {{ resource.operation }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.circuit }}</span>
          <strong>{{ enumLabel(circuitState) }}</strong>
        </article>
        <article>
          <span>{{ copy.calls }}</span>
          <strong>{{ callSummary }}</strong>
        </article>
        <article>
          <span>{{ copy.rejections }}</span>
          <strong>{{ runtime?.totalRejections ?? 0 }}</strong>
        </article>
        <article>
          <span>{{ copy.traceStop }}</span>
          <strong>{{ enumLabel(traceStopReason) }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.runtime }}</span>
            <h3>{{ t('runtime.title') }}</h3>
          </div>
          <StatusBadge :label="circuitState" :state="circuitState" />
        </header>
        <dl class="ops-definition-list">
          <div>
            <dt>{{ t('runtime.traffic') }}</dt>
            <dd>{{ enumLabel(runtime?.trafficClass ?? 'online') }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.priority') }}</dt>
            <dd>{{ enumLabel(runtime?.priority ?? resource.priority) }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.calls') }}</dt>
            <dd>{{ valueOrZero(runtime?.windowCalls) }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.failures') }}</dt>
            <dd>{{ valueOrZero(runtime?.windowFailures) }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.slowCalls') }}</dt>
            <dd>{{ valueOrZero(runtime?.windowSlowCalls) }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.activeConcurrency') }}</dt>
            <dd>{{ valueOrZero(runtime?.activeConcurrency) }} / {{ valueOrZero(runtime?.maxConcurrency) }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.lastOutcome') }}</dt>
            <dd>{{ enumLabel(traceOutcome) }}</dd>
          </div>
          <div>
            <dt>{{ t('runtime.lastReason') }}</dt>
            <dd>{{ enumLabel(runtime?.lastIsolationReason && runtime.lastIsolationReason !== 'NONE' ? runtime.lastIsolationReason : runtime?.lastBlockReason ?? runtime?.lastRejectionReason ?? 'NONE') }}</dd>
          </div>
        </dl>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.policy }}</span>
            <h3>{{ t('policy.title') }}</h3>
          </div>
          <StatusBadge :label="resource.policySnapshot.degraded ? 'DEGRADED' : 'ACTIVE'" :state="resource.policySnapshot.degraded ? 'DEGRADED' : 'CLOSED'" />
        </header>
        <dl class="ops-definition-list">
          <div>
            <dt>{{ t('policy.rateLimit') }}</dt>
            <dd>{{ resource.policySnapshot.maxRequestsPerWindow }} {{ t('policy.per') }} {{ durationLabel(resource.policySnapshot.rateLimitWindow) }}</dd>
          </div>
          <div>
            <dt>{{ t('policy.concurrency') }}</dt>
            <dd>{{ resource.policySnapshot.maxConcurrency }}</dd>
          </div>
          <div>
            <dt>{{ t('policy.failureThreshold') }}</dt>
            <dd>{{ thresholdLabel(resource.policySnapshot.failureRateThreshold) }}</dd>
          </div>
          <div>
            <dt>{{ t('policy.slowCallThreshold') }}</dt>
            <dd>{{ thresholdLabel(resource.policySnapshot.slowCallThreshold) }}</dd>
          </div>
          <div>
            <dt>{{ t('policy.openDuration') }}</dt>
            <dd>{{ durationLabel(resource.policySnapshot.openStateDuration) }}</dd>
          </div>
          <div>
            <dt>{{ t('policy.slidingWindow') }}</dt>
            <dd>{{ resource.policySnapshot.slidingWindowSize }} / {{ durationLabel(resource.policySnapshot.slidingWindowDuration) }}</dd>
          </div>
        </dl>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.traceOutcome }}</span>
          <h3>{{ t('detail.traceState') }}</h3>
        </div>
        <span>{{ t('detail.traceStateNote') }}</span>
      </header>
      <dl class="ops-definition-list">
        <div>
          <dt>{{ t('table.outcome') }}</dt>
          <dd><StatusBadge :label="traceOutcome" :state="traceOutcome" /></dd>
        </div>
        <div>
          <dt>{{ t('table.stopReason') }}</dt>
          <dd><StatusBadge :label="traceStopReason" :state="traceStopReason" /></dd>
        </div>
      </dl>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ t('detail.instanceHealth') }}</span>
          <h3>{{ t('detail.instanceHealth') }}</h3>
        </div>
        <span>{{ instanceSnapshots.length }} {{ t('state.shown') }}</span>
      </header>
      <EmptyState
        v-if="instanceSnapshots.length === 0"
        :title="t('detail.noInstances')"
        :message="t('detail.noInstancesMessage')"
      />
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">{{ t('table.instance') }}</th>
              <th scope="col">{{ t('table.zone') }}</th>
              <th scope="col">{{ t('table.state') }}</th>
              <th scope="col">{{ t('table.reason') }}</th>
              <th scope="col">{{ t('table.advice') }}</th>
              <th scope="col">{{ t('table.calls') }}</th>
              <th scope="col">{{ t('table.ratios') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="snapshot in instanceSnapshots" :key="`${snapshot.resourceKey}-${snapshot.instanceKey}`">
              <td class="table-monospace">{{ snapshot.instanceKey }}</td>
              <td>{{ snapshot.zone }}</td>
              <td><StatusBadge :label="snapshot.state" :state="snapshot.state" /></td>
              <td><StatusBadge :label="snapshot.quarantineReason" :state="snapshot.quarantineReason" /></td>
              <td><StatusBadge :label="snapshot.recoveryAdvice" :state="snapshot.recoveryAdvice" /></td>
              <td>{{ snapshot.windowCalls }} / {{ snapshot.failureCount }} / {{ snapshot.slowCallCount }}</td>
              <td>{{ snapshot.failureRatio.toFixed(2) }} / {{ snapshot.slowRatio.toFixed(2) }} / {{ snapshot.timeoutRatio.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ t('detail.recentEvents') }}</span>
          <h3>{{ t('detail.recentEvents') }}</h3>
        </div>
        <span>{{ relatedEvents.length }} {{ t('state.shown') }}</span>
      </header>
      <EmptyState
        v-if="relatedEvents.length === 0"
        :title="t('detail.noEvents')"
        :message="t('detail.noEventsMessage')"
      />
      <EventTable v-else :events="relatedEvents" />
    </section>
  </div>
</template>
