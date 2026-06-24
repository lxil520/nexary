<script setup lang="ts">
import type { ConsolePolicySnapshot } from '../types/console';
import { useLocale } from '../composables/useLocale';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  policy: ConsolePolicySnapshot;
}>();

const { t } = useLocale();

function thresholdLabel(value: number | null): string {
  return value == null ? t('policy.disabled') : `${value}%`;
}

function durationLabel(value: string | null): string {
  return value ?? t('policy.notSet');
}
</script>

<template>
  <section class="panel">
    <div class="panel__header">
      <h2>{{ t('policy.title') }}</h2>
      <StatusBadge :label="policy.degraded ? 'DEGRADED' : 'ACTIVE'" :state="policy.degraded ? 'DEGRADED' : 'CLOSED'" />
    </div>
    <dl class="definition-grid">
      <div>
        <dt>{{ t('policy.rateLimit') }}</dt>
        <dd>{{ policy.maxRequestsPerWindow }} {{ t('policy.per') }} {{ durationLabel(policy.rateLimitWindow) }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.concurrency') }}</dt>
        <dd>{{ policy.maxConcurrency }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.minimumCalls') }}</dt>
        <dd>{{ policy.minimumRequests }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.failureThreshold') }}</dt>
        <dd>{{ thresholdLabel(policy.failureRateThreshold) }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.slowCallThreshold') }}</dt>
        <dd>{{ thresholdLabel(policy.slowCallThreshold) }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.slowDuration') }}</dt>
        <dd>{{ durationLabel(policy.slowCallDuration) }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.openDuration') }}</dt>
        <dd>{{ durationLabel(policy.openStateDuration) }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.halfOpenCalls') }}</dt>
        <dd>{{ policy.halfOpenMaxCalls }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.slidingWindow') }}</dt>
        <dd>{{ policy.slidingWindowSize }} / {{ durationLabel(policy.slidingWindowDuration) }}</dd>
      </div>
      <div>
        <dt>{{ t('policy.consecutiveFailures') }}</dt>
        <dd>{{ policy.consecutiveFailureThreshold }}</dd>
      </div>
    </dl>
  </section>
</template>
