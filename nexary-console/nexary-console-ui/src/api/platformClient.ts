import type {
  PlatformConnectorState,
  PlatformConnectorConfig,
  PlatformConnectorConfigPayload,
  PlatformConnectorStatus,
  PlatformConnectorTestResult,
  PlatformAuditRecord,
  PlatformDataFreshness,
  PlatformDataSource,
  PlatformDependencyEdge,
  PlatformDryRunResult,
  PlatformEvidenceItem,
  PlatformEvidenceRef,
  PlatformHostSignal,
  PlatformImpactScope,
  PlatformIncidentCandidate,
  PlatformMiddlewareWatermark,
  PlatformNotificationPreview,
  PlatformNotificationRoute,
  PlatformNotificationTestResult,
  PlatformOverview,
  PlatformOverviewSummary,
  PlatformPlanDiff,
  PlatformPlanTarget,
  PlatformPolicyPlan,
  PlatformServiceNode,
  PlatformServiceMapping,
  PlatformServiceMappingPayload,
  PlatformServiceWatermark,
  PlatformSeverity,
  PlatformSignal,
  PlatformSnapshot,
  PlatformRequestFlow,
  PlatformSpan,
  PlatformSuggestedCheck,
  PlatformSourceMode,
  PlatformTraceQuery,
  PlatformTraceQueryResult,
  PlatformTransactionMetric,
  PlatformTopology,
  PlatformZoneWatermark,
} from '../types/platform';

type JsonRecord = Record<string, unknown>;
type SnapshotPayload = Omit<PlatformSnapshot, 'sourceMode' | 'generatedAt' | 'freshness' | 'dataSources' | 'warnings'>;

const defaultPlatformApiBase = '/api/platform';
const dataMode = normalizeDataMode(import.meta.env.VITE_NEXARY_CONSOLE_DATA);
const platformApiBase = normalizeBase(import.meta.env.VITE_NEXARY_PLATFORM_API_BASE, defaultPlatformApiBase);

export async function fetchPlatformSnapshot(): Promise<PlatformSnapshot> {
  if (dataMode === 'mock') {
    return withSnapshotMetadata(mockPlatformSnapshot(), 'MOCK', ['Explicit mock data mode is enabled.']);
  }
  try {
    return toSnapshot(await fetchJson('/snapshot'));
  } catch (error) {
    if (dataMode === 'api') {
      throw error;
    }
    const [overview, topology, services, incidents, connectors, signals] = await Promise.all([
      fetchJson('/overview').then(toOverview),
      fetchJson('/topology').then(toTopology),
      fetchJson('/services').then((data) => readItems(data).map(toServiceNode)),
      fetchJson('/incidents').then((data) => readItems(data).map(toIncident)),
      fetchJson('/connectors').then((data) => readItems(data).map(toConnector)),
      fetchJson('/signals').then((data) => readItems(data).map(toSignal)),
    ]);
    return withSnapshotMetadata({
      overview,
      topology,
      services,
      incidents,
      connectors,
      signals,
      requestFlows: [],
      transactions: [],
      hosts: [],
    }, inferSourceMode(services, connectors, signals), ['Snapshot endpoint unavailable; loaded component platform endpoints.']);
  }
}

export async function fetchPlatformTraces(query: PlatformTraceQuery = {}): Promise<PlatformTraceQueryResult> {
  const params = new URLSearchParams();
  appendQuery(params, 'from', query.from);
  appendQuery(params, 'to', query.to);
  appendQuery(params, 'serviceKey', query.serviceKey);
  appendQuery(params, 'endpointKey', query.endpointKey);
  appendQuery(params, 'status', query.status);
  appendQuery(params, 'minDurationMs', query.minDurationMs);
  appendQuery(params, 'resourceKey', query.resourceKey);
  appendQuery(params, 'source', query.source);
  appendQuery(params, 'sort', query.sort);
  appendQuery(params, 'page', query.page);
  appendQuery(params, 'size', query.size);
  const suffix = params.toString();
  return toTraceQueryResult(await fetchJson(`/traces${suffix ? `?${suffix}` : ''}`));
}

export async function dryRunPlatformPlan(planKey: string): Promise<PlatformDryRunResult> {
  return toDryRunResult(await postJson(`/plans/${encodeURIComponent(planKey)}/dry-run`));
}

export async function exportPlatformPlanReview(planKey: string): Promise<Record<string, unknown>> {
  return asRecord(await postJson(`/plans/${encodeURIComponent(planKey)}/export-review`));
}

export async function previewNotificationRoute(routeKey: string): Promise<PlatformNotificationPreview> {
  return toNotificationPreview(await postJson(`/notification-routes/${encodeURIComponent(routeKey)}/preview`));
}

export async function testNotificationRoute(routeKey: string): Promise<PlatformNotificationTestResult> {
  return toNotificationTestResult(await postJson(`/notification-routes/${encodeURIComponent(routeKey)}/test`));
}

export async function saveConnectorConfig(payload: PlatformConnectorConfigPayload): Promise<PlatformConnectorConfig> {
  return toConnectorConfig(await postJson('/connector-configs', payload));
}

export async function testConnectorConfig(connectorKey: string): Promise<PlatformConnectorTestResult> {
  return toConnectorTestResult(await postJson(`/connector-configs/${encodeURIComponent(connectorKey)}/test`));
}

export async function saveServiceMapping(payload: PlatformServiceMappingPayload): Promise<PlatformServiceMapping> {
  return toServiceMapping(await postJson('/service-mappings', payload));
}

