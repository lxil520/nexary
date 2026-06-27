import { computed, readonly, ref } from 'vue';
import { fetchPlatformSnapshot } from '../api/platformClient';
import type { PlatformSnapshot } from '../types/platform';

const snapshot = ref<PlatformSnapshot | null>(null);
const isLoading = ref(false);
const errorMessage = ref<string | null>(null);
const lastRefreshAt = ref<string | null>(null);
const hasLoaded = ref(false);

export function usePlatformData() {
  async function refreshPlatform(): Promise<void> {
    isLoading.value = true;
    errorMessage.value = null;
    try {
      snapshot.value = await fetchPlatformSnapshot();
      lastRefreshAt.value = new Date().toISOString();
      hasLoaded.value = true;
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'Platform data failed to load';
    } finally {
      isLoading.value = false;
    }
  }

  return {
    snapshot: readonly(snapshot),
    isLoading: readonly(isLoading),
    errorMessage: readonly(errorMessage),
    lastRefreshAt: readonly(lastRefreshAt),
    hasLoaded: readonly(hasLoaded),
    serviceCount: computed(() => snapshot.value?.services.length ?? 0),
    incidentCount: computed(() => snapshot.value?.incidents.length ?? 0),
    dependencyCount: computed(() => snapshot.value?.topology.dependencies.length ?? 0),
    connectorCount: computed(() => snapshot.value?.connectors.length ?? 0),
    refreshPlatform,
  };
}
