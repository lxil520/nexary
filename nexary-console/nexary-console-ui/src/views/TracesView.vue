<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import MetricCard from '../components/MetricCard.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';

const emit = defineEmits<{
  selectTrace: [traceKey: string];
  selectResource: [resourceKey: string];
}>();

const { traces, faultTraceSummary, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { enumLabel, formatTimestamp, t } = useLocale();

const orderedTraces = computed(() =>
  [...traces.value].sort((left, right) => {
    const leftTime = left.lastEventAt ? Date.parse(left.lastEventAt) : 0;
    const rightTime = right.lastEventAt ? Date.parse(right.lastEventAt) : 0;
    return rightTime - leftTime;
  }),
);

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshAll();
  }
});
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="t('traces.loading')" />
  <ErrorState v-else-if="errorMessage" :title="t('traces.errorTitle')" :message="errorMessage" @retry="refreshAll" />
  <div v-else class="view-stack">
    <section class="metric-grid" :aria-label="t('traces.summary')">
      <MetricCard :label="t('traces.retained')" :value="faultTraceSummary?.traceCount ?? traces.length" :detail="t('traces.retainedDetail')" tone="info" />
      <MetricCard :label="t('traces.stopped')" :value="faultTraceSummary?.stoppedCount ?? 0" :detail="t('traces.stoppedDetail')" tone="warning" />
      <MetricCard :label="t('traces.blocked')" :value="faultTraceSummary?.blockedCount ?? 0" :detail="t('traces.blockedDetail')" tone="warning" />
      <MetricCard :label="t('traces.cancelled')" :value="faultTraceSummary?.cancelledCount ?? 0" :detail="t('traces.cancelledDetail')" tone="warning" />
      <MetricCard :label="t('traces.retryStopped')" :value="faultTraceSummary?.retryStoppedCount ?? 0" :detail="t('traces.retryStoppedDetail')" tone="warning" />
      <MetricCard :label="t('traces.instanceRelated')" :value="faultTraceSummary?.instanceRelatedCount ?? 0" :detail="t('traces.instanceRelatedDetail')" tone="danger" />
    </section>

    <section class="panel">
      <div class="panel__header">
        <h2>{{ t('traces.recent') }}</h2>
        <span>{{ orderedTraces.length }} {{ t('state.shown') }}</span>
      </div>
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
