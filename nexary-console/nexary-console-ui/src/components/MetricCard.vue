<script setup lang="ts">
import { computed } from 'vue';

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

const props = defineProps<{
  label: string;
  value: string | number;
  detail?: string;
  tone?: Tone;
}>();

const effectiveTone = computed(() => {
  const numericValue = typeof props.value === 'number' ? props.value : Number(props.value);
  if ((props.tone === 'danger' || props.tone === 'warning') && Number.isFinite(numericValue) && numericValue === 0) {
    return 'neutral';
  }
  return props.tone ?? 'neutral';
});
</script>

<template>
  <section class="metric-card" :data-tone="effectiveTone">
    <div class="metric-card__label">{{ label }}</div>
    <div class="metric-card__value">{{ value }}</div>
    <div v-if="detail" class="metric-card__detail">{{ detail }}</div>
  </section>
</template>
