export type PlatformSeverity = 'INFO' | 'WARNING' | 'CRITICAL';
export type PlatformConnectorState = 'DISABLED' | 'HEALTHY' | 'DEGRADED' | 'FAILED';

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
  resourceKey: string;
  outcome: string;
  timestamp: string | null;
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
  lastSeenAt: string | null;
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

export interface PlatformSnapshot {
  topology: PlatformTopology;
  services: PlatformServiceNode[];
  incidents: PlatformIncidentCandidate[];
  connectors: PlatformConnectorStatus[];
  signals: PlatformSignal[];
}
