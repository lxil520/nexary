<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import OverviewView from './views/OverviewView.vue';
import ResourcesView from './views/ResourcesView.vue';
import ResourceDetailView from './views/ResourceDetailView.vue';
import EventsView from './views/EventsView.vue';
import SettingsReadonlyView from './views/SettingsReadonlyView.vue';
import { useConsoleData } from './composables/useConsoleData';
import { useLocale } from './composables/useLocale';
import logoUrl from './assets/nexary-logo.svg';

type ViewId = 'overview' | 'resources' | 'resource-detail' | 'events' | 'settings';

interface RouteState {
  view: ViewId;
  resourceKey: string | null;
}

const navigationItems: Array<{ id: ViewId; labelKey: 'nav.overview' | 'nav.resources' | 'nav.events' | 'nav.settings' }> = [
  { id: 'overview', labelKey: 'nav.overview' },
  { id: 'resources', labelKey: 'nav.resources' },
  { id: 'events', labelKey: 'nav.events' },
  { id: 'settings', labelKey: 'nav.settings' },
];

const route = ref<RouteState>(routeFromHash());
const { isLoading, lastRefreshAt, refreshAll } = useConsoleData();
const { locale, setLocale, t } = useLocale();

const title = computed(() => {
  if (route.value.view === 'resource-detail') {
    return t('title.resourceDetail');
  }
  const item = navigationItems.find((entry) => entry.id === route.value.view);
  return item ? t(item.labelKey) : t('nav.overview');
});

const refreshLabel = computed(() =>
  lastRefreshAt.value ? `${t('app.updated')} ${lastRefreshAt.value}` : t('app.notRefreshed'),
);

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
        <img class="brand-logo" :src="logoUrl" alt="Nexary" />
        <div class="brand-copy">
          <strong>Nexary Console</strong>
          <span>{{ t('app.subtitle') }}</span>
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
          {{ t(item.labelKey) }}
        </button>
      </nav>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div>
          <p class="eyebrow">{{ t('app.scope') }}</p>
          <h1>{{ title }}</h1>
          <p class="topbar__note">{{ t('app.jvmNote') }}</p>
        </div>
        <div class="topbar__actions">
          <div class="locale-segment" :aria-label="t('app.language')">
            <button type="button" :class="{ 'is-active': locale === 'zh' }" @click="setLocale('zh')">中文</button>
            <button type="button" :class="{ 'is-active': locale === 'en' }" @click="setLocale('en')">EN</button>
          </div>
          <span class="refresh-state">{{ refreshLabel }}</span>
          <button class="button button--primary" type="button" :disabled="isLoading" @click="refreshAll">
            {{ isLoading ? t('app.refreshing') : t('app.refresh') }}
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
