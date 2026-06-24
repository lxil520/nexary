<script setup lang="ts">
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';

const { settings, lastRefreshAt, refreshAll } = useConsoleData();
</script>

<template>
  <div class="view-stack">
    <section class="panel">
      <div class="panel__header">
        <h2>Readonly Console Settings</h2>
        <StatusBadge label="READONLY" state="CLOSED" />
      </div>
      <dl class="definition-grid">
        <div>
          <dt>API base</dt>
          <dd class="table-monospace">{{ settings.apiBase }}</dd>
        </div>
        <div>
          <dt>Data mode</dt>
          <dd>{{ settings.dataMode }}</dd>
        </div>
        <div>
          <dt>Refresh interval</dt>
          <dd>{{ settings.refreshIntervalMs }} ms</dd>
        </div>
        <div>
          <dt>Last refresh</dt>
          <dd>{{ lastRefreshAt ?? 'not refreshed' }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel">
      <div class="panel__header">
        <h2>Endpoints</h2>
        <button class="button" type="button" @click="refreshAll">Refresh</button>
      </div>
      <dl class="endpoint-list">
        <div>
          <dt>Summary</dt>
          <dd>{{ settings.apiBase }}{{ settings.endpointPaths.summary }}</dd>
        </div>
        <div>
          <dt>Resources</dt>
          <dd>{{ settings.apiBase }}{{ settings.endpointPaths.resources }}</dd>
        </div>
        <div>
          <dt>Resource detail</dt>
          <dd>{{ settings.apiBase }}{{ settings.endpointPaths.resourceDetail }}</dd>
        </div>
        <div>
          <dt>Events</dt>
          <dd>{{ settings.apiBase }}{{ settings.endpointPaths.events }}</dd>
        </div>
      </dl>
    </section>
  </div>
</template>
