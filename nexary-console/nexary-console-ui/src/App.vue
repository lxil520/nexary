<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import SettingsReadonlyView from './views/SettingsReadonlyView.vue';
import PlatformWorkbenchView from './views/PlatformWorkbenchView.vue';
import OverviewView from './views/OverviewView.vue';
import ResourcesView from './views/ResourcesView.vue';
import ResourceDetailView from './views/ResourceDetailView.vue';
import EventsView from './views/EventsView.vue';
import TracesView from './views/TracesView.vue';
import TraceDetailView from './views/TraceDetailView.vue';
import { useConsoleData } from './composables/useConsoleData';
import { useLocale } from './composables/useLocale';
import { usePlatformData } from './composables/usePlatformData';
import logoUrl from './assets/nexary-logo.svg';

type PlatformSection =
  | 'overview'
  | 'topology'
  | 'request-flows'
  | 'incidents'
  | 'services'
  | 'hosts'
  | 'middleware'
  | 'resources'
  | 'integrations'
  | 'notifications'
  | 'policies';
type LocalView =
  | 'local-overview'
  | 'local-resources'
  | 'local-resource-detail'
  | 'local-events'
  | 'local-traces'
  | 'local-trace-detail';
type LocalTabView = Exclude<LocalView, 'local-resource-detail' | 'local-trace-detail'> | 'settings';
type NavigationItemId = PlatformSection | 'local';
type ViewId = PlatformSection | LocalView | 'settings';

interface RouteState {
  view: ViewId;
  resourceKey?: string;
  traceKey?: string;
}

const CONSOLE_BASE_PATH = '/nexary/console';

const navigationItems: Array<{
  id: NavigationItemId;
  path: string;
  zh: string;
  en: string;
}> = [
  { id: 'overview', path: '', zh: '总览', en: 'Overview' },
  { id: 'topology', path: 'topology', zh: '拓扑', en: 'Topology' },
  { id: 'request-flows', path: 'request-flows', zh: '请求链路', en: 'Request Flows' },
  { id: 'incidents', path: 'incidents', zh: '事故', en: 'Incidents' },
  { id: 'services', path: 'services', zh: '服务', en: 'Services' },
  { id: 'hosts', path: 'hosts', zh: '主机实例', en: 'Hosts' },
  { id: 'middleware', path: 'middleware', zh: '中间件', en: 'Middleware' },
  { id: 'resources', path: 'resources', zh: '资源治理', en: 'Resources' },
  { id: 'integrations', path: 'integrations', zh: '集成', en: 'Integrations' },
  { id: 'notifications', path: 'notifications', zh: '通知', en: 'Notifications' },
  { id: 'policies', path: 'policies', zh: '策略计划', en: 'Policy Plans' },
  { id: 'local', path: 'local', zh: '本地诊断', en: 'Local Diagnostics' },
];
const platformSections: PlatformSection[] = [
  'overview',
  'topology',
  'request-flows',
  'incidents',
  'services',
  'hosts',
  'middleware',
  'resources',
  'integrations',
  'notifications',
  'policies',
];
const localTabs: Array<{
  view: LocalTabView;
  zh: string;
  en: string;
}> = [
  { view: 'local-overview', zh: '本地概览', en: 'Local Overview' },
  { view: 'local-resources', zh: '资源', en: 'Resources' },
  { view: 'local-events', zh: '事件', en: 'Events' },
  { view: 'local-traces', zh: 'Trace', en: 'Trace' },
  { view: 'settings', zh: '设置', en: 'Settings' },
];

const route = ref<RouteState>(routeFromLocation());
const globalWorkspace = ref('cloud-phone');
const globalEnvironment = ref('prod-demo');
const globalTeam = ref('platform-team');
const globalTimeRange = ref('30m');
const globalSeverity = ref('all');

const { isLoading: localLoading, lastRefreshAt: localLastRefreshAt, refreshAll } = useConsoleData();
const { isLoading: platformLoading, lastRefreshAt: platformLastRefreshAt, refreshPlatform } = usePlatformData();
const { formatTimestamp, locale, setLocale, t } = useLocale();

