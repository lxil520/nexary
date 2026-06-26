import { mockConsoleData } from './mockConsoleData';
import type {
  CallOutcome,
  CancellationReason,
  CircuitState,
  ConsoleEvent,
  ConsoleFaultTrace,
  ConsoleFaultTraceSummary,
  ConsolePolicySnapshot,
  ConsoleResource,
  ConsoleRuntimeSnapshot,
  ConsoleSettings,
  ConsoleSummary,
  ConsoleTraceStep,
  DurationBucket,
  GovernanceEngine,
  BlockReason,
  InstanceHealthState,
  InstanceQuarantineReason,
  InstanceRecoveryAdvice,
  IsolationReason,
  RejectionReason,
  ResourceKind,
  RetryStopReason,
  RuntimeAction,
  TraceStage,
  TraceStopReason,
  TrafficClass,
  GovernancePriority,
} from '../types/console';

type DataMode = ConsoleSettings['dataMode'];
type JsonRecord = Record<string, unknown>;

const defaultApiBase = '/nexary/console/api';
const defaultRefreshIntervalMs = 15_000;

const dataMode = normalizeDataMode(import.meta.env.VITE_NEXARY_CONSOLE_DATA);
const apiBase = normalizeBase(import.meta.env.VITE_NEXARY_CONSOLE_API_BASE);

export const consoleSettings: ConsoleSettings = {
  apiBase,
  dataMode,
  readonly: true,
  refreshIntervalMs: defaultRefreshIntervalMs,
  endpointPaths: {
    summary: '/summary',
    resources: '/resources',
    resourceDetail: '/resources/{resourceKey}',
    events: '/events',
    traces: '/traces',
    traceDetail: '/traces/{traceKey}',
    faultSummary: '/faults/summary',
  },
};

export async function fetchSummary(): Promise<ConsoleSummary> {
  return withFallback(
    () => fetchJson(consoleSettings.endpointPaths.summary).then(toSummary),
    () => mockConsoleData.summary,
  );
}

export async function fetchResources(): Promise<ConsoleResource[]> {
  return withFallback(
    () => fetchJson(consoleSettings.endpointPaths.resources).then((data) => {
      return readItems(data).map(toResource);
    }),
    () => mockConsoleData.resources,
  );
}

export async function fetchResource(resourceKey: string): Promise<ConsoleResource | null> {
  const encodedKey = encodeURIComponent(resourceKey);
  return withFallback(
    () => fetchJson(`/resources/${encodedKey}`).then(toResource),
    () => mockConsoleData.resources.find((resource) => resource.resourceKey === resourceKey) ?? null,
  );
}

export async function fetchEvents(): Promise<ConsoleEvent[]> {
  return withFallback(
    () => fetchJson(consoleSettings.endpointPaths.events).then((data) => {
      return readItems(data).map(toEvent);
    }),
    () => mockConsoleData.events,
  );
}

export async function fetchTraces(): Promise<ConsoleFaultTrace[]> {
  return withFallback(
    () => fetchJson(consoleSettings.endpointPaths.traces).then((data) => {
      return readItems(data).map(toFaultTrace);
    }),
    () => mockConsoleData.traces,
  );
}

export async function fetchTrace(traceKey: string): Promise<ConsoleFaultTrace | null> {
  const encodedKey = encodeURIComponent(traceKey);
  return withFallback(
    () => fetchJson(`/traces/${encodedKey}`).then(toFaultTrace),
    () => mockConsoleData.traces.find((trace) => trace.traceKey === traceKey) ?? null,
  );
}

export async function fetchFaultTraceSummary(): Promise<ConsoleFaultTraceSummary> {
  return withFallback(
    () => fetchJson(consoleSettings.endpointPaths.faultSummary).then(toFaultTraceSummary),
    () => mockConsoleData.faultTraceSummary,
  );
}

export function getConsoleSettings(): ConsoleSettings {
  return consoleSettings;
}

async function withFallback<T>(apiCall: () => Promise<T>, fallback: () => T): Promise<T> {
  if (dataMode === 'mock') {
    return fallback();
  }
  try {
    return await apiCall();
  } catch (error) {
    if (dataMode === 'api') {
      throw error;
    }
    return fallback();
  }
}

