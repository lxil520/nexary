<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingBlock from '../components/LoadingBlock.vue';
import StatusBadge from '../components/StatusBadge.vue';
import {
  dryRunPlatformPlan,
  exportPlatformPlanReview,
  fetchPlatformTraces,
  previewNotificationRoute,
  saveConnectorConfig,
  saveServiceMapping,
  testConnectorConfig,
  testNotificationRoute,
} from '../api/platformClient';
import { useLocale } from '../composables/useLocale';
import { usePlatformData } from '../composables/usePlatformData';
import type {
  PlatformConnectorStatus,
  PlatformConnectorConfig,
  PlatformConnectorState,
  PlatformConnectorTestResult,
  PlatformDataSource,
  PlatformDependencyEdge,
  PlatformDryRunResult,
  PlatformHostSignal,
  PlatformIncidentCandidate,
  PlatformMiddlewareWatermark,
  PlatformNotificationPreview,
  PlatformNotificationRoute,
  PlatformNotificationTestResult,
  PlatformPolicyPlan,
  PlatformRequestFlow,
  PlatformServiceNode,
  PlatformServiceMapping,
  PlatformSignal,
  PlatformSpan,
  PlatformTransactionMetric,
  PlatformTraceQuery,
} from '../types/platform';
import type { TopologyGraphEdge, TopologyGraphNode } from '../types/topologyGraph';

type SectionId =
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

type IncidentToneInput = Pick<PlatformIncidentCandidate, 'severity'>;
type FlowToneInput = Pick<PlatformRequestFlow, 'status'>;
type FlowMetricInput = Pick<PlatformRequestFlow, 'traceKey' | 'endpointKey' | 'durationMs'>;
type SpanMetricInput = Pick<PlatformSpan, 'startOffsetMs' | 'durationMs'>;
type SpanTreeInput = Pick<PlatformSpan, 'spanId' | 'parentSpanId' | 'operation' | 'component' | 'durationMs' | 'startOffsetMs'>;
type FlowSpanInput = FlowMetricInput & { readonly spans: readonly SpanTreeInput[] };
type FlowRiskInput = Pick<PlatformRequestFlow, 'status' | 'durationMs'> & {
  readonly spans: readonly Pick<PlatformSpan, 'status'>[];
  readonly evidenceRefs: readonly unknown[];
};
type FlowSortInput = FlowRiskInput & Pick<PlatformRequestFlow, 'startedAt'>;
type PolicyRiskInput = Pick<PlatformPolicyPlan, 'risk' | 'state' | 'evidenceCount'>;
type FlowEdgeInput = Pick<PlatformRequestFlow, 'entryServiceKey' | 'endpointKey' | 'summary'> & {
  readonly spans: readonly Pick<PlatformSpan, 'resourceKey' | 'serviceKey'>[];
};
type FlowViewMode = 'list' | 'tree' | 'table' | 'stats';

interface TopologyNode {
  key: string;
  label: string;
  caption: string;
  subLabel: string;
  kind: string;
  icon: string;
  zoneKey: string;
  tone: string;
  x: number;
  y: number;
}

interface SpanAggregate {
  operation: string;
  component: string;
  maxMs: number;
  minMs: number;
  sumMs: number;
  avgMs: number;
  hits: number;
}

const props = defineProps<{ section: SectionId }>();
const emit = defineEmits<{
  'navigate-section': [section: SectionId];
}>();

const TopologyGraph = defineAsyncComponent(() => import('../components/TopologyGraph.vue'));

const { formatTimestamp, locale } = useLocale();
const { snapshot, isLoading, errorMessage, hasLoaded, refreshPlatform } = usePlatformData();

const selectedIncidentKey = ref<string | null>(null);
const selectedFlowKey = ref<string | null>(null);
const selectedServiceKey = ref<string | null>(null);
const selectedEdgeKey = ref<string | null>(null);
const selectedTopologyRootKey = ref<string | null>(null);
const selectedTopologyNodeKey = ref<string | null>(null);
const selectedHostKey = ref<string | null>(null);
const selectedMiddlewareKey = ref<string | null>(null);
const selectedSignalKey = ref<string | null>(null);
const selectedConnectorKey = ref<string | null>(null);
const selectedNotificationRouteKey = ref<string | null>(null);
const selectedPolicyPlanKey = ref<string | null>(null);
const selectedZone = ref('ALL');
const searchTerm = ref('');
const selectedFlowView = ref<FlowViewMode>('list');
const selectedFlowStatus = ref('ALL');
const selectedFlowServiceKey = ref('ALL');
const selectedFlowSource = ref('ALL');
const selectedFlowSort = ref('risk');
const flowMinDurationMs = ref('');
const flowResourceFilter = ref('');
const flowPage = ref(0);
const traceQueryItems = ref<PlatformRequestFlow[] | null>(null);
const traceQueryTotal = ref(0);
const traceQueryLoading = ref(false);
const traceQueryError = ref<string | null>(null);
const traceQueryLoaded = ref(false);
const policyDryRunResult = ref<PlatformDryRunResult | null>(null);
const policyExportReview = ref<Record<string, unknown> | null>(null);
const policyActionError = ref<string | null>(null);
const policyActionLoading = ref(false);
const notificationPreview = ref<PlatformNotificationPreview | null>(null);
const notificationTestResult = ref<PlatformNotificationTestResult | null>(null);
const notificationActionError = ref<string | null>(null);
const notificationActionLoading = ref(false);
const connectorActionError = ref<string | null>(null);
const connectorActionLoading = ref(false);
const connectorTestResult = ref<PlatformConnectorTestResult | null>(null);
const connectorForm = ref({
  connectorKey: 'skywalking-readonly',
  kind: 'SKYWALKING',
  displayName: 'SkyWalking APM',
  endpoint: 'http://127.0.0.1:18097/graphql',
  authMode: 'NONE',
  accessMode: 'READ_ONLY',
  state: 'DISABLED' as PlatformConnectorState,
  testEnabled: false,
  targetTeam: 'platform-team',
});
const mappingForm = ref({
  serviceKey: '',
  connectorKey: 'skywalking-readonly',
  sourceKind: 'SKYWALKING',
  externalKey: '',
  resourceKind: 'service',
  confidence: 0.8,
});
const topologyDepth = ref('2');
const USER_NODE_KEY = '__nexary_user__';
const FLOW_PAGE_SIZE = 18;
let traceQuerySequence = 0;

const topologyNodePositions: Record<string, { x: number; y: number }> = {
  'open-api': { x: 14, y: 42 },
  'sdk-api': { x: 30, y: 30 },
  'user-platform': { x: 30, y: 62 },
  signaling: { x: 44, y: 42 },
  'board-service': { x: 48, y: 76 },
  'room-resource': { x: 65, y: 42 },
  'repair-job-worker': { x: 58, y: 76 },
  consumer: { x: 18, y: 78 },
  'redis-room': { x: 89, y: 42 },
  'redis-main': { x: 44, y: 18 },
  'pg-primary': { x: 16, y: 18 },
  'oss-main': { x: 84, y: 74 },
};

const copy = computed(() =>
  locale.value === 'zh'
    ? {
        loading: '正在加载治理平台',
        errorTitle: '治理平台数据不可用',
        emptyTitle: '还没有平台资产',
        emptyMessage: '启动治理平台样例，或向 /api/platform/resources 上报服务、依赖和接入器。',
        allZones: '全部机房',
        search: '搜索服务、资源、链路',
        overview: '总览',
        topology: '拓扑',
        requestFlows: '请求链路',
        incidents: '事故',
        services: '服务',
        hosts: '主机实例',
        middleware: '中间件',
        resources: '资源治理',
        integrations: '集成',
        notifications: '通知',
        policies: '策略计划',
        diagnosis: '诊断结论',
        incidentQueue: '事故队列',
        serviceWaterline: '服务水位',
        zoneWaterline: '机房水位',
        middlewareWaterline: '中间件水位',
        evidence: '证据',
        firstCheck: '优先检查',
        refs: '外部引用',
        spans: 'Span 瀑布',
        transactions: '交易统计',
        impact: '影响范围',
        dryRun: 'Dry-run',
        runDryRun: '执行 Dry-run',
        exportReview: '导出审查材料',
        previewMessage: '预览消息',
        testSend: '测试发送',
        dryRunResult: 'Dry-run 结果',
        notificationPreview: '通知预览',
        notificationTest: '测试结果',
        blockers: '阻断原因',
        impacted: '影响对象',
        requestSamples: '请求样本',
        targetKind: '目标类型',
        before: '建议前',
        after: '建议后',
        testEnabled: '测试开关',
        noDirectWrite: '只读 / 不写生产配置',
        zone: '机房',
        selectedEdge: '当前链路',
        selectedNode: '当前节点',
        source: '来源',
        target: '目标',
        resource: '资源',
        errors: '异常',
        total: '总量',
        failure: '失败',
        sample: '样本',
        qps: 'QPS',
        failureRate: '错误率',
        p95p99: 'P95 / P99',
        host: '主机',
        cpu: 'CPU',
        memory: '内存',
        swap: 'Swap',
        diskIo: '磁盘 IO',
        jitter: '抖动',
        loss: '丢包',
        threads: '线程',
        last: '最近异常',
        kind: '类型',
        usage: '水位',
        latency: '延迟',
        connectedServices: '调用方',
        localDiagnostics: '本地诊断',
        localResourceNote: 'Nexary 本地资源和故障 Trace 仍保留为 SDK 级诊断入口。',
        localIncidentNote: '平台事故使用脱敏资源键回链到本地证据，不写生产策略。',
        enabled: '启用',
        bound: '绑定',
        edges: '链路',
        warning: '警告',
        critical: '严重',
        commandCenter: '事故指挥台',
        affectedPath: '受影响链路',
        riskStream: '风险流',
        traceSamples: '请求样本',
        viewList: '列表',
        viewTree: '树结构',
        viewTable: '表格',
        viewStats: '统计',
        endpoint: '端点',
        duration: '耗时',
        status: '状态',
        component: '组件',
        exec: '执行',
        self: '自身',
        hits: '次数',
        attachedEvents: '事件',
        dataSource: '数据源',
        evidenceGraph: '证据图',
        topologyService: '服务拓扑',
        entryTopology: '全局入口',
        currentDepth: '当前深度',
        focusRisk: '看最高风险',
        riskLinks: '风险链路',
        entryLink: '外部入口流量',
        healthyLink: '健康链路',
        slowLink: '慢调用',
        failedLink: '失败 / 超时',
        jitterLink: '抖动 / 丢包',
        eventInspector: '事件时间线',
        topologyHint: '',
        evidenceDrawer: '证据抽屉',
        whyItMatters: '为什么重要',
        supportingEvidence: '支持证据',
        checkedEvidence: '已检查但不是主因',
        nextActions: '下一步',
        openTopology: '打开拓扑',
        openTrace: '打开 Trace',
        openService: '打开服务',
        openMetrics: '打开指标',
        sourceFreshness: '证据新鲜度',
        confidence: '置信度',
        relatedObjects: '关联对象',
        highestRisk: '最高风险',
        selectedHost: '当前主机',
        selectedMiddleware: '当前中间件',
        selectedResource: '当前资源',
        selectedConnector: '当前集成',
        selectedRoute: '当前路由',
        selectedPolicy: '当前计划',
        service: '服务',
        cluster: '集群',
        state: '状态',
        signalType: '信号',
        outcome: '结果',
        durationBucket: '耗时桶',
        lastSeen: '最近上报',
        observedAt: '观测时间',
        channel: '通道',
        targetTeam: '目标团队',
        minSeverity: '最低级别',
        routeMode: '路由模式',
        lastMessage: '最近消息',
        proposedAction: '建议动作',
        risk: '风险',
        relatedSignals: '关联信号',
        relatedTraces: '关联 Trace',
        callers: '调用方',
        openIncident: '打开事故',
        openHosts: '打开主机',
        openResources: '打开资源',
        openIntegrations: '打开集成',
        openNotifications: '打开通知',
        openPolicies: '打开策略',
        dryRunOnly: '仅演练',
        dataModeLabel: '数据模式',
        generatedAt: '生成时间',
        lastSignal: '最近信号',
        connectorFreshness: '接入器',
        warnings: '提示',
        flowStatusFilter: '状态',
        allStatuses: '全部状态',
        minDuration: '最小耗时(ms)',
        flowSort: '排序',
        riskFirst: '风险优先',
        durationDesc: '耗时从高到低',
        timeDesc: '时间最新',
        previousPage: '上一页',
        nextPage: '下一页',
        page: '页',
        traceQueryLive: '远端查询',
        traceQueryFallback: 'Snapshot 降级',
        traceQueryLoading: '查询中',
        serviceFilter: '服务',
        allServices: '全部服务',
        sourceFilter: '证据源',
        allSources: '全部证据源',
        resourceFilter: '资源',
        resourceFilterPlaceholder: '资源 / 路由 / 端点',
        queryContext: '查询上下文',
        affectedViews: '支撑页面',
        degradedScope: '降级影响',
        readOnlyBoundary: '只读边界',
        connectorMode: '接入模式',
        unavailable: '不可用',
      }
    : {
        loading: 'Loading governance platform',
        errorTitle: 'Governance platform data is unavailable',
        emptyTitle: 'No platform assets yet',
        emptyMessage: 'Start the platform sample or post services, dependencies, and connectors to /api/platform/resources.',
        allZones: 'All zones',
        search: 'Search services, resources, flows',
        overview: 'Overview',
        topology: 'Topology',
        requestFlows: 'Request flows',
        incidents: 'Incidents',
        services: 'Services',
        hosts: 'Hosts',
        middleware: 'Middleware',
        resources: 'Resource governance',
        integrations: 'Integrations',
        notifications: 'Notifications',
        policies: 'Policy plans',
        diagnosis: 'Diagnosis',
        incidentQueue: 'Incident queue',
        serviceWaterline: 'Service waterline',
        zoneWaterline: 'Zone waterline',
        middlewareWaterline: 'Middleware waterline',
        evidence: 'Evidence',
        firstCheck: 'First check',
        refs: 'External refs',
        spans: 'Span waterfall',
        transactions: 'Transactions',
        impact: 'Impact',
        dryRun: 'Dry-run',
        runDryRun: 'Run dry-run',
        exportReview: 'Export review',
        previewMessage: 'Preview message',
        testSend: 'Test send',
        dryRunResult: 'Dry-run result',
        notificationPreview: 'Notification preview',
        notificationTest: 'Test result',
        blockers: 'Blockers',
        impacted: 'Impacted',
        requestSamples: 'Request samples',
        targetKind: 'Target kind',
        before: 'Before',
        after: 'After',
        testEnabled: 'Test enabled',
        noDirectWrite: 'Read-only / no production write',
        zone: 'Zone',
        selectedEdge: 'Selected edge',
        selectedNode: 'Selected node',
        source: 'Source',
        target: 'Target',
        resource: 'Resource',
        errors: 'Errors',
        total: 'Total',
        failure: 'Failure',
        sample: 'Sample',
        qps: 'QPS',
        failureRate: 'Failure rate',
        p95p99: 'P95 / P99',
        host: 'Host',
        cpu: 'CPU',
        memory: 'Memory',
        swap: 'Swap',
        diskIo: 'Disk IO',
        jitter: 'Jitter',
        loss: 'Loss',
        threads: 'Threads',
        last: 'Last',
        kind: 'Kind',
        usage: 'Usage',
        latency: 'Latency',
        connectedServices: 'Services',
        localDiagnostics: 'Local diagnostics',
        localResourceNote: 'Nexary local resources and fault traces remain available for SDK-level diagnosis.',
        localIncidentNote: 'Platform incidents use sanitized resource keys to link back into local evidence.',
        enabled: 'enabled',
        bound: 'bound',
        edges: 'edges',
        warning: 'warning',
        critical: 'critical',
        commandCenter: 'Incident command',
        affectedPath: 'Affected path',
        riskStream: 'Risk stream',
        traceSamples: 'Trace samples',
        viewList: 'List',
        viewTree: 'Tree',
        viewTable: 'Table',
        viewStats: 'Statistics',
        endpoint: 'Endpoint',
        duration: 'Duration',
        status: 'Status',
        component: 'Component',
        exec: 'Exec',
        self: 'Self',
        hits: 'Hits',
        attachedEvents: 'Events',
        dataSource: 'Data source',
        evidenceGraph: 'Evidence graph',
        topologyService: 'Service topology',
        entryTopology: 'Entry paths',
        currentDepth: 'Current depth',
        focusRisk: 'Focus risk',
        riskLinks: 'Risk links',
        entryLink: 'External entry flow',
        healthyLink: 'Healthy link',
        slowLink: 'Slow call',
        failedLink: 'Failure / timeout',
        jitterLink: 'Jitter / loss',
        eventInspector: 'Event timeline',
        topologyHint: '',
        evidenceDrawer: 'Evidence drawer',
        whyItMatters: 'Why it matters',
        supportingEvidence: 'Supporting evidence',
        checkedEvidence: 'Checked but not causal',
        nextActions: 'Next actions',
        openTopology: 'Open topology',
        openTrace: 'Open trace',
        openService: 'Open service',
        openMetrics: 'Open metrics',
        sourceFreshness: 'Evidence freshness',
        confidence: 'Confidence',
        relatedObjects: 'Related objects',
        highestRisk: 'Highest risk',
        selectedHost: 'Selected host',
        selectedMiddleware: 'Selected middleware',
        selectedResource: 'Selected resource',
        selectedConnector: 'Selected integration',
        selectedRoute: 'Selected route',
        selectedPolicy: 'Selected plan',
        service: 'Service',
        cluster: 'Cluster',
        state: 'State',
        signalType: 'Signal',
        outcome: 'Outcome',
        durationBucket: 'Duration bucket',
        lastSeen: 'Last seen',
        observedAt: 'Observed at',
        channel: 'Channel',
        targetTeam: 'Target team',
        minSeverity: 'Min severity',
        routeMode: 'Route mode',
        lastMessage: 'Last message',
        proposedAction: 'Suggested action',
        risk: 'Risk',
        relatedSignals: 'Related signals',
        relatedTraces: 'Related traces',
        callers: 'Callers',
        openIncident: 'Open incident',
        openHosts: 'Open hosts',
        openResources: 'Open resources',
        openIntegrations: 'Open integrations',
        openNotifications: 'Open notifications',
        openPolicies: 'Open policies',
        dryRunOnly: 'Dry-run only',
        dataModeLabel: 'Data mode',
        generatedAt: 'Generated at',
        lastSignal: 'Last signal',
        connectorFreshness: 'Connectors',
        warnings: 'Warnings',
        flowStatusFilter: 'Status',
        allStatuses: 'All statuses',
        minDuration: 'Min duration(ms)',
        flowSort: 'Sort',
        riskFirst: 'Risk first',
        durationDesc: 'Duration high to low',
        timeDesc: 'Newest first',
        previousPage: 'Previous',
        nextPage: 'Next',
        page: 'Page',
        traceQueryLive: 'Remote query',
        traceQueryFallback: 'Snapshot fallback',
        traceQueryLoading: 'Querying',
        serviceFilter: 'Service',
        allServices: 'All services',
        sourceFilter: 'Evidence source',
        allSources: 'All evidence sources',
        resourceFilter: 'Resource',
        resourceFilterPlaceholder: 'Resource / route / endpoint',
        queryContext: 'Query context',
        affectedViews: 'Supported views',
        degradedScope: 'Degraded scope',
        readOnlyBoundary: 'Read-only boundary',
        connectorMode: 'Connector mode',
        unavailable: 'Unavailable',
      },
);

