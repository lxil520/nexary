<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import EventTable from '../components/EventTable.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { uniqueSorted, useFilteredEvents } from '../composables/useLocalFilters';
import { useLocale } from '../composables/useLocale';
import type { EventFilters } from '../types/console';

const { events, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { enumLabel, t } = useLocale();
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
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="t('events.loading')" />
  <ErrorState v-else-if="errorMessage" :title="t('events.errorTitle')" :message="errorMessage" @retry="refreshAll" />
  <div v-else class="view-stack">
    <section class="filter-bar" aria-label="Event filters">
      <label class="field field--search">
        <span>{{ t('filters.search') }}</span>
        <input v-model="filters.keyword" type="search" :placeholder="t('filters.searchEventsPlaceholder')" />
      </label>
      <label class="field">
        <span>{{ t('filters.outcome') }}</span>
        <select v-model="filters.outcome">
          <option value="ALL">{{ t('filters.all') }}</option>
          <option v-for="outcome in outcomeOptions" :key="outcome" :value="outcome">{{ enumLabel(outcome) }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ t('filters.reason') }}</span>
        <select v-model="filters.rejectionReason">
          <option value="ALL">{{ t('filters.all') }}</option>
          <option v-for="reason in reasonOptions" :key="reason" :value="reason">{{ enumLabel(reason) }}</option>
        </select>
      </label>
      <label class="field">
        <span>{{ t('filters.circuit') }}</span>
        <select v-model="filters.circuitState">
          <option value="ALL">{{ t('filters.all') }}</option>
          <option v-for="state in circuitOptions" :key="state" :value="state">{{ enumLabel(state) }}</option>
        </select>
      </label>
    </section>

    <EmptyState
      v-if="hasLoaded && events.length === 0"
      :title="t('state.noEvents')"
      :message="t('state.noEventsMessage')"
    />
    <EmptyState
      v-else-if="filteredEvents.length === 0"
      :title="t('events.noMatch')"
      :message="t('events.noMatchMessage')"
    />
    <EventTable v-else :events="filteredEvents" />
  </div>
</template>
