<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import OverviewView from './views/OverviewView.vue';
import ResourcesView from './views/ResourcesView.vue';
import ResourceDetailView from './views/ResourceDetailView.vue';
import EventsView from './views/EventsView.vue';
import TracesView from './views/TracesView.vue';
import TraceDetailView from './views/TraceDetailView.vue';
import SettingsReadonlyView from './views/SettingsReadonlyView.vue';
import { useConsoleData } from './composables/useConsoleData';
import { useLocale } from './composables/useLocale';
import logoUrl from './assets/nexary-logo.svg';

type ViewId = 'overview' | 'resources' | 'resource-detail' | 'events' | 'traces' | 'trace-detail' | 'settings';

interface RouteState {
  view: ViewId;
  resourceKey: string | null;
  traceKey: string | null;
}

const CONSOLE_BASE_PATH = '/nexary/console';

const navigationItems: Array<{
  id: ViewId;
  labelKey: 'nav.overview' | 'nav.resources' | 'nav.events' | 'nav.traces' | 'nav.settings';
}> = [
  { id: 'overview', labelKey: 'nav.overview' },
  { id: 'resources', labelKey: 'nav.resources' },
  { id: 'events', labelKey: 'nav.events' },
  { id: 'traces', labelKey: 'nav.traces' },
  { id: 'settings', labelKey: 'nav.settings' },
];

const route = ref<RouteState>(routeFromLocation());
const { isLoading, lastRefreshAt, refreshAll } = useConsoleData();
const { locale, setLocale, t } = useLocale();

const title = computed(() => {
  if (route.value.view === 'resource-detail') {
    return t('title.resourceDetail');
  }
  if (route.value.view === 'trace-detail') {
    return t('title.traceDetail');
  }
  const item = navigationItems.find((entry) => entry.id === route.value.view);
  return item ? t(item.labelKey) : t('nav.overview');
});

const refreshLabel = computed(() =>
  lastRefreshAt.value ? `${t('app.updated')} ${lastRefreshAt.value}` : t('app.notRefreshed'),
);

function navigate(view: ViewId): void {
  pushRoute({ view, resourceKey: null, traceKey: null });
}

function openResource(resourceKey: string): void {
  pushRoute({ view: 'resource-detail', resourceKey, traceKey: null });
}

function openTrace(traceKey: string): void {
  pushRoute({ view: 'trace-detail', resourceKey: null, traceKey });
}

function isNavigationItemActive(view: ViewId): boolean {
  if (view === 'resources') {
    return route.value.view === 'resources' || route.value.view === 'resource-detail';
  }
  if (view === 'traces') {
    return route.value.view === 'traces' || route.value.view === 'trace-detail';
  }
  return route.value.view === view;
}

function pushRoute(nextRoute: RouteState): void {
  const nextPath = pathFromRoute(nextRoute);
  if (window.location.pathname !== nextPath || window.location.hash !== '') {
    window.history.pushState(null, '', nextPath);
  }
  route.value = nextRoute;
}

function pathFromRoute(nextRoute: RouteState): string {
  if (nextRoute.view === 'resources') {
    return `${CONSOLE_BASE_PATH}/resources`;
  }
  if (nextRoute.view === 'events') {
    return `${CONSOLE_BASE_PATH}/events`;
  }
  if (nextRoute.view === 'traces') {
    return `${CONSOLE_BASE_PATH}/traces`;
  }
  if (nextRoute.view === 'trace-detail' && nextRoute.traceKey) {
    return `${CONSOLE_BASE_PATH}/traces/${encodeURIComponent(nextRoute.traceKey)}`;
  }
  if (nextRoute.view === 'settings') {
    return `${CONSOLE_BASE_PATH}/settings`;
  }
  if (nextRoute.view === 'resource-detail' && nextRoute.resourceKey) {
    return `${CONSOLE_BASE_PATH}/resources/${encodeURIComponent(nextRoute.resourceKey)}`;
  }
  return CONSOLE_BASE_PATH;
}