const services = computed(() => snapshot.value?.services ?? []);
const dependencies = computed(() => snapshot.value?.topology.dependencies ?? []);
const incidents = computed(() => snapshot.value?.incidents ?? []);
const connectors = computed(() => snapshot.value?.connectors ?? []);
const dataSources = computed(() => snapshot.value?.dataSources ?? []);
const flows = computed(() => snapshot.value?.requestFlows ?? []);
const transactions = computed(() => snapshot.value?.transactions ?? []);
const hosts = computed(() => snapshot.value?.hosts ?? []);
const signals = computed(() => snapshot.value?.signals ?? []);
const middleware = computed(() => snapshot.value?.overview.middlewareWatermarks ?? []);
const notificationRoutes = computed(() => snapshot.value?.overview.notificationRoutes ?? []);
const policyPlans = computed(() => snapshot.value?.overview.policyPlans ?? []);
const connectorConfigs = computed(() => snapshot.value?.connectorConfigs ?? []);
const connectorTests = computed(() => snapshot.value?.connectorTests ?? []);
const serviceMappings = computed(() => snapshot.value?.serviceMappings ?? []);
const serviceWatermarks = computed(() => snapshot.value?.overview.serviceWatermarks ?? []);
const zoneWatermarks = computed(() => snapshot.value?.overview.zoneWatermarks ?? []);
const summary = computed(() => snapshot.value?.overview.summary ?? null);
const sourceMode = computed(() => snapshot.value?.sourceMode ?? 'UNAVAILABLE');
const sourceWarnings = computed(() => snapshot.value?.warnings ?? []);
const sourceWarningText = computed(() => sourceWarnings.value.slice(0, 2).join(' / '));
const dataSourceCount = computed(() => snapshot.value?.dataSources.length ?? connectors.value.length);
const freshness = computed(() => snapshot.value?.freshness ?? null);
const sourceModeTone = computed(() => {
  if (sourceMode.value === 'LIVE') {
    return 'healthy';
  }
  if (sourceMode.value === 'DEMO' || sourceMode.value === 'MOCK' || sourceMode.value === 'STALE') {
    return 'warning';
  }
  return 'critical';
});
const emptyStateTitle = computed(() => `${copy.value.emptyTitle} · ${sourceMode.value}`);
const emptyStateMessage = computed(() => {
  const warning = sourceWarningText.value;
  return warning ? `${copy.value.emptyMessage} ${warning}` : copy.value.emptyMessage;
});
const formattedGeneratedAt = computed(() => formatTimestamp(snapshot.value?.generatedAt ?? null));
const formattedLastSignalAt = computed(() => formatTimestamp(freshness.value?.lastSignalAt ?? null));
const traceServiceOptions = computed(() =>
  [...visibleServices.value].sort((left, right) => serviceRiskScore(right) - serviceRiskScore(left) || left.name.localeCompare(right.name)),
);
const traceSourceOptions = computed(() => {
  const values = new Set<string>();
  dataSources.value.forEach((source) => values.add(source.kind));
  flows.value.forEach((flow) => {
    flow.evidenceRefs.forEach((ref) => values.add(ref.type));
    flow.spans.forEach((span) => span.evidenceRefs.forEach((ref) => values.add(ref.type)));
  });
  return Array.from(values).filter(Boolean).sort();
});
const flowBase = computed(() =>
  props.section === 'request-flows' && traceQueryLoaded.value && traceQueryItems.value !== null ? traceQueryItems.value : flows.value,
);
const traceQueryStatusText = computed(() => {
  if (traceQueryLoading.value) {
    return copy.value.traceQueryLoading;
  }
  if (props.section === 'request-flows' && traceQueryLoaded.value) {
    return copy.value.traceQueryLive;
  }
  return traceQueryError.value ? `${copy.value.traceQueryFallback}: ${traceQueryError.value}` : copy.value.traceQueryFallback;
});
const flowTotalCount = computed(() =>
  props.section === 'request-flows' && traceQueryLoaded.value && selectedZone.value === 'ALL' && searchTerm.value.trim() === ''
    ? traceQueryTotal.value
    : visibleFlows.value.length,
);
const traceQueryContextRows = computed(() => [
  { label: copy.value.flowStatusFilter, value: selectedFlowStatus.value === 'ALL' ? copy.value.allStatuses : selectedFlowStatus.value },
  {
    label: copy.value.serviceFilter,
    value: selectedFlowServiceKey.value === 'ALL'
      ? copy.value.allServices
      : serviceByKey(selectedFlowServiceKey.value)?.name ?? selectedFlowServiceKey.value,
  },
  { label: copy.value.resourceFilter, value: flowResourceFilter.value.trim() || '-' },
  { label: copy.value.sourceFilter, value: selectedFlowSource.value === 'ALL' ? copy.value.allSources : selectedFlowSource.value },
  { label: copy.value.minDuration, value: flowMinDurationMs.value.trim() || '0' },
  { label: copy.value.flowSort, value: selectedFlowSort.value },
  { label: copy.value.page, value: `${flowPage.value + 1} / ${flowPageCount.value}` },
  { label: copy.value.sourceFreshness, value: traceQueryStatusText.value },
]);
const zones = computed(() => ['ALL', ...Array.from(new Set([...services.value.map((service) => service.zoneKey), ...hosts.value.map((host) => host.zoneKey)]))]);

const visibleServices = computed(() =>
  services.value.filter((service) =>
    matchesZone(service.zoneKey) && matchesText([service.name, service.serviceKey, service.clusterKey, service.teamKey]),
  ),
);
const visibleDependencies = computed(() =>
  dependencies.value.filter((edge) => {
    const relatedZone =
      serviceByKey(edge.sourceKey)?.zoneKey ??
      serviceByKey(edge.targetKey)?.zoneKey ??
      middleware.value.find((item) => item.middlewareKey === edge.targetKey)?.zoneKey ??
      'unknown';
    return matchesZone(relatedZone) && matchesText([edge.sourceKey, edge.targetKey, edge.resourceKey, edge.kind]);
  }),
);
const visibleFlows = computed(() =>
  flowBase.value
    .filter((flow) => matchesZone(flow.zoneKey) && matchesText([flow.traceKey, flow.entryServiceKey, flow.endpointKey, flow.primaryError, flow.summary]))
    .filter(matchesFlowStatus)
    .filter(matchesFlowDuration)
    .sort(flowComparator),
);
const flowPageCount = computed(() => Math.max(1, Math.ceil(flowTotalCount.value / FLOW_PAGE_SIZE)));
const pagedFlows = computed(() => {
  if (props.section === 'request-flows' && traceQueryLoaded.value) {
    return visibleFlows.value;
  }
  const currentPage = Math.min(flowPage.value, flowPageCount.value - 1);
  const start = currentPage * FLOW_PAGE_SIZE;
  return visibleFlows.value.slice(start, start + FLOW_PAGE_SIZE);
});
const visibleIncidents = computed(() =>
  incidents.value
    .filter((incident) =>
      matchesZone(incident.impactScope.zoneKey) && matchesText([incident.title, incident.primaryResourceKey, incident.impactScope.serviceKey]),
    )
    .sort((left, right) => severityScore(right.severity) - severityScore(left.severity) || right.evidenceCount - left.evidenceCount),
);
const visibleHosts = computed(() =>
  hosts.value
    .filter((host) => matchesZone(host.zoneKey) && matchesText([host.hostKey, host.serviceKey, host.clusterKey, host.zoneKey, host.lastError]))
    .sort((left, right) => hostRiskScore(right) - hostRiskScore(left) || left.hostKey.localeCompare(right.hostKey)),
);
const visibleMiddleware = computed(() =>
  middleware.value
    .filter((item) => matchesZone(item.zoneKey) && matchesText([item.name, item.middlewareKey, item.kind, item.zoneKey]))
    .sort((left, right) => middlewareRiskScore(right) - middlewareRiskScore(left) || left.name.localeCompare(right.name)),
);
const visibleSignals = computed(() =>
  signals.value
    .filter((signal) =>
      matchesZone(signal.zoneKey) && matchesText([signal.serviceKey, signal.resourceKey, signal.signalType, signal.outcome, signal.durationBucket]),
    )
    .sort((left, right) => signalRiskScore(right) - signalRiskScore(left) || (right.timestamp ?? '').localeCompare(left.timestamp ?? '')),
);
const visibleConnectors = computed(() =>
  connectors.value
    .filter((connector) => matchesText([connector.displayName, connector.connectorKey, connector.kind, connector.state, connector.lastMessage]))
    .sort((left, right) => connectorRiskScore(right) - connectorRiskScore(left) || left.displayName.localeCompare(right.displayName)),
);
const visibleDataSources = computed(() =>
  dataSources.value
    .filter((source) => matchesText([source.displayName, source.sourceKey, source.kind, source.state, source.lastMessage, source.mode]))
    .sort((left, right) => dataSourceRiskScore(right) - dataSourceRiskScore(left) || left.displayName.localeCompare(right.displayName)),
);
const visibleNotificationRoutes = computed(() =>
  notificationRoutes.value
    .filter((route) => matchesText([route.displayName, route.routeKey, route.channel, route.targetTeam, route.minSeverity, route.state, route.lastMessage]))
    .sort((left, right) => routeRiskScore(right) - routeRiskScore(left) || left.displayName.localeCompare(right.displayName)),
);
const visiblePolicyPlans = computed(() =>
  policyPlans.value
    .filter((plan) => {
      const service = serviceByKey(plan.serviceKey);
      return matchesZone(service?.zoneKey ?? 'unknown') && matchesText([plan.title, plan.serviceKey, plan.resourceKey, plan.signalType, plan.risk, plan.state, plan.proposedAction]);
    })
    .sort((left, right) => policyRiskScore(right) - policyRiskScore(left) || left.title.localeCompare(right.title)),
);
const selectedIncident = computed(
  () => visibleIncidents.value.find((incident) => incident.incidentKey === selectedIncidentKey.value) ?? visibleIncidents.value[0] ?? null,
);
const selectedFlow = computed(
  () => visibleFlows.value.find((flow) => flow.traceKey === selectedFlowKey.value) ?? visibleFlows.value[0] ?? null,
);
const selectedService = computed(
  () => visibleServices.value.find((service) => service.serviceKey === selectedServiceKey.value) ?? visibleServices.value[0] ?? null,
);
const selectedEdge = computed(
  () => visibleDependencies.value.find((edge) => edgeKey(edge) === selectedEdgeKey.value) ?? sortedRiskEdges.value[0] ?? visibleDependencies.value[0] ?? null,
);
const selectedHost = computed(() => visibleHosts.value.find((host) => host.hostKey === selectedHostKey.value) ?? visibleHosts.value[0] ?? null);
const selectedMiddleware = computed(
  () => visibleMiddleware.value.find((item) => item.middlewareKey === selectedMiddlewareKey.value) ?? visibleMiddleware.value[0] ?? null,
);
const selectedSignal = computed(() => visibleSignals.value.find((signal) => signalKey(signal) === selectedSignalKey.value) ?? visibleSignals.value[0] ?? null);
const selectedConnector = computed(
  () => visibleConnectors.value.find((connector) => connector.connectorKey === selectedConnectorKey.value) ?? visibleConnectors.value[0] ?? null,
);
const selectedDataSource = computed(
  () => visibleDataSources.value.find((source) => source.sourceKey === selectedConnectorKey.value) ?? visibleDataSources.value[0] ?? null,
);
const visibleConnectorConfigs = computed(() =>
  connectorConfigs.value
    .filter((config) => matchesText([config.displayName, config.connectorKey, config.kind, config.state, config.lastMessage]))
    .sort((left, right) => connectorRiskScore(toConnectorStatus(right)) - connectorRiskScore(toConnectorStatus(left)) || left.displayName.localeCompare(right.displayName)),
);
const selectedConnectorConfig = computed(
  () => visibleConnectorConfigs.value.find((config) => config.connectorKey === selectedConnectorKey.value) ?? visibleConnectorConfigs.value[0] ?? null,
);
const latestConnectorTest = computed(() =>
  connectorTestResult.value
    ?? connectorTests.value.find((test) => test.connectorKey === selectedConnectorConfig.value?.connectorKey)
    ?? null,
);
const visibleServiceMappings = computed(() =>
  serviceMappings.value
    .filter((mapping) => matchesText([mapping.serviceKey, mapping.connectorKey, mapping.sourceKind, mapping.externalKey, mapping.resourceKind]))
    .sort((left, right) => left.serviceKey.localeCompare(right.serviceKey) || right.confidence - left.confidence),
);
const selectedConnectorViews = computed(() => connectorViewSections(selectedDataSource.value?.kind ?? selectedConnector.value?.kind ?? ''));
const selectedConnectorCoverage = computed(() => selectedConnectorViews.value.map(sectionLabel).join(' / ') || copy.value.unavailable);
const selectedNotificationRoute = computed(
  () => visibleNotificationRoutes.value.find((route) => route.routeKey === selectedNotificationRouteKey.value) ?? visibleNotificationRoutes.value[0] ?? null,
);
const selectedPolicyPlan = computed(
  () => visiblePolicyPlans.value.find((plan) => plan.planKey === selectedPolicyPlanKey.value) ?? visiblePolicyPlans.value[0] ?? null,
);
const criticalHosts = computed(() => hosts.value.filter((host) => host.state === 'CRITICAL'));
const selectedEdgeStatus = computed(() => (selectedEdge.value ? edgeStatusLabel(selectedEdge.value) : 'HEALTHY'));
const sortedRiskEdges = computed(() => [...visibleDependencies.value].sort((left, right) => edgeRiskScore(right) - edgeRiskScore(left)));
const topologyDepthLimit = computed(() => (topologyDepth.value === 'ALL' ? Number.POSITIVE_INFINITY : Number(topologyDepth.value)));
const topologyRootServiceKey = computed(() => selectedTopologyRootKey.value ?? '');
const topologyScopeLabel = computed(() => {
  if (!topologyRootServiceKey.value) {
    return copy.value.entryTopology;
  }
  return serviceByKey(topologyRootServiceKey.value)?.name ?? topologyRootServiceKey.value;
});
const topologyServices = computed(() => {
  const relatedKeys = new Set(visibleDependencies.value.flatMap((edge) => [edge.sourceKey, edge.targetKey]));
  return visibleServices.value
    .filter((service) => relatedKeys.has(service.serviceKey))
    .sort((left, right) => serviceRiskScore(right) - serviceRiskScore(left) || left.name.localeCompare(right.name));
});
const topologyEntryServices = computed(() => inferEntryServices(visibleServices.value, visibleDependencies.value));
const topologyEntryServiceKeys = computed(() => new Set(topologyEntryServices.value.map((service) => service.serviceKey)));
const topologyDependencyKindByTarget = computed(() => {
  const byTarget = new Map<string, string>();
  const scoreByTarget = new Map<string, number>();
  visibleDependencies.value.forEach((edge) => {
    const score = edgeRiskScore(edge);
    if (!byTarget.has(edge.targetKey) || score > (scoreByTarget.get(edge.targetKey) ?? -1)) {
      byTarget.set(edge.targetKey, edge.kind);
      scoreByTarget.set(edge.targetKey, score);
    }
  });
  return byTarget;
});
const topologyScopedDependencies = computed(() =>
  topologyRootServiceKey.value
    ? filterDependenciesByDepth(visibleDependencies.value, topologyRootServiceKey.value, topologyDepthLimit.value)
    : visibleDependencies.value,
);
const topologyNodeLayoutMap = computed(() =>
  buildTopologyNodeLayoutMap(topologyScopedDependencies.value, topologyRootServiceKey.value, topologyEntryServiceKeys.value),
);
const topologyNodes = computed<TopologyNode[]>(() => {
  const keys = Array.from(new Set([USER_NODE_KEY, ...topologyScopedDependencies.value.flatMap((edge) => [edge.sourceKey, edge.targetKey]), ...topologyEntryServiceKeys.value]));
  return keys.map((key, index) => buildTopologyNode(key, index, keys.length));
});
const topologyNodeMap = computed(() => new Map(topologyNodes.value.map((node) => [node.key, node])));
const topologyEdges = computed(() => topologyScopedDependencies.value.filter((edge) => topologyNodeMap.value.has(edge.sourceKey) && topologyNodeMap.value.has(edge.targetKey)));
const topologyGraphEdges = computed<TopologyGraphEdge[]>(() =>
  [
    ...topologyEntryServices.value
      .filter((service) => topologyNodeMap.value.has(service.serviceKey))
      .map((service) => ({
        key: entryEdgeKey(service.serviceKey),
        sourceKey: USER_NODE_KEY,
        targetKey: service.serviceKey,
        tone: 'healthy',
        kind: 'ENTRY',
        resourceKey: `entry:${service.serviceKey}`,
        label: '',
        virtual: true,
      })),
    ...topologyEdges.value.map((edge) => ({
      key: edgeKey(edge),
      sourceKey: edge.sourceKey,
      targetKey: edge.targetKey,
      tone: edgeTone(edge),
      kind: edge.kind,
      resourceKey: edge.resourceKey,
      label: `${copy.value.failureRate} ${edgeFailure(edge)} / P95 ${edgeP95(edge)}`,
    })),
  ],
);
const topologyRiskEdges = computed(() => [...topologyEdges.value].sort((left, right) => edgeRiskScore(right) - edgeRiskScore(left)).slice(0, 8));
const selectedTopologyEdge = computed(() => {
  const explicitEdge = topologyEdges.value.find((edge) => edgeKey(edge) === selectedEdgeKey.value) ?? null;
  if (explicitEdge) {
    return explicitEdge;
  }
  if (selectedTopologyNodeKey.value) {
    return null;
  }
  return topologyRiskEdges.value[0] ?? topologyEdges.value[0] ?? null;
});
const selectedTopologyNode = computed(() => (selectedTopologyNodeKey.value ? topologyNodeMap.value.get(selectedTopologyNodeKey.value) ?? null : null));
const selectedTopologyNodeHosts = computed(() => (selectedTopologyNode.value ? hostForService(selectedTopologyNode.value.key) : []));
const selectedTopologyEdgeStatus = computed(() => (selectedTopologyEdge.value ? edgeStatusLabel(selectedTopologyEdge.value) : 'HEALTHY'));
const selectedTopologyServiceKey = computed(() => {
  if (selectedTopologyEdge.value) {
    return serviceByKey(selectedTopologyEdge.value.sourceKey)?.serviceKey ?? serviceByKey(selectedTopologyEdge.value.targetKey)?.serviceKey ?? null;
  }
  if (selectedTopologyNode.value && serviceByKey(selectedTopologyNode.value.key)) {
    return selectedTopologyNode.value.key;
  }
  return null;
});
const topologyEdgeLabels = computed(() =>
  topologyEdges.value
    .filter((edge) => edgeTone(edge) !== 'healthy' || (selectedEdge.value && edgeKey(edge) === edgeKey(selectedEdge.value)))
    .sort((left, right) => edgeRiskScore(right) - edgeRiskScore(left))
    .slice(0, 7),
);
const selectedIncidentEdges = computed(() => {
  const incident = selectedIncident.value;
  if (!incident) {
    return topologyEdges.value.slice(0, 5);
  }
  const evidenceKeys = new Set(incident.evidence.flatMap((item) => [item.resourceKey, item.serviceKey]));
  const related = topologyEdges.value.filter(
    (edge) =>
      edge.resourceKey === incident.primaryResourceKey ||
      edge.sourceKey === incident.impactScope.serviceKey ||
      edge.targetKey === incident.impactScope.serviceKey ||
      evidenceKeys.has(edge.resourceKey) ||
      evidenceKeys.has(edge.sourceKey) ||
      evidenceKeys.has(edge.targetKey),
  );
  return (related.length > 0 ? related : topologyEdges.value).slice(0, 5);
});
const selectedIncidentTransactions = computed(() =>
  transactions.value
    .filter((metric) => {
      const incident = selectedIncident.value;
      return incident
        ? metric.serviceKey === incident.impactScope.serviceKey ||
            metric.endpointKey === incident.primaryResourceKey ||
            metric.sampleTraceKey.includes(incident.impactScope.serviceKey)
        : true;
    })
    .sort((left, right) => right.failureRate - left.failureRate || right.p99Ms - left.p99Ms)
    .slice(0, 4),
);
const selectedIncidentTraces = computed(() => {
  const incident = selectedIncident.value;
  if (!incident) {
    return visibleFlows.value.slice(0, 4);
  }
  const evidenceKeys = new Set(incident.evidence.flatMap((item) => [item.resourceKey, item.serviceKey, item.referenceKey]));
  const related = visibleFlows.value.filter(
    (flow) =>
      flow.entryServiceKey === incident.impactScope.serviceKey ||
      flow.endpointKey === incident.primaryResourceKey ||
      flow.traceKey.includes(incident.impactScope.serviceKey) ||
      flow.spans.some((span) => evidenceKeys.has(span.resourceKey) || evidenceKeys.has(span.serviceKey)),
  );
  return (related.length > 0 ? related : visibleFlows.value).slice(0, 4);
});
const selectedIncidentTrace = computed(() => selectedIncidentTraces.value[0] ?? visibleFlows.value[0] ?? null);
const selectedFlowEdge = computed(() => edgeForFlow(selectedFlow.value));
const selectedServiceEdges = computed(() => {
  const service = selectedService.value;
  if (!service) {
    return [];
  }
  return sortedRiskEdges.value.filter((edge) => edge.sourceKey === service.serviceKey || edge.targetKey === service.serviceKey).slice(0, 6);
});
const selectedServiceHosts = computed(() =>
  selectedService.value ? hostForService(selectedService.value.serviceKey).sort((left, right) => hostRiskScore(right) - hostRiskScore(left)) : [],
);
const selectedServiceTraces = computed(() =>
  selectedService.value
    ? visibleFlows.value.filter((flow) => flow.entryServiceKey === selectedService.value?.serviceKey || flow.spans.some((span) => span.serviceKey === selectedService.value?.serviceKey)).slice(0, 4)
    : [],
);
const selectedHostService = computed(() => (selectedHost.value ? serviceByKey(selectedHost.value.serviceKey) : null));
const selectedHostSignals = computed(() =>
  selectedHost.value ? visibleSignals.value.filter((signal) => signal.serviceKey === selectedHost.value?.serviceKey || signal.clusterKey === selectedHost.value?.clusterKey).slice(0, 4) : [],
);
const selectedHostTraces = computed(() =>
  selectedHost.value ? visibleFlows.value.filter((flow) => flow.entryServiceKey === selectedHost.value?.serviceKey || flow.spans.some((span) => span.serviceKey === selectedHost.value?.serviceKey)).slice(0, 4) : [],
);
const selectedMiddlewareEdges = computed(() =>
  selectedMiddleware.value ? sortedRiskEdges.value.filter((edge) => edge.targetKey === selectedMiddleware.value?.middlewareKey || edge.resourceKey.includes(selectedMiddleware.value?.middlewareKey ?? '')).slice(0, 6) : [],
);
const selectedMiddlewareSignals = computed(() =>
  selectedMiddleware.value
    ? visibleSignals.value
        .filter((signal) => signal.resourceKey.includes(selectedMiddleware.value?.middlewareKey ?? '') || signal.resourceKey.includes(selectedMiddleware.value?.kind.toLowerCase() ?? ''))
        .slice(0, 4)
    : [],
);
const selectedSignalEdge = computed(() => (selectedSignal.value ? edgeForSignal(selectedSignal.value) : null));
const selectedSignalFlow = computed(() => (selectedSignal.value ? flowForSignal(selectedSignal.value) : null));
const selectedPolicyIncident = computed(() =>
  selectedPolicyPlan.value
    ? visibleIncidents.value.find(
        (incident) =>
          incident.impactScope.serviceKey === selectedPolicyPlan.value?.serviceKey ||
          incident.primaryResourceKey === selectedPolicyPlan.value?.resourceKey ||
          incident.evidence.some((item) => item.resourceKey === selectedPolicyPlan.value?.resourceKey),
      ) ?? null
    : null,
);
const selectedRouteIncident = computed(() =>
  visibleIncidents.value.find((incident) => selectedNotificationRoute.value && severityScore(incident.severity) >= severityScore(selectedNotificationRoute.value.minSeverity)) ?? null,
);
const selectedPolicyDryRun = computed(() =>
  policyDryRunResult.value?.planKey === selectedPolicyPlan.value?.planKey ? policyDryRunResult.value : null,
);
const selectedPolicyExport = computed(() =>
  policyExportReview.value?.planKey === selectedPolicyPlan.value?.planKey ? policyExportReview.value : null,
);
const selectedPolicyDiffs = computed(() => selectedPolicyDryRun.value?.diffs ?? selectedPolicyPlan.value?.diffs ?? []);
const selectedNotificationPreview = computed(() =>
  notificationPreview.value?.routeKey === selectedNotificationRoute.value?.routeKey ? notificationPreview.value : null,
);
const selectedNotificationTest = computed(() =>
  notificationTestResult.value?.routeKey === selectedNotificationRoute.value?.routeKey ? notificationTestResult.value : null,
);
const flowViewModes = computed<Array<{ id: FlowViewMode; label: string }>>(() => [
  { id: 'list', label: copy.value.viewList },
  { id: 'tree', label: copy.value.viewTree },
  { id: 'table', label: copy.value.viewTable },
  { id: 'stats', label: copy.value.viewStats },
]);

