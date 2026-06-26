<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import EventTable from '../components/EventTable.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import PolicySummary from '../components/PolicySummary.vue';
import RuntimeWindowStats from '../components/RuntimeWindowStats.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';

const props = defineProps<{
  resourceKey: string;
}>();

const { events, isLoading, errorMessage, hasLoaded, refreshAll, loadResource, resourceByKey } = useConsoleData();
const { enumLabel, t } = useLocale();
const resource = computed(() => resourceByKey(props.resourceKey));
const relatedEvents = computed(() => events.value.filter((event) => event.resourceKey === props.resourceKey).slice(0, 10));
const instanceSnapshots = computed(() => resource.value?.instanceHealthSnapshots ?? []);

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
  <div v-else class="view-stack">
    <section class="detail-header">
      <div>
        <p class="eyebrow">{{ enumLabel(resource.engine ?? 'LOCAL') }} / {{ enumLabel(resource.kind) }} / {{ resource.provider }}</p>
        <h1>{{ resource.name }}</h1>
        <p class="resource-key">{{ resource.resourceKey }}</p>
      </div>
      <div class="detail-header__badges">
        <StatusBadge :label="resource.trafficClass ?? 'online'" :state="resource.trafficClass ?? 'online'" />
        <StatusBadge :label="resource.priority" :state="resource.priority" />
        <StatusBadge :label="resource.engine ?? 'LOCAL'" :state="resource.engine ?? 'LOCAL'" />
        <StatusBadge :label="resource.lastTraceStopReason ?? 'NONE'" :state="resource.lastTraceStopReason ?? 'NONE'" />
        <StatusBadge
          :label="resource.runtimeSnapshot?.circuitState ?? 'NO_STATE'"
          :state="resource.runtimeSnapshot?.circuitState ?? 'NO_STATE'"
        />
      </div>
    </section>

    <section class="split-grid split-grid--balanced">
      <RuntimeWindowStats :runtime="resource.runtimeSnapshot" />
      <PolicySummary :policy="resource.policySnapshot" />
    </section>

    <section class="panel">
      <div class="panel__header">
        <h2>{{ t('detail.traceState') }}</h2>
        <span>{{ t('detail.traceStateNote') }}</span>
      </div>
      <dl class="definition-grid">
        <div>
          <dt>{{ t('table.outcome') }}</dt>
          <dd><StatusBadge :label="resource.lastTraceOutcome ?? 'NONE'" :state="resource.lastTraceOutcome ?? 'NONE'" /></dd>
        </div>
        <div>
          <dt>{{ t('table.stopReason') }}</dt>
          <dd><StatusBadge :label="resource.lastTraceStopReason ?? 'NONE'" :state="resource.lastTraceStopReason ?? 'NONE'" /></dd>
        </div>
      </dl>
    </section>

    <section class="panel">
      <div class="panel__header">
        <h2>{{ t('detail.instanceHealth') }}</h2>
        <span>{{ instanceSnapshots.length }} {{ t('state.shown') }}</span>
      </div>
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

    <section class="panel">
      <div class="panel__header">
        <h2>{{ t('detail.recentEvents') }}</h2>
        <span>{{ relatedEvents.length }} {{ t('state.shown') }}</span>
      </div>
      <EmptyState
        v-if="relatedEvents.length === 0"
        :title="t('detail.noEvents')"
        :message="t('detail.noEventsMessage')"
      />
      <EventTable v-else :events="relatedEvents" />
    </section>
  </div>
</template>
