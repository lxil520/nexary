<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';

const emit = defineEmits<{
  selectTrace: [traceKey: string];
  selectResource: [resourceKey: string];
}>();

const { traces, faultTraceSummary, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { snapshot, isLoading: platformLoading, hasLoaded: platformLoaded, refreshPlatform } = usePlatformData();
const { enumLabel, formatTimestamp, locale, t } = useLocale();

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载证据链',
        traceCenter: '证据链工作台',
        traceCenterNote: '把平台事故证据和本地故障 Trace 放在同一个排障路径里。',
        faultSummary: '故障 Trace',
        evidenceChain: '平台证据链',
        localTraces: '本地 Trace 明细',
        noEvidence: '没有平台事故证据',
        primaryResource: '首要资源',
        suggested: '建议检查',
        reference: '引用',
        signal: '信号',
        outcome: '结果',
        duration: '耗时',
        impact: '影响',
      }
    : {
        loading: 'Loading evidence chains',
        traceCenter: 'Evidence Chain Workbench',
        traceCenterNote: 'Connect platform incident evidence with local fault traces.',
        faultSummary: 'Fault Traces',
        evidenceChain: 'Platform Evidence Chain',
        localTraces: 'Local Trace Details',
        noEvidence: 'No platform incident evidence',
        primaryResource: 'Primary Resource',
        suggested: 'Suggested Check',
        reference: 'Reference',
        signal: 'Signal',
        outcome: 'Outcome',
        duration: 'Duration',
        impact: 'Impact',
      },
);

const orderedTraces = computed(() =>
  [...traces.value].sort((left, right) => {
    const leftTime = left.lastEventAt ? Date.parse(left.lastEventAt) : 0;
    const rightTime = right.lastEventAt ? Date.parse(right.lastEventAt) : 0;
    return rightTime - leftTime;
  }),
);
const incidents = computed(() => snapshot.value?.incidents ?? []);
const primaryIncident = computed(() => incidents.value[0] ?? null);
const evidenceItems = computed(() => primaryIncident.value?.evidence ?? []);
const isInitialLoading = computed(() => (isLoading.value && !hasLoaded.value) || (platformLoading.value && !platformLoaded.value));

function refreshTraces(): void {
  void Promise.all([refreshAll(), refreshPlatform()]);
}

onMounted(() => {
  if (!hasLoaded.value || !platformLoaded.value) {
    refreshTraces();
  }
});
</script>

