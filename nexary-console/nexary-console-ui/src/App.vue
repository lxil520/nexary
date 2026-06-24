<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import OverviewView from './views/OverviewView.vue';
import ResourcesView from './views/ResourcesView.vue';
import ResourceDetailView from './views/ResourceDetailView.vue';
import EventsView from './views/EventsView.vue';
import SettingsReadonlyView from './views/SettingsReadonlyView.vue';
import { useConsoleData } from './composables/useConsoleData';

type ViewId = 'overview' | 'resources' | 'resource-detail' | 'events' | 'settings';

interface RouteState {
  view: ViewId;
  resourceKey: string | null;
}

const navigationItems: Array<{ id: ViewId; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'resources', label: 'Resources' },
  { id: 'events', label: 'Events' },
  { id: 'settings', label: 'Settings' },
];

const route = ref<RouteState>(routeFromHash());
const { isLoading, lastRefreshAt, refreshAll } = useConsoleData();

const title = computed(() => {
  if (route.value.view === 'resource-detail') {
    return 'Resource Detail';
  }
  const item = navigationItems.find((entry) => entry.id === route.value.view);
  return item?.label ?? 'Overview';
});

function navigate(view: ViewId): void {
  setHash(view, null);
}

function openResource(resourceKey: string): void {
  setHash('resource-detail', resourceKey);
}

function setHash(view: ViewId, resourceKey: string | null): void {
  if (view === 'overview') {
    window.location.hash = '';
    route.value = { view, resourceKey: null };
    return;
  }
  const encodedKey = resourceKey == null ? '' : `/${encodeURIComponent(resourceKey)}`;
  window.location.hash = `${view}${encodedKey}`;
  route.value = { view, resourceKey };
}

function syncRoute(): void {
  route.value = routeFromHash();
}

function routeFromHash(): RouteState {
  const rawHash = window.location.hash.replace(/^#/, '');
  if (rawHash.length === 0 || rawHash === 'overview') {
    return { view: 'overview', resourceKey: null };
  }
  const [view, encodedKey] = rawHash.split('/');
  if (view === 'resources' || view === 'events' || view === 'settings') {
    return { view, resourceKey: null };
  }
  if (view === 'resource-detail' && encodedKey) {
    return { view, resourceKey: decodeURIComponent(encodedKey) };
  }
  return { view: 'overview', resourceKey: null };
}

onMounted(() => {
  window.addEventListener('hashchange', syncRoute);
});

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute);
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" aria-label="Console navigation">
      <div class="brand-block">
        <span class="brand-mark">N</span>
        <div>
          <strong>Nexary Console</strong>
          <span>readonly diagnostics</span>
        </div>
      </div>
      <nav class="nav-list">
        <button
          v-for="item in navigationItems"
          :key="item.id"
          type="button"
          :class="{ 'is-active': route.view === item.id }"
          @click="navigate(item.id)"
        >
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <p class="eyebrow">Local governance runtime</p>
          <h1>{{ title }}</h1>
        </div>
        <div class="topbar__actions">
          <span class="refresh-state">{{ lastRefreshAt ? `Updated ${lastRefreshAt}` : 'Not refreshed' }}</span>
          <button class="button button--primary" type="button" :disabled="isLoading" @click="refreshAll">
            {{ isLoading ? 'Refreshing' : 'Refresh' }}
          </button>
        </div>
      </header>

      <OverviewView v-if="route.view === 'overview'" @select-resource="openResource" />
      <ResourcesView v-else-if="route.view === 'resources'" @select-resource="openResource" />
      <ResourceDetailView
        v-else-if="route.view === 'resource-detail' && route.resourceKey"
        :resource-key="route.resourceKey"
      />
      <EventsView v-else-if="route.view === 'events'" />
      <SettingsReadonlyView v-else-if="route.view === 'settings'" />
    </main>
  </div>
</template>