watch(() => props.section, (section) => {
  searchTerm.value = '';
  if (section === 'topology') {
    selectedTopologyRootKey.value = null;
    selectedTopologyNodeKey.value = null;
    selectedEdgeKey.value = null;
  }
  if (section === 'request-flows') {
    void refreshTraceQuery();
  }
});

watch([selectedFlowStatus, selectedFlowServiceKey, selectedFlowSource, selectedFlowSort, flowMinDurationMs, flowResourceFilter, selectedZone, searchTerm], () => {
  flowPage.value = 0;
});

watch([selectedFlowStatus, selectedFlowServiceKey, selectedFlowSource, selectedFlowSort, flowMinDurationMs, flowResourceFilter, flowPage], () => {
  if (props.section === 'request-flows') {
    void refreshTraceQuery();
  }
});

watch(flowPageCount, (pageCount) => {
  if (flowPage.value >= pageCount) {
    flowPage.value = Math.max(0, pageCount - 1);
  }
});

watch(selectedConnectorConfig, (config) => {
  if (!config) {
    return;
  }
  connectorForm.value = {
    connectorKey: config.connectorKey,
    kind: config.kind,
    displayName: config.displayName,
    endpoint: isMaskedEndpoint(config.endpoint) ? '' : config.endpoint,
    authMode: config.authMode,
    accessMode: config.accessMode,
    state: config.state,
    testEnabled: config.testEnabled,
    targetTeam: config.attributes.targetTeam ?? 'platform-team',
  };
  mappingForm.value = {
    ...mappingForm.value,
    connectorKey: config.connectorKey,
    sourceKind: config.kind,
  };
}, { immediate: true });

watch(selectedService, (service) => {
  if (!service || mappingForm.value.serviceKey) {
    return;
  }
  mappingForm.value = {
    ...mappingForm.value,
    serviceKey: service.serviceKey,
    externalKey: service.serviceKey,
  };
}, { immediate: true });

onMounted(() => {
  if (!hasLoaded.value) {
    void refreshPlatform();
  }
  if (props.section === 'request-flows') {
    void refreshTraceQuery();
  }
});

async function refreshTraceQuery(): Promise<void> {
  const sequence = ++traceQuerySequence;
  traceQueryLoading.value = true;
  traceQueryError.value = null;
  try {
    const result = await fetchPlatformTraces(buildTraceQuery());
    if (sequence !== traceQuerySequence) {
      return;
    }
    traceQueryItems.value = result.items;
    traceQueryTotal.value = result.total;
    traceQueryLoaded.value = true;
    if (selectedFlowKey.value && !result.items.some((flow) => flow.traceKey === selectedFlowKey.value)) {
      selectedFlowKey.value = result.items[0]?.traceKey ?? null;
    }
  } catch (error) {
    if (sequence !== traceQuerySequence) {
      return;
    }
    traceQueryLoaded.value = false;
    traceQueryItems.value = null;
    traceQueryTotal.value = 0;
    traceQueryError.value = error instanceof Error ? error.message : 'Trace query failed';
  } finally {
    if (sequence === traceQuerySequence) {
      traceQueryLoading.value = false;
    }
  }
}

async function runSelectedPolicyDryRun(): Promise<void> {
  const plan = selectedPolicyPlan.value;
  if (!plan) {
    return;
  }
  policyActionLoading.value = true;
  policyActionError.value = null;
  policyExportReview.value = null;
  try {
    policyDryRunResult.value = await dryRunPlatformPlan(plan.planKey);
    await refreshPlatform();
  } catch (error) {
    policyActionError.value = error instanceof Error ? error.message : 'Dry-run failed';
  } finally {
    policyActionLoading.value = false;
  }
}

async function exportSelectedPolicyReview(): Promise<void> {
  const plan = selectedPolicyPlan.value;
  if (!plan) {
    return;
  }
  policyActionLoading.value = true;
  policyActionError.value = null;
  try {
    policyExportReview.value = await exportPlatformPlanReview(plan.planKey);
    await refreshPlatform();
  } catch (error) {
    policyActionError.value = error instanceof Error ? error.message : 'Review export failed';
  } finally {
    policyActionLoading.value = false;
  }
}

async function previewSelectedNotification(): Promise<void> {
  const route = selectedNotificationRoute.value;
  if (!route) {
    return;
  }
  notificationActionLoading.value = true;
  notificationActionError.value = null;
  notificationTestResult.value = null;
  try {
    notificationPreview.value = await previewNotificationRoute(route.routeKey);
    await refreshPlatform();
  } catch (error) {
    notificationActionError.value = error instanceof Error ? error.message : 'Notification preview failed';
  } finally {
    notificationActionLoading.value = false;
  }
}

async function testSelectedNotification(): Promise<void> {
  const route = selectedNotificationRoute.value;
  if (!route) {
    return;
  }
  notificationActionLoading.value = true;
  notificationActionError.value = null;
  try {
    notificationTestResult.value = await testNotificationRoute(route.routeKey);
    notificationPreview.value = notificationTestResult.value.preview;
    await refreshPlatform();
  } catch (error) {
    notificationActionError.value = error instanceof Error ? error.message : 'Notification test failed';
  } finally {
    notificationActionLoading.value = false;
  }
}

async function saveCurrentConnectorConfig(): Promise<void> {
  connectorActionError.value = null;
  connectorActionLoading.value = true;
  try {
    const form = connectorForm.value;
    if (isMaskedEndpoint(form.endpoint)) {
      connectorActionError.value = 'Endpoint is masked in the API response. Re-enter the real connector URL before saving.';
      return;
    }
    const saved = await saveConnectorConfig({
      connectorKey: form.connectorKey.trim(),
      kind: form.kind,
      displayName: form.displayName.trim(),
      endpoint: form.endpoint.trim(),
      authMode: form.authMode,
      accessMode: form.accessMode,
      state: selectedConnectorConfig.value?.state ?? form.state,
      testEnabled: form.testEnabled,
      attributes: {
        targetTeam: form.targetTeam.trim() || 'platform-team',
        writeDisabled: 'true',
        productionWrite: 'false',
      },
    });
    selectedConnectorKey.value = saved.connectorKey;
    await refreshPlatform();
  } catch (error) {
    connectorActionError.value = error instanceof Error ? error.message : 'Connector save failed';
  } finally {
    connectorActionLoading.value = false;
  }
}

async function testSelectedConnector(): Promise<void> {
  const key = selectedConnectorConfig.value?.connectorKey ?? connectorForm.value.connectorKey;
  if (!key) {
    return;
  }
  connectorActionError.value = null;
  connectorActionLoading.value = true;
  try {
    connectorTestResult.value = await testConnectorConfig(key);
    await refreshPlatform();
  } catch (error) {
    connectorActionError.value = error instanceof Error ? error.message : 'Connector test failed';
  } finally {
    connectorActionLoading.value = false;
  }
}

async function saveCurrentServiceMapping(): Promise<void> {
  connectorActionError.value = null;
  connectorActionLoading.value = true;
  try {
    const form = mappingForm.value;
    const serviceKey = form.serviceKey.trim();
    const externalKey = form.externalKey.trim();
    const connectorKey = form.connectorKey.trim();
    await saveServiceMapping({
      mappingKey: `${connectorKey}-${serviceKey}-${form.resourceKind}`.replace(/[^A-Za-z0-9_:.\\/-]/g, '-'),
      serviceKey,
      connectorKey,
      sourceKind: form.sourceKind,
      externalKey,
      resourceKind: form.resourceKind,
      confidence: Number(form.confidence),
      attributes: {
        writeDisabled: 'true',
        reviewOnly: 'true',
      },
    });
    await refreshPlatform();
  } catch (error) {
    connectorActionError.value = error instanceof Error ? error.message : 'Service mapping save failed';
  } finally {
    connectorActionLoading.value = false;
  }
}

function buildTraceQuery(): PlatformTraceQuery {
  const minDuration = Number(flowMinDurationMs.value);
  return {
    serviceKey: selectedFlowServiceKey.value === 'ALL' ? null : selectedFlowServiceKey.value,
    status: selectedFlowStatus.value === 'ALL' ? null : selectedFlowStatus.value,
    minDurationMs: Number.isFinite(minDuration) && minDuration > 0 ? minDuration : null,
    resourceKey: flowResourceFilter.value.trim() || null,
    source: selectedFlowSource.value === 'ALL' ? null : selectedFlowSource.value,
    sort: traceSortParam(),
    page: flowPage.value,
    size: FLOW_PAGE_SIZE,
  };
}

function traceSortParam(): string {
  if (selectedFlowSort.value === 'duration_desc') {
    return 'duration_desc';
  }
  if (selectedFlowSort.value === 'time_desc') {
    return 'time_desc';
  }
  return 'risk';
}

function matchesZone(zoneKey: string): boolean {
  return selectedZone.value === 'ALL' || zoneKey === selectedZone.value;
}

function matchesText(values: Array<string | null | undefined>): boolean {
  const needle = searchTerm.value.trim().toLowerCase();
  if (!needle) {
    return true;
  }
  return values.some((value) => (value ?? '').toLowerCase().includes(needle));
}

function matchesFlowStatus(flow: FlowToneInput): boolean {
  return selectedFlowStatus.value === 'ALL' || flow.status === selectedFlowStatus.value;
}

function matchesFlowDuration(flow: FlowMetricInput): boolean {
  const minDuration = Number(flowMinDurationMs.value);
  return !Number.isFinite(minDuration) || minDuration <= 0 || flow.durationMs >= minDuration;
}

function flowComparator(left: FlowSortInput, right: FlowSortInput): number {
  if (selectedFlowSort.value === 'duration_desc') {
    return right.durationMs - left.durationMs || flowRiskScore(right) - flowRiskScore(left);
  }
  if (selectedFlowSort.value === 'time_desc') {
    return (right.startedAt ?? '').localeCompare(left.startedAt ?? '') || flowRiskScore(right) - flowRiskScore(left);
  }
  return flowRiskScore(right) - flowRiskScore(left) || right.durationMs - left.durationMs || (right.startedAt ?? '').localeCompare(left.startedAt ?? '');
}

function serviceByKey(serviceKey: string): PlatformServiceNode | null {
  return services.value.find((service) => service.serviceKey === serviceKey) ?? null;
}

function edgeKey(edge: PlatformDependencyEdge): string {
  return `${edge.sourceKey}->${edge.targetKey}:${edge.resourceKey}`;
}

function entryEdgeKey(serviceKey: string): string {
  return `${USER_NODE_KEY}->${serviceKey}:entry`;
}

function inferEntryServices(serviceList: PlatformServiceNode[], edgeList: PlatformDependencyEdge[]): PlatformServiceNode[] {
  const explicitEntries = serviceList.filter(isUserEntryService);
  if (explicitEntries.length > 0) {
    return sortEntryServices(explicitEntries);
  }
  const serviceKeys = new Set(serviceList.map((service) => service.serviceKey));
  const targetKeys = new Set(edgeList.filter((edge) => serviceKeys.has(edge.targetKey)).map((edge) => edge.targetKey));
  const sourceKeys = new Set(edgeList.filter((edge) => serviceKeys.has(edge.sourceKey)).map((edge) => edge.sourceKey));
  return sortEntryServices(
    serviceList.filter((service) => sourceKeys.has(service.serviceKey) && !targetKeys.has(service.serviceKey) && !isBackgroundService(service)),
  );
}

