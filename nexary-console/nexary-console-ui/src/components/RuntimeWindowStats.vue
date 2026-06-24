<script setup lang="ts">
import type { ConsoleRuntimeSnapshot } from '../types/console';
import { useLocale } from '../composables/useLocale';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  runtime: ConsoleRuntimeSnapshot | null;
}>();

const { enumLabel, t } = useLocale();

function valueOrZero(value: number | undefined): number {
  return value ?? 0;
}
</script>

<template>
  <section class="panel">
    <div class="panel__header">
      <h2>{{ t('runtime.title') }}</h2>
      <StatusBadge :label="runtime?.circuitState ?? 'NO_STATE'" :state="runtime?.circuitState ?? 'NO_STATE'" />
    </div>
    <dl class="stat-grid">
      <div>
        <dt>{{ t('runtime.calls') }}</dt>
        <dd>{{ valueOrZero(runtime?.windowCalls) }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.failures') }}</dt>
        <dd>{{ valueOrZero(runtime?.windowFailures) }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.slowCalls') }}</dt>
        <dd>{{ valueOrZero(runtime?.windowSlowCalls) }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.rejected') }}</dt>
        <dd>{{ valueOrZero(runtime?.totalRejections) }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.activeConcurrency') }}</dt>
        <dd>{{ valueOrZero(runtime?.activeConcurrency) }} / {{ valueOrZero(runtime?.maxConcurrency) }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.lastOutcome') }}</dt>
        <dd>{{ enumLabel(runtime?.lastOutcome ?? 'NONE') }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.lastReason') }}</dt>
        <dd>{{ enumLabel(runtime?.lastRejectionReason ?? 'NONE') }}</dd>
      </div>
      <div>
        <dt>{{ t('runtime.openUntil') }}</dt>
        <dd>{{ runtime?.openUntil ?? t('runtime.notOpen') }}</dd>
      </div>
    </dl>
  </section>
</template>
