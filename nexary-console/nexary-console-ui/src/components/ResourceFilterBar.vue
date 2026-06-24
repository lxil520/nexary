<script setup lang="ts">
import type { ResourceFilters } from '../types/console';

const props = defineProps<{
  filters: ResourceFilters;
  kindOptions: string[];
  circuitOptions: string[];
  providerOptions: string[];
}>();

const emit = defineEmits<{
  update: [filters: ResourceFilters];
}>();

function updateFilter(key: keyof ResourceFilters, value: string): void {
  emit('update', { ...props.filters, [key]: value });
}
</script>

<template>
  <section class="filter-bar" aria-label="Resource filters">
    <label class="field field--search">
      <span>Search</span>
      <input
        :value="filters.keyword"
        type="search"
        placeholder="resource, provider, operation"
        @input="updateFilter('keyword', ($event.target as HTMLInputElement).value)"
      />
    </label>
    <label class="field">
      <span>Kind</span>
      <select :value="filters.kind" @change="updateFilter('kind', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">All</option>
        <option v-for="kind in kindOptions" :key="kind" :value="kind">{{ kind }}</option>
      </select>
    </label>
    <label class="field">
      <span>Circuit</span>
      <select
        :value="filters.circuitState"
        @change="updateFilter('circuitState', ($event.target as HTMLSelectElement).value)"
      >
        <option value="ALL">All</option>
        <option v-for="state in circuitOptions" :key="state" :value="state">{{ state }}</option>
      </select>
    </label>
    <label class="field">
      <span>Provider</span>
      <select :value="filters.provider" @change="updateFilter('provider', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">All</option>
        <option v-for="provider in providerOptions" :key="provider" :value="provider">{{ provider }}</option>
      </select>
    </label>
  </section>
</template>
