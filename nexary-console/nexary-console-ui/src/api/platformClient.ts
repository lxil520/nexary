import type {
  PlatformConnectorState,
  PlatformConnectorStatus,
  PlatformDependencyEdge,
  PlatformEvidenceItem,
  PlatformImpactScope,
  PlatformIncidentCandidate,
  PlatformServiceNode,
  PlatformSeverity,
  PlatformSignal,
  PlatformSnapshot,
  PlatformSuggestedCheck,
  PlatformTopology,
} from '../types/platform';

type JsonRecord = Record<string, unknown>;

const defaultPlatformApiBase = '/api/platform';
const dataMode = normalizeDataMode(import.meta.env.VITE_NEXARY_CONSOLE_DATA);
const platformApiBase = normalizeBase(import.meta.env.VITE_NEXARY_PLATFORM_API_BASE, defaultPlatformApiBase);

export async function fetchPlatformSnapshot(): Promise<PlatformSnapshot> {
  return withFallback(async () => {
    const [topology, services, incidents, connectors, signals] = await Promise.all([
      fetchJson('/topology').then(toTopology),
      fetchJson('/services').then((data) => readItems(data).map(toServiceNode)),
      fetchJson('/incidents').then((data) => readItems(data).map(toIncident)),
      fetchJson('/connectors').then((data) => readItems(data).map(toConnector)),
      fetchJson('/signals').then((data) => readItems(data).map(toSignal)),
    ]);
    return { topology, services, incidents, connectors, signals };
  }, mockPlatformSnapshot);
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
  const response = await fetch(`${platformApiBase}${path}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Platform API ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<unknown>;
}

function toTopology(data: unknown): PlatformTopology {
  const record = asRecord(data);
  return {
    services: readItems(record.services).map(toServiceNode),
    dependencies: readItems(record.dependencies).map(toDependency),
    connectors: readItems(record.connectors).map(toConnector),
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

function mockPlatformSnapshot(): PlatformSnapshot {
  const services: PlatformServiceNode[] = [
    { serviceKey: 'open-api', name: 'Open API', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'open-api-cluster', zoneKey: 'cn-east', warningCount: 1, criticalCount: 0, attributes: {} },
    { serviceKey: 'room-resource', name: 'Room Resource', teamKey: 'platform-team', environmentKey: 'prod-demo', clusterKey: 'room-resource-cluster', zoneKey: 'room-a', warningCount: 0, criticalCount: 1, attributes: {} },
  ];
  const dependencies: PlatformDependencyEdge[] = [
    { sourceKey: 'open-api', targetKey: 'redis-main', kind: 'CACHE', resourceKey: 'cache:open-api:profile', warningCount: 0, criticalCount: 0, attributes: {} },
    { sourceKey: 'room-resource', targetKey: 'redis-room', kind: 'CACHE', resourceKey: 'cache:room-resource:state', warningCount: 0, criticalCount: 1, attributes: {} },
  ];
  const connectors: PlatformConnectorStatus[] = [
    { connectorKey: 'nexary-sdk-demo', kind: 'NEXARY_SDK', state: 'HEALTHY', displayName: 'Nexary SDK', lastMessage: 'demo signals received', lastSeenAt: null },
    { connectorKey: 'sentinel-readonly-demo', kind: 'SENTINEL', state: 'DEGRADED', displayName: 'Sentinel', lastMessage: 'read-only demo data', lastSeenAt: null },
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
  return {
    topology: { services, dependencies, connectors },
    services,
    incidents,
    connectors,
    signals: [],
  };
}

function normalizeBase(rawValue: unknown, fallback: string): string {
  if (typeof rawValue !== 'string' || rawValue.trim().length === 0) {
    return fallback;
  }
  return rawValue.trim().replace(/\/+$/, '');
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