function sortEntryServices(serviceList: PlatformServiceNode[]): PlatformServiceNode[] {
  return [...serviceList].sort((left, right) => numberAttr(right.attributes, 'qps') - numberAttr(left.attributes, 'qps') || left.name.localeCompare(right.name)).slice(0, 5);
}

function isUserEntryService(service: PlatformServiceNode): boolean {
  const value = `${service.serviceKey} ${service.name}`.toLowerCase();
  if (isBackgroundService(service)) {
    return false;
  }
  return ['api', 'console', 'admin', 'portal', 'gateway', 'platform'].some((token) => value.includes(token));
}

function isBackgroundService(service: PlatformServiceNode): boolean {
  const value = `${service.serviceKey} ${service.name}`.toLowerCase();
  return ['scheduler', 'consumer', 'job', 'worker'].some((token) => value.includes(token));
}

function edgeTone(edge: PlatformDependencyEdge): string {
  if (edge.criticalCount > 0) {
    return 'critical';
  }
  if (edge.warningCount > 0) {
    return 'warning';
  }
  if (edge.attributes.packetLossPercent && Number(edge.attributes.packetLossPercent) > 0) {
    return 'jitter';
  }
  return 'healthy';
}

function edgeRiskScore(edge: PlatformDependencyEdge): number {
  return edge.criticalCount * 100 + edge.warningCount * 10 + numberAttr(edge.attributes, 'packetLossPercent') * 5 + numberAttr(edge.attributes, 'errorRate') * 100;
}

function serviceRiskScore(service: PlatformServiceNode): number {
  return service.criticalCount * 100 + service.warningCount * 10 + numberAttr(service.attributes, 'errorRate') * 100 + numberAttr(service.attributes, 'p99Ms') / 1000;
}

function severityScore(severity: string | null | undefined): number {
  const value = (severity ?? '').toUpperCase();
  if (value === 'CRITICAL' || value === 'HIGH' || value === 'FAILED' || value === 'ERROR') {
    return 100;
  }
  if (value === 'WARNING' || value === 'MEDIUM' || value === 'DEGRADED' || value === 'SLOW') {
    return 50;
  }
  return 0;
}

function hostRiskScore(host: PlatformHostSignal): number {
  return (
    severityScore(host.state) +
    host.cpuPercent / 2 +
    host.memoryPercent / 3 +
    host.swapPercent +
    host.diskIoPercent / 2 +
    host.networkJitterMs / 20 +
    host.packetLossPercent * 5 +
    (host.lastError ? 20 : 0)
  );
}

function middlewareRiskScore(item: PlatformMiddlewareWatermark): number {
  return severityScore(item.state) + item.criticalCount * 80 + item.warningCount * 20 + item.usagePercent / 2 + item.latencyMs / 10 + item.errorRate * 160;
}

function signalRiskScore(signal: PlatformSignal): number {
  return severityScore(signal.severity) + severityScore(signal.outcome) / 2 + (signal.durationBucket.includes('GT') ? 20 : 0);
}

function connectorRiskScore(connector: PlatformConnectorStatus): number {
  return severityScore(connector.state) + (connector.lastMessage ? 5 : 0);
}

function dataSourceRiskScore(source: PlatformDataSource): number {
  return severityScore(source.state) + (source.lastMessage ? 5 : 0);
}

function connectorViewSections(kind: string): SectionId[] {
  const value = kind.toUpperCase();
  if (value === 'SKYWALKING' || value === 'OPENTELEMETRY') {
    return ['topology', 'request-flows', 'services', 'incidents'];
  }
  if (value === 'PROMETHEUS' || value === 'MICROMETER') {
    return ['overview', 'services', 'hosts', 'middleware'];
  }
  if (value === 'SENTINEL' || value === 'GATEWAY') {
    return ['resources', 'incidents', 'policies', 'topology'];
  }
  if (value === 'ALERTMANAGER') {
    return ['incidents', 'notifications'];
  }
  if (value === 'FEISHU' || value === 'DINGTALK') {
    return ['notifications'];
  }
  if (value === 'NEXARY_SDK') {
    return ['resources', 'request-flows', 'incidents', 'topology'];
  }
  return ['integrations'];
}

function sectionLabel(section: SectionId): string {
  switch (section) {
    case 'overview':
      return copy.value.overview;
    case 'topology':
      return copy.value.topology;
    case 'request-flows':
      return copy.value.requestFlows;
    case 'incidents':
      return copy.value.incidents;
    case 'services':
      return copy.value.services;
    case 'hosts':
      return copy.value.hosts;
    case 'middleware':
      return copy.value.middleware;
    case 'resources':
      return copy.value.resources;
    case 'integrations':
      return copy.value.integrations;
    case 'notifications':
      return copy.value.notifications;
    case 'policies':
      return copy.value.policies;
    default:
      return section;
  }
}

function connectorImpactText(source: PlatformDataSource | null): string {
  if (!source) {
    return copy.value.unavailable;
  }
  const views = connectorViewSections(source.kind).map(sectionLabel).join(' / ');
  const state = source.state.toUpperCase();
  if (state === 'FAILED' || state === 'DEGRADED') {
    return locale.value === 'zh'
      ? `${views} 会降级为其他来源或已保留证据，不影响业务 SDK。`
      : `${views} fall back to other sources or retained evidence; business SDK traffic is not affected.`;
  }
  if (state === 'DISABLED') {
    return locale.value === 'zh'
      ? `${views} 不采集生产数据，只保留配置或 dry-run 说明。`
      : `${views} do not collect production data; only configuration or dry-run notes are retained.`;
  }
  return locale.value === 'zh'
    ? `${views} 可使用该来源的只读证据。`
    : `${views} can use read-only evidence from this source.`;
}

function toConnectorStatus(config: PlatformConnectorConfig): PlatformConnectorStatus {
  return {
    connectorKey: config.connectorKey,
    kind: config.kind,
    state: config.state,
    displayName: config.displayName,
    lastMessage: config.lastMessage,
    lastSeenAt: config.updatedAt,
  };
}

function isMaskedEndpoint(endpoint: string): boolean {
  return endpoint.includes('/***');
}

function routeRiskScore(route: PlatformNotificationRoute): number {
  return severityScore(route.state) + severityScore(route.minSeverity) + route.boundIncidentCount * 8 + (route.dryRun ? 6 : 0);
}

function policyRiskScore(plan: PolicyRiskInput): number {
  return severityScore(plan.risk) + severityScore(plan.state) + plan.evidenceCount * 8;
}

function filterDependenciesByDepth(edges: PlatformDependencyEdge[], rootKey: string, depthLimit: number): PlatformDependencyEdge[] {
  if (!rootKey || !Number.isFinite(depthLimit)) {
    return edges;
  }
  const visited = new Set([rootKey]);
  let frontier = new Set([rootKey]);
  for (let depth = 0; depth < depthLimit; depth += 1) {
    const next = new Set<string>();
    edges.forEach((edge) => {
      if (frontier.has(edge.sourceKey)) {
        next.add(edge.targetKey);
      }
      if (frontier.has(edge.targetKey)) {
        next.add(edge.sourceKey);
      }
    });
    next.forEach((key) => visited.add(key));
    frontier = next;
    if (frontier.size === 0) {
      break;
    }
  }
  return edges.filter((edge) => visited.has(edge.sourceKey) && visited.has(edge.targetKey));
}

function buildTopologyNodeLayoutMap(edges: PlatformDependencyEdge[], rootKey: string, entryKeys: Set<string>): Map<string, { x: number; y: number }> {
  const levels = new Map<string, number>([[USER_NODE_KEY, 0]]);
  const externalTriggerKeys = new Set(edges.filter((edge) => !serviceByKey(edge.sourceKey) && !!serviceByKey(edge.targetKey)).map((edge) => edge.sourceKey));
  entryKeys.forEach((key) => levels.set(key, 1));
  externalTriggerKeys.forEach((key) => levels.set(key, 1));
  if (rootKey && !levels.has(rootKey)) {
    levels.set(rootKey, 2);
  }

  for (let pass = 0; pass < edges.length + 3; pass += 1) {
    edges.forEach((edge) => {
      const sourceLevel = levels.get(edge.sourceKey);
      const targetLevel = levels.get(edge.targetKey);
      if (sourceLevel !== undefined && targetLevel === undefined) {
        levels.set(edge.targetKey, Math.min(sourceLevel + 1, 4));
      } else if (sourceLevel !== undefined && targetLevel !== undefined && targetLevel <= sourceLevel && !entryKeys.has(edge.targetKey)) {
        levels.set(edge.targetKey, Math.min(sourceLevel + 1, 4));
      }
      if (rootKey && targetLevel !== undefined && sourceLevel === undefined && serviceByKey(edge.sourceKey)) {
        levels.set(edge.sourceKey, Math.max(targetLevel - 1, 1));
      }
    });
  }

  edges.forEach((edge) => {
    if (!levels.has(edge.sourceKey)) {
      levels.set(edge.sourceKey, serviceByKey(edge.sourceKey) || externalTriggerKeys.has(edge.sourceKey) ? 2 : 4);
    }
    if (!levels.has(edge.targetKey)) {
      levels.set(edge.targetKey, serviceByKey(edge.targetKey) ? Math.min((levels.get(edge.sourceKey) ?? 1) + 1, 4) : 4);
    }
  });

  levels.forEach((level, key) => {
    if (key !== USER_NODE_KEY && !serviceByKey(key) && !externalTriggerKeys.has(key)) {
      levels.set(key, Math.max(level, 4));
    }
  });

  const grouped = new Map<number, string[]>();
  levels.forEach((level, key) => {
    const clamped = Math.max(0, Math.min(4, level));
    const bucket = grouped.get(clamped) ?? [];
    bucket.push(key);
    grouped.set(clamped, bucket);
  });
  const xByLevel = new Map([
    [0, 9],
    [1, 27],
    [2, 47],
    [3, 66],
    [4, 86],
  ]);
  const layout = new Map<string, { x: number; y: number }>();
  grouped.forEach((keys, level) => {
    const sortedKeys = [...keys].sort((left, right) => {
      if (left === USER_NODE_KEY) {
        return -1;
      }
      if (right === USER_NODE_KEY) {
        return 1;
      }
      return nodeRiskScore(right, edges) - nodeRiskScore(left, edges) || left.localeCompare(right);
    });
    sortedKeys.forEach((key, index) => {
      const count = sortedKeys.length;
      layout.set(key, {
        x: xByLevel.get(level) ?? 50,
        y: count === 1 ? 50 : 24 + (index * 56) / Math.max(count - 1, 1),
      });
    });
  });
  alignMiddlewareTargetsToCallers(edges, layout);
  return layout;
}

function alignMiddlewareTargetsToCallers(edges: PlatformDependencyEdge[], layout: Map<string, { x: number; y: number }>): void {
  const middlewareTargets = new Set<string>();
  edges.forEach((edge) => {
    if (serviceByKey(edge.targetKey)) {
      return;
    }
    middlewareTargets.add(edge.targetKey);
  });

  const sortedTargets = Array.from(middlewareTargets).sort((left, right) => nodeRiskScore(right, edges) - nodeRiskScore(left, edges) || left.localeCompare(right));
  sortedTargets.forEach((key, index) => {
    const count = sortedTargets.length;
    layout.set(key, {
      x: 86,
      y: count === 1 ? layout.get(key)?.y ?? 50 : 24 + (index * 56) / Math.max(count - 1, 1),
    });
  });
}

function nodeRiskScore(key: string, edges: PlatformDependencyEdge[]): number {
  return edges
    .filter((edge) => edge.sourceKey === key || edge.targetKey === key)
    .reduce((total, edge) => total + edgeRiskScore(edge), 0);
}

function stateTone(state: string | null | undefined): string {
  const value = (state ?? '').toUpperCase();
  if (['CRITICAL', 'HIGH', 'FAILED', 'ERROR', 'OPEN', 'NEEDS_ACTION'].includes(value)) {
    return 'critical';
  }
  if (['WARNING', 'MEDIUM', 'DEGRADED', 'WATCH', 'SLOW', 'DISABLED'].includes(value)) {
    return 'warning';
  }
  return 'healthy';
}

function incidentTone(incident: IncidentToneInput): string {
  return incident.severity === 'CRITICAL' ? 'critical' : incident.severity === 'WARNING' ? 'warning' : 'healthy';
}

function flowTone(flow: FlowToneInput): string {
  return stateTone(flow.status === 'OK' ? 'HEALTHY' : flow.status);
}

function flowRiskScore(flow: FlowRiskInput): number {
  const status = flow.status.toUpperCase();
  const statusScore = status === 'ERROR' ? 120 : status === 'SLOW' ? 70 : status === 'OK' ? 0 : 30;
  const spanPenalty = flow.spans.some((span) => stateTone(span.status) === 'critical') ? 40 : flow.spans.some((span) => stateTone(span.status) === 'warning') ? 20 : 0;
  return statusScore + spanPenalty + flow.evidenceRefs.length * 4 + flow.durationMs / 100;
}

function edgeStatusLabel(edge: PlatformDependencyEdge): string {
  const tone = edgeTone(edge);
  if (tone === 'jitter') {
    return 'NETWORK_JITTER';
  }
  return tone.toUpperCase();
}

function serviceStatusLabel(service: PlatformServiceNode | null): string {
  if (!service) {
    return 'HEALTHY';
  }
  if (service.criticalCount > 0) {
    return 'CRITICAL';
  }
  if (service.warningCount > 0) {
    return 'WARNING';
  }
  return 'HEALTHY';
}

function spanStyle(span: SpanMetricInput, flow: FlowMetricInput): Record<string, string> {
  const total = Math.max(flow.durationMs, 1);
  const offset = Math.min((span.startOffsetMs / total) * 100, 82);
  const width = Math.max(6, Math.min((span.durationMs / total) * 100, 100));
  return {
    '--offset': `${offset}%`,
    '--width': `${width}%`,
  };
}

function percent(value: number): string {
  return `${(value * 100).toFixed(value >= 0.1 ? 1 : 2)}%`;
}

function wholePercent(value: number): string {
  return `${Math.round(value)}%`;
}

function ms(value: number): string {
  return `${Math.round(value)}ms`;
}

function numberAttr(attributes: Record<string, string> | undefined, key: string, fallback = 0): number {
  const rawValue = attributes?.[key];
  if (rawValue === undefined || rawValue === '') {
    return fallback;
  }
  const parsed = Number(rawValue);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function txForFlow(flow: FlowMetricInput | null): PlatformTransactionMetric | null {
  if (!flow) {
    return transactions.value[0] ?? null;
  }
  return transactions.value.find((metric) => metric.sampleTraceKey === flow.traceKey || metric.endpointKey === flow.endpointKey) ?? null;
}

function txForEdge(edge: PlatformDependencyEdge | null): PlatformTransactionMetric | null {
  if (!edge) {
    return null;
  }
  const exact = transactions.value.find(
    (metric) =>
      metric.serviceKey === edge.sourceKey &&
      (metric.endpointKey === edge.resourceKey || edge.resourceKey.includes(metric.endpointKey) || metric.endpointKey.includes(edge.targetKey)),
  );
  if (exact) {
    return exact;
  }
  return transactions.value
    .filter((metric) => metric.serviceKey === edge.sourceKey)
    .sort((left, right) => right.failureRate - left.failureRate || right.p95Ms - left.p95Ms)[0] ?? null;
}

function edgeForFlow(flow: FlowEdgeInput | null): PlatformDependencyEdge | null {
  if (!flow) {
    return null;
  }
  const spanResourceKeys = new Set(flow.spans.flatMap((span) => [span.resourceKey, span.serviceKey]));
  const exact = sortedRiskEdges.value.find(
    (edge) =>
      edge.resourceKey === flow.endpointKey ||
      spanResourceKeys.has(edge.resourceKey) ||
      (edge.sourceKey === flow.entryServiceKey && spanResourceKeys.has(edge.targetKey)),
  );
  if (exact) {
    return exact;
  }
  return (
    sortedRiskEdges.value.find(
      (edge) =>
        edge.sourceKey === flow.entryServiceKey ||
        flow.summary.toLowerCase().includes(edge.targetKey.toLowerCase()) ||
        flow.summary.toLowerCase().includes(edge.resourceKey.toLowerCase()),
    ) ?? null
  );
}

function signalKey(signal: PlatformSignal): string {
  return `${signal.timestamp ?? 'no-time'}:${signal.serviceKey}:${signal.resourceKey}:${signal.signalType}:${signal.outcome}`;
}

function edgeForSignal(signal: PlatformSignal): PlatformDependencyEdge | null {
  return (
    sortedRiskEdges.value.find(
      (edge) =>
        edge.resourceKey === signal.resourceKey ||
        edge.sourceKey === signal.serviceKey ||
        edge.targetKey === signal.serviceKey ||
        signal.resourceKey.includes(edge.targetKey) ||
        signal.resourceKey.includes(edge.resourceKey),
    ) ?? null
  );
}

function flowForSignal(signal: PlatformSignal) {
  return (
    visibleFlows.value.find(
      (flow) =>
        flow.entryServiceKey === signal.serviceKey ||
        flow.endpointKey === signal.resourceKey ||
        flow.spans.some((span) => span.resourceKey === signal.resourceKey || span.serviceKey === signal.serviceKey),
    ) ?? null
  );
}

function flowForService(serviceKey: string | null | undefined) {
  if (!serviceKey) {
    return null;
  }
  return visibleFlows.value.find((flow) => flow.entryServiceKey === serviceKey || flow.spans.some((span) => span.serviceKey === serviceKey)) ?? null;
}

function edgeForService(serviceKey: string | null | undefined): PlatformDependencyEdge | null {
  if (!serviceKey) {
    return null;
  }
  return sortedRiskEdges.value.find((edge) => edge.sourceKey === serviceKey || edge.targetKey === serviceKey) ?? null;
}

function edgeQps(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return tx.qps.toFixed(2);
  }
  return numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'qps').toFixed(0);
}

function edgeFailure(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return percent(tx.failureRate);
  }
  return percent(numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'errorRate'));
}

function edgeP95(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return ms(tx.p95Ms);
  }
  return ms(numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'p95Ms'));
}

function edgeP99(edge: PlatformDependencyEdge): string {
  const tx = txForEdge(edge);
  if (tx) {
    return ms(tx.p99Ms);
  }
  return ms(numberAttr(serviceByKey(edge.sourceKey)?.attributes, 'p99Ms'));
}

