export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type Severity = 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertSortField = 'detectedAt' | 'severity' | 'status' | 'threatType' | 'createdAt';
export type SortDirection = 'ASC' | 'DESC';

export interface Alert {
  alertId: string;
  ruleId: string;
  threatType: string;
  severity: Severity;
  sourceIp?: string | null;
  description: string;
  evidence?: Record<string, unknown> | null;
  firstSeen: string;
  lastSeen: string;
  detectedAt: string;
  status: AlertStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AlertPage {
  items: Alert[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AlertStatistics {
  totalAlerts: number;
  openAlerts: number;
  acknowledgedAlerts: number;
  resolvedAlerts: number;
  bySeverity: Record<string, number>;
  byThreatType: Record<string, number>;
}

export interface AlertFilters {
  page?: number;
  size?: number;
  sortBy?: AlertSortField;
  direction?: SortDirection;
  severity?: Severity;
  status?: AlertStatus;
  threatType?: string;
  sourceIp?: string;
  from?: string;
  to?: string;
}