async function fetchJson(path: string): Promise<unknown> {
  const response = await fetch(`${apiBase}${path}`, {
    headers: {
      Accept: 'application/json',
    },
  });
  if (!response.ok) {
    throw new Error(`Console API ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<unknown>;
}

function toSummary(data: unknown): ConsoleSummary {
  const record = asRecord(data);
  return {
    resourceCount: readNumber(record, 'resourceCount'),
    snapshotCount: readNumber(record, 'snapshotCount'),
    eventCount: readNumber(record, 'eventCount'),
    successCount: readNumber(record, 'successCount'),
    failureCount: readNumber(record, 'failureCount'),
    rejectedCount: readNumber(record, 'rejectedCount'),
    cancelledCount: readNumber(record, 'cancelledCount'),
    fallbackCount: readNumber(record, 'fallbackCount'),
    retryStoppedCount: readNumber(record, 'retryStoppedCount'),
    blockedCount: readNumber(record, 'blockedCount'),
    isolatedCount: readNumber(record, 'isolatedCount'),
    sentinelResourceCount: readNumber(record, 'sentinelResourceCount'),
    openCircuitCount: readNumber(record, 'openCircuitCount'),
    halfOpenCircuitCount: readNumber(record, 'halfOpenCircuitCount'),
    degradedResourceCount: readNumber(record, 'degradedResourceCount'),
    instanceSuspectCount: readNumber(record, 'instanceSuspectCount'),
    quarantineCandidateCount: readNumber(record, 'quarantineCandidateCount'),
    recoveryProbeCount: readNumber(record, 'recoveryProbeCount'),
    faultTraceCount: readNumber(record, 'faultTraceCount'),
    stoppedTraceCount: readNumber(record, 'stoppedTraceCount'),
    trafficClassCounts: readRecordOfNumbers(record, 'trafficClassCounts'),
    priorityCounts: readRecordOfNumbers(record, 'priorityCounts'),
    lastEventAt: readNullableString(record, 'lastEventAt'),
  };
}

function toResource(data: unknown): ConsoleResource {
  const record = asRecord(data);
  return {
    resourceKey: readString(record, 'resourceKey', readString(record, 'id', 'custom:unknown:unknown:default')),
    kind: readString(record, 'kind', 'CUSTOM') as ResourceKind,
    engine: readString(record, 'engine', 'LOCAL') as GovernanceEngine,
    name: readString(record, 'name', 'unknown'),
    provider: readString(record, 'provider', 'unknown'),
    operation: readString(record, 'operation', 'default'),
    trafficClass: readString(record, 'trafficClass', 'ONLINE'),
    priority: readString(record, 'priority', 'normal'),
    policySnapshot: toPolicySnapshot(record.policySnapshot ?? record.policy),
    runtimeSnapshot: readOptionalSnapshot(record.runtimeSnapshot ?? record.runtime),
    instanceHealthSnapshots: readItems(record.instanceHealthSnapshots).map(toInstanceHealthSnapshot),
    lastTraceOutcome: readString(record, 'lastTraceOutcome', 'NONE') as CallOutcome,
    lastTraceStopReason: readString(record, 'lastTraceStopReason', 'NONE') as TraceStopReason,
  };
}

function toPolicySnapshot(data: unknown): ConsolePolicySnapshot {
  const record = asRecord(data);
  return {
    maxRequestsPerWindow: readNumber(record, 'maxRequestsPerWindow', 0),
    rateLimitWindow: readNullableString(record, 'rateLimitWindow'),
    maxConcurrency: readNumber(record, 'maxConcurrency', 0),
    degraded: readBoolean(record, 'degraded'),
    minimumRequests: readNumber(record, 'minimumRequests', 0),
    failureRateThreshold: readNullableNumber(record, 'failureRateThreshold'),
    slowCallThreshold: readNullableNumber(record, 'slowCallThreshold'),
    slowCallDuration: readNullableString(record, 'slowCallDuration'),
    openStateDuration: readNullableString(record, 'openStateDuration'),
    halfOpenMaxCalls: readNumber(record, 'halfOpenMaxCalls', 0),
    slidingWindowSize: readNumber(record, 'slidingWindowSize', 0),
    slidingWindowDuration: readNullableString(record, 'slidingWindowDuration'),
    consecutiveFailureThreshold: readNumber(record, 'consecutiveFailureThreshold', 0),
  };
}

function toRuntimeSnapshot(data: unknown): ConsoleRuntimeSnapshot {
  const record = asRecord(data);
  return {
    resourceKey: readString(record, 'resourceKey', readString(record, 'resourceId', 'custom:unknown:unknown:default')),
    engine: readString(record, 'engine', 'LOCAL') as GovernanceEngine,
    trafficClass: readString(record, 'trafficClass', 'ONLINE'),
    priority: readString(record, 'priority', 'normal'),
    circuitState: readString(record, 'circuitState', 'CLOSED') as CircuitState,
    windowCalls: readNumber(record, 'windowCalls'),
    windowFailures: readNumber(record, 'windowFailures'),
    windowSlowCalls: readNumber(record, 'windowSlowCalls'),
    consecutiveFailures: readNumber(record, 'consecutiveFailures'),
    totalRejections: readNumber(record, 'totalRejections'),
    lastRejectionReason: readString(record, 'lastRejectionReason', 'NONE') as RejectionReason,
    lastBlockReason: readString(record, 'lastBlockReason', 'NONE') as BlockReason,
    lastIsolationReason: readString(record, 'lastIsolationReason', 'NONE') as IsolationReason,
    lastCancellationReason: readString(record, 'lastCancellationReason', 'NONE') as CancellationReason,
    lastRetryStopReason: readString(record, 'lastRetryStopReason', 'NONE') as RetryStopReason,
    openUntil: readNullableString(record, 'openUntil'),
    activeConcurrency: readNumber(record, 'activeConcurrency'),
    maxConcurrency: readNumber(record, 'maxConcurrency'),
    maxRequestsPerWindow: readNumber(record, 'maxRequestsPerWindow'),
    rateLimitWindow: readNullableString(record, 'rateLimitWindow'),
    degraded: readBoolean(record, 'degraded'),
    minimumRequests: readNumber(record, 'minimumRequests'),
    failureRateThreshold: readNullableNumber(record, 'failureRateThreshold'),
    slowCallThreshold: readNullableNumber(record, 'slowCallThreshold'),
    slowCallDuration: readNullableString(record, 'slowCallDuration'),
    openStateDuration: readNullableString(record, 'openStateDuration'),
    halfOpenMaxCalls: readNumber(record, 'halfOpenMaxCalls'),
    slidingWindowSize: readNumber(record, 'slidingWindowSize'),
    slidingWindowDuration: readNullableString(record, 'slidingWindowDuration'),
    consecutiveFailureThreshold: readNumber(record, 'consecutiveFailureThreshold'),
    lastStateTransitionAt: readNullableString(record, 'lastStateTransitionAt'),
    lastOutcome: readString(record, 'lastOutcome', 'NONE') as CallOutcome,
    lastOutcomeAt: readNullableString(record, 'lastOutcomeAt'),
  };
}

function toEvent(data: unknown): ConsoleEvent {
  const record = asRecord(data);
  return {
    resourceKey: readString(record, 'resourceKey', readString(record, 'resourceId', 'custom:unknown:unknown:default')),
    engine: readString(record, 'engine', 'LOCAL') as GovernanceEngine,
    trafficClass: readString(record, 'trafficClass', 'ONLINE') as TrafficClass,
    priority: readString(record, 'priority', 'NORMAL') as GovernancePriority,
    action: readString(record, 'action', 'EXECUTE') as RuntimeAction,
    outcome: readString(record, 'outcome', 'NONE') as CallOutcome,
    rejectionReason: readString(record, 'rejectionReason', 'NONE') as RejectionReason,
    isolationReason: readString(record, 'isolationReason', 'NONE') as IsolationReason,
    blockReason: readString(record, 'blockReason', 'NONE') as BlockReason,
    cancellationReason: readString(record, 'cancellationReason', 'NONE') as CancellationReason,
    retryStopReason: readString(record, 'retryStopReason', 'NONE') as RetryStopReason,
    instanceHealthState: readString(record, 'instanceHealthState', 'HEALTHY') as InstanceHealthState,
    quarantineReason: readString(record, 'quarantineReason', 'NONE') as InstanceQuarantineReason,
    recoveryAdvice: readString(record, 'recoveryAdvice', 'NONE') as InstanceRecoveryAdvice,
    traceStage: readString(record, 'traceStage', 'GOVERNANCE') as TraceStage,
    tracePrimaryStopReason: readString(record, 'tracePrimaryStopReason', 'NONE') as TraceStopReason,
    circuitState: readString(record, 'circuitState', 'CLOSED') as CircuitState,
    timestamp: readNullableString(record, 'timestamp'),
    durationBucket: readString(record, 'durationBucket', 'NOT_RUN') as DurationBucket,
  };
}

function toFaultTrace(data: unknown): ConsoleFaultTrace {
  const record = asRecord(data);
  return {
    traceKey: readString(record, 'traceKey', 'trace-unknown'),
    rootResourceKey: readString(record, 'rootResourceKey', 'custom:unknown:unknown:default'),
    startedAt: readNullableString(record, 'startedAt'),
    lastEventAt: readNullableString(record, 'lastEventAt'),
    terminalOutcome: readString(record, 'terminalOutcome', 'NONE') as CallOutcome,
    primaryStopReason: readString(record, 'primaryStopReason', 'NONE') as TraceStopReason,
    suggestedResourceKey: readNullableString(record, 'suggestedResourceKey'),
    steps: readItems(record.steps).map(toTraceStep),
  };
}

function toTraceStep(data: unknown): ConsoleTraceStep {
  const record = asRecord(data);
  return {
    stage: readString(record, 'stage', 'GOVERNANCE') as TraceStage,
    resourceKey: readString(record, 'resourceKey', 'custom:unknown:unknown:default'),
    action: readString(record, 'action', 'EXECUTE') as RuntimeAction,
    outcome: readString(record, 'outcome', 'NONE') as CallOutcome,
    durationBucket: readString(record, 'durationBucket', 'NOT_RUN') as DurationBucket,
    timestamp: readNullableString(record, 'timestamp'),
    rejectionReason: readString(record, 'rejectionReason', 'NONE') as RejectionReason,
    blockReason: readString(record, 'blockReason', 'NONE') as BlockReason,
    cancellationReason: readString(record, 'cancellationReason', 'NONE') as CancellationReason,
    retryStopReason: readString(record, 'retryStopReason', 'NONE') as RetryStopReason,
    isolationReason: readString(record, 'isolationReason', 'NONE') as IsolationReason,
    instanceHealthState: readString(record, 'instanceHealthState', 'HEALTHY') as InstanceHealthState,
    quarantineReason: readString(record, 'quarantineReason', 'NONE') as InstanceQuarantineReason,
  };
}

function toFaultTraceSummary(data: unknown): ConsoleFaultTraceSummary {
  const record = asRecord(data);
  return {
    traceCount: readNumber(record, 'traceCount'),
    stoppedCount: readNumber(record, 'stoppedCount'),
    blockedCount: readNumber(record, 'blockedCount'),
    cancelledCount: readNumber(record, 'cancelledCount'),
    retryStoppedCount: readNumber(record, 'retryStoppedCount'),
    instanceRelatedCount: readNumber(record, 'instanceRelatedCount'),
    topStopReasons: readRecordOfNumbers(record, 'topStopReasons'),
  };
}

function toInstanceHealthSnapshot(data: unknown) {
  const record = asRecord(data);
  return {
    resourceKey: readString(record, 'resourceKey', 'custom:unknown:unknown:default'),
    serviceKey: readString(record, 'serviceKey', 'unknown'),
    instanceKey: readString(record, 'instanceKey', 'masked'),
    zone: readString(record, 'zone', 'unknown'),
    state: readString(record, 'state', 'HEALTHY') as InstanceHealthState,
    quarantineReason: readString(record, 'quarantineReason', 'NONE') as InstanceQuarantineReason,
    recoveryAdvice: readString(record, 'recoveryAdvice', 'NONE') as InstanceRecoveryAdvice,
    windowCalls: readNumber(record, 'windowCalls'),
    failureCount: readNumber(record, 'failureCount'),
    slowCallCount: readNumber(record, 'slowCallCount'),
    timeoutCount: readNumber(record, 'timeoutCount'),
    resetCount: readNumber(record, 'resetCount'),
    serverErrorCount: readNumber(record, 'serverErrorCount'),
    failureRatio: readNumber(record, 'failureRatio'),
    slowRatio: readNumber(record, 'slowRatio'),
    timeoutRatio: readNumber(record, 'timeoutRatio'),
    skewFactor: readNumber(record, 'skewFactor'),
    lastSignalAt: readNullableString(record, 'lastSignalAt'),
    lastChangedAt: readNullableString(record, 'lastChangedAt'),
  };
}

function normalizeDataMode(value: unknown): DataMode {
  return value === 'api' || value === 'mock' ? value : 'auto';
}

function normalizeBase(value: unknown): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    return defaultApiBase;
  }
  return value.trim().replace(/\/$/, '');
}

function asRecord(value: unknown): JsonRecord {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as JsonRecord)
    : {};
}

function readItems(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  const record = asRecord(value);
  return Array.isArray(record.items) ? record.items : [];
}

function readOptionalSnapshot(value: unknown): ConsoleRuntimeSnapshot | null {
  return value == null ? null : toRuntimeSnapshot(value);
}

function readString(record: JsonRecord, key: string, fallback: string): string {
  const value = record[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : fallback;
}

function readNullableString(record: JsonRecord, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

function readNumber(record: JsonRecord, key: string, fallback = 0): number {
  const value = record[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function readNullableNumber(record: JsonRecord, key: string): number | null {
  const value = record[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function readBoolean(record: JsonRecord, key: string): boolean {
  return record[key] === true;
}

function readRecordOfNumbers(record: JsonRecord, key: string): Record<string, number> {
  const value = record[key];
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  const result: Record<string, number> = {};
  for (const [entryKey, entryValue] of Object.entries(value)) {
    if (typeof entryValue === 'number' && Number.isFinite(entryValue)) {
      result[entryKey] = entryValue;
    }
  }
  return result;
}
