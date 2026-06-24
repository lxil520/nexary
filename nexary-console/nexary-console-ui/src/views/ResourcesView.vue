<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import ResourceFilterBar from '../components/ResourceFilterBar.vue';
import ResourceTable from '../components/ResourceTable.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { uniqueSorted, useFilteredResources } from '../composables/useLocalFilters';
import { useLocale } from '../composables/useLocale';
import type { ResourceFilters } from '../types/console';

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { resources, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { t } = useLocale();
const filters = ref<ResourceFilters>({
  keyword: '',
  kind: 'ALL',
  circuitState: 'ALL',
  provider: 'ALL',
});

const filteredResources = useFilteredResources(resources, filters);
const kindOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.kind)));
const circuitOptions = computed(() =>
  uniqueSorted(resources.value.map((resource) => resource.runtimeSnapshot?.circuitState ?? 'NO_STATE')),
);
const providerOptions = computed(() => uniqueSorted(resources.value.map((resource) => resource.provider)));

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshAll();
  }
});
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="t('resources.loading')" />
  <ErrorState
    v-else-if="errorMessage"
    :title="t('resources.errorTitle')"
    :message="errorMessage"
    @retry="refreshAll"
  />
  <div v-else class="view-stack">
    <ResourceFilterBar
      :filters="filters"
      :kind-options="kindOptions"
      :circuit-options="circuitOptions"
      :provider-options="providerOptions"
      @update="filters = $event"
    />
    <EmptyState
      v-if="hasLoaded && resources.length === 0"
      :title="t('resources.noResources')"
      :message="t('resources.noResourcesMessage')"
    />
    <EmptyState
      v-else-if="filteredResources.length === 0"
      :title="t('resources.noMatch')"
      :message="t('resources.noMatchMessage')"
    />
    <ResourceTable v-else :resources="filteredResources" @select="emit('selectResource', $event)" />
  </div>
</template>
