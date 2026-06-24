<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  label: string;
  state?: string | null;
}>();

const tone = computed(() => {
  const value = (props.state ?? props.label).toUpperCase();
  if (value === 'SUCCESS' || value === 'CLOSED') {
    return 'success';
  }
  if (value === 'OPEN' || value === 'FAILURE' || value === 'REJECTED' || value.includes('LIMITED')) {
    return 'danger';
  }
  if (value === 'HALF_OPEN' || value === 'DEGRADED' || value.includes('SLOW')) {
    return 'warning';
  }
  if (value === 'EXECUTE' || value === 'FALLBACK') {
    return 'info';
  }
  return 'neutral';
});
</script>

<template>
  <span class="status-badge" :data-tone="tone">{{ label }}</span>
</template>
