<script setup lang="ts">
import { useLocale } from '../composables/useLocale';

defineProps<{
  title: string;
  message: string;
}>();

defineEmits<{
  retry: [];
}>();

const { t } = useLocale();
</script>

<template>
  <section class="state-block state-block--error">
    <div>
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
    </div>
    <button class="button button--primary" type="button" @click="$emit('retry')">{{ t('button.retry') }}</button>
  </section>
</template>
