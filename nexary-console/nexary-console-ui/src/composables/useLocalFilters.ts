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
          `${resource.resourceKey} ${resource.engine ?? ''} ${resource.kind} ${resource.name} ${resource.provider} ${resource.operation} ${resource.priority}`,
        ).includes(keyword);
      const matchesEngine = filters.value.engine === 'ALL' || resource.engine === filters.value.engine;
      const matchesKind = filters.value.kind === 'ALL' || resource.kind === filters.value.kind;
      const matchesCircuit =
        filters.value.circuitState === 'ALL' || circuitState === filters.value.circuitState;
      const matchesProvider = filters.value.provider === 'ALL' || resource.provider === filters.value.provider;
      return matchesKeyword && matchesEngine && matchesKind && matchesCircuit && matchesProvider;
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
          `${event.resourceKey} ${event.engine ?? ''} ${event.action} ${event.outcome} ${event.rejectionReason} ${event.blockReason ?? ''} ${event.circuitState} ${event.durationBucket}`,
        ).includes(keyword);
      const matchesOutcome = filters.value.outcome === 'ALL' || event.outcome === filters.value.outcome;
      const matchesReason =
        filters.value.rejectionReason === 'ALL' || event.rejectionReason === filters.value.rejectionReason;
      const matchesCircuit =
        filters.value.circuitState === 'ALL' || event.circuitState === filters.value.circuitState;
      return matchesKeyword && matchesOutcome && matchesReason && matchesCircuit;
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
