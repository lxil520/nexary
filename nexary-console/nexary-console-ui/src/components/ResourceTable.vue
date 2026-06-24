<script setup lang="ts">
import type { ConsoleResource } from '../types/console';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  resources: readonly ConsoleResource[];
}>();

defineEmits<{
  select: [resourceKey: string];
}>();

function circuitLabel(resource: ConsoleResource): string {
  return resource.runtimeSnapshot?.circuitState ?? 'NO_STATE';
}

function callsLabel(resource: ConsoleResource): string {
  const runtime = resource.runtimeSnapshot;
  return runtime ? `${runtime.windowCalls} / ${runtime.windowFailures} / ${runtime.windowSlowCalls}` : '0 / 0 / 0';
}

function rejectionLabel(resource: ConsoleResource): string {
  return resource.runtimeSnapshot?.lastRejectionReason ?? 'NONE';
}
</script>

<template>
  <div class="table-wrap">
    <table class="data-table">
      <thead>
        <tr>
          <th scope="col">Resource</th>
          <th scope="col">Kind</th>
          <th scope="col">Provider</th>
          <th scope="col">Priority</th>
          <th scope="col">Circuit</th>
          <th scope="col">Calls / Fail / Slow</th>
          <th scope="col">Rejected</th>
          <th scope="col">Last reason</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="resource in resources" :key="resource.resourceKey">
          <td>
            <button class="link-button" type="button" @click="$emit('select', resource.resourceKey)">
              <span class="table-primary">{{ resource.name }}</span>
              <span class="table-secondary">{{ resource.operation }}</span>
            </button>
          </td>
          <td>{{ resource.kind }}</td>
          <td>{{ resource.provider }}</td>
          <td>{{ resource.priority }}</td>
          <td><StatusBadge :label="circuitLabel(resource)" :state="circuitLabel(resource)" /></td>
          <td>{{ callsLabel(resource) }}</td>
          <td>{{ resource.runtimeSnapshot?.totalRejections ?? 0 }}</td>
          <td><StatusBadge :label="rejectionLabel(resource)" :state="rejectionLabel(resource)" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
