<script setup lang="ts">
import type { ConsoleResource } from '../types/console';
import { useLocale } from '../composables/useLocale';
import StatusBadge from './StatusBadge.vue';

defineProps<{
  resources: readonly ConsoleResource[];
  compact?: boolean;
}>();

defineEmits<{
  select: [resourceKey: string];
}>();

const { enumLabel, t } = useLocale();

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
    <table class="data-table" :class="{ 'data-table--compact': compact }">
      <thead>
        <tr>
          <th scope="col">{{ t('table.resource') }}</th>
          <th scope="col">{{ t('table.kind') }}</th>
          <th v-if="!compact" scope="col">{{ t('table.provider') }}</th>
          <th v-if="!compact" scope="col">{{ t('table.priority') }}</th>
          <th scope="col">{{ t('table.circuit') }}</th>
          <th scope="col">{{ t('table.calls') }}</th>
          <th scope="col">{{ t('table.rejected') }}</th>
          <th scope="col">{{ t('table.lastReason') }}</th>
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
          <td>{{ enumLabel(resource.kind) }}</td>
          <td v-if="!compact">{{ resource.provider }}</td>
          <td v-if="!compact">{{ resource.priority }}</td>
          <td><StatusBadge :label="circuitLabel(resource)" :state="circuitLabel(resource)" /></td>
          <td>{{ callsLabel(resource) }}</td>
          <td>{{ resource.runtimeSnapshot?.totalRejections ?? 0 }}</td>
          <td><StatusBadge :label="rejectionLabel(resource)" :state="rejectionLabel(resource)" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