function hostForService(serviceKey: string): PlatformHostSignal[] {
  return hosts.value.filter((host) => host.serviceKey === serviceKey);
}

function buildTopologyNode(key: string, index: number, total: number): TopologyNode {
  if (key === USER_NODE_KEY) {
    return {
      key,
      label: 'User',
      caption: 'external',
      subLabel: 'client traffic',
      kind: 'USER',
      icon: topologyIcon('USER', 'healthy'),
      zoneKey: 'client',
      tone: 'healthy',
      x: topologyNodeLayoutMap.value.get(key)?.x ?? 8,
      y: topologyNodeLayoutMap.value.get(key)?.y ?? 50,
    };
  }
  const service = serviceByKey(key);
  const middlewareItem = middleware.value.find((item) => item.middlewareKey === key);
  const inferredKind = service
    ? serviceTopologyKind(service)
    : normalizeTopologyKind([middlewareItem?.kind, middlewareItem?.name, key, topologyDependencyKindByTarget.value.get(key)]);
  const tone = service ? relatedTopologyTone(key, serviceStatusLabel(service).toLowerCase()) : relatedTopologyTone(key, stateTone(middlewareItem?.state));
  const position = topologyEntryPosition(key) ?? topologyNodeLayoutMap.value.get(key) ?? topologyNodePositions[key] ?? fallbackTopologyPosition(index, total, middlewareItem ? 'MIDDLEWARE' : 'SERVICE');
  const nodeHosts = hostForService(key);
  return {
    key,
    label: service?.name ?? middlewareItem?.name ?? key,
    caption: service ? topologyServiceCaption(service, nodeHosts) : middlewareItem ? `${middlewareItem.kind} · ${middlewareItem.zoneKey}` : '',
    subLabel: service ? `${service.clusterKey} / ${service.zoneKey}` : middlewareItem ? `${middlewareItem.kind} / ${middlewareItem.zoneKey}` : 'external',
    kind: inferredKind,
    icon: topologyIcon(inferredKind, tone),
    zoneKey: service?.zoneKey ?? middlewareItem?.zoneKey ?? 'unknown',
    tone,
    x: position.x,
    y: position.y,
  };
}

function topologyServiceCaption(service: PlatformServiceNode, nodeHosts: PlatformHostSignal[]): string {
  const instanceCount = numberAttr(service.attributes, 'instances') || nodeHosts.length;
  return `${instanceCount || 0} inst · ${service.zoneKey}`;
}

function topologyEntryPosition(key: string): { x: number; y: number } | null {
  if (topologyRootServiceKey.value || !topologyEntryServiceKeys.value.has(key)) {
    return null;
  }
  const entries = topologyEntryServices.value;
  const index = entries.findIndex((service) => service.serviceKey === key);
  if (index < 0) {
    return null;
  }
  return {
    x: 18,
    y: entries.length === 1 ? 50 : 28 + (index * 44) / Math.max(entries.length - 1, 1),
  };
}

function serviceTopologyKind(service: PlatformServiceNode): string {
  const value = `${service.serviceKey} ${service.name}`.toLowerCase();
  if (value.includes('scheduler') || value.includes('job')) {
    return 'JOB';
  }
  if (value.includes('consumer') || value.includes('worker')) {
    return 'CONSUMER';
  }
  if (value.includes('signal')) {
    return 'SIGNALING';
  }
  if (isUserEntryService(service)) {
    return 'API';
  }
  return 'SERVICE';
}

function normalizeTopologyKind(values: Array<string | null | undefined>): string {
  const value = values
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  if (value.includes('redis') || value.includes('cache')) {
    return 'CACHE';
  }
  if (value.includes('postgres') || value.includes('postgre') || value.includes('pg-') || value.includes('database') || value.includes('jdbc')) {
    return 'DATABASE';
  }
  if (value.includes('rabbit') || value.includes('kafka') || value.includes('mq') || value.includes('messaging') || value.includes('queue')) {
    return 'MESSAGING';
  }
  if (value.includes('oss') || value.includes('object') || value.includes('storage')) {
    return 'OBJECT_STORAGE';
  }
  if (value.includes('job') || value.includes('scheduler')) {
    return 'JOB';
  }
  if (value.includes('signal')) {
    return 'SIGNALING';
  }
  if (value.includes('http') || value.includes('resource') || value.includes('downstream')) {
    return 'SERVICE';
  }
  return 'EXTERNAL';
}

function relatedTopologyTone(key: string, fallback: string): string {
  const critical = visibleDependencies.value.some((edge) => (edge.sourceKey === key || edge.targetKey === key) && edge.criticalCount > 0);
  if (critical) {
    return 'critical';
  }
  const warning = visibleDependencies.value.some((edge) => (edge.sourceKey === key || edge.targetKey === key) && edge.warningCount > 0);
  if (warning) {
    return 'warning';
  }
  return fallback;
}

