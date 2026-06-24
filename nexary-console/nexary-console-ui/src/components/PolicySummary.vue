<script setup lang="ts">
import type { ConsolePolicySnapshot } from '../types/console';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  policy: ConsolePolicySnapshot;
}>();

function thresholdLabel(value: number | null): string {
  return value == null ? 'disabled' : `${value}%`;
}

function durationLabel(value: string | null): string {
  return value ?? 'not set';
}
</script>

<template>
  <section class="panel">
    <div class="panel__header">
      <h2>Policy Snapshot</h2>
      <StatusBadge :label="policy.degraded ? 'DEGRADED' : 'ACTIVE'" :state="policy.degraded ? 'DEGRADED' : 'CLOSED'" />
    </div>
    <dl class="definition-grid">
      <div>
        <dt>Rate limit</dt>
        <dd>{{ policy.maxRequestsPerWindow }} per {{ durationLabel(policy.rateLimitWindow) }}</dd>
      </div>
      <div>
        <dt>Concurrency</dt>
        <dd>{{ policy.maxConcurrency }}</dd>
      </div>
      <div>
        <dt>Minimum calls</dt>
        <dd>{{ policy.minimumRequests }}</dd>
      </div>
      <div>
        <dt>Failure threshold</dt>
        <dd>{{ thresholdLabel(policy.failureRateThreshold) }}</dd>
      </div>
      <div>
        <dt>Slow call threshold</dt>
        <dd>{{ thresholdLabel(policy.slowCallThreshold) }}</dd>
      </div>
      <div>
        <dt>Slow duration</dt>
        <dd>{{ durationLabel(policy.slowCallDuration) }}</dd>
      </div>
      <div>
        <dt>Open duration</dt>
        <dd>{{ durationLabel(policy.openStateDuration) }}</dd>
      </div>
      <div>
        <dt>Half-open calls</dt>
        <dd>{{ policy.halfOpenMaxCalls }}</dd>
      </div>
      <div>
        <dt>Sliding window</dt>
        <dd>{{ policy.slidingWindowSize }} / {{ durationLabel(policy.slidingWindowDuration) }}</dd>
      </div>
      <div>
        <dt>Consecutive failures</dt>
        <dd>{{ policy.consecutiveFailureThreshold }}</dd>
      </div>
    </dl>
  </section>
</template>
