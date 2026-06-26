import { computed, readonly, ref } from 'vue';
import {
  fetchEvents,
  fetchFaultTraceSummary,
  fetchResource,
  fetchResources,
  fetchSummary,
  fetchTrace,
  fetchTraces,
  getConsoleSettings,
} from '../api/consoleClient';
import type { ConsoleEvent, ConsoleFaultTrace, ConsoleFaultTraceSummary, ConsoleResource, ConsoleSummary } from '../types/console';

const summary = ref<ConsoleSummary | null>(null);
const resources = ref<ConsoleResource[]>([]);
const events = ref<ConsoleEvent[]>([]);
const traces = ref<ConsoleFaultTrace[]>([]);
const faultTraceSummary = ref<ConsoleFaultTraceSummary | null>(null);
const resourceDetails = ref<Map<string, ConsoleResource>>(new Map());
const traceDetails = ref<Map<string, ConsoleFaultTrace>>(new Map());
const isLoading = ref(false);
const errorMessage = ref<string | null>(null);
const lastRefreshAt = ref<string | null>(null);
const hasLoaded = ref(false);

export function useConsoleData() {
  const settings = getConsoleSettings();

  async function refreshAll(): Promise<void> {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const [nextSummary, nextResources, nextEvents, nextTraces, nextFaultTraceSummary] = await Promise.all([
        fetchSummary(),
        fetchResources(),
        fetchEvents(),
        fetchTraces(),
        fetchFaultTraceSummary(),
      ]);
      summary.value = nextSummary;
      resources.value = nextResources;
      events.value = nextEvents;
      traces.value = nextTraces;
      faultTraceSummary.value = nextFaultTraceSummary;
      resourceDetails.value = new Map(nextResources.map((resource) => [resource.resourceKey, resource]));
      traceDetails.value = new Map(nextTraces.map((trace) => [trace.traceKey, trace]));
      lastRefreshAt.value = new Date().toISOString();
      hasLoaded.value = true;
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'Console data failed to load';
    } finally {
      isLoading.value = false;
    }
  }

  async function loadResource(resourceKey: string): Promise<ConsoleResource | null> {
    const cached = resourceDetails.value.get(resourceKey);
    if (cached) {
      return cached;
    }
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const resource = await fetchResource(resourceKey);
      if (resource) {
        const next = new Map(resourceDetails.value);
        next.set(resource.resourceKey, resource);
        resourceDetails.value = next;
      }
      return resource;
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'Resource detail failed to load';
      return null;
    } finally {
      isLoading.value = false;
    }
  }

  function resourceByKey(resourceKey: string): ConsoleResource | null {
    return resourceDetails.value.get(resourceKey) ?? null;
  }

  async function loadTrace(traceKey: string): Promise<ConsoleFaultTrace | null> {
    const cached = traceDetails.value.get(traceKey);
    if (cached) {
      return cached;
    }
    isLoading.value = true;
    errorMessage.value = null;
    try {
      const trace = await fetchTrace(traceKey);
      if (trace) {
        const next = new Map(traceDetails.value);
        next.set(trace.traceKey, trace);
        traceDetails.value = next;
      }
      return trace;
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'Trace detail failed to load';
      return null;
    } finally {
      isLoading.value = false;
    }
  }

  function traceByKey(traceKey: string): ConsoleFaultTrace | null {
    return traceDetails.value.get(traceKey) ?? null;
  }

  return {
    summary: readonly(summary),
    resources: readonly(resources),
    events: readonly(events),
    traces: readonly(traces),
    faultTraceSummary: readonly(faultTraceSummary),
    settings,
    isLoading: readonly(isLoading),
    errorMessage: readonly(errorMessage),
    lastRefreshAt: readonly(lastRefreshAt),
    hasLoaded: readonly(hasLoaded),
    hasResources: computed(() => resources.value.length > 0),
    hasEvents: computed(() => events.value.length > 0),
    hasTraces: computed(() => traces.value.length > 0),
    refreshAll,
    loadResource,
    resourceByKey,
    loadTrace,
    traceByKey,
  };
}
