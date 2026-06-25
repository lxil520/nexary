<script setup lang="ts">
import type { ConsoleEvent } from '../types/console';
import { useLocale } from '../composables/useLocale';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  events: readonly ConsoleEvent[];
}>();

const { formatTimestamp, t } = useLocale();
</script>

<template>
  <div class="table-wrap">
    <table class="data-table">
      <thead>
        <tr>
          <th scope="col">{{ t('table.time') }}</th>
          <th scope="col">{{ t('table.resource') }}</th>
          <th scope="col">{{ t('table.engine') }}</th>
          <th scope="col">{{ t('table.action') }}</th>
          <th scope="col">{{ t('table.outcome') }}</th>
          <th scope="col">{{ t('table.reason') }}</th>
          <th scope="col">{{ t('table.cancelReason') }}</th>
          <th scope="col">{{ t('table.circuit') }}</th>
          <th scope="col">{{ t('table.duration') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="event in events" :key="`${event.timestamp}-${event.resourceKey}-${event.action}`">
          <td>{{ formatTimestamp(event.timestamp) }}</td>
          <td class="table-monospace">{{ event.resourceKey }}</td>
          <td><StatusBadge :label="event.engine ?? 'LOCAL'" :state="event.engine ?? 'LOCAL'" /></td>
          <td><StatusBadge :label="event.action" :state="event.action" /></td>
          <td><StatusBadge :label="event.outcome" :state="event.outcome" /></td>
          <td><StatusBadge :label="event.blockReason ?? event.rejectionReason" :state="event.blockReason ?? event.rejectionReason" /></td>
          <td><StatusBadge :label="event.cancellationReason" :state="event.cancellationReason" /></td>
          <td><StatusBadge :label="event.circuitState" :state="event.circuitState" /></td>
          <td>{{ event.durationBucket }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