const platformSection = computed<PlatformSection>(() => (isPlatformSection(route.value.view) ? route.value.view : 'overview'));
const isSettings = computed(() => route.value.view === 'settings');
const isLocalArea = computed(() => isLocalView(route.value.view) || isSettings.value);
const isLoading = computed(() => (isLocalArea.value ? localLoading.value : platformLoading.value));
const lastRefreshAt = computed(() => {
  const timestamps = [isLocalArea.value ? localLastRefreshAt.value : platformLastRefreshAt.value]
    .filter((value): value is string => Boolean(value))
    .sort();
  return timestamps.at(-1) ?? null;
});

const title = computed(() => {
  if (isLocalArea.value) {
    return localLabel(route.value.view);
  }
  return isPlatformSection(route.value.view) ? navLabel(route.value.view) : navLabel('overview');
});

const subtitle = computed(() => {
  if (isLocalArea.value) {
    return locale.value === 'zh'
      ? '当前 JVM 只读诊断：资源、事件、fault trace 和 Console 边界'
      : 'Current-JVM read-only diagnostics: resources, events, fault traces, and Console boundary';
  }
  return locale.value === 'zh'
    ? '统一治理平台 RC：跨工具证据聚合、请求分析、事故归因和治理编排入口'
    : 'Governance platform RC: cross-tool evidence, request analysis, incident diagnosis, and dry-run planning';
});

const localBoundaryRows = computed(() =>
  locale.value === 'zh'
    ? [
        { label: '数据模式', value: 'LOCAL_JVM' },
        { label: '最近刷新', value: formatTimestamp(localLastRefreshAt.value) },
        { label: '读取端点', value: '/nexary/governance/*' },
        { label: '作用范围', value: '当前进程 / 当前实例' },
      ]
    : [
        { label: 'Data mode', value: 'LOCAL_JVM' },
        { label: 'Last refresh', value: formatTimestamp(localLastRefreshAt.value) },
        { label: 'Read endpoint', value: '/nexary/governance/*' },
        { label: 'Scope', value: 'Current process / instance' },
      ],
);

const localBoundaryNote = computed(() =>
  locale.value === 'zh'
    ? '本页只展示当前 JVM 保留的低基数诊断，不代表平台请求链路、SkyWalking trace 或跨实例历史数据。'
    : 'This page shows low-cardinality diagnostics retained by this JVM only; it is not platform request tracing, SkyWalking trace data, or cross-instance history.',
);

const refreshLabel = computed(() =>
  lastRefreshAt.value ? `${t('app.updated')} ${formatTimestamp(lastRefreshAt.value)}` : t('app.notRefreshed'),
);

const workspaceLabel = computed(() =>
  locale.value === 'zh' && globalWorkspace.value === 'cloud-phone' ? '云手机' : globalWorkspace.value,
);

const environmentLabel = computed(() => {
  if (locale.value !== 'zh') {
    return globalEnvironment.value;
  }
  return globalEnvironment.value === 'prod-demo' ? '生产演示' : '预发演示';
});

const teamLabel = computed(() => globalTeam.value);

const timeRangeOptions = computed(() =>
  locale.value === 'zh'
    ? [
        { value: '30m', label: '近 30 分钟' },
        { value: '2h', label: '近 2 小时' },
        { value: '24h', label: '近 24 小时' },
      ]
    : [
        { value: '30m', label: '30m' },
        { value: '2h', label: '2h' },
        { value: '24h', label: '24h' },
      ],
);

const severityOptions = computed(() =>
  locale.value === 'zh'
    ? [
        { value: 'all', label: '全部级别' },
        { value: 'critical', label: '严重' },
        { value: 'warning', label: '警告' },
      ]
    : [
        { value: 'all', label: 'All severity' },
        { value: 'critical', label: 'Critical' },
        { value: 'warning', label: 'Warning' },
      ],
);

function navLabel(view: NavigationItemId): string {
  const item = navigationItems.find((entry) => entry.id === view);
  return item ? (locale.value === 'zh' ? item.zh : item.en) : navigationItems[0].zh;
}

function localLabel(view: ViewId): string {
  if (view === 'local-resource-detail') {
    return locale.value === 'zh' ? '资源详情' : 'Resource Detail';
  }
  if (view === 'local-trace-detail') {
    return locale.value === 'zh' ? 'Trace 详情' : 'Trace Detail';
  }
  const item = localTabs.find((entry) => entry.view === view);
  return item ? (locale.value === 'zh' ? item.zh : item.en) : localTabs[0].zh;
}