<template>
  <LoadingBlock v-if="isInitialLoading" :label="copy.loading" />
  <ErrorState v-else-if="errorMessage" :title="t('traces.errorTitle')" :message="errorMessage" @retry="refreshTraces" />
  <div v-else class="ops-page">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.traceCenter }}</p>
        <h2>{{ copy.traceCenter }}</h2>
        <p>{{ copy.traceCenterNote }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ t('traces.retained') }}</span>
          <strong>{{ faultTraceSummary?.traceCount ?? traces.length }}</strong>
        </article>
        <article>
          <span>{{ t('traces.stopped') }}</span>
          <strong>{{ faultTraceSummary?.stoppedCount ?? 0 }}</strong>
        </article>
        <article>
          <span>{{ t('traces.instanceRelated') }}</span>
          <strong>{{ faultTraceSummary?.instanceRelatedCount ?? 0 }}</strong>
        </article>
        <article>
          <span>{{ copy.evidenceChain }}</span>
          <strong>{{ evidenceItems.length }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.evidenceChain }}</span>
            <h3>{{ primaryIncident?.title ?? copy.evidenceChain }}</h3>
            <p v-if="primaryIncident">
              {{ copy.impact }} {{ primaryIncident.impactScope.serviceKey }} / {{ primaryIncident.impactScope.clusterKey }} / {{ primaryIncident.impactScope.zoneKey }}
            </p>
          </div>
          <StatusBadge :label="primaryIncident?.severity ?? 'INFO'" :state="primaryIncident?.severity ?? 'INFO'" />
        </header>
        <EmptyState v-if="!primaryIncident" :title="copy.noEvidence" :message="t('traces.emptyMessage')" />
        <div v-else class="ops-evidence-board">
          <dl>
            <div>
              <dt>{{ copy.primaryResource }}</dt>
              <dd>{{ primaryIncident.primaryResourceKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.suggested }}</dt>
              <dd>
                <button
                  v-if="primaryIncident.suggestedCheck"
                  class="link-button"
                  type="button"
                  @click="emit('selectResource', primaryIncident.suggestedCheck.resourceKey)"
                >
                  <span class="table-primary">{{ primaryIncident.suggestedCheck.resourceKey }}</span>
                </button>
                <span v-else>{{ enumLabel('NONE') }}</span>
              </dd>
            </div>
          </dl>
          <ol class="ops-timeline">
            <li v-for="item in evidenceItems" :key="`${item.timestamp}-${item.resourceKey}-${item.signalType}`">
              <time>{{ formatTimestamp(item.timestamp) }}</time>
              <strong>{{ item.resourceKey }}</strong>
              <span>{{ copy.signal }} {{ item.signalType }} / {{ copy.outcome }} {{ item.outcome }} / {{ copy.duration }} {{ item.durationBucket }}</span>
              <small>{{ copy.reference }} {{ item.referenceType }} / {{ item.referenceKey }}</small>
            </li>
          </ol>
        </div>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.faultSummary }}</span>
            <h3>{{ copy.faultSummary }}</h3>
          </div>
        </header>
        <div class="ops-signal-grid ops-signal-grid--three">
          <article data-tone="warning">
            <span>{{ t('traces.blocked') }}</span>
            <strong>{{ faultTraceSummary?.blockedCount ?? 0 }}</strong>
          </article>
          <article data-tone="warning">
            <span>{{ t('traces.cancelled') }}</span>
            <strong>{{ faultTraceSummary?.cancelledCount ?? 0 }}</strong>
          </article>
          <article data-tone="critical">
            <span>{{ t('traces.retryStopped') }}</span>
            <strong>{{ faultTraceSummary?.retryStoppedCount ?? 0 }}</strong>
          </article>
        </div>
        <div class="ops-mini-list">
          <article v-for="(count, reason) in faultTraceSummary?.topStopReasons ?? {}" :key="reason">
            <strong>{{ enumLabel(reason) }}</strong>
            <span>{{ t('table.stopReason') }}</span>
            <b>{{ count }}</b>
          </article>
        </div>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.localTraces }}</span>
          <h3>{{ t('traces.recent') }}</h3>
        </div>
        <span>{{ orderedTraces.length }} {{ t('state.shown') }}</span>
      </header>
      <EmptyState
        v-if="orderedTraces.length === 0"
        :title="t('traces.emptyTitle')"
        :message="t('traces.emptyMessage')"
      />
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">{{ t('table.time') }}</th>
              <th scope="col">{{ t('table.trace') }}</th>
              <th scope="col">{{ t('table.resource') }}</th>
              <th scope="col">{{ t('table.outcome') }}</th>
              <th scope="col">{{ t('table.stopReason') }}</th>
              <th scope="col">{{ t('table.suggestedResource') }}</th>
              <th scope="col">{{ t('table.steps') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="trace in orderedTraces" :key="trace.traceKey">
              <td>{{ formatTimestamp(trace.lastEventAt) }}</td>
              <td>
                <button class="link-button" type="button" @click="emit('selectTrace', trace.traceKey)">
                  <span class="table-primary">{{ trace.traceKey }}</span>
                  <span class="table-secondary">{{ formatTimestamp(trace.startedAt) }}</span>
                </button>
              </td>
              <td class="table-monospace">{{ trace.rootResourceKey }}</td>
              <td><StatusBadge :label="trace.terminalOutcome" :state="trace.terminalOutcome" /></td>
              <td><StatusBadge :label="trace.primaryStopReason" :state="trace.primaryStopReason" /></td>
              <td>
                <button
                  v-if="trace.suggestedResourceKey"
                  class="link-button"
                  type="button"
                  @click="emit('selectResource', trace.suggestedResourceKey)"
                >
                  <span class="table-primary">{{ trace.suggestedResourceKey }}</span>
                </button>
                <span v-else>{{ enumLabel('NONE') }}</span>
              </td>
              <td>{{ trace.steps.length }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
