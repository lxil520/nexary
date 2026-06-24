<script setup lang="ts">
defineProps<{
  title: string;
  message: string;
}>();
</script>

<template>
  <section class="state-block state-block--empty">
    <div class="state-block__copy">
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
    </div>
    <div class="state-block__trace" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </div>
  </section>
</template>