async function fetchJson(path: string): Promise<unknown> {
  const response = await fetch(`${platformApiBase}${path}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Platform API ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<unknown>;
}

async function postJson(path: string, body?: unknown): Promise<unknown> {
  const response = await fetch(`${platformApiBase}${path}`, {
    method: 'POST',
    headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`Platform API ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<unknown>;
}

function appendQuery(params: URLSearchParams, key: string, value: string | number | null | undefined): void {
  if (value === null || value === undefined || value === '') {
    return;
  }
  params.set(key, String(value));
}

function toTopology(data: unknown): PlatformTopology {
  const record = asRecord(data);
  return {
    services: readItems(record.services).map(toServiceNode),
    dependencies: readItems(record.dependencies).map(toDependency),
    connectors: readItems(record.connectors).map(toConnector),
  };
}

function toSnapshot(data: unknown): PlatformSnapshot {
  const record = asRecord(data);
  const payload: SnapshotPayload = {
    overview: toOverview(record.overview),
    topology: toTopology(record.topology),
    services: readItems(record.services).map(toServiceNode),
    incidents: readItems(record.incidents).map(toIncident),
    connectors: readItems(record.connectors).map(toConnector),
    signals: readItems(record.signals).map(toSignal),
    requestFlows: readItems(record.requestFlows).map(toRequestFlow),
    transactions: readItems(record.transactions).map(toTransaction),
    hosts: readItems(record.hosts).map(toHostSignal),
    plans: readItems(record.plans).map(toPolicyPlan),
    notificationRoutes: readItems(record.notificationRoutes).map(toNotificationRoute),
    connectorConfigs: readItems(record.connectorConfigs).map(toConnectorConfig),
    connectorTests: readItems(record.connectorTests).map(toConnectorTestResult),
    serviceMappings: readItems(record.serviceMappings).map(toServiceMapping),
    auditRecords: readItems(record.auditRecords).map(toAuditRecord),
  };
  const sourceMode = toSourceMode(readString(record, 'sourceMode', inferSourceMode(payload.services, payload.connectors, payload.signals)));
  const dataSources = readItems(record.dataSources).map(toDataSource);
  return {
    ...payload,
    sourceMode,
    generatedAt: readNullableString(record, 'generatedAt'),
    freshness: toFreshness(record.freshness, payload, sourceMode),
    dataSources: dataSources.length > 0 ? dataSources : withSnapshotMetadata(payload, sourceMode, []).dataSources,
    warnings: readStringList(record.warnings),
  };
}

function toTraceQueryResult(data: unknown): PlatformTraceQueryResult {
  const record = asRecord(data);
  return {
    items: readItems(record.items).map(toRequestFlow),
    page: readNumber(record, 'page'),
    size: readNumber(record, 'size'),
    total: readNumber(record, 'total'),
    sort: readString(record, 'sort', 'risk'),
    filters: readScalarRecord(record, 'filters'),
  };
}

function toOverview(data: unknown): PlatformOverview {
  const record = asRecord(data);
  return {
    summary: toOverviewSummary(record.summary),
    serviceWatermarks: readItems(record.serviceWatermarks).map(toServiceWatermark),
    zoneWatermarks: readItems(record.zoneWatermarks).map(toZoneWatermark),
    middlewareWatermarks: readItems(record.middlewareWatermarks).map(toMiddlewareWatermark),
    policyPlans: readItems(record.policyPlans).map(toPolicyPlan),
    notificationRoutes: readItems(record.notificationRoutes).map(toNotificationRoute),
  };
}

function toOverviewSummary(data: unknown): PlatformOverviewSummary {
  const record = asRecord(data);
  return {
    workspaceKey: readString(record, 'workspaceKey', 'unknown'),
    environmentKey: readString(record, 'environmentKey', 'unknown'),
    health: readString(record, 'health', 'HEALTHY'),
    criticalIncidents: readNumber(record, 'criticalIncidents'),
    warningIncidents: readNumber(record, 'warningIncidents'),
    serviceCount: readNumber(record, 'serviceCount'),
    zoneCount: readNumber(record, 'zoneCount'),
    dependencyCount: readNumber(record, 'dependencyCount'),
    connectorCount: readNumber(record, 'connectorCount'),
    middlewareCount: readNumber(record, 'middlewareCount'),
    openPolicyPlans: readNumber(record, 'openPolicyPlans'),
    notificationRoutes: readNumber(record, 'notificationRoutes'),
    lastSignalAt: readNullableString(record, 'lastSignalAt'),
  };
}

function toServiceWatermark(data: unknown): PlatformServiceWatermark {
  const record = asRecord(data);
  return {
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    name: readString(record, 'name', 'unknown'),
    teamKey: readString(record, 'teamKey', 'unknown'),
    environmentKey: readString(record, 'environmentKey', 'unknown'),
    clusterKey: readString(record, 'clusterKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    qps: readNumber(record, 'qps'),
    errorRate: readNumber(record, 'errorRate'),
    p95Ms: readNumber(record, 'p95Ms'),
    p99Ms: readNumber(record, 'p99Ms'),
    instanceCount: readNumber(record, 'instanceCount'),
    cpuPercent: readNumber(record, 'cpuPercent'),
    memoryPercent: readNumber(record, 'memoryPercent'),
    watermarkPercent: readNumber(record, 'watermarkPercent'),
    sentinelState: readString(record, 'sentinelState', 'CLOSED'),
    gatewayState: readString(record, 'gatewayState', 'HEALTHY'),
    warningCount: readNumber(record, 'warningCount'),
    criticalCount: readNumber(record, 'criticalCount'),
    activeIncidents: readNumber(record, 'activeIncidents'),
    state: readString(record, 'state', 'HEALTHY'),
  };
}

function toZoneWatermark(data: unknown): PlatformZoneWatermark {
  const record = asRecord(data);
  return {
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    environmentKey: readString(record, 'environmentKey', 'unknown'),
    serviceCount: readNumber(record, 'serviceCount'),
    warningCount: readNumber(record, 'warningCount'),
    criticalCount: readNumber(record, 'criticalCount'),
    cpuPercent: readNumber(record, 'cpuPercent'),
    memoryPercent: readNumber(record, 'memoryPercent'),
    networkJitterMs: readNumber(record, 'networkJitterMs'),
    packetLossPercent: readNumber(record, 'packetLossPercent'),
    httpFailureRate: readNumber(record, 'httpFailureRate'),
    state: readString(record, 'state', 'HEALTHY'),
  };
}

function toMiddlewareWatermark(data: unknown): PlatformMiddlewareWatermark {
  const record = asRecord(data);
  return {
    middlewareKey: readString(record, 'middlewareKey', 'unknown'),
    name: readString(record, 'name', 'unknown'),
    kind: readString(record, 'kind', 'middleware'),
    environmentKey: readString(record, 'environmentKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    usagePercent: readNumber(record, 'usagePercent'),
    latencyMs: readNumber(record, 'latencyMs'),
    errorRate: readNumber(record, 'errorRate'),
    connectedServices: readNumber(record, 'connectedServices'),
    warningCount: readNumber(record, 'warningCount'),
    criticalCount: readNumber(record, 'criticalCount'),
    state: readString(record, 'state', 'HEALTHY'),
  };
}

function toPolicyPlan(data: unknown): PlatformPolicyPlan {
  const record = asRecord(data);
  const evidence = readItems(record.evidence).map(toEvidence);
  return {
    planKey: readString(record, 'planKey', 'unknown'),
    incidentKey: readString(record, 'incidentKey', ''),
    title: readString(record, 'title', 'unknown'),
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    resourceKey: readString(record, 'resourceKey', 'unknown'),
    signalType: readString(record, 'signalType', evidence[0]?.signalType ?? 'RESOURCE_EVENT'),
    state: readString(record, 'state', 'DRY_RUN'),
    risk: readString(record, 'risk', 'MEDIUM'),
    target: record.target ? toPlanTarget(record.target) : null,
    diffs: readItems(record.diffs).map(toPlanDiff),
    evidence,
    proposedAction: readString(record, 'proposedAction', ''),
    evidenceCount: readNumber(record, 'evidenceCount'),
    impactedServiceCount: readNumber(record, 'impactedServiceCount'),
    impactedInstanceCount: readNumber(record, 'impactedInstanceCount'),
    createdAt: readNullableString(record, 'createdAt'),
    updatedAt: readNullableString(record, 'updatedAt'),
    lastSeenAt: readNullableString(record, 'lastSeenAt') ?? readNullableString(record, 'updatedAt'),
  };
}

function toPlanTarget(data: unknown): PlatformPlanTarget {
  const record = asRecord(data);
  return {
    kind: readString(record, 'kind', 'OWNERSHIP_MAPPING'),
    targetKey: readString(record, 'targetKey', 'unknown'),
    displayName: readString(record, 'displayName', 'unknown'),
  };
}

function toPlanDiff(data: unknown): PlatformPlanDiff {
  const record = asRecord(data);
  return {
    fieldKey: readString(record, 'fieldKey', 'unknown'),
    beforeValue: readString(record, 'beforeValue', 'unknown'),
    afterValue: readString(record, 'afterValue', 'review-required'),
    reason: readString(record, 'reason', ''),
  };
}

function toDryRunResult(data: unknown): PlatformDryRunResult {
  const record = asRecord(data);
  return {
    planKey: readString(record, 'planKey', 'unknown'),
    passed: readBoolean(record, 'passed'),
    risk: readString(record, 'risk', 'MEDIUM'),
    impactedServices: readStringList(record.impactedServices),
    impactedInstances: readStringList(record.impactedInstances),
    impactedDependencies: readStringList(record.impactedDependencies),
    requestSampleCount: readNumber(record, 'requestSampleCount'),
    blockers: readStringList(record.blockers),
    diffs: readItems(record.diffs).map(toPlanDiff),
    evidence: readItems(record.evidence).map(toEvidence),
    summary: readString(record, 'summary', ''),
    generatedAt: readNullableString(record, 'generatedAt'),
  };
}

function toNotificationRoute(data: unknown): PlatformNotificationRoute {
  const record = asRecord(data);
  return {
    routeKey: readString(record, 'routeKey', 'unknown'),
    channel: readString(record, 'channel', 'FEISHU'),
    displayName: readString(record, 'displayName', 'unknown'),
    targetTeam: readString(record, 'targetTeam', 'platform-team'),
    minSeverity: readString(record, 'minSeverity', 'CRITICAL'),
    mode: readString(record, 'mode', readBoolean(record, 'dryRun') ? 'DRY_RUN' : 'DISABLED'),
    state: readString(record, 'state', 'DISABLED'),
    dryRun: readBoolean(record, 'dryRun'),
    testEnabled: readBoolean(record, 'testEnabled'),
    lastMessage: readString(record, 'lastMessage', ''),
    boundIncidentCount: readNumber(record, 'boundIncidentCount'),
    attributes: readStringRecord(record, 'attributes'),
  };
}

function toNotificationPreview(data: unknown): PlatformNotificationPreview {
  const record = asRecord(data);
  return {
    routeKey: readString(record, 'routeKey', 'unknown'),
    incidentKey: readString(record, 'incidentKey', 'unknown'),
    subject: readString(record, 'subject', ''),
    body: readString(record, 'body', ''),
    recipients: readStringList(record.recipients),
    mode: readString(record, 'mode', 'DRY_RUN'),
    createdAt: readNullableString(record, 'createdAt'),
  };
}

function toNotificationTestResult(data: unknown): PlatformNotificationTestResult {
  const record = asRecord(data);
  return {
    testKey: readString(record, 'testKey', 'unknown'),
    routeKey: readString(record, 'routeKey', 'unknown'),
    accepted: readBoolean(record, 'accepted'),
    status: readString(record, 'status', 'TEST_DISABLED'),
    message: readString(record, 'message', ''),
    attemptedAt: readNullableString(record, 'attemptedAt'),
    preview: record.preview ? toNotificationPreview(record.preview) : null,
  };
}

function toAuditRecord(data: unknown): PlatformAuditRecord {
  const record = asRecord(data);
  return {
    auditKey: readString(record, 'auditKey', 'unknown'),
    action: readString(record, 'action', 'UNKNOWN'),
    subjectKey: readString(record, 'subjectKey', 'unknown'),
    result: readString(record, 'result', 'OK'),
    message: readString(record, 'message', ''),
    createdAt: readNullableString(record, 'createdAt'),
  };
}

function toServiceNode(data: unknown): PlatformServiceNode {
  const record = asRecord(data);
  return {
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    name: readString(record, 'name', 'unknown'),
    teamKey: readString(record, 'teamKey', 'unknown'),
    environmentKey: readString(record, 'environmentKey', 'unknown'),
    clusterKey: readString(record, 'clusterKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    warningCount: readNumber(record, 'warningCount'),
    criticalCount: readNumber(record, 'criticalCount'),
    attributes: readStringRecord(record, 'attributes'),
  };
}

function toDependency(data: unknown): PlatformDependencyEdge {
  const record = asRecord(data);
  return {
    sourceKey: readString(record, 'sourceKey', 'unknown'),
    targetKey: readString(record, 'targetKey', 'unknown'),
    kind: readString(record, 'kind', 'HTTP'),
    resourceKey: readString(record, 'resourceKey', ''),
    warningCount: readNumber(record, 'warningCount'),
    criticalCount: readNumber(record, 'criticalCount'),
    attributes: readStringRecord(record, 'attributes'),
  };
}

function toConnector(data: unknown): PlatformConnectorStatus {
  const record = asRecord(data);
  return {
    connectorKey: readString(record, 'connectorKey', 'unknown'),
    kind: readString(record, 'kind', 'NEXARY_SDK'),
    state: readString(record, 'state', 'DISABLED') as PlatformConnectorState,
    displayName: readString(record, 'displayName', 'unknown'),
    lastMessage: readString(record, 'lastMessage', ''),
    lastSeenAt: readNullableString(record, 'lastSeenAt'),
  };
}

function toConnectorConfig(data: unknown): PlatformConnectorConfig {
  const record = asRecord(data);
  return {
    connectorKey: readString(record, 'connectorKey', 'unknown'),
    kind: readString(record, 'kind', 'NEXARY_SDK'),
    displayName: readString(record, 'displayName', 'Unknown connector'),
    endpoint: readString(record, 'endpoint', ''),
    authMode: readString(record, 'authMode', 'NONE'),
    accessMode: readString(record, 'accessMode', 'READ_ONLY'),
    state: readString(record, 'state', 'DISABLED') as PlatformConnectorState,
    testEnabled: readBoolean(record, 'testEnabled'),
    capabilities: readStringList(record.capabilities),
    lastMessage: readString(record, 'lastMessage', ''),
    attributes: readStringRecord(record, 'attributes'),
    createdAt: readNullableString(record, 'createdAt'),
    updatedAt: readNullableString(record, 'updatedAt'),
    writeDisabled: record.writeDisabled !== false,
  };
}

function toConnectorTestResult(data: unknown): PlatformConnectorTestResult {
  const record = asRecord(data);
  return {
    testKey: readString(record, 'testKey', 'unknown'),
    connectorKey: readString(record, 'connectorKey', 'unknown'),
    accepted: readBoolean(record, 'accepted'),
    status: readString(record, 'status', 'UNKNOWN'),
    message: readString(record, 'message', ''),
    testedAt: readNullableString(record, 'testedAt'),
    capabilities: readStringList(record.capabilities),
  };
}

function toServiceMapping(data: unknown): PlatformServiceMapping {
  const record = asRecord(data);
  return {
    mappingKey: readString(record, 'mappingKey', 'unknown'),
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    connectorKey: readString(record, 'connectorKey', 'unknown'),
    sourceKind: readString(record, 'sourceKind', 'NEXARY_SDK'),
    externalKey: readString(record, 'externalKey', 'unknown'),
    resourceKind: readString(record, 'resourceKind', 'service'),
    confidence: readNumber(record, 'confidence'),
    attributes: readStringRecord(record, 'attributes'),
    updatedAt: readNullableString(record, 'updatedAt'),
  };
}

function toDataSource(data: unknown): PlatformDataSource {
  const record = asRecord(data);
  return {
    sourceKey: readString(record, 'sourceKey', readString(record, 'connectorKey', 'platform-ingest')),
    kind: readString(record, 'kind', 'NEXARY_SDK'),
    state: readString(record, 'state', 'UNKNOWN'),
    displayName: readString(record, 'displayName', 'Platform ingest'),
    lastSeenAt: readNullableString(record, 'lastSeenAt'),
    lastMessage: readString(record, 'lastMessage', ''),
    mode: toSourceMode(readString(record, 'mode', 'UNKNOWN')),
  };
}

function toFreshness(data: unknown, payload: SnapshotPayload, sourceMode: PlatformSourceMode): PlatformDataFreshness {
  const record = asRecord(data);
  return {
    state: toSourceMode(readString(record, 'state', sourceMode)),
    generatedAt: readNullableString(record, 'generatedAt'),
    lastSignalAt: readNullableString(record, 'lastSignalAt') ?? payload.overview.summary.lastSignalAt,
    lastConnectorSeenAt: readNullableString(record, 'lastConnectorSeenAt') ?? latestConnectorSeenAt(payload.connectors),
    staleAfterSeconds: readNumber(record, 'staleAfterSeconds') || 900,
  };
}

function withSnapshotMetadata(payload: SnapshotPayload, sourceMode: PlatformSourceMode, warnings: string[]): PlatformSnapshot {
  const generatedAt = new Date().toISOString();
  const dataSources = payload.connectors.length > 0
    ? payload.connectors.map((connector) => ({
        sourceKey: connector.connectorKey,
        kind: connector.kind,
        state: connector.state,
        displayName: connector.displayName,
        lastSeenAt: connector.lastSeenAt,
        lastMessage: connector.lastMessage,
        mode: sourceMode,
      }))
    : [{
        sourceKey: 'platform-ingest',
        kind: 'NEXARY_SDK',
        state: sourceMode,
        displayName: 'Nexary platform ingest',
        lastSeenAt: payload.overview.summary.lastSignalAt,
        lastMessage: '',
        mode: sourceMode,
      }];
  return {
    ...payload,
    sourceMode,
    generatedAt,
    freshness: {
      state: sourceMode,
      generatedAt,
      lastSignalAt: payload.overview.summary.lastSignalAt,
      lastConnectorSeenAt: latestConnectorSeenAt(payload.connectors),
      staleAfterSeconds: 900,
    },
    dataSources,
    warnings,
  };
}

function toIncident(data: unknown): PlatformIncidentCandidate {
  const record = asRecord(data);
  return {
    incidentKey: readString(record, 'incidentKey', 'unknown'),
    title: readString(record, 'title', 'unknown'),
    severity: readString(record, 'severity', 'WARNING') as PlatformSeverity,
    impactScope: toImpact(record.impactScope),
    evidence: readItems(record.evidence).map(toEvidence),
    suggestedCheck: record.suggestedCheck ? toSuggestedCheck(record.suggestedCheck) : null,
    startedAt: readNullableString(record, 'startedAt'),
    lastSeenAt: readNullableString(record, 'lastSeenAt'),
    primaryResourceKey: readString(record, 'primaryResourceKey', 'unknown'),
    evidenceCount: readNumber(record, 'evidenceCount'),
    impactedResourceCount: readNumber(record, 'impactedResourceCount'),
  };
}

function toImpact(data: unknown): PlatformImpactScope {
  const record = asRecord(data);
  return {
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    clusterKey: readString(record, 'clusterKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
  };
}

function toEvidence(data: unknown): PlatformEvidenceItem {
  const record = asRecord(data);
  return {
    signalType: readString(record, 'signalType', 'RESOURCE_EVENT'),
    severity: readString(record, 'severity', 'INFO') as PlatformSeverity,
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    clusterKey: readString(record, 'clusterKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    resourceKey: readString(record, 'resourceKey', 'unknown'),
    outcome: readString(record, 'outcome', 'NONE'),
    durationBucket: readString(record, 'durationBucket', 'NOT_RUN'),
    message: readString(record, 'message', ''),
    referenceType: readString(record, 'referenceType', 'NONE'),
    referenceKey: readString(record, 'referenceKey', 'NONE'),
    timestamp: readNullableString(record, 'timestamp'),
  };
}

function toSuggestedCheck(data: unknown): PlatformSuggestedCheck {
  const record = asRecord(data);
  return {
    resourceKey: readString(record, 'resourceKey', 'unknown'),
    message: readString(record, 'message', ''),
  };
}

function toSignal(data: unknown): PlatformSignal {
  const record = asRecord(data);
  return {
    workspaceKey: readString(record, 'workspaceKey', 'unknown'),
    environmentKey: readString(record, 'environmentKey', 'unknown'),
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    clusterKey: readString(record, 'clusterKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    resourceKey: readString(record, 'resourceKey', 'unknown'),
    signalType: readString(record, 'signalType', 'RESOURCE_EVENT'),
    severity: readString(record, 'severity', 'INFO') as PlatformSeverity,
    outcome: readString(record, 'outcome', 'NONE'),
    durationBucket: readString(record, 'durationBucket', 'NOT_RUN'),
    timestamp: readNullableString(record, 'timestamp'),
    attributes: readStringRecord(record, 'attributes'),
  };
}

function toRequestFlow(data: unknown): PlatformRequestFlow {
  const record = asRecord(data);
  return {
    traceKey: readString(record, 'traceKey', 'unknown'),
    entryServiceKey: readString(record, 'entryServiceKey', 'unknown'),
    endpointKey: readString(record, 'endpointKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    status: readString(record, 'status', 'OK'),
    durationMs: readNumber(record, 'durationMs'),
    startedAt: readNullableString(record, 'startedAt'),
    spanCount: readNumber(record, 'spanCount'),
    primaryError: readString(record, 'primaryError', 'NONE'),
    summary: readString(record, 'summary', ''),
    spans: readItems(record.spans).map(toSpan),
    evidenceRefs: readItems(record.evidenceRefs).map(toEvidenceRef),
  };
}

function toSpan(data: unknown): PlatformSpan {
  const record = asRecord(data);
  return {
    spanId: readString(record, 'spanId', 'unknown'),
    parentSpanId: readString(record, 'parentSpanId', ''),
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    resourceKey: readString(record, 'resourceKey', 'unknown'),
    component: readString(record, 'component', 'service'),
    operation: readString(record, 'operation', 'unknown'),
    startOffsetMs: readNumber(record, 'startOffsetMs'),
    durationMs: readNumber(record, 'durationMs'),
    status: readString(record, 'status', 'OK'),
    errorType: readString(record, 'errorType', 'NONE'),
    evidenceRefs: readItems(record.evidenceRefs).map(toEvidenceRef),
  };
}

function toEvidenceRef(data: unknown): PlatformEvidenceRef {
  const record = asRecord(data);
  return {
    type: readString(record, 'type', 'LOG_QUERY'),
    refKey: readString(record, 'refKey', 'unknown'),
    label: readString(record, 'label', 'Evidence'),
    href: readString(record, 'href', ''),
  };
}

function toTransaction(data: unknown): PlatformTransactionMetric {
  const record = asRecord(data);
  return {
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    endpointKey: readString(record, 'endpointKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    windowStart: readNullableString(record, 'windowStart'),
    windowEnd: readNullableString(record, 'windowEnd'),
    total: readNumber(record, 'total'),
    failure: readNumber(record, 'failure'),
    failureRate: readNumber(record, 'failureRate'),
    tps: readNumber(record, 'tps'),
    qps: readNumber(record, 'qps'),
    minMs: readNumber(record, 'minMs'),
    maxMs: readNumber(record, 'maxMs'),
    avgMs: readNumber(record, 'avgMs'),
    p95Ms: readNumber(record, 'p95Ms'),
    p99Ms: readNumber(record, 'p99Ms'),
    sampleTraceKey: readString(record, 'sampleTraceKey', ''),
  };
}

function toHostSignal(data: unknown): PlatformHostSignal {
  const record = asRecord(data);
  return {
    hostKey: readString(record, 'hostKey', 'unknown'),
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    clusterKey: readString(record, 'clusterKey', 'unknown'),
    zoneKey: readString(record, 'zoneKey', 'unknown'),
    state: readString(record, 'state', 'HEALTHY'),
    cpuPercent: readNumber(record, 'cpuPercent'),
    memoryPercent: readNumber(record, 'memoryPercent'),
    swapPercent: readNumber(record, 'swapPercent'),
    diskIoPercent: readNumber(record, 'diskIoPercent'),
    networkJitterMs: readNumber(record, 'networkJitterMs'),
    packetLossPercent: readNumber(record, 'packetLossPercent'),
    connectionCount: readNumber(record, 'connectionCount'),
    jvmThreadCount: readNumber(record, 'jvmThreadCount'),
    gcPauseMs: readNumber(record, 'gcPauseMs'),
    lastError: readString(record, 'lastError', 'NONE'),
    lastSeenAt: readNullableString(record, 'lastSeenAt'),
    attributes: readStringRecord(record, 'attributes'),
  };
}

function mockPlatformSnapshot(): SnapshotPayload {
  const services: PlatformServiceNode[] = [
    { serviceKey: 'open-api', name: 'Open API', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'open-api-cluster', zoneKey: 'cn-east', warningCount: 2, criticalCount: 1, attributes: {} },
    { serviceKey: 'sdk-api', name: 'SDK API', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'sdk-api-cluster', zoneKey: 'cn-east', warningCount: 1, criticalCount: 0, attributes: {} },
    { serviceKey: 'room-resource', name: 'Room Resource', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'room-resource-cluster', zoneKey: 'room-a', warningCount: 1, criticalCount: 1, attributes: {} },
    { serviceKey: 'signaling', name: 'Signaling', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'signaling-cluster', zoneKey: 'room-a', warningCount: 1, criticalCount: 0, attributes: {} },
  ];
  const dependencies: PlatformDependencyEdge[] = [
    { sourceKey: 'open-api', targetKey: 'redis-main', kind: 'CACHE', resourceKey: 'cache:open-api:profile', warningCount: 0, criticalCount: 0, attributes: {} },
    { sourceKey: 'sdk-api', targetKey: 'signaling', kind: 'SIGNALING', resourceKey: 'signal:sdk-api:connect', warningCount: 1, criticalCount: 0, attributes: {} },
    { sourceKey: 'room-resource', targetKey: 'redis-room', kind: 'CACHE', resourceKey: 'cache:room-resource:state', warningCount: 0, criticalCount: 1, attributes: {} },
  ];
  const connectors: PlatformConnectorStatus[] = [
    { connectorKey: 'nexary-sdk-demo', kind: 'NEXARY_SDK', state: 'HEALTHY', displayName: 'Nexary SDK', lastMessage: 'demo signals received', lastSeenAt: null },
    { connectorKey: 'prometheus-readonly-demo', kind: 'PROMETHEUS', state: 'HEALTHY', displayName: 'Prometheus', lastMessage: 'watermarks query demo', lastSeenAt: null },
    { connectorKey: 'skywalking-readonly-demo', kind: 'SKYWALKING', state: 'HEALTHY', displayName: 'SkyWalking', lastMessage: 'service trace evidence demo', lastSeenAt: null },
    { connectorKey: 'sentinel-readonly-demo', kind: 'SENTINEL', state: 'DEGRADED', displayName: 'Sentinel', lastMessage: 'read-only demo data', lastSeenAt: null },
    { connectorKey: 'gateway-readonly-demo', kind: 'GATEWAY', state: 'DEGRADED', displayName: 'Spring Cloud Gateway', lastMessage: 'route health demo', lastSeenAt: null },
    { connectorKey: 'feishu-dry-run-demo', kind: 'FEISHU', state: 'DISABLED', displayName: 'Feishu Dry Run', lastMessage: 'critical incident test message only', lastSeenAt: null },
  ];
  const incidents: PlatformIncidentCandidate[] = [
    {
      incidentKey: 'incident-room-resource-downstream',
      title: 'QUARANTINE_CANDIDATE on room-resource',
      severity: 'CRITICAL',
      impactScope: { serviceKey: 'room-resource', clusterKey: 'room-resource-cluster', zoneKey: 'room-a' },
      evidence: [{
        signalType: 'QUARANTINE_CANDIDATE',
        severity: 'CRITICAL',
        serviceKey: 'room-resource',
        clusterKey: 'room-resource-cluster',
        zoneKey: 'room-a',
        resourceKey: 'downstream:room-resource:allocate',
        outcome: 'SUSPECT',
        durationBucket: 'GT_2S',
        message: 'QUARANTINE_CANDIDATE / SUSPECT / GT_2S',
        referenceType: 'INSTANCE_HEALTH',
        referenceKey: 'downstream:room-resource:allocate',
        timestamp: null,
      }],
      suggestedCheck: { resourceKey: 'downstream:room-resource:allocate', message: 'Check resource evidence before changing policy' },
      startedAt: null,
      lastSeenAt: null,
      primaryResourceKey: 'downstream:room-resource:allocate',
      evidenceCount: 1,
      impactedResourceCount: 1,
    },
  ];
  const overview: PlatformOverview = {
    summary: {
      workspaceKey: 'cloud-phone',
      environmentKey: 'prod-demo',
      health: 'NEEDS_ACTION',
      criticalIncidents: 1,
      warningIncidents: 2,
      serviceCount: services.length,
      zoneCount: 2,
      dependencyCount: dependencies.length,
      connectorCount: connectors.length,
      middlewareCount: 4,
      openPolicyPlans: 1,
      notificationRoutes: 1,
      lastSignalAt: null,
    },
    serviceWatermarks: [
      { serviceKey: 'open-api', name: 'Open API', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'open-api-cluster', zoneKey: 'cn-east', qps: 145, errorRate: 0.026, p95Ms: 620, p99Ms: 1340, instanceCount: 12, cpuPercent: 68, memoryPercent: 72, watermarkPercent: 74, sentinelState: 'OPEN', gatewayState: 'WATCH', warningCount: 2, criticalCount: 1, activeIncidents: 1, state: 'CRITICAL' },
      { serviceKey: 'sdk-api', name: 'SDK API', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'sdk-api-cluster', zoneKey: 'cn-east', qps: 212, errorRate: 0.011, p95Ms: 260, p99Ms: 740, instanceCount: 16, cpuPercent: 55, memoryPercent: 63, watermarkPercent: 61, sentinelState: 'CLOSED', gatewayState: 'WATCH', warningCount: 1, criticalCount: 0, activeIncidents: 0, state: 'WARNING' },
      { serviceKey: 'room-resource', name: 'Room Resource', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'room-resource-cluster', zoneKey: 'room-a', qps: 96, errorRate: 0.091, p95Ms: 1800, p99Ms: 3200, instanceCount: 18, cpuPercent: 86, memoryPercent: 82, watermarkPercent: 91, sentinelState: 'CLOSED', gatewayState: 'DEGRADED', warningCount: 1, criticalCount: 1, activeIncidents: 1, state: 'CRITICAL' },
      { serviceKey: 'signaling', name: 'Signaling', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'signaling-cluster', zoneKey: 'room-a', qps: 184, errorRate: 0.017, p95Ms: 380, p99Ms: 900, instanceCount: 14, cpuPercent: 73, memoryPercent: 69, watermarkPercent: 76, sentinelState: 'CLOSED', gatewayState: 'DEGRADED', warningCount: 1, criticalCount: 0, activeIncidents: 0, state: 'WARNING' },
    ],
    zoneWatermarks: [
      { zoneKey: 'cn-east', environmentKey: 'prod-demo', serviceCount: 2, warningCount: 3, criticalCount: 1, cpuPercent: 61.5, memoryPercent: 67.5, networkJitterMs: 4.2, packetLossPercent: 1.4, httpFailureRate: 0.022, state: 'CRITICAL' },
      { zoneKey: 'room-a', environmentKey: 'prod-demo', serviceCount: 2, warningCount: 2, criticalCount: 1, cpuPercent: 79.5, memoryPercent: 75.5, networkJitterMs: 18.5, packetLossPercent: 2.8, httpFailureRate: 0.083, state: 'CRITICAL' },
    ],
    middlewareWatermarks: [
      { middlewareKey: 'redis-main', name: 'Redis Main', kind: 'cache', environmentKey: 'prod-demo', zoneKey: 'cn-east', usagePercent: 61, latencyMs: 18, errorRate: 0.004, connectedServices: 1, warningCount: 0, criticalCount: 0, state: 'HEALTHY' },
      { middlewareKey: 'pg-primary', name: 'Postgres Primary', kind: 'database', environmentKey: 'prod-demo', zoneKey: 'cn-east', usagePercent: 67, latencyMs: 42, errorRate: 0.006, connectedServices: 1, warningCount: 0, criticalCount: 0, state: 'HEALTHY' },
      { middlewareKey: 'redis-room', name: 'Room Redis', kind: 'cache', environmentKey: 'prod-demo', zoneKey: 'room-a', usagePercent: 89, latencyMs: 210, errorRate: 0.071, connectedServices: 1, warningCount: 0, criticalCount: 1, state: 'CRITICAL' },
    ],
    policyPlans: [
      {
        planKey: 'plan-incident-room-resource-downstream',
        incidentKey: 'incident-room-resource-downstream',
        title: 'Dry-run review for downstream:room-resource:allocate',
        serviceKey: 'room-resource',
        resourceKey: 'downstream:room-resource:allocate',
        signalType: 'QUARANTINE_CANDIDATE',
        state: 'DRY_RUN',
        risk: 'HIGH',
        target: { kind: 'INSTANCE_CANDIDATE', targetKey: 'downstream:room-resource:allocate', displayName: 'downstream:room-resource:allocate' },
        diffs: [{ fieldKey: 'instanceIsolation', beforeValue: 'none', afterValue: 'candidate-only', reason: 'Instance signal requires manual traffic review' }],
        evidence: incidents[0].evidence,
        proposedAction: 'Keep manual approval before traffic quarantine',
        evidenceCount: 1,
        impactedServiceCount: 1,
        impactedInstanceCount: 0,
        createdAt: null,
        updatedAt: null,
        lastSeenAt: null,
      },
    ],
    notificationRoutes: [
      {
        routeKey: 'feishu-dry-run-demo',
        channel: 'FEISHU',
        displayName: 'Feishu Dry Run',
        targetTeam: 'platform-team',
        minSeverity: 'CRITICAL',
        mode: 'DRY_RUN',
        state: 'DISABLED',
        dryRun: true,
        testEnabled: false,
        lastMessage: 'critical incident test message only',
        boundIncidentCount: 1,
        attributes: { readOnly: 'true', writeDisabled: 'true', productionSend: 'false' },
      },
    ],
  };
  const requestFlows: PlatformRequestFlow[] = [
    {
      traceKey: 'flow-signaling-redis-timeout',
      entryServiceKey: 'signaling',
      endpointKey: 'http:signaling:heartbeat',
      zoneKey: 'room-a',
      status: 'ERROR',
      durationMs: 2860,
      startedAt: null,
      spanCount: 2,
      primaryError: 'REDIS_TIMEOUT',
      summary: 'Redis command timeout with swap and disk IO pressure',
      spans: [
        { spanId: 'flow-signaling-redis-timeout-span-1', parentSpanId: '', serviceKey: 'signaling', resourceKey: 'http:signaling:heartbeat', component: 'http', operation: 'heartbeat', startOffsetMs: 0, durationMs: 2860, status: 'ERROR', errorType: 'REDIS_TIMEOUT', evidenceRefs: [] },
        { spanId: 'flow-signaling-redis-timeout-span-2', parentSpanId: 'flow-signaling-redis-timeout-span-1', serviceKey: 'redis-room', resourceKey: 'cache:redis-room:state', component: 'redis', operation: 'GET room state', startOffsetMs: 42, durationMs: 2310, status: 'ERROR', errorType: 'REDIS_TIMEOUT', evidenceRefs: [] },
      ],
      evidenceRefs: [
        { type: 'SKYWALKING_TRACE', refKey: 'sw-flow-signaling-redis-timeout', label: 'SkyWalking trace', href: '' },
        { type: 'CAT_TRANSACTION', refKey: 'cat-signaling-redis-room', label: 'CAT transaction', href: '' },
        { type: 'PROMQL', refKey: 'prom-redis-room-swap-io', label: 'PromQL', href: '' },
      ],
    },
    {
      traceKey: 'flow-signaling-board-broken-pipe',
      entryServiceKey: 'signaling',
      endpointKey: 'http:signaling:instance-callback',
      zoneKey: 'room-a',
      status: 'SLOW',
      durationMs: 940,
      startedAt: null,
      spanCount: 1,
      primaryError: 'BROKEN_PIPE',
      summary: 'Board service became unreachable or closed the response',
      spans: [
        { spanId: 'flow-signaling-board-broken-pipe-span-1', parentSpanId: '', serviceKey: 'board-service', resourceKey: 'http:board-service:callback', component: 'http', operation: 'callback write', startOffsetMs: 0, durationMs: 870, status: 'SLOW', errorType: 'BROKEN_PIPE', evidenceRefs: [] },
      ],
      evidenceRefs: [
        { type: 'LOG_QUERY', refKey: 'log-signaling-board-broken-pipe', label: 'Log query', href: '' },
        { type: 'GATEWAY_ROUTE', refKey: 'gateway-board-callback', label: 'Gateway route', href: '' },
      ],
    },
  ];
  const transactions: PlatformTransactionMetric[] = [
    { serviceKey: 'signaling', endpointKey: 'http:signaling:heartbeat', zoneKey: 'room-a', windowStart: null, windowEnd: null, total: 184, failure: 21, failureRate: 0.114, tps: 0.61, qps: 0.61, minMs: 42, maxMs: 3410, avgMs: 810, p95Ms: 2260, p99Ms: 3410, sampleTraceKey: 'flow-signaling-redis-timeout' },
    { serviceKey: 'room-resource', endpointKey: 'http:room-resource:allocate', zoneKey: 'room-a', windowStart: null, windowEnd: null, total: 96, failure: 18, failureRate: 0.188, tps: 0.32, qps: 0.32, minMs: 88, maxMs: 4210, avgMs: 1180, p95Ms: 2840, p99Ms: 4210, sampleTraceKey: 'flow-room-resource-redis-room' },
  ];
  const hosts: PlatformHostSignal[] = [
    { hostKey: 'signaling-room-a-01', serviceKey: 'signaling', clusterKey: 'signaling-cluster', zoneKey: 'room-a', state: 'WARNING', cpuPercent: 73, memoryPercent: 69, swapPercent: 12, diskIoPercent: 52, networkJitterMs: 16.2, packetLossPercent: 1.9, connectionCount: 1280, jvmThreadCount: 184, gcPauseMs: 56, lastError: 'BROKEN_PIPE', lastSeenAt: null, attributes: {} },
    { hostKey: 'redis-room-a-primary', serviceKey: 'redis-room', clusterKey: 'redis-room-cluster', zoneKey: 'room-a', state: 'CRITICAL', cpuPercent: 77, memoryPercent: 94, swapPercent: 68, diskIoPercent: 91, networkJitterMs: 2.2, packetLossPercent: 0.2, connectionCount: 2160, jvmThreadCount: 62, gcPauseMs: 0, lastError: 'REDIS_TIMEOUT', lastSeenAt: null, attributes: {} },
  ];
  return {
    overview,
    topology: { services, dependencies, connectors },
    services,
    incidents,
    connectors,
    signals: [],
    requestFlows,
    transactions,
    hosts,
  };
}

function normalizeBase(rawValue: unknown, fallback: string): string {
  if (typeof rawValue !== 'string' || rawValue.trim().length === 0) {
    return fallback;
  }
  return rawValue.trim().replace(/\/+$/, '');
}

function inferSourceMode(
  services: PlatformServiceNode[],
  connectors: PlatformConnectorStatus[],
  signals: PlatformSignal[],
): PlatformSourceMode {
  if (services.length === 0 && connectors.length === 0 && signals.length === 0) {
    return 'UNAVAILABLE';
  }
  const hasDemoConnector = connectors.some((connector) =>
    [connector.connectorKey, connector.displayName, connector.lastMessage].some((value) => value.toLowerCase().includes('demo')),
  );
  const hasDemoSignal = signals.some((signal) => signal.attributes.source?.toLowerCase() === 'demo');
  return hasDemoConnector || hasDemoSignal ? 'DEMO' : 'LIVE';
}

function latestConnectorSeenAt(connectors: PlatformConnectorStatus[]): string | null {
  const sorted = connectors
    .map((connector) => connector.lastSeenAt)
    .filter((value): value is string => !!value)
    .sort();
  return sorted.length > 0 ? sorted[sorted.length - 1] : null;
}

function toSourceMode(value: string): PlatformSourceMode {
  const normalized = value.toUpperCase();
  return normalized === 'DEMO' || normalized === 'MOCK' || normalized === 'LIVE' || normalized === 'STALE' || normalized === 'UNAVAILABLE'
    ? normalized
    : 'UNKNOWN';
}

function normalizeDataMode(rawValue: unknown): 'api' | 'mock' | 'auto' {
  return rawValue === 'api' || rawValue === 'mock' || rawValue === 'auto' ? rawValue : 'auto';
}

function readItems(data: unknown): unknown[] {
  if (Array.isArray(data)) {
    return data;
  }
  const record = asRecord(data);
  return Array.isArray(record.items) ? record.items : [];
}

function asRecord(data: unknown): JsonRecord {
  return data && typeof data === 'object' && !Array.isArray(data) ? (data as JsonRecord) : {};
}

function readString(record: JsonRecord, key: string, fallback: string): string {
  const value = record[key];
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function readNullableString(record: JsonRecord, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' && value.length > 0 ? value : null;
}

function readNumber(record: JsonRecord, key: string): number {
  const value = record[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function readBoolean(record: JsonRecord, key: string): boolean {
  return record[key] === true;
}

function readStringList(data: unknown): string[] {
  if (!Array.isArray(data)) {
    return [];
  }
  return data.filter((value): value is string => typeof value === 'string' && value.length > 0);
}

function readStringRecord(record: JsonRecord, key: string): Record<string, string> {
  const value = record[key];
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  const result: Record<string, string> = {};
  Object.entries(value as JsonRecord).forEach(([entryKey, entryValue]) => {
    if (typeof entryValue === 'string') {
      result[entryKey] = entryValue;
    }
  });
  return result;
}

function readScalarRecord(record: JsonRecord, key: string): Record<string, string | number | null> {
  const value = record[key];
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  const result: Record<string, string | number | null> = {};
  Object.entries(value as JsonRecord).forEach(([entryKey, entryValue]) => {
    if (typeof entryValue === 'string' || typeof entryValue === 'number' || entryValue === null) {
      result[entryKey] = entryValue;
    }
  });
  return result;
}
