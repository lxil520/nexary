<script setup lang="ts">
import cytoscape, { type Core, type ElementDefinition, type EventObject } from 'cytoscape';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { TopologyGraphEdge, TopologyGraphNode } from '../types/topologyGraph';

const props = defineProps<{
  nodes: TopologyGraphNode[];
  edges: TopologyGraphEdge[];
  selectedEdgeKey: string | null;
  selectedNodeKey?: string | null;
}>();

const emit = defineEmits<{
  'select-edge': [edgeKey: string];
  'select-node': [nodeKey: string];
}>();

const graphEl = ref<HTMLDivElement | null>(null);
let graph: Core | null = null;
let edgeFlowFrame: number | null = null;
let stableFitFrame: number | null = null;
let stableFitFrame2: number | null = null;
let resetFitTimer: number | null = null;
let resizeObserver: ResizeObserver | null = null;

const riskEdgeKey = computed(() => props.edges.find((edge) => edge.tone === 'critical')?.key ?? props.edges.find((edge) => edge.tone === 'warning')?.key ?? props.edges[0]?.key ?? null);

onMounted(() => {
  void nextTick(() => {
    mountGraph();
  });
});

onBeforeUnmount(() => {
  stopEdgeFlow();
  cancelStableFit();
  clearResetFitTimer();
  resizeObserver?.disconnect();
  resizeObserver = null;
  graph?.destroy();
  graph = null;
});

watch(
  () => [props.nodes, props.edges],
  () => {
    if (!graph) {
      mountGraph();
      return;
    }
    graph.elements().remove();
    graph.add(toElements());
    applyPresetPositions(false);
    applySelection();
    startEdgeFlow();
    scheduleStableFit();
  },
  { deep: true },
);

watch(
  () => props.selectedEdgeKey,
  () => {
    applySelection();
  },
);

watch(
  () => props.selectedNodeKey,
  () => {
    applySelection();
  },
);

function mountGraph(): void {
  if (!graphEl.value) {
    return;
  }
  resizeObserver?.disconnect();
  graph?.destroy();
  graph = cytoscape({
    container: graphEl.value,
    elements: toElements(),
    minZoom: 0.35,
    maxZoom: 2.5,
    boxSelectionEnabled: false,
    autoungrabify: false,
    style: [
      {
        selector: 'node',
        style: {
          width: 54,
          height: 46,
          shape: 'round-rectangle',
          'background-color': '#1b2638',
          'background-image': 'data(icon)',
          'background-width': '26px',
          'background-height': '26px',
          'background-fit': 'contain',
          'border-width': 2,
          'border-color': '#7b8799',
          color: '#dbe7f5',
          label: 'data(label)',
          'font-size': 9,
          'font-weight': 800,
          'text-wrap': 'wrap',
          'text-max-width': '128px',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'text-margin-y': 9,
          'text-outline-color': '#111827',
          'text-outline-width': 3,
          'overlay-opacity': 0,
        },
      },
      {
        selector: 'node[kind = "USER"]',
        style: {
          width: 58,
          height: 50,
          shape: 'ellipse',
          'border-color': '#38bdf8',
          'background-color': '#0f2b3f',
        },
      },
      {
        selector: 'node[kind = "API"]',
        style: {
          'border-color': '#22d3ee',
        },
      },
      {
        selector: 'node[kind = "SIGNALING"]',
        style: {
          'border-color': '#a78bfa',
        },
      },
      {
        selector: 'node[kind = "JOB"]',
        style: {
          'border-color': '#f59e0b',
        },
      },
      {
        selector: 'node[kind = "CONSUMER"]',
        style: {
          'border-color': '#818cf8',
        },
      },
      {
        selector: 'node[kind = "CACHE"], node[kind = "DATABASE"], node[kind = "MESSAGING"], node[kind = "OBJECT_STORAGE"]',
        style: {
          'background-color': '#142032',
          'border-color': '#94a3b8',
        },
      },
      {
        selector: 'node[kind = "CACHE"]',
        style: {
          shape: 'round-diamond',
          'background-color': '#5b6b86',
        },
      },
      {
        selector: 'node[kind = "DATABASE"]',
        style: {
          shape: 'barrel',
          'background-color': '#526073',
        },
      },
      {
        selector: 'node[kind = "MESSAGING"]',
        style: {
          shape: 'hexagon',
          'background-color': '#596274',
        },
      },
      {
        selector: 'node[kind = "OBJECT_STORAGE"]',
        style: {
          shape: 'round-hexagon',
          'background-color': '#546179',
        },
      },
      {
        selector: 'node[tone = "critical"]',
        style: {
          'border-color': '#ef4444',
          'border-width': 3,
        },
      },
      {
        selector: 'node[tone = "warning"]',
        style: {
          'border-color': '#f59e0b',
          'border-width': 3,
        },
      },
      {
        selector: 'edge',
        style: {
          width: 2.2,
          'line-color': '#22c55e',
          'target-arrow-color': '#22c55e',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          'line-style': 'dashed',
          'line-dash-pattern': [9, 7],
          opacity: 0.72,
          label: '',
          color: '#9fb0c3',
          'font-size': 9,
          'font-weight': 800,
          'text-background-color': '#07111f',
          'text-background-opacity': 0.92,
          'text-background-padding': '4px',
          'text-border-width': 1,
          'text-border-color': '#24324a',
          'text-border-opacity': 1,
          'text-rotation': 'autorotate',
          'overlay-opacity': 0,
        },
      },
      {
        selector: 'edge[tone = "healthy"]',
        style: {
          label: '',
        },
      },
      {
        selector: 'edge[kind = "ENTRY"]',
        style: {
          width: 2.6,
          'line-color': '#38bdf8',
          'target-arrow-color': '#38bdf8',
          'line-dash-pattern': [10, 8],
          opacity: 0.86,
        },
      },
      {
        selector: 'edge[tone = "critical"]',
        style: {
          width: 3.5,
          'line-color': '#ef4444',
          'target-arrow-color': '#ef4444',
          opacity: 0.95,
        },
      },
      {
        selector: 'edge[tone = "warning"]',
        style: {
          width: 2.8,
          'line-color': '#f59e0b',
          'target-arrow-color': '#f59e0b',
          opacity: 0.9,
        },
      },
      {
        selector: 'edge[tone = "jitter"]',
        style: {
          width: 2.8,
          'line-color': '#a78bfa',
          'target-arrow-color': '#a78bfa',
          'line-dash-pattern': [3, 8],
          opacity: 0.9,
        },
      },
      {
        selector: 'node.is-selected',
        style: {
          'border-color': '#22d3ee',
          'border-width': 4,
          'z-index': 20,
        },
      },
      {
        selector: 'node.is-service-focus',
        style: {
          'border-color': '#38bdf8',
          'border-width': 4,
          'z-index': 18,
        },
      },
      {
        selector: 'edge.is-selected',
        style: {
          'line-color': '#22d3ee',
          'target-arrow-color': '#22d3ee',
          width: 4.2,
          opacity: 1,
          label: 'data(label)',
          'z-index': 20,
        },
      },
      {
        selector: 'edge.is-risk-primary',
        style: {
          width: 3.8,
          opacity: 1,
          label: 'data(label)',
          'z-index': 16,
        },
      },
      {
        selector: '.is-faded',
        style: {
          opacity: 0.48,
        },
      },
    ],
  });

  graph.on('tap', 'node', (event: EventObject) => {
    emit('select-node', event.target.id());
  });
  graph.on('tap', 'edge', (event: EventObject) => {
    emit('select-edge', event.target.id());
  });
  observeGraphSize();
  applyPresetPositions(false);
  applySelection();
  startEdgeFlow();
  scheduleStableFit();
}