function syncRoute(): void {
  route.value = routeFromLocation();
}

function routeFromLocation(): RouteState {
  const hashRoute = routeFromHash();
  if (hashRoute) {
    return hashRoute;
  }
  return routeFromPath(window.location.pathname);
}

function routeFromHash(): RouteState | null {
  const rawHash = window.location.hash.replace(/^#\/?/, '');
  if (rawHash.length === 0) {
    return null;
  }
  if (rawHash === 'overview') {
    return { view: 'overview', resourceKey: null, traceKey: null };
  }
  const [view, encodedKey] = rawHash.split('/');
  if (view === 'resources' || view === 'events' || view === 'traces' || view === 'settings') {
    return { view, resourceKey: null, traceKey: null };
  }
  if (view === 'resource-detail' && encodedKey) {
    const resourceKey = decodePathSegment(encodedKey);
    return resourceKey
      ? { view, resourceKey, traceKey: null }
      : { view: 'overview', resourceKey: null, traceKey: null };
  }
  if (view === 'trace-detail' && encodedKey) {
    const traceKey = decodePathSegment(encodedKey);
    return traceKey ? { view, resourceKey: null, traceKey } : { view: 'overview', resourceKey: null, traceKey: null };
  }
  return { view: 'overview', resourceKey: null, traceKey: null };
}

function routeFromPath(pathname: string): RouteState {
  const normalizedPath = pathname.replace(/\/+$/, '') || '/';
  if (normalizedPath === CONSOLE_BASE_PATH || normalizedPath === `${CONSOLE_BASE_PATH}/overview`) {
    return { view: 'overview', resourceKey: null, traceKey: null };
  }
  if (!normalizedPath.startsWith(`${CONSOLE_BASE_PATH}/`)) {
    return { view: 'overview', resourceKey: null, traceKey: null };
  }
  const relativePath = normalizedPath.slice(CONSOLE_BASE_PATH.length + 1);
  if (relativePath === 'resources') {
    return { view: 'resources', resourceKey: null, traceKey: null };
  }
  if (relativePath === 'events') {
    return { view: 'events', resourceKey: null, traceKey: null };
  }
  if (relativePath === 'traces') {
    return { view: 'traces', resourceKey: null, traceKey: null };
  }
  if (relativePath === 'settings') {
    return { view: 'settings', resourceKey: null, traceKey: null };
  }
  if (relativePath.startsWith('resources/')) {
    const encodedKey = relativePath.slice('resources/'.length);
    const resourceKey = decodePathSegment(encodedKey);
    return resourceKey
      ? { view: 'resource-detail', resourceKey, traceKey: null }
      : { view: 'resources', resourceKey: null, traceKey: null };
  }
  if (relativePath.startsWith('traces/')) {
    const encodedKey = relativePath.slice('traces/'.length);
    const traceKey = decodePathSegment(encodedKey);
    return traceKey ? { view: 'trace-detail', resourceKey: null, traceKey } : { view: 'traces', resourceKey: null, traceKey: null };
  }
  return { view: 'overview', resourceKey: null, traceKey: null };
}

function decodePathSegment(value: string): string | null {
  try {
    return decodeURIComponent(value);
  } catch {
    return null;
  }
}

onMounted(() => {
  window.addEventListener('hashchange', syncRoute);
  window.addEventListener('popstate', syncRoute);
});

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute);
  window.removeEventListener('popstate', syncRoute);
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
          :class="{ 'is-active': isNavigationItemActive(item.id) }"
          :aria-current="isNavigationItemActive(item.id) ? 'page' : undefined"
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
      <TracesView v-else-if="route.view === 'traces'" @select-trace="openTrace" @select-resource="openResource" />
      <TraceDetailView
        v-else-if="route.view === 'trace-detail' && route.traceKey"
        :trace-key="route.traceKey"
        @select-resource="openResource"
      />
      <SettingsReadonlyView v-else-if="route.view === 'settings'" />
    </main>
  </div>
</template>
