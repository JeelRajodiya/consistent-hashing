export interface LoadBalancer {
  port: number;
  uptime: number;
  uptimeFormatted: string;
  virtualNodesPerServer: number;
}

export interface Performance {
  totalRequests: number;
  totalErrors: number;
  errorRate: number;
  requestsPerSecond: number;
  currentLoad: number;
  avgRequestsPerServer: number;
}

export interface AutoScaling {
  enabled: boolean;
  minServers: number;
  maxServers: number;
  scaleUpThreshold: number;
  scaleDownThreshold: number;
  checkInterval: number;
  lastScaleAction: string;
  lastScaleTime: number;
}

export interface HashRing {
  totalVirtualNodes: number;
  physicalNodes: number;
}

export interface ServerNode {
  id: string;
  address: string;
  active: boolean;
  uptime: number;
  uptimeFormatted: string;
  requestCount: number;
  requestsPerSecond: number;
  loadPercentage: number;
}

export interface Servers {
  total: number;
  active: number;
  inactive: number;
  nodes: ServerNode[];
}

export interface Stats {
  loadBalancer: LoadBalancer;
  performance: Performance;
  autoScaling: AutoScaling;
  hashRing: HashRing;
  servers: Servers;
}
