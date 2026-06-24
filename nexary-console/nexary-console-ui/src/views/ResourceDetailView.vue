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
        <p class="eyebrow">{{ enumLabel(resource.kind) }} / {{ resource.provider }}</p>
        <h1>{{ resource.name }}</h1>
        <p class="resource-key">{{ resource.resourceKey }}</p>
      </div>
      <div class="detail-header__badges">
        <StatusBadge :label="resource.priority" :state="resource.priority" />
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
