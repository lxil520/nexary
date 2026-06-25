<script setup lang="ts">
import { useLocale } from '../composables/useLocale';
import type { ResourceFilters } from '../types/console';

const props = defineProps<{
  filters: ResourceFilters;
  engineOptions: string[];
  kindOptions: string[];
  circuitOptions: string[];
  providerOptions: string[];
  trafficOptions: string[];
  priorityOptions: string[];
}>();

const emit = defineEmits<{
  update: [filters: ResourceFilters];
}>();

const { enumLabel, t } = useLocale();

function updateFilter(key: keyof ResourceFilters, value: string): void {
  emit('update', { ...props.filters, [key]: value });
}
</script>

<template>
  <section class="filter-bar" aria-label="Resource filters">
    <label class="field field--search">
      <span>{{ t('filters.search') }}</span>
      <input
        :value="filters.keyword"
        type="search"
        :placeholder="t('filters.searchResourcesPlaceholder')"
        @input="updateFilter('keyword', ($event.target as HTMLInputElement).value)"
      />
    </label>
    <label class="field">
      <span>{{ t('filters.engine') }}</span>
      <select :value="filters.engine" @change="updateFilter('engine', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">{{ t('filters.all') }}</option>
        <option v-for="engine in engineOptions" :key="engine" :value="engine">{{ enumLabel(engine) }}</option>
      </select>
    </label>
    <label class="field">
      <span>{{ t('filters.kind') }}</span>
      <select :value="filters.kind" @change="updateFilter('kind', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">{{ t('filters.all') }}</option>
        <option v-for="kind in kindOptions" :key="kind" :value="kind">{{ enumLabel(kind) }}</option>
      </select>
    </label>
    <label class="field">
      <span>{{ t('filters.circuit') }}</span>
      <select
        :value="filters.circuitState"
        @change="updateFilter('circuitState', ($event.target as HTMLSelectElement).value)"
      >
        <option value="ALL">{{ t('filters.all') }}</option>
        <option v-for="state in circuitOptions" :key="state" :value="state">{{ enumLabel(state) }}</option>
      </select>
    </label>
    <label class="field">
      <span>{{ t('filters.provider') }}</span>
      <select :value="filters.provider" @change="updateFilter('provider', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">{{ t('filters.all') }}</option>
        <option v-for="provider in providerOptions" :key="provider" :value="provider">{{ provider }}</option>
      </select>
    </label>
    <label class="field">
      <span>{{ t('filters.traffic') }}</span>
      <select
        :value="filters.trafficClass"
        @change="updateFilter('trafficClass', ($event.target as HTMLSelectElement).value)"
      >
        <option value="ALL">{{ t('filters.all') }}</option>
        <option v-for="traffic in trafficOptions" :key="traffic" :value="traffic">{{ enumLabel(traffic) }}</option>
      </select>
    </label>
    <label class="field">
      <span>{{ t('filters.priority') }}</span>
      <select :value="filters.priority" @change="updateFilter('priority', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">{{ t('filters.all') }}</option>
        <option v-for="priority in priorityOptions" :key="priority" :value="priority">{{ enumLabel(priority) }}</option>
      </select>
    </label>
  </section>
</template>