function localTabLabel(view: LocalTabView): string {
  const item = localTabs.find((entry) => entry.view === view);
  return item ? (locale.value === 'zh' ? item.zh : item.en) : localTabs[0].zh;
}

function navigate(view: NavigationItemId): void {
  if (view === 'local') {
    pushRoute({ view: 'local-overview' });
    return;
  }
  pushRoute({ view });
}

function navigatePlatformSection(view: PlatformSection): void {
  pushRoute({ view });
}

function navigateLocal(view: LocalTabView): void {
  pushRoute({ view });
}

function refreshCurrent(): void {
  if (isLocalArea.value) {
    void refreshAll();
    return;
  }
  void refreshPlatform();
}

function isNavigationItemActive(view: NavigationItemId): boolean {
  if (view === 'local') {
    return isLocalArea.value;
  }
  return route.value.view === view;
}

function isLocalTabActive(view: LocalTabView): boolean {
  if (view === 'local-resources') {
    return route.value.view === 'local-resources' || route.value.view === 'local-resource-detail';
  }
  if (view === 'local-traces') {
    return route.value.view === 'local-traces' || route.value.view === 'local-trace-detail';
  }
  return route.value.view === view;
}

function openLocalResource(resourceKey: string): void {
  pushRoute({ view: 'local-resource-detail', resourceKey });
}

function openLocalTrace(traceKey: string): void {
  pushRoute({ view: 'local-trace-detail', traceKey });
}

function isPlatformSection(view: ViewId): view is PlatformSection {
  return platformSections.includes(view as PlatformSection);
}

function isLocalView(view: ViewId): view is LocalView {
  return view.startsWith('local-');
}

function pushRoute(nextRoute: RouteState): void {
  const nextPath = pathFromRoute(nextRoute);
  if (window.location.pathname !== nextPath || window.location.hash !== '') {
    window.history.pushState(null, '', nextPath);
  }
  route.value = nextRoute;
}

function pathFromRoute(nextRoute: RouteState): string {
  if (nextRoute.view === 'settings') {
    return `${CONSOLE_BASE_PATH}/settings`;
  }
  if (nextRoute.view === 'local-overview') {
    return `${CONSOLE_BASE_PATH}/local`;
  }
  if (nextRoute.view === 'local-resources') {
    return `${CONSOLE_BASE_PATH}/local/resources`;
  }
  if (nextRoute.view === 'local-resource-detail') {
    return `${CONSOLE_BASE_PATH}/local/resources/${encodeURIComponent(nextRoute.resourceKey ?? '')}`;
  }
  if (nextRoute.view === 'local-events') {
    return `${CONSOLE_BASE_PATH}/local/events`;
  }
  if (nextRoute.view === 'local-traces') {
    return `${CONSOLE_BASE_PATH}/local/traces`;
  }
  if (nextRoute.view === 'local-trace-detail') {
    return `${CONSOLE_BASE_PATH}/local/traces/${encodeURIComponent(nextRoute.traceKey ?? '')}`;
  }
  const item = navigationItems.find((entry) => entry.id === nextRoute.view);
  return item?.path ? `${CONSOLE_BASE_PATH}/${item.path}` : CONSOLE_BASE_PATH;
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
  const legacyMap: Record<string, ViewId> = {
    overview: 'overview',
    platform: 'overview',
    topology: 'topology',
    local: 'local-overview',
    traces: 'request-flows',
    'request-flows': 'request-flows',
    events: 'incidents',
    incidents: 'incidents',
    services: 'services',
    hosts: 'hosts',
    middleware: 'middleware',
    resources: 'resources',
    integrations: 'integrations',
    notifications: 'notifications',
    policies: 'policies',
    settings: 'settings',
  };
  return { view: legacyMap[rawHash.split('/')[0]] ?? 'overview' };
}

function routeFromPath(pathname: string): RouteState {
  const normalizedPath = pathname.replace(/\/+$/, '') || '/';
  if (normalizedPath === CONSOLE_BASE_PATH || normalizedPath === `${CONSOLE_BASE_PATH}/overview`) {
    return { view: 'overview' };
  }
  if (!normalizedPath.startsWith(`${CONSOLE_BASE_PATH}/`)) {
    return { view: 'overview' };
  }
  const relativePath = normalizedPath.slice(CONSOLE_BASE_PATH.length + 1);
  if (relativePath === 'settings') {
    return { view: 'settings' };
  }
  const localRoute = routeFromLocalPath(relativePath);
  if (localRoute) {
    return localRoute;
  }
  if (relativePath === 'platform') {
    return { view: 'overview' };
  }
  if (
    relativePath === 'traces' ||
    relativePath.startsWith('traces/') ||
    relativePath === 'flows' ||
    relativePath.startsWith('flows/')
  ) {
    return { view: 'request-flows' };
  }
  if (relativePath === 'events') {
    return { view: 'incidents' };
  }
  const item = navigationItems.find((entry) => entry.path === relativePath);
  return { view: item && item.id !== 'local' ? item.id : 'overview' };
}

