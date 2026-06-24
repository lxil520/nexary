<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import EventTable from '../components/EventTable.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { uniqueSorted, useFilteredEvents } from '../composables/useLocalFilters';
import type { EventFilters } from '../types/console';

const { events, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const filters = ref<EventFilters>({
  keyword: '',
  outcome: 'ALL',
  rejectionReason: 'ALL',
  circuitState: 'ALL',
});

const filteredEvents = useFilteredEvents(events, filters);
const outcomeOptions = computed(() => uniqueSorted(events.value.map((event) => event.outcome)));
const reasonOptions = computed(() => uniqueSorted(events.value.map((event) => event.rejectionReason)));
const circuitOptions = computed(() => uniqueSorted(events.value.map((event) => event.circuitState)));

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshAll();
  }
});
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" label="Loading events" />
  <ErrorState v-else-if="errorMessage" title="Events failed to load" :message="errorMessage" @retry="refreshAll" />
  <div v-else class="view-stack">
    <section class="filter-bar" aria-label="Event filters">
      <label class="field field--search">
        <span>Search</span>
        <input v-model="filters.keyword" type="search" placeholder="resource, action, reason" />
      </label>
      <label class="field">
        <span>Outcome</span>
        <select v-model="filters.outcome">
          <option value="ALL">All</option>
          <option v-for="outcome in outcomeOptions" :key="outcome" :value="outcome">{{ outcome }}</option>
        </select>
      </label>
      <label class="field">
        <span>Reason</span>
        <select v-model="filters.rejectionReason">
          <option value="ALL">All</option>
          <option v-for="reason in reasonOptions" :key="reason" :value="reason">{{ reason }}</option>
        </select>
      </label>
      <label class="field">
        <span>Circuit</span>
        <select v-model="filters.circuitState">
          <option value="ALL">All</option>
          <option v-for="state in circuitOptions" :key="state" :value="state">{{ state }}</option>
        </select>
      </label>
    </section>

    <EmptyState
      v-if="hasLoaded && events.length === 0"
      title="No events retained"
      message="Run a governed call to record low-cardinality success, failure, rejection, or fallback events."
    />
    <EmptyState
      v-else-if="filteredEvents.length === 0"
      title="No events match the filters"
      message="Clear a filter or search for a known resource key, action, or rejection reason."
    />
    <EventTable v-else :events="filteredEvents" />
  </div>
</template>
