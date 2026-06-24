<script setup lang="ts">
type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

defineProps<{
  label: string;
  value: string | number;
  detail?: string;
  tone?: Tone;
}>();
</script>

<template>
  <section class="metric-card" :data-tone="tone ?? 'neutral'">
    <div class="metric-card__label">{{ label }}</div>
    <div class="metric-card__value">{{ value }}</div>
    <div v-if="detail" class="metric-card__detail">{{ detail }}</div>
  </section>
</template>
