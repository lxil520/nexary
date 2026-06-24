<script setup lang="ts">
import type { ConsoleRuntimeSnapshot } from '../types/console';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  runtime: ConsoleRuntimeSnapshot | null;
}>();

function valueOrZero(value: number | undefined): number {
  return value ?? 0;
}
</script>

<template>
  <section class="panel">
    <div class="panel__header">
      <h2>Runtime Window</h2>
      <StatusBadge :label="runtime?.circuitState ?? 'NO_STATE'" :state="runtime?.circuitState ?? 'NO_STATE'" />
    </div>
    <dl class="stat-grid">
      <div>
        <dt>Calls</dt>
        <dd>{{ valueOrZero(runtime?.windowCalls) }}</dd>
      </div>
      <div>
        <dt>Failures</dt>
        <dd>{{ valueOrZero(runtime?.windowFailures) }}</dd>
      </div>
      <div>
        <dt>Slow calls</dt>
        <dd>{{ valueOrZero(runtime?.windowSlowCalls) }}</dd>
      </div>
      <div>
        <dt>Rejected</dt>
        <dd>{{ valueOrZero(runtime?.totalRejections) }}</dd>
      </div>
      <div>
        <dt>Active concurrency</dt>
        <dd>{{ valueOrZero(runtime?.activeConcurrency) }} / {{ valueOrZero(runtime?.maxConcurrency) }}</dd>
      </div>
      <div>
        <dt>Last outcome</dt>
        <dd>{{ runtime?.lastOutcome ?? 'NONE' }}</dd>
      </div>
      <div>
        <dt>Last reason</dt>
        <dd>{{ runtime?.lastRejectionReason ?? 'NONE' }}</dd>
      </div>
      <div>
        <dt>Open until</dt>
        <dd>{{ runtime?.openUntil ?? 'not open' }}</dd>
      </div>
    </dl>
  </section>
</template>