function routeFromLocalPath(relativePath: string): RouteState | null {
  const segments = relativePath.split('/').filter(Boolean);
  if (segments[0] !== 'local') {
    return null;
  }
  if (segments.length === 1) {
    return { view: 'local-overview' };
  }
  if (segments[1] === 'resources') {
    return segments[2]
      ? { view: 'local-resource-detail', resourceKey: decodeURIComponent(segments[2]) }
      : { view: 'local-resources' };
  }
  if (segments[1] === 'events') {
    return { view: 'local-events' };
  }
  if (segments[1] === 'traces') {
    return segments[2]
      ? { view: 'local-trace-detail', traceKey: decodeURIComponent(segments[2]) }
      : { view: 'local-traces' };
  }
  return { view: 'local-overview' };
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
          <span>Governance RC</span>
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
          {{ navLabel(item.id) }}
        </button>
      </nav>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="topbar__identity">
          <span>{{ subtitle }}</span>
          <h1>{{ title }}</h1>
        </div>
        <div class="topbar__scope" aria-label="Observation scope">
          <span>{{ workspaceLabel }}</span>
          <span>{{ environmentLabel }}</span>
          <span>{{ teamLabel }}</span>
          <label>
            <select v-model="globalTimeRange" aria-label="Time range">
              <option v-for="option in timeRangeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label>
            <select v-model="globalSeverity" aria-label="Severity">
              <option v-for="option in severityOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
        <div class="topbar__actions">
          <div class="locale-segment" :aria-label="t('app.language')">
            <button type="button" :class="{ 'is-active': locale === 'zh' }" @click="setLocale('zh')">中文</button>
            <button type="button" :class="{ 'is-active': locale === 'en' }" @click="setLocale('en')">EN</button>
          </div>
          <span class="refresh-state">{{ refreshLabel }}</span>
          <button class="button button--primary" type="button" :disabled="isLoading" @click="refreshCurrent">
            {{ isLoading ? t('app.refreshing') : t('app.refresh') }}
          </button>
        </div>
      </header>

      <section
        v-if="isLocalArea"
        class="ops-data-mode-strip local-boundary-strip"
        data-tone="warning"
        aria-label="Local diagnostics data boundary"
      >
        <div v-for="row in localBoundaryRows" :key="row.label">
          <span>{{ row.label }}</span>
          <strong>{{ row.value }}</strong>
        </div>
        <p>{{ localBoundaryNote }}</p>
      </section>

      <div v-if="isLocalArea" class="local-tabs" aria-label="Local diagnostics">
        <button
          v-for="tab in localTabs"
          :key="tab.view"
          type="button"
          :class="{ 'is-active': isLocalTabActive(tab.view) }"
          @click="navigateLocal(tab.view)"
        >
          {{ localTabLabel(tab.view) }}
        </button>
      </div>

      <SettingsReadonlyView v-if="route.view === 'settings'" />
      <OverviewView v-else-if="route.view === 'local-overview'" @select-resource="openLocalResource" />
      <ResourcesView v-else-if="route.view === 'local-resources'" @select-resource="openLocalResource" />
      <ResourceDetailView
        v-else-if="route.view === 'local-resource-detail'"
        :resource-key="route.resourceKey ?? ''"
      />
      <EventsView v-else-if="route.view === 'local-events'" />
      <TracesView
        v-else-if="route.view === 'local-traces'"
        @select-trace="openLocalTrace"
        @select-resource="openLocalResource"
      />
      <TraceDetailView
        v-else-if="route.view === 'local-trace-detail'"
        :trace-key="route.traceKey ?? ''"
        @select-resource="openLocalResource"
      />
      <PlatformWorkbenchView v-else :section="platformSection" @navigate-section="navigatePlatformSection" />
    </main>
  </div>
</template>
