<script setup lang="ts">
import StatusBadge from '../components/StatusBadge.vue';
import { useConsoleData } from '../composables/useConsoleData';
import { useLocale } from '../composables/useLocale';

const { settings, lastRefreshAt, refreshAll } = useConsoleData();
const { t } = useLocale();
</script>

<template>
  <div class="view-stack">
    <section class="panel">
      <div class="panel__header">
        <h2>{{ t('settings.title') }}</h2>
        <StatusBadge label="READONLY" state="CLOSED" />
      </div>
      <dl class="definition-grid definition-grid--settings">
        <div class="definition-grid__full">
          <dt>{{ t('settings.apiBase') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}</dd>
        </div>
        <div>
          <dt>{{ t('settings.dataMode') }}</dt>
          <dd>{{ settings.dataMode }}</dd>
        </div>
        <div>
          <dt>{{ t('settings.refreshInterval') }}</dt>
          <dd>{{ settings.refreshIntervalMs }} ms</dd>
        </div>
        <div>
          <dt>{{ t('settings.lastRefresh') }}</dt>
          <dd>{{ lastRefreshAt ?? t('app.notRefreshed') }}</dd>
        </div>
        <div class="definition-grid__full">
          <dt>{{ t('settings.boundary') }}</dt>
          <dd>{{ t('settings.boundaryText') }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel">
      <div class="panel__header">
        <h2>{{ t('settings.endpoints') }}</h2>
        <button class="button" type="button" @click="refreshAll">{{ t('app.refresh') }}</button>
      </div>
      <dl class="endpoint-list endpoint-list--api">
        <div>
          <dt><span>GET</span>{{ t('settings.summary') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.summary }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.resources') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.resources }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.resourceDetail') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.resourceDetail }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.events') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.events }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.traces') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.traces }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.traceDetail') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.traceDetail }}</dd>
        </div>
        <div>
          <dt><span>GET</span>{{ t('settings.faultSummary') }}</dt>
          <dd class="table-monospace">{{ settings.apiBase }}{{ settings.endpointPaths.faultSummary }}</dd>
        </div>
      </dl>
    </section>
  </div>
</template>
