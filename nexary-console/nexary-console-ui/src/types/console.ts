export type ResourceKind = 'CACHE' | 'MESSAGING' | 'JOB' | 'GOVERNANCE' | 'CUSTOM' | string;

export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN' | string;

export type CallOutcome = 'SUCCESS' | 'FAILURE' | 'REJECTED' | 'CANCELLED' | 'RETRY_STOPPED' | 'NONE' | string;

export type RuntimeAction = 'EXECUTE' | 'REJECT' | 'FALLBACK' | 'CANCEL' | 'STOP_RETRY' | string;

export type GovernanceEngine = 'LOCAL' | 'SENTINEL' | string;

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

export type DurationBucket = 'NOT_RUN' | 'FAST' | 'NORMAL' | 'SLOW' | string;

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
  sentinelResourceCount?: number;
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
  priority: string;
  circuitState: CircuitState;
  windowCalls: number;
  windowFailures: number;
  windowSlowCalls: number;
  consecutiveFailures: number;
  totalRejections: number;
  lastRejectionReason: RejectionReason;
  lastBlockReason?: BlockReason | null;
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

export interface ConsoleResource {
  resourceKey: string;
  engine?: GovernanceEngine | null;
  kind: ResourceKind;
  name: string;
  provider: string;
  operation: string;
  priority: string;
  policySnapshot: ConsolePolicySnapshot;
  runtimeSnapshot: ConsoleRuntimeSnapshot | null;
}

export interface ConsoleEvent {
  resourceKey: string;
  engine?: GovernanceEngine | null;
  action: RuntimeAction;
  outcome: CallOutcome;
  rejectionReason: RejectionReason;
  blockReason?: BlockReason | null;
  cancellationReason: CancellationReason;
  retryStopReason?: RetryStopReason | null;
  circuitState: CircuitState;
  timestamp: string | null;
  durationBucket: DurationBucket;
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
  };
}

export interface ConsoleDataSet {
  summary: ConsoleSummary;
  resources: ConsoleResource[];
  events: ConsoleEvent[];
  settings: ConsoleSettings;
}

export interface ResourceFilters {
  keyword: string;
  engine: string;
  kind: string;
  circuitState: string;
  provider: string;
}

export interface EventFilters {
  keyword: string;
  outcome: string;
  rejectionReason: string;
  circuitState: string;
}
