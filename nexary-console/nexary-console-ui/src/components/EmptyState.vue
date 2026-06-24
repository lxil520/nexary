<script setup lang="ts">
defineProps<{
  title: string;
  message: string;
}>();
</script>

<template>
  <section class="state-block state-block--empty">
    <h2>{{ title }}</h2>
    <p>{{ message }}</p>
  </section>
</template>
