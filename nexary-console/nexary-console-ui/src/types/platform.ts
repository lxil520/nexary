export type PlatformSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type PlatformConnectorState = 'DISABLED' | 'HEALTHY' | 'DEGRADED' | 'FAILED';
export type PlatformSourceMode = 'DEMO' | 'MOCK' | 'LIVE' | 'STALE' | 'UNAVAILABLE' | 'UNKNOWN';

export interface PlatformDataFreshness {
  state: PlatformSourceMode;
  generatedAt: string | null;
  lastSignalAt: string | null;
  lastConnectorSeenAt: string | null;
  staleAfterSeconds: number;
}

export interface PlatformDataSource {
  sourceKey: string;
  kind: string;
  state: string;
  displayName: string;
  lastSeenAt: string | null;
  lastMessage: string;
  mode: PlatformSourceMode;
}

export interface PlatformServiceNode {
  serviceKey: string;
  name: string;
  teamKey: string;
  environmentKey: string;
  clusterKey: string;
  zoneKey: string;
  warningCount: number;
  criticalCount: number;
  attributes: Record<string, string>;
}

export interface PlatformDependencyEdge {
  sourceKey: string;
  targetKey: string;
  kind: string;
  resourceKey: string;
  warningCount: number;
  criticalCount: number;
  attributes: Record<string, string>;
}

export interface PlatformConnectorStatus {
  connectorKey: string;
  kind: string;
  state: PlatformConnectorState;
  displayName: string;
  lastMessage: string;
  lastSeenAt: string | null;
}

export interface PlatformImpactScope {
  serviceKey: string;
  clusterKey: string;
  zoneKey: string;
}

export interface PlatformEvidenceItem {
  signalType: string;
  severity: PlatformSeverity;
  serviceKey: string;
  clusterKey: string;
  zoneKey: string;
  resourceKey: string;
  outcome: string;
  durationBucket: string;
  message: string;
  referenceType: string;
  referenceKey: string;
  timestamp: string | null;
}

export interface PlatformEvidenceRef {
  type: string;
  refKey: string;
  label: string;
  href: string;
}

export interface PlatformSpan {
  spanId: string;
  parentSpanId: string;
  serviceKey: string;
  resourceKey: string;
  component: string;
  operation: string;
  startOffsetMs: number;
  durationMs: number;
  status: string;
  errorType: string;
  evidenceRefs: PlatformEvidenceRef[];
}

export interface PlatformRequestFlow {
  traceKey: string;
  entryServiceKey: string;
  endpointKey: string;
  zoneKey: string;
  status: string;
  durationMs: number;
  startedAt: string | null;
  spanCount: number;
  primaryError: string;
  summary: string;
  spans: PlatformSpan[];
  evidenceRefs: PlatformEvidenceRef[];
}

export interface PlatformTraceQuery {
  from?: string | null;
  to?: string | null;
  serviceKey?: string | null;
  endpointKey?: string | null;
  status?: string | null;
  minDurationMs?: number | null;
  resourceKey?: string | null;
  source?: string | null;
  sort?: string | null;
  page?: number | null;
  size?: number | null;
}

export interface PlatformTraceQueryResult {
  items: PlatformRequestFlow[];
  page: number;
  size: number;
  total: number;
  sort: string;
  filters: Record<string, string | number | null>;
}

export interface PlatformTransactionMetric {
  serviceKey: string;
  endpointKey: string;
  zoneKey: string;
  windowStart: string | null;
  windowEnd: string | null;
  total: number;
  failure: number;
  failureRate: number;
  tps: number;
  qps: number;
  minMs: number;
  maxMs: number;
  avgMs: number;
  p95Ms: number;
  p99Ms: number;
  sampleTraceKey: string;
}

export interface PlatformHostSignal {
  hostKey: string;
  serviceKey: string;
  clusterKey: string;
  zoneKey: string;
  state: string;
  cpuPercent: number;
  memoryPercent: number;
  swapPercent: number;
  diskIoPercent: number;
  networkJitterMs: number;
  packetLossPercent: number;
  connectionCount: number;
  jvmThreadCount: number;
  gcPauseMs: number;
  lastError: string;
  lastSeenAt: string | null;
  attributes: Record<string, string>;
}

