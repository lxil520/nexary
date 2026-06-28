<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import EventTable from '../components/EventTable.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { uniqueSorted, useFilteredEvents } from '../composables/useLocalFilters';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type { EventFilters } from '../types/console';

const { events, isLoading, errorMessage, hasLoaded, refreshAll } = useConsoleData();
const { snapshot, isLoading: platformLoading, hasLoaded: platformLoaded, refreshPlatform } = usePlatformData();
const { enumLabel, formatTimestamp, locale, t } = useLocale();
const filters = ref<EventFilters>({
  keyword: '',
  outcome: 'ALL',
  rejectionReason: 'ALL',
  isolationReason: 'ALL',
  trafficClass: 'ALL',
  priority: 'ALL',
  traceStage: 'ALL',
  traceStopReason: 'ALL',
  circuitState: 'ALL',
});

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载事件流',
        commandFeed: '事件指挥流',
        commandFeedNote: '先看事故级平台信号，再看当前 JVM 事件宽表。',
        incidentLane: '事故泳道',
        signalLane: '平台信号',
        localLane: '本地事件',
        filters: '本地事件筛选',
        noIncidents: '没有事故候选',
        noSignals: '没有平台信号',
        primaryResource: '首要资源',
        evidence: '证据',
        impact: '影响',
        severity: '级别',
        signalType: '信号',
        outcome: '结果',
      }
    : {
        loading: 'Loading event stream',
        commandFeed: 'Event Command Feed',
        commandFeedNote: 'Read platform-level signals first, then inspect current-JVM events.',
        incidentLane: 'Incident Lane',
        signalLane: 'Platform Signals',
        localLane: 'Local Events',
        filters: 'Local Event Filters',
        noIncidents: 'No incident candidates',
        noSignals: 'No platform signals',
        primaryResource: 'Primary Resource',
        evidence: 'Evidence',
        impact: 'Impact',
        severity: 'Severity',
        signalType: 'Signal',
        outcome: 'Outcome',
      },
);

const filteredEvents = useFilteredEvents(events, filters);
const outcomeOptions = computed(() => uniqueSorted(events.value.map((event) => event.outcome)));
const reasonOptions = computed(() => uniqueSorted(events.value.map((event) => event.rejectionReason)));
const isolationOptions = computed(() => uniqueSorted(events.value.map((event) => event.isolationReason ?? 'NONE')));
const trafficOptions = computed(() => uniqueSorted(events.value.map((event) => event.trafficClass ?? 'ONLINE')));
const priorityOptions = computed(() => uniqueSorted(events.value.map((event) => event.priority ?? 'NORMAL')));
const traceStageOptions = computed(() => uniqueSorted(events.value.map((event) => event.traceStage ?? 'GOVERNANCE')));
const traceStopOptions = computed(() => uniqueSorted(events.value.map((event) => event.tracePrimaryStopReason ?? 'NONE')));
const circuitOptions = computed(() => uniqueSorted(events.value.map((event) => event.circuitState)));
const incidents = computed(() => snapshot.value?.incidents ?? []);
const signals = computed(() =>
  [...(snapshot.value?.signals ?? [])].sort((left, right) => {
    const leftTime = left.timestamp ? Date.parse(left.timestamp) : 0;
    const rightTime = right.timestamp ? Date.parse(right.timestamp) : 0;
    return rightTime - leftTime;
  }).slice(0, 8),
);
const isInitialLoading = computed(() => (isLoading.value && !hasLoaded.value) || (platformLoading.value && !platformLoaded.value));

function refreshEvents(): void {
  void Promise.all([refreshAll(), refreshPlatform()]);
}

onMounted(() => {
  if (!hasLoaded.value || !platformLoaded.value) {
    refreshEvents();
  }
});
</script>

