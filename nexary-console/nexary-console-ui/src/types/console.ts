export type ResourceKind = 'CACHE' | 'MESSAGING' | 'JOB' | 'GOVERNANCE' | 'CUSTOM' | string;

export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | string;

export type CallOutcome = 'SUCCESS' | 'FAILURE' | 'REJECTED' | 'CANCELLED' | 'RETRY_STOPPED' | 'ISOLATED' | 'NONE' | string;

export type RuntimeAction =
  | 'EXECUTE'
  | 'REJECT'
  | 'FALLBACK'
  | 'CANCEL'
  | 'STOP_RETRY'
  | 'WARN'
  | 'INSTANCE_SUSPECT'
  | 'QUARANTINE_CANDIDATE'
  | 'RECOVERY_PROBE'
  | 'INSTANCE_RECOVERED'
  | string;

export type GovernanceEngine = 'LOCAL' | 'SENTINEL' | string;

export type TrafficClass = 'ONLINE' | 'OFFLINE' | 'BATCH' | 'BACKGROUND' | string;

export type GovernancePriority = 'HIGH' | 'NORMAL' | 'LOW' | string;

export type BlockReason = 'NONE' | 'RATE_LIMITED' | 'BULKHEAD_FULL' | 'CIRCUIT_OPEN' | 'DEGRADED' | 'UNKNOWN' | string;

export type CancellationReason =
  | 'NONE'
  | 'CLIENT_DISCONNECTED'
  | 'DEADLINE_EXPIRED'
  | 'UPSTREAM_CANCELLED'
  | 'MANUAL'
  | 'SHUTDOWN'
  | string;

export type RetryStopReason =
  | 'NONE'
  | 'DEADLINE_EXPIRED'
  | 'CANCELLED'
  | 'CLIENT_DISCONNECTED'
  | 'UPSTREAM_CANCELLED'
  | 'SHUTDOWN'
  | 'RATE_LIMITED'
  | 'BULKHEAD_FULL'
  | 'CIRCUIT_OPEN'
  | 'DEGRADED'
  | 'RETRY_EXHAUSTED'
  | 'TIMEOUT'
  | 'REJECTED'
  | 'UNKNOWN'
  | string;

export type RejectionReason =
  | 'NONE'
  | 'RATE_LIMITED'
  | 'CONCURRENCY_LIMITED'
  | 'CIRCUIT_OPEN'
  | 'HALF_OPEN_LIMITED'
  | 'DEADLINE_EXPIRED'
  | 'DEGRADED'
  | string;

export type IsolationReason =
  | 'NONE'
  | 'PRIORITY_RATE_LIMITED'
  | 'PRIORITY_BULKHEAD_FULL'
  | 'PRIORITY_DEGRADED'
  | 'PRIORITY_CIRCUIT_OPEN'
  | 'MIXED_TRAFFIC'
  | 'UNKNOWN'
  | string;

export type InstanceHealthState = 'HEALTHY' | 'SUSPECT' | 'QUARANTINE_CANDIDATE' | 'RECOVERING' | string;

export type InstanceQuarantineReason =
  | 'NONE'
  | 'CONNECT_TIMEOUT_SPIKE'
  | 'RESET_SPIKE'
  | 'READ_TIMEOUT_SPIKE'
  | 'SERVER_ERROR_RATIO'
  | 'SLOW_RATIO'
  | 'STATUS_CODE_SKEW'
  | string;

export type InstanceRecoveryAdvice =
  | 'NONE'
  | 'BACKOFF'
  | 'QUARANTINE_CANDIDATE'
  | 'MANUAL_ACTION_REQUIRED'
  | 'RECOVERY_PROBE'
  | string;

export type DurationBucket = 'NOT_RUN' | 'LT_10_MS' | 'LT_100_MS' | 'LT_500_MS' | 'GE_500_MS' | string;

export type TraceStage =
  | 'REQUEST'
  | 'GOVERNANCE'
  | 'DOWNSTREAM'
  | 'CACHE'
  | 'MESSAGING'
  | 'JOB'
  | 'INSTANCE_HEALTH'
  | 'RETRY'
  | string;

export type TraceStopReason =
  | 'NONE'
  | 'DEADLINE_EXPIRED'
  | 'CANCELLED'
  | 'RETRY_STOPPED'
  | 'BLOCKED'
  | 'REJECTED'
  | 'ISOLATED'
  | 'INSTANCE_QUARANTINE_CANDIDATE'
  | 'FAILURE'
  | string;

export interface ConsoleSummary {
  resourceCount: number;
  snapshotCount: number;
  eventCount: number;
  successCount: number;
  failureCount: number;
  rejectedCount: number;
  cancelledCount: number;
  fallbackCount: number;
  retryStoppedCount?: number;
  blockedCount?: number;
  isolatedCount?: number;
  sentinelResourceCount?: number;
  instanceSuspectCount?: number;
  quarantineCandidateCount?: number;
  recoveryProbeCount?: number;
  faultTraceCount?: number;
  stoppedTraceCount?: number;
  trafficClassCounts?: Record<string, number>;
  priorityCounts?: Record<string, number>;
  openCircuitCount: number;
  halfOpenCircuitCount: number;
  degradedResourceCount: number;
  lastEventAt: string | null;
}

export interface ConsolePolicySnapshot {
  maxRequestsPerWindow: number;
  rateLimitWindow: string | null;
  maxConcurrency: number;
  degraded: boolean;
  minimumRequests: number;
  failureRateThreshold: number | null;
  slowCallThreshold: number | null;
  slowCallDuration: string | null;
  openStateDuration: string | null;
  halfOpenMaxCalls: number;
  slidingWindowSize: number;
  slidingWindowDuration: string | null;
  consecutiveFailureThreshold: number;
}

