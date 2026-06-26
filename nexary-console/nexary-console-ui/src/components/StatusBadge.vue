<script setup lang="ts">
import { computed } from 'vue';
import { useLocale } from '../composables/useLocale';

const props = defineProps<{
  label: string;
  state?: string | null;
}>();

const tone = computed(() => {
  const value = (props.state ?? props.label).toUpperCase();
  if (value === 'SUCCESS' || value === 'CLOSED') {
    return 'success';
  }
  if (
    value === 'OPEN' ||
    value === 'FAILURE' ||
    value === 'REJECTED' ||
    value === 'BLOCKED' ||
    value === 'INSTANCE_QUARANTINE_CANDIDATE' ||
    value === 'QUARANTINE_CANDIDATE' ||
    value.includes('LIMITED')
  ) {
    return 'danger';
  }
  if (
    value === 'HALF_OPEN' ||
    value === 'DEGRADED' ||
    value === 'ISOLATED' ||
    value === 'CANCELLED' ||
    value === 'RETRY_STOPPED' ||
    value === 'WARN' ||
    value.includes('SLOW')
  ) {
    return 'warning';
  }
  if (value === 'EXECUTE' || value === 'FALLBACK') {
    return 'info';
  }
  return 'neutral';
});

const { enumLabel } = useLocale();
</script>

<template>
  <span class="status-badge" :data-tone="tone" :title="label">
    <span class="status-badge__dot" aria-hidden="true"></span>
    {{ enumLabel(label) }}
  </span>
</template>