<template>
  <LoadingBlock v-if="isInitialLoading" :label="copy.loading" />
  <ErrorState v-else-if="errorMessage" :title="t('events.errorTitle')" :message="errorMessage" @retry="refreshEvents" />
  <div v-else class="ops-page">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.commandFeed }}</p>
        <h2>{{ copy.commandFeed }}</h2>
        <p>{{ copy.commandFeedNote }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.incidentLane }}</span>
          <strong>{{ incidents.length }}</strong>
        </article>
        <article>
          <span>{{ copy.signalLane }}</span>
          <strong>{{ signals.length }}</strong>
        </article>
        <article>
          <span>{{ copy.localLane }}</span>
          <strong>{{ events.length }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-dual-grid">
      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.incidentLane }}</span>
            <h3>{{ copy.incidentLane }}</h3>
          </div>
          <span>{{ incidents.length }} {{ t('state.shown') }}</span>
        </header>
        <EmptyState v-if="incidents.length === 0" :title="copy.noIncidents" :message="t('state.noEventsMessage')" />
        <div v-else class="ops-incident-list">
          <article v-for="incident in incidents" :key="incident.incidentKey">
            <div>
              <StatusBadge :label="incident.severity" :state="incident.severity" />
              <strong>{{ incident.title }}</strong>
              <small>{{ copy.impact }} {{ incident.impactScope.serviceKey }} / {{ incident.impactScope.zoneKey }}</small>
            </div>
            <dl>
              <div>
                <dt>{{ copy.primaryResource }}</dt>
                <dd>{{ incident.primaryResourceKey }}</dd>
              </div>
              <div>
                <dt>{{ copy.evidence }}</dt>
                <dd>{{ incident.evidenceCount }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </div>

      <div class="ops-panel">
        <header>
          <div>
            <span>{{ copy.signalLane }}</span>
            <h3>{{ copy.signalLane }}</h3>
          </div>
          <span>{{ signals.length }} {{ t('state.shown') }}</span>
        </header>
        <EmptyState v-if="signals.length === 0" :title="copy.noSignals" :message="t('state.noEventsMessage')" />
        <ol v-else class="ops-timeline ops-timeline--dense">
          <li v-for="signal in signals" :key="`${signal.timestamp}-${signal.resourceKey}-${signal.signalType}`">
            <time>{{ formatTimestamp(signal.timestamp) }}</time>
            <strong>{{ signal.serviceKey }} / {{ signal.resourceKey }}</strong>
            <span>{{ copy.signalType }} {{ signal.signalType }} / {{ copy.outcome }} {{ signal.outcome }}</span>
          </li>
        </ol>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ copy.filters }}</span>
          <h3>{{ copy.localLane }}</h3>
        </div>
        <span>{{ filteredEvents.length }} {{ t('state.shown') }}</span>
      </header>
      <section class="filter-bar ops-filter-bar" aria-label="Event filters">
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
          <span>{{ t('filters.isolation') }}</span>
          <select v-model="filters.isolationReason">
            <option value="ALL">{{ t('filters.all') }}</option>
            <option v-for="reason in isolationOptions" :key="reason" :value="reason">{{ enumLabel(reason) }}</option>
          </select>
        </label>
        <label class="field">
          <span>{{ t('filters.traffic') }}</span>
          <select v-model="filters.trafficClass">
            <option value="ALL">{{ t('filters.all') }}</option>
            <option v-for="traffic in trafficOptions" :key="traffic" :value="traffic">{{ enumLabel(traffic) }}</option>
          </select>
        </label>
        <label class="field">
          <span>{{ t('filters.priority') }}</span>
          <select v-model="filters.priority">
            <option value="ALL">{{ t('filters.all') }}</option>
            <option v-for="priority in priorityOptions" :key="priority" :value="priority">{{ enumLabel(priority) }}</option>
          </select>
        </label>
        <label class="field">
          <span>{{ t('filters.traceStage') }}</span>
          <select v-model="filters.traceStage">
            <option value="ALL">{{ t('filters.all') }}</option>
            <option v-for="stage in traceStageOptions" :key="stage" :value="stage">{{ enumLabel(stage) }}</option>
          </select>
        </label>
        <label class="field">
          <span>{{ t('filters.stopReason') }}</span>
          <select v-model="filters.traceStopReason">
            <option value="ALL">{{ t('filters.all') }}</option>
            <option v-for="reason in traceStopOptions" :key="reason" :value="reason">{{ enumLabel(reason) }}</option>
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
    </section>
  </div>
</template>
