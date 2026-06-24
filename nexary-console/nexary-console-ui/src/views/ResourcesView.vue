<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import ResourceFilterBar from '../components/ResourceFilterBar.vue';
import ResourceTable from '../components/ResourceTable.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { uniqueSorted, useFilteredResources } from '../composables/useLocalFilters';
import type { ResourceFilters } from '../types/console';

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { resources, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
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
  <LoadingBlock v-if="isLoading && !hasLoaded" label="Loading resources" />
  <ErrorState
    v-else-if="errorMessage"
    title="Resources failed to load"
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
      title="No resources found"
      message="Configure governance resources or run the governance sample to produce descriptors."
    />
    <EmptyState
      v-else-if="filteredResources.length === 0"
      title="No resources match the filters"
      message="Clear a filter or search for a known resource name, provider, or operation."
    />
    <ResourceTable v-else :resources="filteredResources" @select="emit('selectResource', $event)" />
  </div>
</template>