function topologyIcon(kind: string, tone: string): string {
  const palette: Record<string, { color: string; label: string; glyph: string }> = {
    USER: {
      color: '#38bdf8',
      label: '',
      glyph: '<circle cx="24" cy="25" r="6"/><circle cx="40" cy="25" r="6"/><circle cx="32" cy="21" r="7"/><path d="M16 45c2-9 10-13 16-13s14 4 16 13" fill="none" stroke-width="5" stroke-linecap="round"/>',
    },
    API: { color: '#22d3ee', label: 'API', glyph: '' },
    SERVICE: {
      color: '#94a3b8',
      label: '',
      glyph: '<path d="M18 25 32 16l14 9v16L32 50 18 41Z" fill-opacity=".55"/><path d="M18 25 32 34l14-9M32 34v16" fill="none" stroke-width="4" stroke-linejoin="round"/>',
    },
    SIGNALING: {
      color: '#a78bfa',
      label: '',
      glyph: '<path d="M18 42c8-14 20-14 28 0M22 34c6-8 14-8 20 0M27 27c3-3 7-3 10 0" fill="none" stroke-width="5" stroke-linecap="round"/>',
    },
    CACHE: {
      color: '#ef4444',
      label: '',
      glyph: '<ellipse cx="32" cy="22" rx="15" ry="7"/><path d="M17 22v18c0 4 7 7 15 7s15-3 15-7V22M17 31c0 4 7 7 15 7s15-3 15-7" fill="none" stroke-width="4"/>',
    },
    DATABASE: {
      color: '#cbd5e1',
      label: '',
      glyph: '<ellipse cx="32" cy="21" rx="15" ry="7"/><path d="M17 21v22c0 4 7 7 15 7s15-3 15-7V21M17 32c0 4 7 7 15 7s15-3 15-7" fill="none" stroke-width="4"/>',
    },
    MESSAGING: {
      color: '#f59e0b',
      label: '',
      glyph: '<path d="M17 24h30v22H17Z" fill-opacity=".35"/><path d="m18 25 14 12 14-12M18 45l10-10M46 45 36 35" fill="none" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>',
    },
    OBJECT_STORAGE: {
      color: '#22c55e',
      label: '',
      glyph: '<path d="M21 44h24l4-19H15Z"/><path d="M22 24c2-6 8-9 15-6 5 2 8 4 10 6" fill="none" stroke-width="4" stroke-linecap="round"/>',
    },
    JOB: {
      color: '#fbbf24',
      label: '',
      glyph: '<circle cx="32" cy="32" r="17" fill-opacity=".25"/><path d="M32 20v13l9 6" fill="none" stroke-width="5" stroke-linecap="round"/>',
    },
    CONSUMER: {
      color: '#818cf8',
      label: '',
      glyph: '<path d="M18 21h28M18 32h28M18 43h28" fill="none" stroke-width="6" stroke-linecap="round"/><circle cx="48" cy="21" r="4"/><circle cx="48" cy="32" r="4"/><circle cx="48" cy="43" r="4"/>',
    },
    EXTERNAL: { color: '#94a3b8', label: 'EXT', glyph: '' },
  };
  const meta = palette[kind] ?? palette.EXTERNAL;
  const ring = tone === 'critical' ? '#ef4444' : tone === 'warning' ? '#f59e0b' : meta.color;
  const textColor = kind === 'DATABASE' ? '#0f172a' : '#e5f8ff';
  const glyph = meta.glyph
    ? `<g fill="${meta.color}" stroke="${meta.color}" stroke-linejoin="round">${meta.glyph}</g>`
    : `<circle cx="32" cy="32" r="18" fill="${meta.color}" fill-opacity="0.95"/><text x="32" y="37" text-anchor="middle" font-family="Inter,Arial,sans-serif" font-size="${meta.label.length > 1 ? 14 : 20}" font-weight="800" fill="${textColor}">${meta.label}</text>`;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><rect x="8" y="8" width="48" height="48" rx="15" fill="${meta.color}" fill-opacity="0.16" stroke="${ring}" stroke-width="5"/>${glyph}</svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

function fallbackTopologyPosition(index: number, total: number, kind: string): { x: number; y: number } {
  const columns = kind === 'MIDDLEWARE' ? [78, 88, 72] : [16, 32, 48, 64];
  const column = columns[index % columns.length];
  const rowCount = Math.max(1, Math.ceil(total / columns.length));
  const row = Math.floor(index / columns.length);
  return {
    x: column,
    y: 20 + ((row % rowCount) * 56) / Math.max(rowCount - 1, 1),
  };
}

function topologyNodeStyle(node: TopologyNode): Record<string, string> {
  return {
    left: `${node.x}%`,
    top: `${node.y}%`,
  };
}

function topologyX(key: string): number {
  return (topologyNodeMap.value.get(key)?.x ?? 50) * 10;
}

function topologyY(key: string): number {
  return (topologyNodeMap.value.get(key)?.y ?? 50) * 5.6;
}

function edgeMidpointStyle(edge: PlatformDependencyEdge): Record<string, string> {
  const source = topologyNodeMap.value.get(edge.sourceKey);
  const target = topologyNodeMap.value.get(edge.targetKey);
  return {
    left: `${((source?.x ?? 50) + (target?.x ?? 50)) / 2}%`,
    top: `${((source?.y ?? 50) + (target?.y ?? 50)) / 2}%`,
  };
}

function topologyNodeDisplayName(key: string): string {
  if (key === USER_NODE_KEY) {
    return 'User';
  }
  return topologyNodeMap.value.get(key)?.label ?? serviceByKey(key)?.name ?? middleware.value.find((item) => item.middlewareKey === key)?.name ?? key;
}

function topologyEdgeDisplayName(edge: PlatformDependencyEdge): string {
  return `${topologyNodeDisplayName(edge.sourceKey)} -> ${topologyNodeDisplayName(edge.targetKey)}`;
}

function selectTopologyNode(node: TopologyNode): void {
  selectedTopologyNodeKey.value = node.key;
  if (serviceByKey(node.key)) {
    selectedServiceKey.value = node.key;
  }
  selectedEdgeKey.value = null;
}

function selectTopologyNodeByKey(nodeKey: string): void {
  const node = topologyNodeMap.value.get(nodeKey);
  if (node) {
    selectTopologyNode(node);
  }
}

function focusHighestRiskEdge(): void {
  const riskEdge = sortedRiskEdges.value[0];
  if (!riskEdge) {
    return;
  }
  selectedTopologyNodeKey.value = riskEdge.targetKey;
  selectedEdgeKey.value = edgeKey(riskEdge);
}

function openIncidentTopology(): void {
  const edge = selectedIncidentEdges.value[0] ?? sortedRiskEdges.value[0] ?? null;
  if (edge) {
    selectedEdgeKey.value = edgeKey(edge);
    selectedTopologyNodeKey.value = null;
  }
  emit('navigate-section', 'topology');
}

function openIncidentTrace(): void {
  if (selectedIncidentTrace.value) {
    selectedFlowKey.value = selectedIncidentTrace.value.traceKey;
  }
  emit('navigate-section', 'request-flows');
}

function openSelectedTopologyTrace(): void {
  const edge = selectedTopologyEdge.value;
  const flow = edge
    ? visibleFlows.value.find((item) => {
        const matchedEdge = edgeForFlow(item);
        return matchedEdge ? edgeKey(matchedEdge) === edgeKey(edge) : false;
      })
    : null;
  if (flow) {
    selectedFlowKey.value = flow.traceKey;
  }
  emit('navigate-section', 'request-flows');
}

function openSelectedTopologyService(): void {
  if (selectedTopologyServiceKey.value) {
    selectedServiceKey.value = selectedTopologyServiceKey.value;
  }
  emit('navigate-section', 'services');
}

function openSelectedFlowTopology(): void {
  if (selectedFlowEdge.value) {
    selectedEdgeKey.value = edgeKey(selectedFlowEdge.value);
    selectedTopologyNodeKey.value = null;
  }
  emit('navigate-section', 'topology');
}

function openSelectedFlowService(): void {
  if (selectedFlow.value) {
    selectedServiceKey.value = selectedFlow.value.entryServiceKey;
  }
  emit('navigate-section', 'services');
}

function openServiceTopology(serviceKey: string | null | undefined): void {
  if (!serviceKey) {
    return;
  }
  selectedTopologyRootKey.value = serviceKey;
  const edge = edgeForService(serviceKey);
  if (edge) {
    selectedEdgeKey.value = edgeKey(edge);
    selectedTopologyNodeKey.value = null;
  } else {
    selectedEdgeKey.value = null;
    selectedTopologyNodeKey.value = serviceKey;
  }
  emit('navigate-section', 'topology');
}

function openServiceTrace(serviceKey: string | null | undefined): void {
  const flow = flowForService(serviceKey);
  if (flow) {
    selectedFlowKey.value = flow.traceKey;
  }
  emit('navigate-section', 'request-flows');
}

function openSelectedIncidentService(): void {
  if (selectedIncident.value) {
    selectedServiceKey.value = selectedIncident.value.impactScope.serviceKey;
  }
  emit('navigate-section', 'services');
}

function openSelectedServiceTopology(): void {
  openServiceTopology(selectedService.value?.serviceKey);
}

function openSelectedServiceTrace(): void {
  openServiceTrace(selectedService.value?.serviceKey);
}

function openSelectedServiceHosts(): void {
  const host = selectedServiceHosts.value[0];
  if (host) {
    selectedHostKey.value = host.hostKey;
  }
  emit('navigate-section', 'hosts');
}

function openSelectedServiceResources(): void {
  const signal = visibleSignals.value.find((item) => item.serviceKey === selectedService.value?.serviceKey);
  if (signal) {
    selectedSignalKey.value = signalKey(signal);
  }
  emit('navigate-section', 'resources');
}

function openSelectedHostService(): void {
  if (selectedHost.value) {
    selectedServiceKey.value = selectedHost.value.serviceKey;
  }
  emit('navigate-section', 'services');
}

function openSelectedHostTrace(): void {
  const flow = selectedHostTraces.value[0] ?? flowForService(selectedHost.value?.serviceKey);
  if (flow) {
    selectedFlowKey.value = flow.traceKey;
  }
  emit('navigate-section', 'request-flows');
}

function openSelectedHostTopology(): void {
  openServiceTopology(selectedHost.value?.serviceKey);
}

function openSelectedHostResources(): void {
  const signal = selectedHostSignals.value[0];
  if (signal) {
    selectedSignalKey.value = signalKey(signal);
  }
  emit('navigate-section', 'resources');
}

function openSelectedMiddlewareTopology(): void {
  const edge = selectedMiddlewareEdges.value[0];
  if (edge) {
    selectedTopologyRootKey.value = edge.sourceKey;
    selectedEdgeKey.value = edgeKey(edge);
    selectedTopologyNodeKey.value = null;
  } else if (selectedMiddleware.value) {
    selectedTopologyRootKey.value = null;
    selectedTopologyNodeKey.value = selectedMiddleware.value.middlewareKey;
    selectedEdgeKey.value = null;
  }
  emit('navigate-section', 'topology');
}

function openSelectedMiddlewareTrace(): void {
  const middlewareKey = selectedMiddleware.value?.middlewareKey;
  const flow = middlewareKey
    ? visibleFlows.value.find((item) => item.summary.includes(middlewareKey) || item.spans.some((span) => span.resourceKey.includes(middlewareKey)))
    : null;
  if (flow) {
    selectedFlowKey.value = flow.traceKey;
  }
  emit('navigate-section', 'request-flows');
}

function openSelectedMiddlewareResources(): void {
  const signal = selectedMiddlewareSignals.value[0];
  if (signal) {
    selectedSignalKey.value = signalKey(signal);
  }
  emit('navigate-section', 'resources');
}

function openSelectedMiddlewareIncident(): void {
  const middlewareKey = selectedMiddleware.value?.middlewareKey;
  const incident = middlewareKey
    ? visibleIncidents.value.find(
        (item) => item.primaryResourceKey.includes(middlewareKey) || item.evidence.some((evidence) => evidence.resourceKey.includes(middlewareKey)),
      )
    : null;
  if (incident) {
    selectedIncidentKey.value = incident.incidentKey;
  }
  emit('navigate-section', 'incidents');
}

function openSelectedResourceTopology(): void {
  const edge = selectedSignalEdge.value;
  if (edge) {
    selectedTopologyRootKey.value = edge.sourceKey;
    selectedEdgeKey.value = edgeKey(edge);
    selectedTopologyNodeKey.value = null;
  } else if (selectedSignal.value) {
    openServiceTopology(selectedSignal.value.serviceKey);
    return;
  }
  emit('navigate-section', 'topology');
}

function openSelectedResourceTrace(): void {
  if (selectedSignalFlow.value) {
    selectedFlowKey.value = selectedSignalFlow.value.traceKey;
  }
  emit('navigate-section', 'request-flows');
}

function openSelectedResourceService(): void {
  if (selectedSignal.value) {
    selectedServiceKey.value = selectedSignal.value.serviceKey;
  }
  emit('navigate-section', 'services');
}

function openSelectedResourcePolicy(): void {
  const signal = selectedSignal.value;
  const plan = signal
    ? visiblePolicyPlans.value.find(
        (item) => item.serviceKey === signal.serviceKey || item.resourceKey === signal.resourceKey || signal.resourceKey.includes(item.resourceKey),
      )
    : null;
  if (plan) {
    selectedPolicyPlanKey.value = plan.planKey;
  }
  emit('navigate-section', 'policies');
}

function openSelectedIncidentPolicy(): void {
  const incident = selectedIncident.value;
  const plan = incident
    ? visiblePolicyPlans.value.find(
        (item) =>
          item.serviceKey === incident.impactScope.serviceKey ||
          item.resourceKey === incident.primaryResourceKey ||
          incident.evidence.some((evidence) => evidence.resourceKey === item.resourceKey),
      )
    : null;
  if (plan) {
    selectedPolicyPlanKey.value = plan.planKey;
  }
  emit('navigate-section', 'policies');
}

function openSelectedRouteIncident(): void {
  if (selectedRouteIncident.value) {
    selectedIncidentKey.value = selectedRouteIncident.value.incidentKey;
  }
  emit('navigate-section', 'incidents');
}

function openSelectedPolicyIncident(): void {
  if (selectedPolicyIncident.value) {
    selectedIncidentKey.value = selectedPolicyIncident.value.incidentKey;
  }
  emit('navigate-section', 'incidents');
}

function openSelectedPolicyTopology(): void {
  openServiceTopology(selectedPolicyPlan.value?.serviceKey);
}

function openSelectedPolicyService(): void {
  if (selectedPolicyPlan.value) {
    selectedServiceKey.value = selectedPolicyPlan.value.serviceKey;
  }
  emit('navigate-section', 'services');
}

function spanDepth(span: SpanTreeInput, flow: FlowSpanInput): number {
  let depth = 0;
  let currentParent = span.parentSpanId;
  const seen = new Set<string>();
  while (currentParent && !seen.has(currentParent)) {
    seen.add(currentParent);
    const parent = flow.spans.find((item) => item.spanId === currentParent);
    if (!parent) {
      break;
    }
    depth += 1;
    currentParent = parent.parentSpanId;
  }
  return Math.min(depth, 6);
}

function spanRowStyle(span: SpanTreeInput, flow: FlowSpanInput): Record<string, string> {
  return {
    ...spanStyle(span, flow),
    '--depth': `${spanDepth(span, flow)}`,
  };
}

function spanSelfMs(span: SpanTreeInput, flow: FlowSpanInput): number {
  const childTotal = flow.spans
    .filter((item) => item.parentSpanId === span.spanId)
    .reduce((total, item) => total + item.durationMs, 0);
  return Math.max(0, span.durationMs - childTotal);
}

function spanExecPercent(span: SpanTreeInput, flow: FlowMetricInput): string {
  return `${Math.round((span.durationMs / Math.max(flow.durationMs, 1)) * 100)}%`;
}

function spanStats(flow: FlowSpanInput | null): SpanAggregate[] {
  if (!flow) {
    return [];
  }
  const aggregate = new Map<string, SpanAggregate>();
  flow.spans.forEach((span) => {
    const key = `${span.operation}:${span.component}`;
    const current =
      aggregate.get(key) ??
      {
        operation: span.operation,
        component: span.component,
        maxMs: span.durationMs,
        minMs: span.durationMs,
        sumMs: 0,
        avgMs: 0,
        hits: 0,
      };
    current.maxMs = Math.max(current.maxMs, span.durationMs);
    current.minMs = Math.min(current.minMs, span.durationMs);
    current.sumMs += span.durationMs;
    current.hits += 1;
    current.avgMs = Math.round(current.sumMs / current.hits);
    aggregate.set(key, current);
  });
  return Array.from(aggregate.values()).sort((left, right) => right.sumMs - left.sumMs).slice(0, 12);
}
</script>

<template>
  <LoadingBlock v-if="isLoading && !hasLoaded" :label="copy.loading" />
  <ErrorState v-else-if="errorMessage" :title="copy.errorTitle" :message="errorMessage" @retry="refreshPlatform" />
  <EmptyState v-else-if="hasLoaded && services.length === 0" :title="emptyStateTitle" :message="emptyStateMessage" />
  <div v-else class="ops-workbench">
    <section class="ops-data-mode-strip" :data-tone="sourceModeTone" aria-label="Platform data source mode">
      <div>
        <span>{{ copy.dataModeLabel }}</span>
        <strong>{{ sourceMode }}</strong>
      </div>
      <div>
        <span>{{ copy.generatedAt }}</span>
        <strong>{{ formattedGeneratedAt }}</strong>
      </div>
      <div>
        <span>{{ copy.lastSignal }}</span>
        <strong>{{ formattedLastSignalAt }}</strong>
      </div>
      <div>
        <span>{{ copy.connectorFreshness }}</span>
        <strong>{{ dataSourceCount }}</strong>
      </div>
      <p v-if="sourceWarningText">{{ copy.warnings }}: {{ sourceWarningText }}</p>
    </section>

    <section v-if="section !== 'topology'" class="ops-section-bar" aria-label="Page filters">
      <label>
        <span>{{ copy.zone }}</span>
        <select v-model="selectedZone">
          <option v-for="zone in zones" :key="zone" :value="zone">{{ zone === 'ALL' ? copy.allZones : zone }}</option>
        </select>
      </label>
      <label class="ops-search">
        <span>{{ copy.search }}</span>
        <input v-model="searchTerm" type="search" :placeholder="copy.search" />
      </label>
    </section>

    <section v-if="section === 'overview'" class="ops-summary-strip">
      <article :data-tone="stateTone(summary?.health)">
        <span>Health</span>
        <strong>{{ summary?.health ?? 'HEALTHY' }}</strong>
      </article>
      <article data-tone="critical">
        <span>{{ copy.incidents }}</span>
        <strong>{{ summary?.criticalIncidents ?? 0 }} / {{ summary?.warningIncidents ?? 0 }}</strong>
      </article>
      <article>
        <span>{{ copy.services }}</span>
        <strong>{{ summary?.serviceCount ?? services.length }}</strong>
      </article>
      <article>
        <span>{{ copy.middleware }}</span>
        <strong>{{ summary?.middlewareCount ?? middleware.length }}</strong>
      </article>
      <article>
        <span>{{ copy.requestFlows }}</span>
        <strong>{{ flows.length }}</strong>
      </article>
      <article>
        <span>{{ copy.integrations }}</span>
        <strong>{{ summary?.connectorCount ?? connectors.length }}</strong>
      </article>
    </section>

    <template v-if="section === 'overview'">
      <section class="ops-command-grid">
        <div class="ops-panel ops-panel--queue ops-command-queue">
          <header>
            <span>{{ copy.commandCenter }}</span>
            <strong>{{ visibleIncidents.length }}</strong>
          </header>
          <button
            v-for="incident in visibleIncidents"
            :key="incident.incidentKey"
            type="button"
            class="ops-list-button"
            :class="{ 'is-active': selectedIncident?.incidentKey === incident.incidentKey }"
            :data-tone="incidentTone(incident)"
            @click="selectedIncidentKey = incident.incidentKey"
          >
            <StatusBadge :label="incident.severity" :state="incident.severity" />
            <strong>{{ incident.title }}</strong>
            <small>{{ incident.impactScope.serviceKey }} / {{ incident.impactScope.zoneKey }}</small>
          </button>
        </div>

        <div class="ops-panel ops-incident-map">
          <header>
            <span>{{ copy.affectedPath }}</span>
            <strong>{{ selectedIncident?.primaryResourceKey ?? copy.noDirectWrite }}</strong>
          </header>
          <div class="incident-path">
            <button
              v-for="edge in selectedIncidentEdges"
              :key="edgeKey(edge)"
              type="button"
              class="incident-hop"
              :data-tone="edgeTone(edge)"
              :class="{ 'is-active': selectedEdge && edgeKey(edge) === edgeKey(selectedEdge) }"
              @click="selectedEdgeKey = edgeKey(edge)"
            >
              <span class="incident-hop__node">{{ edge.sourceKey }}</span>
              <span class="incident-hop__line">
                <i></i>
                <b>{{ edge.kind }}</b>
              </span>
              <span class="incident-hop__node">{{ edge.targetKey }}</span>
              <small>{{ copy.qps }} {{ edgeQps(edge) }} / {{ copy.failureRate }} {{ edgeFailure(edge) }} / {{ copy.p95p99 }} {{ edgeP95(edge) }} / {{ edgeP99(edge) }}</small>
            </button>
          </div>
          <div class="ops-risk-stream" aria-label="Risk stream">
            <header>
              <span>{{ copy.riskStream }}</span>
              <strong>{{ selectedIncidentTransactions.length }} {{ copy.transactions }}</strong>
            </header>
            <div class="risk-stream-grid">
              <article v-for="metric in selectedIncidentTransactions" :key="`${metric.serviceKey}-${metric.endpointKey}`">
                <strong>{{ metric.endpointKey }}</strong>
                <span>{{ metric.serviceKey }} / {{ copy.failureRate }} {{ percent(metric.failureRate) }}</span>
                <b>{{ copy.p95p99 }} {{ ms(metric.p95Ms) }} / {{ ms(metric.p99Ms) }}</b>
              </article>
            </div>
          </div>
        </div>

        <aside class="ops-panel ops-diagnosis ops-evidence-drawer">
          <header>
            <span>{{ copy.evidenceDrawer }}</span>
            <StatusBadge :label="selectedIncident?.severity ?? 'INFO'" :state="selectedIncident?.severity ?? 'INFO'" />
          </header>
          <h2>{{ selectedIncident?.title ?? copy.diagnosis }}</h2>
          <p>{{ selectedIncident?.suggestedCheck?.message ?? copy.noDirectWrite }}</p>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedIncident" @click="openIncidentTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="!selectedIncidentTrace" @click="openIncidentTrace">{{ copy.openTrace }}</button>
          </div>
          <dl v-if="selectedIncident">
            <div>
              <dt>{{ copy.impact }}</dt>
              <dd>{{ selectedIncident.impactScope.serviceKey }} / {{ selectedIncident.impactScope.clusterKey }} / {{ selectedIncident.impactScope.zoneKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.firstCheck }}</dt>
              <dd>{{ selectedIncident.primaryResourceKey }}</dd>
            </div>
            <div>
              <dt>{{ copy.evidence }}</dt>
              <dd>{{ selectedIncident.evidenceCount }}</dd>
            </div>
            <div>
              <dt>{{ copy.sourceFreshness }}</dt>
              <dd>{{ selectedIncident.lastSeenAt ?? '-' }}</dd>
            </div>
          </dl>
          <h3>{{ copy.supportingEvidence }}</h3>
          <div class="ref-list">
            <span v-for="item in selectedIncident?.evidence.slice(0, 4) ?? []" :key="`${item.timestamp}-${item.referenceKey}`">
              {{ item.referenceType }} / {{ item.referenceKey }} / {{ item.message }}
            </span>
          </div>
          <h3>{{ copy.relatedObjects }}</h3>
          <div class="ref-list">
            <span v-for="flow in selectedIncidentTraces" :key="flow.traceKey">
              {{ flow.status }} / {{ flow.endpointKey }} / {{ ms(flow.durationMs) }}
            </span>
          </div>
        </aside>
      </section>

      <section class="ops-four-grid">
        <div class="ops-panel">
          <header><span>{{ copy.serviceWaterline }}</span><strong>{{ serviceWatermarks.length }}</strong></header>
          <div class="mini-table">
            <button v-for="service in serviceWatermarks.slice(0, 6)" :key="service.serviceKey" type="button" @click="selectedServiceKey = service.serviceKey">
              <span>{{ service.name }}</span>
              <b>{{ percent(service.errorRate) }}</b>
              <em>{{ ms(service.p95Ms) }}</em>
            </button>
          </div>
        </div>
        <div class="ops-panel">
          <header><span>{{ copy.zoneWaterline }}</span><strong>{{ zoneWatermarks.length }}</strong></header>
          <div class="mini-table">
            <button v-for="zone in zoneWatermarks" :key="zone.zoneKey" type="button" @click="selectedZone = zone.zoneKey">
              <span>{{ zone.zoneKey }}</span>
              <b>{{ wholePercent(zone.memoryPercent) }}</b>
              <em>{{ ms(zone.networkJitterMs) }}</em>
            </button>
          </div>
        </div>
        <div class="ops-panel">
          <header><span>{{ copy.middlewareWaterline }}</span><strong>{{ middleware.length }}</strong></header>
          <div class="mini-table">
            <button v-for="item in middleware" :key="item.middlewareKey" type="button">
              <span>{{ item.name }}</span>
              <b>{{ wholePercent(item.usagePercent) }}</b>
              <em>{{ ms(item.latencyMs) }}</em>
            </button>
          </div>
        </div>
        <div class="ops-panel">
          <header><span>{{ copy.hosts }}</span><strong>{{ criticalHosts.length }}</strong></header>
          <div class="mini-table">
            <button v-for="host in hosts.slice(0, 6)" :key="host.hostKey" type="button">
              <span>{{ host.hostKey }}</span>
              <b>{{ wholePercent(host.swapPercent) }}</b>
              <em>{{ host.lastError }}</em>
            </button>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="section === 'topology'">
      <section class="ops-panel topology-focus-page">
        <div class="topology-floating-controls">
          <label>
            <span>{{ copy.topologyService }}</span>
            <select v-model="selectedTopologyRootKey">
              <option :value="null">{{ copy.entryTopology }}</option>
              <option v-for="service in topologyServices" :key="service.serviceKey" :value="service.serviceKey">
                {{ service.name }} / {{ service.zoneKey }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ copy.currentDepth }}</span>
            <select v-model="topologyDepth">
              <option value="1">1</option>
              <option value="2">2</option>
              <option value="3">3</option>
              <option value="ALL">All</option>
            </select>
          </label>
          <button type="button" @click="focusHighestRiskEdge">{{ copy.focusRisk }}</button>
        </div>

        <div class="topology-floating-legend" aria-label="Topology legend">
          <span data-tone="entry">{{ copy.entryLink }}</span>
          <span data-tone="healthy">{{ copy.healthyLink }}</span>
          <span data-tone="warning">{{ copy.slowLink }}</span>
          <span data-tone="critical">{{ copy.failedLink }}</span>
          <span data-tone="jitter">{{ copy.jitterLink }}</span>
        </div>

        <TopologyGraph
          :nodes="topologyNodes"
          :edges="topologyGraphEdges"
          :selected-edge-key="selectedTopologyEdge ? edgeKey(selectedTopologyEdge) : null"
          :selected-node-key="selectedTopologyNodeKey"
          @select-edge="selectedEdgeKey = $event"
          @select-node="selectTopologyNodeByKey"
        />

        <aside class="topology-floating-inspector ops-evidence-drawer" v-if="selectedTopologyEdge || selectedTopologyNode">
          <header>
            <span>{{ selectedTopologyEdge ? copy.selectedEdge : copy.selectedNode }}</span>
            <StatusBadge
              :label="selectedTopologyEdge ? selectedTopologyEdgeStatus : (selectedTopologyNode?.tone ?? 'healthy').toUpperCase()"
              :state="selectedTopologyEdge ? selectedTopologyEdgeStatus : (selectedTopologyNode?.tone ?? 'healthy').toUpperCase()"
            />
          </header>
          <h2 v-if="selectedTopologyEdge">{{ topologyEdgeDisplayName(selectedTopologyEdge) }}</h2>
          <h2 v-else>{{ selectedTopologyNode?.label ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedTopologyEdge" @click="openSelectedTopologyTrace">{{ copy.openTrace }}</button>
            <button type="button" :disabled="!selectedTopologyServiceKey" @click="openSelectedTopologyService">{{ copy.openService }}</button>
          </div>
          <dl v-if="selectedTopologyEdge">
            <div><dt>{{ copy.resource }}</dt><dd>{{ selectedTopologyEdge.resourceKey }}</dd></div>
            <div><dt>{{ copy.qps }}</dt><dd>{{ edgeQps(selectedTopologyEdge) }}</dd></div>
            <div><dt>{{ copy.failureRate }}</dt><dd>{{ edgeFailure(selectedTopologyEdge) }}</dd></div>
            <div><dt>{{ copy.p95p99 }}</dt><dd>{{ edgeP95(selectedTopologyEdge) }} / {{ edgeP99(selectedTopologyEdge) }}</dd></div>
          </dl>
          <dl v-else>
            <div><dt>{{ copy.kind }}</dt><dd>{{ selectedTopologyNode?.kind ?? '-' }}</dd></div>
            <div><dt>{{ copy.zone }}</dt><dd>{{ selectedTopologyNode?.zoneKey ?? '-' }}</dd></div>
            <div><dt>{{ copy.impact }}</dt><dd>{{ selectedTopologyNode?.subLabel ?? '-' }}</dd></div>
            <div><dt>{{ copy.hosts }}</dt><dd>{{ selectedTopologyNodeHosts.length }}</dd></div>
          </dl>
          <div class="topology-node-hosts" v-if="!selectedTopologyEdge && selectedTopologyNodeHosts.length">
            <span v-for="host in selectedTopologyNodeHosts.slice(0, 3)" :key="host.hostKey" :data-tone="stateTone(host.state)">
              <b>{{ host.hostKey }}</b>
              <small>{{ wholePercent(host.cpuPercent) }} CPU · {{ wholePercent(host.swapPercent) }} Swap · {{ host.lastError }}</small>
            </span>
          </div>
          <h3 v-if="selectedTopologyEdge">{{ copy.supportingEvidence }}</h3>
          <div v-if="selectedTopologyEdge" class="ref-list">
            <span>{{ copy.dataSource }} {{ sourceMode }} / {{ selectedTopologyEdge.kind }} / {{ selectedTopologyEdge.resourceKey }}</span>
            <span>{{ copy.sourceFreshness }} {{ freshness?.lastSignalAt ?? '-' }} / {{ copy.p95p99 }} {{ edgeP95(selectedTopologyEdge) }} / {{ edgeP99(selectedTopologyEdge) }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'request-flows'">
      <section class="ops-flow-layout">
        <aside class="ops-panel ops-flow-list">
          <header>
            <span>{{ copy.traceSamples }} <small class="trace-query-state">{{ traceQueryStatusText }}</small></span>
            <strong>{{ flowTotalCount }}</strong>
          </header>
          <div class="trace-filter-grid">
            <label>
              <span>{{ copy.serviceFilter }}</span>
              <select v-model="selectedFlowServiceKey" :aria-label="copy.serviceFilter">
                <option value="ALL">{{ copy.allServices }}</option>
                <option v-for="service in traceServiceOptions" :key="service.serviceKey" :value="service.serviceKey">
                  {{ service.name }}
                </option>
              </select>
            </label>
            <label>
              <span>{{ copy.flowStatusFilter }}</span>
              <select v-model="selectedFlowStatus" :aria-label="copy.flowStatusFilter">
                <option value="ALL">{{ copy.allStatuses }}</option>
                <option value="ERROR">ERROR</option>
                <option value="SLOW">SLOW</option>
                <option value="OK">OK</option>
              </select>
            </label>
            <label>
              <span>{{ copy.minDuration }}</span>
              <input v-model="flowMinDurationMs" type="number" min="0" step="50" placeholder="0" :aria-label="copy.minDuration" />
            </label>
            <label>
              <span>{{ copy.resourceFilter }}</span>
              <input v-model="flowResourceFilter" type="search" :placeholder="copy.resourceFilterPlaceholder" :aria-label="copy.resourceFilter" />
            </label>
            <label>
              <span>{{ copy.sourceFilter }}</span>
              <select v-model="selectedFlowSource" :aria-label="copy.sourceFilter">
                <option value="ALL">{{ copy.allSources }}</option>
                <option v-for="source in traceSourceOptions" :key="source" :value="source">{{ source }}</option>
              </select>
            </label>
            <label>
              <span>{{ copy.flowSort }}</span>
              <select v-model="selectedFlowSort" :aria-label="copy.flowSort">
                <option value="risk">{{ copy.riskFirst }}</option>
                <option value="duration_desc">{{ copy.durationDesc }}</option>
                <option value="time_desc">{{ copy.timeDesc }}</option>
              </select>
            </label>
          </div>
          <button
            v-for="flow in pagedFlows"
            :key="flow.traceKey"
            type="button"
            class="ops-list-button"
            :class="{ 'is-active': selectedFlow?.traceKey === flow.traceKey }"
            :data-tone="flowTone(flow)"
            @click="selectedFlowKey = flow.traceKey"
          >
            <strong>{{ flow.endpointKey }}</strong>
            <small>{{ flow.entryServiceKey }} / {{ flow.primaryError }} / {{ ms(flow.durationMs) }}</small>
          </button>
          <div class="trace-pagination">
            <button type="button" :disabled="flowPage === 0" @click="flowPage -= 1">{{ copy.previousPage }}</button>
            <span>{{ copy.page }} {{ flowPage + 1 }} / {{ flowPageCount }}</span>
            <button type="button" :disabled="flowPage >= flowPageCount - 1" @click="flowPage += 1">{{ copy.nextPage }}</button>
          </div>
        </aside>
        <main class="ops-panel span-panel trace-workbench">
          <header>
            <span>{{ selectedFlow?.endpointKey ?? copy.requestFlows }}</span>
            <strong>{{ selectedFlow ? `${copy.duration} ${ms(selectedFlow.durationMs)} / ${copy.spans} ${selectedFlow.spanCount}` : '-' }}</strong>
          </header>
          <div v-if="selectedFlow" class="trace-toolbar">
            <div class="trace-meta">
              <StatusBadge :label="selectedFlow.status" :state="selectedFlow.status" />
              <span>{{ selectedFlow.startedAt ?? '-' }}</span>
              <span>{{ selectedFlow.traceKey }}</span>
            </div>
            <div class="trace-view-tabs" aria-label="Request flow view">
              <button
                v-for="mode in flowViewModes"
                :key="mode.id"
                type="button"
                :class="{ 'is-active': selectedFlowView === mode.id }"
                @click="selectedFlowView = mode.id"
              >
                {{ mode.label }}
              </button>
            </div>
          </div>
          <div v-if="selectedFlow && selectedFlowView === 'list'" class="span-waterfall trace-list-view">
            <article
              v-for="span in selectedFlow.spans"
              :key="span.spanId"
              :data-tone="stateTone(span.status)"
              :style="spanRowStyle(span, selectedFlow)"
            >
              <div class="span-title">
                <strong>{{ span.operation }}</strong>
                <small>{{ span.serviceKey }} / {{ span.component }} / {{ span.errorType }}</small>
              </div>
              <span class="duration-line"></span>
              <b>{{ ms(span.durationMs) }}</b>
            </article>
          </div>
          <div v-else-if="selectedFlow && selectedFlowView === 'tree'" class="trace-tree-view">
            <article
              v-for="span in selectedFlow.spans"
              :key="`tree-${span.spanId}`"
              :data-tone="stateTone(span.status)"
              :style="spanRowStyle(span, selectedFlow)"
            >
              <span class="tree-branch"></span>
              <div>
                <strong>{{ span.operation }}</strong>
                <small>{{ span.component }} / {{ span.serviceKey }}</small>
              </div>
              <b>{{ ms(span.durationMs) }}</b>
            </article>
          </div>
          <div v-else-if="selectedFlow && selectedFlowView === 'table'" class="trace-table-view">
            <div class="trace-table-head">
              <span>Method</span><span>{{ copy.exec }}</span><span>Exec(%)</span><span>{{ copy.self }}</span><span>API</span><span>Service</span><span>{{ copy.attachedEvents }}</span>
            </div>
            <div
              v-for="span in selectedFlow.spans"
              :key="`table-${span.spanId}`"
              class="trace-table-row"
              :data-tone="stateTone(span.status)"
            >
              <span>{{ span.operation }}</span>
              <b>{{ ms(span.durationMs) }}</b>
              <span>{{ spanExecPercent(span, selectedFlow) }}</span>
              <span>{{ ms(spanSelfMs(span, selectedFlow)) }}</span>
              <span>{{ span.component }}</span>
              <span>{{ span.serviceKey }}</span>
              <span>{{ span.evidenceRefs.length }}</span>
            </div>
          </div>
          <div v-else-if="selectedFlow" class="trace-stats-view">
            <div class="trace-table-head">
              <span>{{ copy.endpoint }}</span><span>{{ copy.component }}</span><span>Max</span><span>Min</span><span>Sum</span><span>Avg</span><span>{{ copy.hits }}</span>
            </div>
            <div v-for="stat in spanStats(selectedFlow)" :key="`${stat.operation}-${stat.component}`" class="trace-table-row">
              <span>{{ stat.operation }}</span>
              <span>{{ stat.component }}</span>
              <b>{{ ms(stat.maxMs) }}</b>
              <span>{{ ms(stat.minMs) }}</span>
              <span>{{ ms(stat.sumMs) }}</span>
              <span>{{ ms(stat.avgMs) }}</span>
              <span>{{ stat.hits }}</span>
            </div>
          </div>
        </main>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.transactions }}</span><StatusBadge :label="selectedFlow?.status ?? 'OK'" :state="selectedFlow?.status ?? 'OK'" /></header>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedFlowEdge" @click="openSelectedFlowTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="!selectedFlow" @click="openSelectedFlowService">{{ copy.openService }}</button>
          </div>
          <dl v-if="txForFlow(selectedFlow)">
            <div><dt>{{ copy.total }}</dt><dd>{{ txForFlow(selectedFlow)?.total }}</dd></div>
            <div><dt>{{ copy.failure }}</dt><dd>{{ txForFlow(selectedFlow)?.failure }} / {{ percent(txForFlow(selectedFlow)?.failureRate ?? 0) }}</dd></div>
            <div><dt>{{ copy.p95p99 }}</dt><dd>{{ ms(txForFlow(selectedFlow)?.p95Ms ?? 0) }} / {{ ms(txForFlow(selectedFlow)?.p99Ms ?? 0) }}</dd></div>
            <div><dt>{{ copy.sample }}</dt><dd>{{ txForFlow(selectedFlow)?.sampleTraceKey || selectedFlow?.traceKey }}</dd></div>
            <div><dt>{{ copy.relatedObjects }}</dt><dd>{{ selectedFlowEdge ? topologyEdgeDisplayName(selectedFlowEdge) : '-' }}</dd></div>
          </dl>
          <h3>{{ copy.refs }}</h3>
          <div class="ref-list">
            <span v-for="ref in selectedFlow?.evidenceRefs ?? []" :key="`${ref.type}-${ref.refKey}`">{{ ref.type }} / {{ ref.refKey }}</span>
          </div>
          <h3>{{ copy.queryContext }}</h3>
          <div class="ref-list">
            <span v-for="item in traceQueryContextRows" :key="item.label">{{ item.label }} / {{ item.value }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'incidents'">
      <section class="ops-two-column">
        <div class="ops-panel">
          <header><span>{{ copy.incidents }}</span><strong>{{ visibleIncidents.length }}</strong></header>
          <button
            v-for="incident in visibleIncidents"
            :key="incident.incidentKey"
            type="button"
            class="ops-list-button"
            :data-tone="incidentTone(incident)"
            :class="{ 'is-active': selectedIncident?.incidentKey === incident.incidentKey }"
            @click="selectedIncidentKey = incident.incidentKey"
          >
            <StatusBadge :label="incident.severity" :state="incident.severity" />
            <strong>{{ incident.title }}</strong>
            <small>{{ incident.primaryResourceKey }} / {{ incident.evidenceCount }} {{ copy.evidence }}</small>
          </button>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.evidence }}</span><StatusBadge :label="selectedIncident?.severity ?? 'INFO'" :state="selectedIncident?.severity ?? 'INFO'" /></header>
          <h2>{{ selectedIncident?.title ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedIncident" @click="openIncidentTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="!selectedIncidentTrace" @click="openIncidentTrace">{{ copy.openTrace }}</button>
            <button type="button" :disabled="!selectedIncident" @click="openSelectedIncidentService">{{ copy.openService }}</button>
            <button type="button" :disabled="!selectedIncident" @click="openSelectedIncidentPolicy">{{ copy.openPolicies }}</button>
          </div>
          <dl v-if="selectedIncident">
            <div><dt>{{ copy.impact }}</dt><dd>{{ selectedIncident.impactScope.serviceKey }} / {{ selectedIncident.impactScope.zoneKey }}</dd></div>
            <div><dt>{{ copy.firstCheck }}</dt><dd>{{ selectedIncident.suggestedCheck?.resourceKey ?? selectedIncident.primaryResourceKey }}</dd></div>
            <div><dt>{{ copy.sourceFreshness }}</dt><dd>{{ selectedIncident.lastSeenAt ?? selectedIncident.startedAt ?? '-' }}</dd></div>
            <div><dt>{{ copy.relatedObjects }}</dt><dd>{{ selectedIncident.impactedResourceCount }} {{ copy.resources }} / {{ selectedIncident.evidenceCount }} {{ copy.evidence }}</dd></div>
          </dl>
          <h3>{{ copy.relatedTraces }}</h3>
          <div class="ref-list">
            <span v-for="flow in selectedIncidentTraces" :key="flow.traceKey">{{ flow.status }} / {{ flow.endpointKey }} / {{ ms(flow.durationMs) }}</span>
          </div>
          <ol class="timeline-list">
            <li v-for="item in selectedIncident?.evidence ?? []" :key="`${item.timestamp}-${item.resourceKey}-${item.signalType}`">
              <strong>{{ item.signalType }}</strong>
              <span>{{ item.resourceKey }} / {{ item.outcome }} / {{ item.durationBucket }}</span>
              <small>{{ item.referenceType }} / {{ item.referenceKey }}</small>
            </li>
          </ol>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'services'">
      <section class="ops-two-column ops-two-column--wide">
        <div class="ops-panel">
          <header><span>{{ copy.services }}</span><strong>{{ visibleServices.length }}</strong></header>
          <div class="ops-table">
            <button v-for="service in visibleServices" :key="service.serviceKey" type="button" :class="{ 'is-active': selectedService?.serviceKey === service.serviceKey }" @click="selectedServiceKey = service.serviceKey">
              <span><strong>{{ service.name }}</strong><small>{{ service.clusterKey }} / {{ service.zoneKey }}</small></span>
              <b>{{ service.warningCount }} / {{ service.criticalCount }}</b>
              <em>{{ service.attributes.qps ?? '-' }} qps</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ selectedService?.serviceKey ?? '-' }}</span><StatusBadge :label="serviceStatusLabel(selectedService)" :state="serviceStatusLabel(selectedService)" /></header>
          <h2>{{ selectedService?.name ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedService" @click="openSelectedServiceTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="selectedServiceTraces.length === 0" @click="openSelectedServiceTrace">{{ copy.openTrace }}</button>
            <button type="button" :disabled="selectedServiceHosts.length === 0" @click="openSelectedServiceHosts">{{ copy.openHosts }}</button>
            <button type="button" :disabled="!selectedService" @click="openSelectedServiceResources">{{ copy.openResources }}</button>
          </div>
          <dl v-if="selectedService">
            <div><dt>{{ copy.qps }}</dt><dd>{{ selectedService.attributes.qps ?? '-' }}</dd></div>
            <div><dt>{{ copy.failureRate }}</dt><dd>{{ selectedService.attributes.errorRate ?? '-' }}</dd></div>
            <div><dt>{{ copy.p95p99 }}</dt><dd>{{ selectedService.attributes.p95Ms ?? '-' }} / {{ selectedService.attributes.p99Ms ?? '-' }}ms</dd></div>
            <div><dt>{{ copy.hosts }}</dt><dd>{{ selectedServiceHosts.length }}</dd></div>
          </dl>
          <h3>{{ copy.topology }}</h3>
          <div class="ref-list">
            <span v-for="edge in selectedServiceEdges" :key="edgeKey(edge)">{{ edge.kind }} / {{ topologyEdgeDisplayName(edge) }} / {{ edge.resourceKey }}</span>
          </div>
          <h3>{{ copy.hosts }}</h3>
          <div class="ref-list">
            <span v-for="host in selectedServiceHosts" :key="host.hostKey">{{ host.hostKey }} / {{ host.state }} / {{ wholePercent(host.cpuPercent) }} CPU</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'hosts'">
      <section class="ops-two-column ops-two-column--wide">
        <div class="ops-panel">
          <header><span>{{ copy.hosts }}</span><strong>{{ visibleHosts.length }}</strong></header>
          <div class="ops-table">
            <button v-for="host in visibleHosts" :key="host.hostKey" type="button" :data-tone="stateTone(host.state)" :class="{ 'is-active': selectedHost?.hostKey === host.hostKey }" @click="selectedHostKey = host.hostKey">
              <span><strong>{{ host.hostKey }}</strong><small>{{ host.serviceKey }} / {{ host.zoneKey }}</small></span>
              <b>{{ wholePercent(host.cpuPercent) }} CPU</b>
              <em>{{ host.lastError || host.state }}</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.selectedHost }}</span><StatusBadge :label="selectedHost?.state ?? 'INFO'" :state="selectedHost?.state ?? 'INFO'" /></header>
          <h2>{{ selectedHost?.hostKey ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedHost" @click="openSelectedHostTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="selectedHostTraces.length === 0" @click="openSelectedHostTrace">{{ copy.openTrace }}</button>
            <button type="button" :disabled="!selectedHostService" @click="openSelectedHostService">{{ copy.openService }}</button>
            <button type="button" :disabled="selectedHostSignals.length === 0" @click="openSelectedHostResources">{{ copy.openResources }}</button>
          </div>
          <dl v-if="selectedHost">
            <div><dt>{{ copy.service }}</dt><dd>{{ selectedHost.serviceKey }}</dd></div>
            <div><dt>{{ copy.cluster }}</dt><dd>{{ selectedHost.clusterKey }} / {{ selectedHost.zoneKey }}</dd></div>
            <div><dt>{{ copy.cpu }}</dt><dd>{{ wholePercent(selectedHost.cpuPercent) }}</dd></div>
            <div><dt>{{ copy.memory }}</dt><dd>{{ wholePercent(selectedHost.memoryPercent) }}</dd></div>
            <div><dt>{{ copy.swap }}</dt><dd>{{ wholePercent(selectedHost.swapPercent) }}</dd></div>
            <div><dt>{{ copy.diskIo }}</dt><dd>{{ wholePercent(selectedHost.diskIoPercent) }}</dd></div>
            <div><dt>{{ copy.jitter }}</dt><dd>{{ ms(selectedHost.networkJitterMs) }}</dd></div>
            <div><dt>{{ copy.loss }}</dt><dd>{{ wholePercent(selectedHost.packetLossPercent) }}</dd></div>
            <div><dt>{{ copy.threads }}</dt><dd>{{ selectedHost.jvmThreadCount }}</dd></div>
            <div><dt>{{ copy.lastSeen }}</dt><dd>{{ selectedHost.lastSeenAt ?? '-' }}</dd></div>
          </dl>
          <h3>{{ copy.relatedSignals }}</h3>
          <div class="ref-list">
            <span v-for="signal in selectedHostSignals" :key="signalKey(signal)">{{ signal.severity }} / {{ signal.signalType }} / {{ signal.resourceKey }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'middleware'">
      <section class="ops-two-column ops-two-column--wide">
        <div class="ops-panel">
          <header><span>{{ copy.middleware }}</span><strong>{{ visibleMiddleware.length }}</strong></header>
          <div class="ops-table">
            <button v-for="item in visibleMiddleware" :key="item.middlewareKey" type="button" :data-tone="stateTone(item.state)" :class="{ 'is-active': selectedMiddleware?.middlewareKey === item.middlewareKey }" @click="selectedMiddlewareKey = item.middlewareKey">
              <span><strong>{{ item.name }}</strong><small>{{ item.kind }} / {{ item.zoneKey }}</small></span>
              <b>{{ wholePercent(item.usagePercent) }}</b>
              <em>{{ ms(item.latencyMs) }} / {{ percent(item.errorRate) }}</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.selectedMiddleware }}</span><StatusBadge :label="selectedMiddleware?.state ?? 'INFO'" :state="selectedMiddleware?.state ?? 'INFO'" /></header>
          <h2>{{ selectedMiddleware?.name ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedMiddleware" @click="openSelectedMiddlewareTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="!selectedMiddleware" @click="openSelectedMiddlewareTrace">{{ copy.openTrace }}</button>
            <button type="button" :disabled="selectedMiddlewareSignals.length === 0" @click="openSelectedMiddlewareResources">{{ copy.openResources }}</button>
            <button type="button" :disabled="!selectedMiddleware" @click="openSelectedMiddlewareIncident">{{ copy.openIncident }}</button>
          </div>
          <dl v-if="selectedMiddleware">
            <div><dt>{{ copy.kind }}</dt><dd>{{ selectedMiddleware.kind }}</dd></div>
            <div><dt>{{ copy.zone }}</dt><dd>{{ selectedMiddleware.zoneKey }}</dd></div>
            <div><dt>{{ copy.usage }}</dt><dd>{{ wholePercent(selectedMiddleware.usagePercent) }}</dd></div>
            <div><dt>{{ copy.latency }}</dt><dd>{{ ms(selectedMiddleware.latencyMs) }}</dd></div>
            <div><dt>{{ copy.failureRate }}</dt><dd>{{ percent(selectedMiddleware.errorRate) }}</dd></div>
            <div><dt>{{ copy.callers }}</dt><dd>{{ selectedMiddleware.connectedServices }}</dd></div>
          </dl>
          <h3>{{ copy.affectedPath }}</h3>
          <div class="ref-list">
            <span v-for="edge in selectedMiddlewareEdges" :key="edgeKey(edge)">{{ topologyEdgeDisplayName(edge) }} / {{ edge.resourceKey }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'resources'">
      <section class="ops-two-column">
        <div class="ops-panel">
          <header><span>{{ copy.resources }}</span><strong>{{ visibleSignals.length }}</strong></header>
          <div class="ops-table">
            <button v-for="signal in visibleSignals" :key="signalKey(signal)" type="button" :data-tone="incidentTone({ severity: signal.severity })" :class="{ 'is-active': selectedSignal && signalKey(selectedSignal) === signalKey(signal) }" @click="selectedSignalKey = signalKey(signal)">
              <span><strong>{{ signal.resourceKey }}</strong><small>{{ signal.serviceKey }} / {{ signal.signalType }}</small></span>
              <b>{{ signal.severity }}</b>
              <em>{{ signal.durationBucket }}</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.selectedResource }}</span><StatusBadge :label="selectedSignal?.severity ?? 'INFO'" :state="selectedSignal?.severity ?? 'INFO'" /></header>
          <h2>{{ selectedSignal?.resourceKey ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedSignalEdge" @click="openSelectedResourceTopology">{{ copy.openTopology }}</button>
            <button type="button" :disabled="!selectedSignalFlow" @click="openSelectedResourceTrace">{{ copy.openTrace }}</button>
            <button type="button" :disabled="!selectedSignal" @click="openSelectedResourceService">{{ copy.openService }}</button>
            <button type="button" :disabled="!selectedSignal" @click="openSelectedResourcePolicy">{{ copy.openPolicies }}</button>
          </div>
          <dl v-if="selectedSignal">
            <div><dt>{{ copy.service }}</dt><dd>{{ selectedSignal.serviceKey }}</dd></div>
            <div><dt>{{ copy.signalType }}</dt><dd>{{ selectedSignal.signalType }}</dd></div>
            <div><dt>{{ copy.outcome }}</dt><dd>{{ selectedSignal.outcome }}</dd></div>
            <div><dt>{{ copy.durationBucket }}</dt><dd>{{ selectedSignal.durationBucket }}</dd></div>
            <div><dt>{{ copy.cluster }}</dt><dd>{{ selectedSignal.clusterKey }} / {{ selectedSignal.zoneKey }}</dd></div>
            <div><dt>{{ copy.observedAt }}</dt><dd>{{ selectedSignal.timestamp ?? '-' }}</dd></div>
          </dl>
          <h3>{{ copy.relatedObjects }}</h3>
          <div class="ref-list">
            <span>{{ selectedSignalEdge ? topologyEdgeDisplayName(selectedSignalEdge) : copy.noDirectWrite }}</span>
            <span>{{ selectedSignalFlow ? `${selectedSignalFlow.traceKey} / ${ms(selectedSignalFlow.durationMs)}` : copy.localIncidentNote }}</span>
          </div>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'integrations'">
      <section class="ops-three-column ops-integrations-workbench">
        <div class="ops-panel">
          <header><span>{{ copy.integrations }}</span><strong>{{ visibleConnectorConfigs.length }}</strong></header>
          <div class="ops-table">
            <button v-for="config in visibleConnectorConfigs" :key="config.connectorKey" type="button" :data-tone="stateTone(config.state)" :class="{ 'is-active': selectedConnectorConfig?.connectorKey === config.connectorKey }" @click="selectedConnectorKey = config.connectorKey">
              <span><strong>{{ config.displayName }}</strong><small>{{ config.kind }} / {{ config.connectorKey }}</small></span>
              <b>{{ config.state }}</b>
              <em>{{ config.accessMode }} / write disabled</em>
            </button>
          </div>
          <h3>Discovered sources</h3>
          <div class="ref-list">
            <span v-for="source in visibleDataSources" :key="source.sourceKey">{{ source.kind }} / {{ source.displayName }} / {{ source.state }}</span>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>Connector setup</span><StatusBadge :label="selectedConnectorConfig?.state ?? 'DISABLED'" :state="selectedConnectorConfig?.state ?? 'DISABLED'" /></header>
          <div class="connector-form-grid">
            <label>
              <span>Kind</span>
              <select v-model="connectorForm.kind" :disabled="connectorActionLoading">
                <option value="SKYWALKING">SkyWalking APM / alert</option>
                <option value="GATEWAY">Spring Cloud Gateway</option>
                <option value="SENTINEL">Sentinel</option>
                <option value="PROMETHEUS">Prometheus</option>
                <option value="MICROMETER">Micrometer</option>
                <option value="ALERTMANAGER">Alertmanager</option>
                <option value="FEISHU">Feishu</option>
                <option value="DINGTALK">DingTalk</option>
              </select>
            </label>
            <label><span>Connector key</span><input v-model="connectorForm.connectorKey" :disabled="connectorActionLoading" placeholder="skywalking-readonly" /></label>
            <label><span>Name</span><input v-model="connectorForm.displayName" :disabled="connectorActionLoading" placeholder="SkyWalking APM" /></label>
            <label><span>Endpoint</span><input v-model="connectorForm.endpoint" :disabled="connectorActionLoading" placeholder="http://127.0.0.1:18097/graphql" /></label>
            <label>
              <span>Auth</span>
              <select v-model="connectorForm.authMode" :disabled="connectorActionLoading">
                <option value="NONE">NONE</option>
                <option value="BASIC">BASIC</option>
                <option value="BEARER_TOKEN">BEARER_TOKEN</option>
                <option value="WEBHOOK_SECRET">WEBHOOK_SECRET</option>
                <option value="ACTUATOR_BASIC">ACTUATOR_BASIC</option>
              </select>
            </label>
            <label>
              <span>Mode</span>
              <select v-model="connectorForm.accessMode" :disabled="connectorActionLoading">
                <option value="READ_ONLY">READ_ONLY</option>
                <option value="DRY_RUN">DRY_RUN</option>
                <option value="TEST">TEST</option>
                <option value="DISABLED">DISABLED</option>
              </select>
            </label>
            <label><span>Team</span><input v-model="connectorForm.targetTeam" :disabled="connectorActionLoading" placeholder="platform-team" /></label>
            <label class="inline-check"><input v-model="connectorForm.testEnabled" type="checkbox" :disabled="connectorActionLoading" /><span>Enable TEST / DRY-RUN delivery</span></label>
          </div>
          <div class="drawer-actions">
            <button type="button" :disabled="connectorActionLoading" @click="saveCurrentConnectorConfig">Save local config</button>
            <button type="button" :disabled="connectorActionLoading || !selectedConnectorConfig" @click="testSelectedConnector">Test connector</button>
          </div>
          <p v-if="connectorActionError" class="inline-warning">{{ connectorActionError }}</p>
          <dl v-if="selectedConnectorConfig">
            <div><dt>{{ copy.kind }}</dt><dd>{{ selectedConnectorConfig.kind }}</dd></div>
            <div><dt>{{ copy.state }}</dt><dd>{{ selectedConnectorConfig.state }}</dd></div>
            <div><dt>{{ copy.connectorMode }}</dt><dd>{{ selectedConnectorConfig.accessMode }}</dd></div>
            <div><dt>{{ copy.affectedViews }}</dt><dd>{{ selectedConnectorConfig.capabilities.join(' / ') }}</dd></div>
            <div><dt>{{ copy.lastSeen }}</dt><dd>{{ formatTimestamp(selectedConnectorConfig.updatedAt) }}</dd></div>
          </dl>
          <h3>{{ copy.readOnlyBoundary }}</h3>
          <p>{{ copy.noDirectWrite }}</p>
          <template v-if="latestConnectorTest">
            <h3>Last connector test</h3>
            <dl>
              <div><dt>{{ copy.status }}</dt><dd>{{ latestConnectorTest.status }}</dd></div>
              <div><dt>{{ copy.state }}</dt><dd>{{ latestConnectorTest.accepted ? copy.enabled : copy.unavailable }}</dd></div>
              <div><dt>{{ copy.observedAt }}</dt><dd>{{ formatTimestamp(latestConnectorTest.testedAt) }}</dd></div>
            </dl>
            <p>{{ latestConnectorTest.message }}</p>
          </template>
        </aside>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>Service mapping</span><strong>{{ visibleServiceMappings.length }}</strong></header>
          <div class="connector-form-grid">
            <label>
              <span>Service</span>
              <select v-model="mappingForm.serviceKey" :disabled="connectorActionLoading">
                <option value="">Select service</option>
                <option v-for="service in services" :key="service.serviceKey" :value="service.serviceKey">{{ service.name }}</option>
              </select>
            </label>
            <label>
              <span>Connector</span>
              <select v-model="mappingForm.connectorKey" :disabled="connectorActionLoading">
                <option v-for="config in connectorConfigs" :key="config.connectorKey" :value="config.connectorKey">{{ config.displayName }}</option>
              </select>
            </label>
            <label><span>External key</span><input v-model="mappingForm.externalKey" :disabled="connectorActionLoading" placeholder="nexary-governance-console" /></label>
            <label>
              <span>Resource kind</span>
              <select v-model="mappingForm.resourceKind" :disabled="connectorActionLoading">
                <option value="service">service</option>
                <option value="route">route</option>
                <option value="resource">resource</option>
                <option value="alert">alert</option>
              </select>
            </label>
            <label><span>Confidence</span><input v-model.number="mappingForm.confidence" type="number" min="0" max="1" step="0.1" :disabled="connectorActionLoading" /></label>
          </div>
          <div class="drawer-actions">
            <button type="button" :disabled="connectorActionLoading || !mappingForm.serviceKey || !mappingForm.connectorKey || !mappingForm.externalKey" @click="saveCurrentServiceMapping">Save mapping</button>
          </div>
          <h3>Mappings</h3>
          <div class="ref-list">
            <span v-for="mapping in visibleServiceMappings" :key="mapping.mappingKey">{{ mapping.serviceKey }} / {{ mapping.sourceKind }} / {{ mapping.externalKey }} / {{ Math.round(mapping.confidence * 100) }}%</span>
          </div>
          <h3>{{ copy.degradedScope }}</h3>
          <p>{{ connectorImpactText(selectedDataSource) }}</p>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'notifications'">
      <section class="ops-two-column">
        <div class="ops-panel">
          <header><span>{{ copy.notifications }}</span><strong>{{ visibleNotificationRoutes.length }}</strong></header>
          <div class="ops-table">
            <button v-for="route in visibleNotificationRoutes" :key="route.routeKey" type="button" :data-tone="stateTone(route.state)" :class="{ 'is-active': selectedNotificationRoute?.routeKey === route.routeKey }" @click="selectedNotificationRouteKey = route.routeKey">
              <span><strong>{{ route.displayName }}</strong><small>{{ route.channel }} / {{ route.targetTeam }}</small></span>
              <b>{{ route.minSeverity }}</b>
              <em>{{ route.dryRun ? copy.dryRun : copy.enabled }} / {{ route.boundIncidentCount }} {{ copy.bound }}</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.selectedRoute }}</span><StatusBadge :label="selectedNotificationRoute?.state ?? 'INFO'" :state="selectedNotificationRoute?.state ?? 'INFO'" /></header>
          <h2>{{ selectedNotificationRoute?.displayName ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedNotificationRoute || notificationActionLoading" @click="previewSelectedNotification">{{ copy.previewMessage }}</button>
            <button type="button" :disabled="!selectedNotificationRoute || notificationActionLoading" @click="testSelectedNotification">{{ copy.testSend }}</button>
            <button type="button" :disabled="!selectedRouteIncident" @click="openSelectedRouteIncident">{{ copy.openIncident }}</button>
            <button type="button" @click="emit('navigate-section', 'integrations')">{{ copy.openIntegrations }}</button>
          </div>
          <dl v-if="selectedNotificationRoute">
            <div><dt>{{ copy.channel }}</dt><dd>{{ selectedNotificationRoute.channel }}</dd></div>
            <div><dt>{{ copy.targetTeam }}</dt><dd>{{ selectedNotificationRoute.targetTeam }}</dd></div>
            <div><dt>{{ copy.minSeverity }}</dt><dd>{{ selectedNotificationRoute.minSeverity }}</dd></div>
            <div><dt>{{ copy.routeMode }}</dt><dd>{{ selectedNotificationRoute.mode }}</dd></div>
            <div><dt>{{ copy.testEnabled }}</dt><dd>{{ selectedNotificationRoute.testEnabled ? copy.enabled : copy.unavailable }}</dd></div>
            <div><dt>{{ copy.bound }}</dt><dd>{{ selectedNotificationRoute.boundIncidentCount }}</dd></div>
          </dl>
          <p v-if="notificationActionError" class="inline-warning">{{ notificationActionError }}</p>
          <template v-if="selectedNotificationPreview">
            <h3>{{ copy.notificationPreview }}</h3>
            <dl>
              <div><dt>{{ copy.status }}</dt><dd>{{ selectedNotificationPreview.mode }}</dd></div>
              <div><dt>{{ copy.targetTeam }}</dt><dd>{{ selectedNotificationPreview.recipients.join(', ') || '-' }}</dd></div>
              <div><dt>{{ copy.incidents }}</dt><dd>{{ selectedNotificationPreview.incidentKey }}</dd></div>
            </dl>
            <p>{{ selectedNotificationPreview.subject }}</p>
            <p>{{ selectedNotificationPreview.body }}</p>
          </template>
          <template v-if="selectedNotificationTest">
            <h3>{{ copy.notificationTest }}</h3>
            <dl>
              <div><dt>{{ copy.status }}</dt><dd>{{ selectedNotificationTest.status }}</dd></div>
              <div><dt>{{ copy.state }}</dt><dd>{{ selectedNotificationTest.accepted ? copy.enabled : copy.unavailable }}</dd></div>
              <div><dt>{{ copy.observedAt }}</dt><dd>{{ formatTimestamp(selectedNotificationTest.attemptedAt) }}</dd></div>
            </dl>
            <p>{{ selectedNotificationTest.message }}</p>
          </template>
          <h3>{{ copy.lastMessage }}</h3>
          <p>{{ selectedNotificationRoute?.lastMessage ?? '-' }}</p>
        </aside>
      </section>
    </template>

    <template v-else-if="section === 'policies'">
      <section class="ops-two-column">
        <div class="ops-panel">
          <header><span>{{ copy.policies }}</span><strong>{{ visiblePolicyPlans.length }}</strong></header>
          <div class="ops-table ops-table--plans">
            <button v-for="plan in visiblePolicyPlans" :key="plan.planKey" type="button" :data-tone="stateTone(plan.risk)" :class="{ 'is-active': selectedPolicyPlan?.planKey === plan.planKey }" @click="selectedPolicyPlanKey = plan.planKey">
              <span><strong>{{ plan.title }}</strong><small>{{ plan.serviceKey }} / {{ plan.resourceKey }}</small></span>
              <b>{{ plan.risk }}</b>
              <em>{{ plan.state }} / {{ plan.proposedAction }}</em>
            </button>
          </div>
        </div>
        <aside class="ops-panel detail-panel ops-evidence-drawer">
          <header><span>{{ copy.selectedPolicy }}</span><StatusBadge :label="selectedPolicyPlan?.risk ?? 'INFO'" :state="selectedPolicyPlan?.risk ?? 'INFO'" /></header>
          <h2>{{ selectedPolicyPlan?.title ?? '-' }}</h2>
          <div class="drawer-actions">
            <button type="button" :disabled="!selectedPolicyPlan || policyActionLoading" @click="runSelectedPolicyDryRun">{{ copy.runDryRun }}</button>
            <button type="button" :disabled="!selectedPolicyPlan || policyActionLoading" @click="exportSelectedPolicyReview">{{ copy.exportReview }}</button>
            <button type="button" :disabled="!selectedPolicyIncident" @click="openSelectedPolicyIncident">{{ copy.openIncident }}</button>
            <button type="button" :disabled="!selectedPolicyPlan" @click="openSelectedPolicyTopology">{{ copy.openTopology }}</button>
          </div>
          <dl v-if="selectedPolicyPlan">
            <div><dt>{{ copy.service }}</dt><dd>{{ selectedPolicyPlan.serviceKey }}</dd></div>
            <div><dt>{{ copy.resource }}</dt><dd>{{ selectedPolicyPlan.resourceKey }}</dd></div>
            <div><dt>{{ copy.targetKind }}</dt><dd>{{ selectedPolicyPlan.target?.kind ?? '-' }}</dd></div>
            <div><dt>{{ copy.signalType }}</dt><dd>{{ selectedPolicyPlan.signalType }}</dd></div>
            <div><dt>{{ copy.state }}</dt><dd>{{ selectedPolicyPlan.state }}</dd></div>
            <div><dt>{{ copy.risk }}</dt><dd>{{ selectedPolicyPlan.risk }}</dd></div>
            <div><dt>{{ copy.evidence }}</dt><dd>{{ selectedPolicyPlan.evidenceCount }}</dd></div>
            <div><dt>{{ copy.lastSeen }}</dt><dd>{{ selectedPolicyPlan.lastSeenAt ?? '-' }}</dd></div>
          </dl>
          <p v-if="policyActionError" class="inline-warning">{{ policyActionError }}</p>
          <h3>{{ copy.proposedAction }}</h3>
          <p>{{ selectedPolicyPlan?.proposedAction ?? '-' }}</p>
          <template v-if="selectedPolicyDiffs.length > 0">
            <h3>{{ copy.exportReview }}</h3>
            <div class="ops-table ops-table--compact">
              <div v-for="diff in selectedPolicyDiffs" :key="`${diff.fieldKey}:${diff.afterValue}`" class="evidence-row">
                <strong>{{ diff.fieldKey }}</strong>
                <small>{{ copy.before }}: {{ diff.beforeValue }}</small>
                <small>{{ copy.after }}: {{ diff.afterValue }}</small>
                <em>{{ diff.reason }}</em>
              </div>
            </div>
          </template>
          <template v-if="selectedPolicyDryRun">
            <h3>{{ copy.dryRunResult }}</h3>
            <dl>
              <div><dt>{{ copy.state }}</dt><dd>{{ selectedPolicyDryRun.passed ? copy.enabled : copy.unavailable }}</dd></div>
              <div><dt>{{ copy.impacted }}</dt><dd>{{ selectedPolicyDryRun.impactedServices.join(', ') || '-' }}</dd></div>
              <div><dt>{{ copy.requestSamples }}</dt><dd>{{ selectedPolicyDryRun.requestSampleCount }}</dd></div>
              <div><dt>{{ copy.observedAt }}</dt><dd>{{ formatTimestamp(selectedPolicyDryRun.generatedAt) }}</dd></div>
            </dl>
            <p>{{ selectedPolicyDryRun.summary }}</p>
            <p v-if="selectedPolicyDryRun.blockers.length > 0">{{ copy.blockers }}: {{ selectedPolicyDryRun.blockers.join(' / ') }}</p>
          </template>
          <template v-if="selectedPolicyExport">
            <h3>{{ copy.exportReview }}</h3>
            <p>{{ String(selectedPolicyExport.summary ?? selectedPolicyExport.mode ?? '-') }}</p>
          </template>
        </aside>
      </section>
    </template>
  </div>
</template>
