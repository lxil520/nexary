<script setup lang="ts">
import { computed, onMounted } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import MetricCard from '../components/MetricCard.vue';
import ResourceTable from '../components/ResourceTable.vue';
import { useConsoleData } from '../composables/useConsoleData';

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { summary, resources, events, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();

const degradedCount = computed(() =>
  resources.value.filter((resource) => resource.runtimeSnapshot?.degraded || resource.policySnapshot.degraded).length,
);
const hotResources = computed(() =>
  [...resources.value]
    .sort((left, right) => (right.runtimeSnapshot?.totalRejections ?? 0) - (left.runtimeSnapshot?.totalRejections ?? 0))
    .slice(0, 5),
);
const recentEvents = computed(() => events.value.slice(0, 5));

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshAll();
  }
});
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" label="Loading console summary" />
  <ErrorState
    v-else-if="errorMessage"
    title="Console data is unavailable"
    :message="errorMessage"
    @retry="refreshAll"
  />
  <EmptyState
    v-else-if="hasLoaded && !summary"
    title="No summary returned"
    message="Enable the read-only console API or switch the adapter to mock mode for local UI checks."
  />
  <div v-else class="view-stack">
    <section class="metric-grid" aria-label="Runtime summary">
      <MetricCard label="Resources" :value="summary?.resourceCount ?? 0" detail="known descriptors" tone="info" />
      <MetricCard label="Open circuits" :value="summary?.openCircuitCount ?? 0" detail="currently blocking" tone="danger" />
      <MetricCard label="Rejected" :value="summary?.rejectedCount ?? 0" detail="retained events" tone="warning" />
      <MetricCard label="Failures" :value="summary?.failureCount ?? 0" detail="retained events" tone="danger" />
      <MetricCard label="Fallback" :value="summary?.fallbackCount ?? 0" detail="fallback actions" tone="neutral" />
      <MetricCard label="Degraded" :value="degradedCount" detail="policy or runtime" tone="warning" />
    </section>

    <section class="split-grid">
      <div class="panel">
        <div class="panel__header">
          <h2>Resources Needing Attention</h2>
          <span>{{ hotResources.length }} shown</span>
        </div>
        <EmptyState
          v-if="hotResources.length === 0"
          title="No resources yet"
          message="Run a sample or hit a governed path to populate resource diagnostics."
        />
        <ResourceTable v-else :resources="hotResources" @select="emit('selectResource', $event)" />
      </div>

      <div class="panel">
        <div class="panel__header">
          <h2>Recent Events</h2>
          <span>{{ recentEvents.length }} shown</span>
        </div>
        <EmptyState
          v-if="recentEvents.length === 0"
          title="No events retained"
          message="Trigger governed calls to see success, failure, rejection, and fallback events."
        />
        <ul v-else class="event-list">
          <li v-for="event in recentEvents" :key="`${event.timestamp}-${event.resourceKey}`">
            <span>{{ event.outcome }}</span>
            <strong>{{ event.resourceKey }}</strong>
            <small>{{ event.rejectionReason }}</small>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>