export interface PlatformSuggestedCheck {
  resourceKey: string;
  message: string;
}

export interface PlatformIncidentCandidate {
  incidentKey: string;
  title: string;
  severity: PlatformSeverity;
  impactScope: PlatformImpactScope;
  evidence: PlatformEvidenceItem[];
  suggestedCheck: PlatformSuggestedCheck | null;
  startedAt: string | null;
  lastSeenAt: string | null;
  primaryResourceKey: string;
  evidenceCount: number;
  impactedResourceCount: number;
}

export interface PlatformSignal {
  workspaceKey: string;
  environmentKey: string;
  serviceKey: string;
  clusterKey: string;
  zoneKey: string;
  resourceKey: string;
  signalType: string;
  severity: PlatformSeverity;
  outcome: string;
  durationBucket: string;
  timestamp: string | null;
  attributes: Record<string, string>;
}

export interface PlatformTopology {
  services: PlatformServiceNode[];
  dependencies: PlatformDependencyEdge[];
  connectors: PlatformConnectorStatus[];
}

export interface PlatformOverviewSummary {
  workspaceKey: string;
  environmentKey: string;
  health: string;
  criticalIncidents: number;
  warningIncidents: number;
  serviceCount: number;
  zoneCount: number;
  dependencyCount: number;
  connectorCount: number;
  middlewareCount: number;
  openPolicyPlans: number;
  notificationRoutes: number;
  lastSignalAt: string | null;
}

export interface PlatformServiceWatermark {
  serviceKey: string;
  name: string;
  teamKey: string;
  environmentKey: string;
  clusterKey: string;
  zoneKey: string;
  qps: number;
  errorRate: number;
  p95Ms: number;
  p99Ms: number;
  instanceCount: number;
  cpuPercent: number;
  memoryPercent: number;
  watermarkPercent: number;
  sentinelState: string;
  gatewayState: string;
  warningCount: number;
  criticalCount: number;
  activeIncidents: number;
  state: string;
}

export interface PlatformZoneWatermark {
  zoneKey: string;
  environmentKey: string;
  serviceCount: number;
  warningCount: number;
  criticalCount: number;
  cpuPercent: number;
  memoryPercent: number;
  networkJitterMs: number;
  packetLossPercent: number;
  httpFailureRate: number;
  state: string;
}

export interface PlatformMiddlewareWatermark {
  middlewareKey: string;
  name: string;
  kind: string;
  environmentKey: string;
  zoneKey: string;
  usagePercent: number;
  latencyMs: number;
  errorRate: number;
  connectedServices: number;
  warningCount: number;
  criticalCount: number;
  state: string;
}

export interface PlatformPolicyPlan {
  planKey: string;
  title: string;
  serviceKey: string;
  resourceKey: string;
  signalType: string;
  state: string;
  risk: string;
  proposedAction: string;
  evidenceCount: number;
  lastSeenAt: string | null;
}

export interface PlatformNotificationRoute {
  routeKey: string;
  channel: string;
  displayName: string;
  targetTeam: string;
  minSeverity: string;
  state: string;
  dryRun: boolean;
  lastMessage: string;
  boundIncidentCount: number;
}

export interface PlatformOverview {
  summary: PlatformOverviewSummary;
  serviceWatermarks: PlatformServiceWatermark[];
  zoneWatermarks: PlatformZoneWatermark[];
  middlewareWatermarks: PlatformMiddlewareWatermark[];
  policyPlans: PlatformPolicyPlan[];
  notificationRoutes: PlatformNotificationRoute[];
}

export interface PlatformSnapshot {
  sourceMode: PlatformSourceMode;
  generatedAt: string | null;
  freshness: PlatformDataFreshness;
  dataSources: PlatformDataSource[];
  warnings: string[];
  overview: PlatformOverview;
  topology: PlatformTopology;
  services: PlatformServiceNode[];
  incidents: PlatformIncidentCandidate[];
  connectors: PlatformConnectorStatus[];
  signals: PlatformSignal[];
  requestFlows: PlatformRequestFlow[];
  transactions: PlatformTransactionMetric[];
  hosts: PlatformHostSignal[];
}