function observeGraphSize(): void {
  if (!graphEl.value || typeof ResizeObserver === 'undefined') {
    return;
  }
  resizeObserver = new ResizeObserver((entries) => {
    const rect = entries[0]?.contentRect;
    if (rect && rect.width > 120 && rect.height > 120) {
      scheduleStableFit();
    }
  });
  resizeObserver.observe(graphEl.value);
}

function scheduleStableFit(padding = 54, attempt = 0): void {
  cancelStableFit();
  stableFitFrame = window.requestAnimationFrame(() => {
    stableFitFrame = null;
    stableFitFrame2 = window.requestAnimationFrame(() => {
      stableFitFrame2 = null;
      if (!graph || !graphEl.value) {
        return;
      }
      const box = graphEl.value.getBoundingClientRect();
      if (box.width < 120 || box.height < 120) {
        if (attempt < 8) {
          scheduleStableFit(padding, attempt + 1);
        }
        return;
      }
      graph.resize();
      graph.fit(undefined, padding);
    });
  });
}

function cancelStableFit(): void {
  if (stableFitFrame !== null) {
    window.cancelAnimationFrame(stableFitFrame);
    stableFitFrame = null;
  }
  if (stableFitFrame2 !== null) {
    window.cancelAnimationFrame(stableFitFrame2);
    stableFitFrame2 = null;
  }
}

function clearResetFitTimer(): void {
  if (resetFitTimer !== null) {
    window.clearTimeout(resetFitTimer);
    resetFitTimer = null;
  }
}

function startEdgeFlow(): void {
  stopEdgeFlow();
  const animate = (timestamp: number) => {
    if (!graph) {
      edgeFlowFrame = null;
      return;
    }
    const offset = -((timestamp / 62) % 18);
    graph.edges().style('line-dash-offset', offset);
    edgeFlowFrame = window.requestAnimationFrame(animate);
  };
  edgeFlowFrame = window.requestAnimationFrame(animate);
}

