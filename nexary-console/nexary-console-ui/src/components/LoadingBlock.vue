<script setup lang="ts">
defineProps<{
  label: string;
}>();
</script>

<template>
  <section class="state-block state-block--loading" aria-live="polite">
    <span class="loading-dot" aria-hidden="true"></span>
    <span>{{ label }}</span>
  </section>
</template>
