<script setup lang="ts">
import type { ConsoleEvent } from '../types/console';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  events: readonly ConsoleEvent[];
}>();

function formatTimestamp(value: string | null): string {
  if (!value) {
    return 'not recorded';
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value));
}
</script>

<template>
  <div class="table-wrap">
    <table class="data-table">
      <thead>
        <tr>
          <th scope="col">Time</th>
          <th scope="col">Resource</th>
          <th scope="col">Action</th>
          <th scope="col">Outcome</th>
          <th scope="col">Reason</th>
          <th scope="col">Circuit</th>
          <th scope="col">Duration</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="event in events" :key="`${event.timestamp}-${event.resourceKey}-${event.action}`">
          <td>{{ formatTimestamp(event.timestamp) }}</td>
          <td class="table-monospace">{{ event.resourceKey }}</td>
          <td><StatusBadge :label="event.action" :state="event.action" /></td>
          <td><StatusBadge :label="event.outcome" :state="event.outcome" /></td>
          <td><StatusBadge :label="event.rejectionReason" :state="event.rejectionReason" /></td>
          <td><StatusBadge :label="event.circuitState" :state="event.circuitState" /></td>
          <td>{{ event.durationBucket }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
