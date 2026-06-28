<script setup lang="ts">
import { computed, onMounted, watch } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';
import type { ConsoleTraceStep } from '../types/console';

const props = defineProps<{
  traceKey: string;
}>();

const emit = defineEmits<{
  selectResource: [resourceKey: string];
}>();

const { isLoading, errorMessage, hasLoaded, refreshAll, loadTrace, traceByKey } = useConsoleData();
const { enumLabel, formatTimestamp, locale, t } = useLocale();
const trace = computed(() => traceByKey(props.traceKey));
const copy = computed(() =>
  locale.value === 'zh'
    ? {
        hero: 'Trace 证据详情',
        heroNote: '把一次本地故障 Trace 拆成阶段、资源、动作、原因和建议检查资源。',
        outcome: '最终结果',
        stopReason: '停止原因',
        rootResource: '根资源',
        steps: '步骤',
      }
    : {
        hero: 'Trace Evidence Detail',
        heroNote: 'Break one local fault trace into stages, resources, actions, reasons, and suggested checks.',
        outcome: 'Terminal Outcome',
        stopReason: 'Stop Reason',
        rootResource: 'Root Resource',
        steps: 'Steps',
      },
);

function stepReason(step: ConsoleTraceStep): string {
  if (step.quarantineReason && step.quarantineReason !== 'NONE') {
    return step.quarantineReason;
  }
  if (step.isolationReason && step.isolationReason !== 'NONE') {
    return step.isolationReason;
  }
  if (step.retryStopReason && step.retryStopReason !== 'NONE') {
    return step.retryStopReason;
  }
  if (step.blockReason && step.blockReason !== 'NONE') {
    return step.blockReason;
  }
  if (step.cancellationReason && step.cancellationReason !== 'NONE') {
    return step.cancellationReason;
  }
  return step.rejectionReason ?? 'NONE';
}

async function ensureTraceLoaded(): Promise<void> {
  if (!hasLoaded.value) {
    await refreshAll();
  }
  if (!trace.value && props.traceKey) {
    await loadTrace(props.traceKey);
  }
}

onMounted(() => {
  void ensureTraceLoaded();
});

watch(
  () => props.traceKey,
  () => {
    void ensureTraceLoaded();
  },
);
</script>

<template>
  <LoadingBlock v-if="isLoading && !trace" :label="t('traceDetail.loading')" />
  <ErrorState
    v-else-if="errorMessage"
    :title="t('traceDetail.errorTitle')"
    :message="errorMessage"
    @retry="ensureTraceLoaded"
  />
  <EmptyState
    v-else-if="!trace"
    :title="t('traceDetail.notFound')"
    :message="t('traceDetail.notFoundMessage')"
  />
  <div v-else class="ops-page">
    <section class="ops-hero ops-hero--compact">
      <div>
        <p class="eyebrow">{{ copy.hero }}</p>
        <h2>{{ trace.traceKey }}</h2>
        <p class="resource-key">{{ trace.rootResourceKey }}</p>
        <p>{{ copy.heroNote }}</p>
      </div>
      <div class="ops-hero__metrics">
        <article>
          <span>{{ copy.outcome }}</span>
          <strong>{{ enumLabel(trace.terminalOutcome) }}</strong>
        </article>
        <article>
          <span>{{ copy.stopReason }}</span>
          <strong>{{ enumLabel(trace.primaryStopReason) }}</strong>
        </article>
        <article>
          <span>{{ copy.rootResource }}</span>
          <strong>{{ trace.rootResourceKey }}</strong>
        </article>
        <article>
          <span>{{ copy.steps }}</span>
          <strong>{{ trace.steps.length }}</strong>
        </article>
      </div>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ t('traceDetail.summary') }}</span>
          <h3>{{ t('traceDetail.summary') }}</h3>
        </div>
        <span>{{ trace.steps.length }} {{ t('table.steps') }}</span>
      </header>
      <dl class="ops-definition-list">
        <div>
          <dt>{{ t('table.resource') }}</dt>
          <dd>{{ trace.rootResourceKey }}</dd>
        </div>
        <div>
          <dt>{{ t('table.suggestedResource') }}</dt>
          <dd>
            <button
              v-if="trace.suggestedResourceKey"
              class="link-button"
              type="button"
              @click="emit('selectResource', trace.suggestedResourceKey)"
            >
              <span class="table-primary">{{ trace.suggestedResourceKey }}</span>
            </button>
            <span v-else>{{ enumLabel('NONE') }}</span>
          </dd>
        </div>
        <div>
          <dt>{{ t('traceDetail.startedAt') }}</dt>
          <dd>{{ formatTimestamp(trace.startedAt) }}</dd>
        </div>
        <div>
          <dt>{{ t('traceDetail.lastEventAt') }}</dt>
          <dd>{{ formatTimestamp(trace.lastEventAt) }}</dd>
        </div>
      </dl>
    </section>

    <section class="ops-panel">
      <header>
        <div>
          <span>{{ t('traceDetail.lowCardinality') }}</span>
          <h3>{{ t('traceDetail.steps') }}</h3>
        </div>
        <span>{{ t('traceDetail.lowCardinality') }}</span>
      </header>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th scope="col">{{ t('table.time') }}</th>
              <th scope="col">{{ t('table.stage') }}</th>
              <th scope="col">{{ t('table.resource') }}</th>
              <th scope="col">{{ t('table.action') }}</th>
              <th scope="col">{{ t('table.outcome') }}</th>
              <th scope="col">{{ t('table.reason') }}</th>
              <th scope="col">{{ t('table.instanceState') }}</th>
              <th scope="col">{{ t('table.duration') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(step, index) in trace.steps" :key="`${step.timestamp}-${step.resourceKey}-${index}`">
              <td>{{ formatTimestamp(step.timestamp) }}</td>
              <td><StatusBadge :label="step.stage" :state="step.stage" /></td>
              <td class="table-monospace">{{ step.resourceKey }}</td>
              <td><StatusBadge :label="step.action" :state="step.action" /></td>
              <td><StatusBadge :label="step.outcome" :state="step.outcome" /></td>
              <td><StatusBadge :label="stepReason(step)" :state="stepReason(step)" /></td>
              <td><StatusBadge :label="step.instanceHealthState ?? 'HEALTHY'" :state="step.instanceHealthState ?? 'HEALTHY'" /></td>
              <td>{{ step.durationBucket }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
