export interface TopologyGraphNode {
  key: string;
  label: string;
  caption: string;
  subLabel: string;
  kind: string;
  icon: string;
  tone: string;
  x: number;
  y: number;
}

export interface TopologyGraphEdge {
  key: string;
  sourceKey: string;
  targetKey: string;
  tone: string;
  kind: string;
  resourceKey: string;
  label: string;
  virtual?: boolean;
}
