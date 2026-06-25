<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import MetricCard from '../components/MetricCard.vue';
import ResourceTable from '../components/ResourceTable.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { summary, resources, events, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { enumLabel, t } = useLocale();

const degradedCount = computed(() =>
  resources.value.filter((resource) => resource.runtimeSnapshot?.degraded || resource.policySnapshot.degraded).length,
);
const hotResources = computed(() =>
  [...resources.value]
    .sort((left, right) => (right.runtimeSnapshot?.totalRejections ?? 0) - (left.runtimeSnapshot?.totalRejections ?? 0))
    .slice(0, 5),
);
const recentEvents = computed(() => events.value.slice(0, 5));
const retainedEventCount = computed(() => events.value.length);
const hasAttention = computed(
  () =>
    (summary.value?.openCircuitCount ?? 0) > 0 ||
    (summary.value?.rejectedCount ?? 0) > 0 ||
    (summary.value?.cancelledCount ?? 0) > 0 ||
    (summary.value?.retryStoppedCount ?? 0) > 0 ||
    (summary.value?.blockedCount ?? 0) > 0 ||
    (summary.value?.isolatedCount ?? 0) > 0 ||
    (summary.value?.failureCount ?? 0) > 0 ||
    degradedCount.value > 0,
);
const postureLabel = computed(() => (hasAttention.value ? t('overview.postureWatch') : t('overview.postureStable')));

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshAll();
  }
});
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="t('overview.loading')" />
  <ErrorState
    v-else-if="errorMessage"
    :title="t('overview.errorTitle')"
    :message="errorMessage"
    @retry="refreshAll"
  />
  <EmptyState
    v-else-if="hasLoaded && !summary"
    :title="t('overview.emptyTitle')"
    :message="t('overview.emptyMessage')"
  />
  <div v-else class="view-stack">
    <section class="signal-deck" :data-tone="hasAttention ? 'warning' : 'stable'">
      <div class="signal-deck__main">
        <p class="eyebrow">{{ t('overview.cockpitLabel') }}</p>
        <h2>{{ t('overview.posture') }}</h2>
        <div class="signal-deck__status">
          <span aria-hidden="true"></span>
          <strong>{{ postureLabel }}</strong>
        </div>
        <p>{{ t('overview.postureDetail') }}</p>
      </div>
      <div class="signal-rail">
        <div>
          <span>{{ t('overview.trackedResources') }}</span>
          <strong>{{ summary?.resourceCount ?? 0 }}</strong>
        </div>
        <div>
          <span>{{ t('overview.retainedEvents') }}</span>
          <strong>{{ retainedEventCount }}</strong>
        </div>
        <div>
          <span>{{ t('overview.guardrail') }}</span>
          <strong>{{ t('overview.guardrailDetail') }}</strong>
        </div>
      </div>
    </section>

    <section class="metric-grid" :aria-label="t('overview.runtimeSummary')">
      <MetricCard :label="t('overview.resources')" :value="summary?.resourceCount ?? 0" :detail="t('overview.resourcesDetail')" tone="info" />
      <MetricCard :label="t('overview.openCircuits')" :value="summary?.openCircuitCount ?? 0" :detail="t('overview.openCircuitsDetail')" tone="danger" />
      <MetricCard :label="t('overview.rejected')" :value="summary?.rejectedCount ?? 0" :detail="t('overview.rejectedDetail')" tone="warning" />
      <MetricCard :label="t('overview.blocked')" :value="summary?.blockedCount ?? 0" :detail="t('overview.blockedDetail')" tone="warning" />
      <MetricCard :label="t('overview.isolated')" :value="summary?.isolatedCount ?? 0" :detail="t('overview.isolatedDetail')" tone="warning" />
      <MetricCard :label="t('overview.sentinel')" :value="summary?.sentinelResourceCount ?? 0" :detail="t('overview.sentinelDetail')" tone="info" />
      <MetricCard :label="t('overview.cancelled')" :value="summary?.cancelledCount ?? 0" :detail="t('overview.cancelledDetail')" tone="warning" />
      <MetricCard :label="t('overview.retryStopped')" :value="summary?.retryStoppedCount ?? 0" :detail="t('overview.retryStoppedDetail')" tone="warning" />
      <MetricCard :label="t('overview.failures')" :value="summary?.failureCount ?? 0" :detail="t('overview.failuresDetail')" tone="danger" />
      <MetricCard :label="t('overview.fallback')" :value="summary?.fallbackCount ?? 0" :detail="t('overview.fallbackDetail')" tone="neutral" />
      <MetricCard :label="t('overview.degraded')" :value="degradedCount" :detail="t('overview.degradedDetail')" tone="warning" />
    </section>

    <section class="split-grid">
      <div class="panel">
        <div class="panel__header">
          <h2>{{ t('overview.attention') }}</h2>
          <span>{{ hotResources.length }} {{ t('state.shown') }}</span>
        </div>
        <EmptyState
          v-if="hotResources.length === 0"
          :title="t('state.noResources')"
          :message="t('state.noResourcesMessage')"
        />
        <ResourceTable v-else :resources="hotResources" compact @select="emit('selectResource', $event)" />
      </div>

      <div class="panel">
        <div class="panel__header">
          <h2>{{ t('overview.recentEvents') }}</h2>
          <span>{{ recentEvents.length }} {{ t('state.shown') }}</span>
        </div>
        <EmptyState
          v-if="recentEvents.length === 0"
          :title="t('state.noEvents')"
          :message="t('state.noEventsMessage')"
        />
        <ul v-else class="event-list">
          <li v-for="event in recentEvents" :key="`${event.timestamp}-${event.resourceKey}`">
            <span>{{ enumLabel(event.outcome) }}</span>
            <strong>{{ event.resourceKey }}</strong>
            <small>{{ enumLabel(event.isolationReason && event.isolationReason !== 'NONE' ? event.isolationReason : event.retryStopReason && event.retryStopReason !== 'NONE' ? event.retryStopReason : event.blockReason && event.blockReason !== 'NONE' ? event.blockReason : event.cancellationReason !== 'NONE' ? event.cancellationReason : event.rejectionReason) }}</small>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>
