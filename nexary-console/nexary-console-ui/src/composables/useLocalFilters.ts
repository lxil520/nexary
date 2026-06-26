import { computed, type Ref } from 'vue';
import type { ConsoleEvent, ConsoleResource, EventFilters, ResourceFilters } from '../types/console';

export function useFilteredResources(resources: Ref<readonly ConsoleResource[]>, filters: Ref<ResourceFilters>) {
  return computed(() => {
    const keyword = normalize(filters.value.keyword);
    return resources.value.filter((resource) => {
      const runtime = resource.runtimeSnapshot;
      const circuitState = runtime?.circuitState ?? 'NO_STATE';
      const matchesKeyword =
        keyword.length === 0 ||
        normalize(
          `${resource.resourceKey} ${resource.engine ?? ''} ${resource.kind} ${resource.name} ${resource.provider} ${resource.operation} ${resource.trafficClass ?? ''} ${resource.priority}`,
        ).includes(keyword);
      const matchesEngine = filters.value.engine === 'ALL' || resource.engine === filters.value.engine;
      const matchesKind = filters.value.kind === 'ALL' || resource.kind === filters.value.kind;
      const matchesCircuit =
        filters.value.circuitState === 'ALL' || circuitState === filters.value.circuitState;
      const matchesProvider = filters.value.provider === 'ALL' || resource.provider === filters.value.provider;
      const matchesTraffic = filters.value.trafficClass === 'ALL' || resource.trafficClass === filters.value.trafficClass;
      const matchesPriority = filters.value.priority === 'ALL' || resource.priority === filters.value.priority;
      return matchesKeyword && matchesEngine && matchesKind && matchesCircuit && matchesProvider && matchesTraffic && matchesPriority;
    });
  });
}

export function useFilteredEvents(events: Ref<readonly ConsoleEvent[]>, filters: Ref<EventFilters>) {
  return computed(() => {
    const keyword = normalize(filters.value.keyword);
    return events.value.filter((event) => {
      const matchesKeyword =
        keyword.length === 0 ||
        normalize(
          `${event.resourceKey} ${event.engine ?? ''} ${event.trafficClass ?? ''} ${event.priority ?? ''} ${event.action} ${event.outcome} ${event.rejectionReason} ${event.isolationReason ?? ''} ${event.blockReason ?? ''} ${event.traceStage ?? ''} ${event.tracePrimaryStopReason ?? ''} ${event.circuitState} ${event.durationBucket}`,
        ).includes(keyword);
      const matchesOutcome = filters.value.outcome === 'ALL' || event.outcome === filters.value.outcome;
      const matchesReason =
        filters.value.rejectionReason === 'ALL' || event.rejectionReason === filters.value.rejectionReason;
      const matchesIsolation =
        filters.value.isolationReason === 'ALL' || event.isolationReason === filters.value.isolationReason;
      const matchesTraffic = filters.value.trafficClass === 'ALL' || event.trafficClass === filters.value.trafficClass;
      const matchesPriority = filters.value.priority === 'ALL' || event.priority === filters.value.priority;
      const matchesTraceStage = filters.value.traceStage === 'ALL' || event.traceStage === filters.value.traceStage;
      const matchesTraceStop =
        filters.value.traceStopReason === 'ALL' || event.tracePrimaryStopReason === filters.value.traceStopReason;
      const matchesCircuit =
        filters.value.circuitState === 'ALL' || event.circuitState === filters.value.circuitState;
      return (
        matchesKeyword &&
        matchesOutcome &&
        matchesReason &&
        matchesIsolation &&
        matchesTraffic &&
        matchesPriority &&
        matchesTraceStage &&
        matchesTraceStop &&
        matchesCircuit
      );
    });
  });
}

export function uniqueSorted(values: readonly string[]): string[] {
  return Array.from(new Set(values.filter((value) => value.trim().length > 0))).sort((left, right) =>
    left.localeCompare(right),
  );
}

function normalize(value: string): string {
  return value.trim().toLowerCase();
}