export interface ConsoleRuntimeSnapshot {
  resourceKey: string;
  engine?: GovernanceEngine | null;
  trafficClass?: string;
  priority: string;
  circuitState: CircuitState;
  windowCalls: number;
  windowFailures: number;
  windowSlowCalls: number;
  consecutiveFailures: number;
  totalRejections: number;
  lastRejectionReason: RejectionReason;
  lastBlockReason?: BlockReason | null;
  lastIsolationReason?: IsolationReason | null;
  lastCancellationReason: CancellationReason;
  lastRetryStopReason?: RetryStopReason | null;
  openUntil: string | null;
  activeConcurrency: number;
  maxConcurrency: number;
  maxRequestsPerWindow: number;
  rateLimitWindow: string | null;
  degraded: boolean;
  minimumRequests: number;
  failureRateThreshold: number | null;
  slowCallThreshold: number | null;
  slowCallDuration: string | null;
  openStateDuration: string | null;
  halfOpenMaxCalls: number;
  slidingWindowSize: number;
  slidingWindowDuration: string | null;
  consecutiveFailureThreshold: number;
  lastStateTransitionAt: string | null;
  lastOutcome: CallOutcome;
  lastOutcomeAt: string | null;
}

export interface ConsoleInstanceHealthSnapshot {
  resourceKey: string;
  serviceKey: string;
  instanceKey: string;
  zone: string;
  state: InstanceHealthState;
  quarantineReason: InstanceQuarantineReason;
  recoveryAdvice: InstanceRecoveryAdvice;
  windowCalls: number;
  failureCount: number;
  slowCallCount: number;
  timeoutCount: number;
  resetCount: number;
  serverErrorCount: number;
  failureRatio: number;
  slowRatio: number;
  timeoutRatio: number;
  skewFactor: number;
  lastSignalAt: string | null;
  lastChangedAt: string | null;
}

export interface ConsoleResource {
  resourceKey: string;
  engine?: GovernanceEngine | null;
  kind: ResourceKind;
  name: string;
  provider: string;
  operation: string;
  trafficClass?: string;
  priority: string;
  policySnapshot: ConsolePolicySnapshot;
  runtimeSnapshot: ConsoleRuntimeSnapshot | null;
  instanceHealthSnapshots?: readonly ConsoleInstanceHealthSnapshot[];
  lastTraceOutcome?: CallOutcome | null;
  lastTraceStopReason?: TraceStopReason | null;
}

export interface ConsoleEvent {
  resourceKey: string;
  engine?: GovernanceEngine | null;
  trafficClass?: TrafficClass | null;
  priority?: GovernancePriority | null;
  action: RuntimeAction;
  outcome: CallOutcome;
  rejectionReason: RejectionReason;
  isolationReason?: IsolationReason | null;
  blockReason?: BlockReason | null;
  cancellationReason: CancellationReason;
  retryStopReason?: RetryStopReason | null;
  instanceHealthState?: InstanceHealthState | null;
  quarantineReason?: InstanceQuarantineReason | null;
  recoveryAdvice?: InstanceRecoveryAdvice | null;
  traceStage?: TraceStage | null;
  tracePrimaryStopReason?: TraceStopReason | null;
  circuitState: CircuitState;
  timestamp: string | null;
  durationBucket: DurationBucket;
}

export interface ConsoleTraceStep {
  stage: TraceStage;
  resourceKey: string;
  action: RuntimeAction;
  outcome: CallOutcome;
  durationBucket: DurationBucket;
  timestamp: string | null;
  rejectionReason?: RejectionReason | null;
  blockReason?: BlockReason | null;
  cancellationReason?: CancellationReason | null;
  retryStopReason?: RetryStopReason | null;
  isolationReason?: IsolationReason | null;
  instanceHealthState?: InstanceHealthState | null;
  quarantineReason?: InstanceQuarantineReason | null;
}

export interface ConsoleFaultTrace {
  traceKey: string;
  rootResourceKey: string;
  startedAt: string | null;
  lastEventAt: string | null;
  terminalOutcome: CallOutcome;
  primaryStopReason: TraceStopReason;
  suggestedResourceKey: string | null;
  steps: ConsoleTraceStep[];
}

export interface ConsoleFaultTraceSummary {
  traceCount: number;
  stoppedCount: number;
  blockedCount: number;
  cancelledCount: number;
  retryStoppedCount: number;
  instanceRelatedCount: number;
  topStopReasons: Record<string, number>;
}

export interface ConsoleSettings {
  apiBase: string;
  dataMode: 'auto' | 'api' | 'mock';
  readonly: true;
  refreshIntervalMs: number;
  endpointPaths: {
    summary: string;
    resources: string;
    resourceDetail: string;
    events: string;
    traces: string;
    traceDetail: string;
    faultSummary: string;
  };
}

export interface ConsoleDataSet {
  summary: ConsoleSummary;
  resources: ConsoleResource[];
  events: ConsoleEvent[];
  traces: ConsoleFaultTrace[];
  faultTraceSummary: ConsoleFaultTraceSummary;
  settings: ConsoleSettings;
}

export interface ResourceFilters {
  keyword: string;
  engine: string;
  kind: string;
  circuitState: string;
  provider: string;
  trafficClass: string;
  priority: string;
}

export interface EventFilters {
  keyword: string;
  outcome: string;
  rejectionReason: string;
  isolationReason: string;
  trafficClass: string;
  priority: string;
  traceStage: string;
  traceStopReason: string;
  circuitState: string;
}
