import { mockConsoleData } from './mockConsoleData';
import type {
  CallOutcome,
  CircuitState,
  ConsoleEvent,
  ConsolePolicySnapshot,
  ConsoleResource,
  ConsoleRuntimeSnapshot,
  ConsoleSettings,
  ConsoleSummary,
  DurationBucket,
  RejectionReason,
  ResourceKind,
  RuntimeAction,
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
    fallbackCount: readNumber(record, 'fallbackCount'),
    openCircuitCount: readNumber(record, 'openCircuitCount'),
    halfOpenCircuitCount: readNumber(record, 'halfOpenCircuitCount'),
    degradedResourceCount: readNumber(record, 'degradedResourceCount'),
    lastEventAt: readNullableString(record, 'lastEventAt'),
  };
}

function toResource(data: unknown): ConsoleResource {
  const record = asRecord(data);
  return {
    resourceKey: readString(record, 'resourceKey', readString(record, 'id', 'custom:unknown:unknown:default')),
    kind: readString(record, 'kind', 'CUSTOM') as ResourceKind,
    name: readString(record, 'name', 'unknown'),
    provider: readString(record, 'provider', 'unknown'),
    operation: readString(record, 'operation', 'default'),
    priority: readString(record, 'priority', 'normal'),
    policySnapshot: toPolicySnapshot(record.policySnapshot ?? record.policy),
    runtimeSnapshot: readOptionalSnapshot(record.runtimeSnapshot ?? record.runtime),
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
    priority: readString(record, 'priority', 'normal'),
    circuitState: readString(record, 'circuitState', 'CLOSED') as CircuitState,
    windowCalls: readNumber(record, 'windowCalls'),
    windowFailures: readNumber(record, 'windowFailures'),
    windowSlowCalls: readNumber(record, 'windowSlowCalls'),
    consecutiveFailures: readNumber(record, 'consecutiveFailures'),
    totalRejections: readNumber(record, 'totalRejections'),
    lastRejectionReason: readString(record, 'lastRejectionReason', 'NONE') as RejectionReason,
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
    action: readString(record, 'action', 'EXECUTE') as RuntimeAction,
    outcome: readString(record, 'outcome', 'NONE') as CallOutcome,
    rejectionReason: readString(record, 'rejectionReason', 'NONE') as RejectionReason,
    circuitState: readString(record, 'circuitState', 'CLOSED') as CircuitState,
    timestamp: readNullableString(record, 'timestamp'),
    durationBucket: readString(record, 'durationBucket', 'NOT_RUN') as DurationBucket,
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