function stopEdgeFlow(): void {
  if (edgeFlowFrame !== null) {
    window.cancelAnimationFrame(edgeFlowFrame);
    edgeFlowFrame = null;
  }
}

function toElements(): ElementDefinition[] {
  const entryKeys = props.edges.filter((edge) => edge.kind === 'ENTRY').map((edge) => edge.targetKey);
  return [
    ...props.nodes.map((node) => ({
      group: 'nodes' as const,
      data: {
        id: node.key,
        label: node.caption ? `${node.label}\n${node.caption}` : node.label,
        kind: node.kind,
        icon: node.icon,
        tone: node.tone,
      },
      position: nodePosition(node, entryKeys),
      grabbable: true,
    })),
    ...props.edges.map((edge) => ({
      group: 'edges' as const,
      data: {
        id: edge.key,
        source: edge.sourceKey,
        target: edge.targetKey,
        label: edge.label,
        tone: edge.tone,
        kind: edge.kind,
        resourceKey: edge.resourceKey,
      },
    })),
  ];
}

function nodePosition(node: TopologyGraphNode, entryKeys: string[]): { x: number; y: number } {
  if (node.kind === 'USER') {
    return { x: 90, y: 280 };
  }
  const entryIndex = entryKeys.indexOf(node.key);
  if (entryIndex >= 0) {
    return {
      x: 220,
      y: entryKeys.length === 1 ? 280 : 150 + (entryIndex * 260) / Math.max(entryKeys.length - 1, 1),
    };
  }
  return {
    x: node.x * 10,
    y: node.y * 5.6,
  };
}

function applyPresetPositions(animate: boolean): void {
  if (!graph) {
    return;
  }
  const entryKeys = props.edges.filter((edge) => edge.kind === 'ENTRY').map((edge) => edge.targetKey);
  const positions = new Map(props.nodes.map((node) => [node.key, nodePosition(node, entryKeys)]));
  if (!animate) {
    graph.nodes().positions((node) => positions.get(node.id()) ?? node.position());
    return;
  }
  graph.nodes().forEach((node) => {
    const position = positions.get(node.id());
    if (position) {
      node.animate({ position }, { duration: 240 });
    }
  });
}

function applySelection(): void {
  if (!graph) {
    return;
  }
  graph.elements().removeClass('is-selected is-service-focus is-faded is-risk-primary');
  if (props.selectedNodeKey) {
    const node = graph.getElementById(props.selectedNodeKey);
    if (!node.empty()) {
      node.addClass('is-service-focus');
    }
  }
  const selectedKey = props.selectedEdgeKey;
  if (!selectedKey) {
    if (riskEdgeKey.value) {
      graph.getElementById(riskEdgeKey.value).addClass('is-risk-primary');
    }
    return;
  }
  const edge = graph.getElementById(selectedKey);
  if (edge.empty()) {
    return;
  }
  edge.addClass('is-selected');
  const focusNode = props.selectedNodeKey ? graph.getElementById(props.selectedNodeKey) : graph.collection();
  const selectedElements = edge.union(edge.connectedNodes()).union(focusNode);
  edge.connectedNodes().addClass('is-selected');
  graph.elements().difference(selectedElements).addClass('is-faded');
}

function fitGraph(): void {
  if (!graph) {
    return;
  }
  graph.resize();
  graph.fit(undefined, 54);
}

function zoomIn(): void {
  if (!graph) {
    return;
  }
  graph.zoom({ level: graph.zoom() * 1.18, renderedPosition: { x: graph.width() / 2, y: graph.height() / 2 } });
}

function zoomOut(): void {
  if (!graph) {
    return;
  }
  graph.zoom({ level: graph.zoom() / 1.18, renderedPosition: { x: graph.width() / 2, y: graph.height() / 2 } });
}

function focusRisk(): void {
  if (!graph || !riskEdgeKey.value) {
    return;
  }
  emit('select-edge', riskEdgeKey.value);
  const edge = graph.getElementById(riskEdgeKey.value);
  if (!edge.empty()) {
    graph.fit(edge.union(edge.connectedNodes()), 96);
  }
}

function layoutGraph(): void {
  if (!graph) {
    return;
  }
  clearResetFitTimer();
  applyPresetPositions(true);
  resetFitTimer = window.setTimeout(() => {
    resetFitTimer = null;
    scheduleStableFit();
  }, 280);
}
</script>

<template>
  <div class="cyto-shell">
    <div class="cyto-toolbar" aria-label="Topology controls">
      <button type="button" @click="fitGraph">Fit</button>
      <button type="button" @click="layoutGraph">Reset</button>
      <button type="button" @click="zoomOut">-</button>
      <button type="button" @click="zoomIn">+</button>
    </div>
    <div ref="graphEl" class="cyto-canvas" aria-label="Interactive topology graph"></div>
  </div>
</template>
