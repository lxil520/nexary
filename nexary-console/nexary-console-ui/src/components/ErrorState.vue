<script setup lang="ts">
defineProps<{
  title: string;
  message: string;
}>();

defineEmits<{
  retry: [];
}>();
</script>

<template>
  <section class="state-block state-block--error">
    <div>
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
    </div>
    <button class="button button--primary" type="button" @click="$emit('retry')">Retry</button>
  </section>
</template>
