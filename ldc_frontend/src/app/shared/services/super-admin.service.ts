import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AppStats {
  totalUsers: number;
  activeUsers: number;
  totalReports: number;
  totalPeriods: number;
  totalStructures: number;
  totalRoles: number;
}

export interface FilteredStats {
  reportCount: number;
  reportsByStatus: Record<string, number>;
  periodLabel: string;
  structureLabel: string;
  equipmentLabel: string;
}

export interface StatsFilter {
  periodId: number | null;
  structureId: number | null;
  equipmentId: number | null;
}

export interface AuditLog {
  id: number;
  accountId: number | null;
  entityType: string;
  entityId: number | null;
  action: 'CREATE' | 'UPDATE' | 'DELETE';
  timestamp: string;
  snapshot: string;
}

export interface AuditLogPage {
  content: AuditLog[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface AuditLogFilter {
  entityType: string | null;
  action: string | null;
  accountId: number | null;
  from: string | null;
  to: string | null;
}

@Injectable({ providedIn: 'root' })
export class SuperAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/super-admin';


  // Stats
  getStats(): Observable<AppStats> {
    return this.http.get<AppStats>(`${this.base}/stats`);
  }

  getFilteredStats(filter: StatsFilter): Observable<FilteredStats> {
    let params = new HttpParams();
    if (filter.periodId !== null && filter.periodId !== undefined) {
      params = params.set('periodId', filter.periodId.toString());
    }
    if (filter.structureId !== null && filter.structureId !== undefined) {
      params = params.set('structureId', filter.structureId.toString());
    }
    if (filter.equipmentId !== null && filter.equipmentId !== undefined) {
      params = params.set('equipmentId', filter.equipmentId.toString());
    }
    return this.http.get<FilteredStats>(`${this.base}/stats/filtered`, { params });
  }

  // Accounts / Users
  getAccounts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/accounts`);
  }

  activateAccount(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/accounts/${id}/activate`, {});
  }

  deactivateAccount(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/accounts/${id}/deactivate`, {});
  }

  changeRole(accountId: number, roleId: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/accounts/role`, { accountId, roleId });
  }

  resetPassword(userId: number, newPassword: string): Observable<any> {
    return this.http.patch<any>(`${this.base}/users/reset-password`, { userId, newPassword });
  }

  deleteUser(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/users/${id}`);
  }

  // Reports
  getReports(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/reports`);
  }

  changeReportStatus(reportId: number, statusId: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/reports/status`, { reportId, statusId });
  }

  reassignReport(reportId: number, accountId: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/reports/reassign`, { reportId, accountId });
  }

  deleteReport(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/reports/${id}`);
  }

  // Periods
  getPeriods(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/periods`);
  }

  createPeriod(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/periods`, data);
  }

  updatePeriod(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/periods/${id}`, data);
  }

  deletePeriod(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/periods/${id}`);
  }

  // Roles
  getRoles(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/roles`);
  }

  updateRole(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/roles/${id}`, data);
  }

  // Structures
  getStructures(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/structures`);
  }

  toggleStructure(id: number): Observable<any> {
    return this.http.patch<any>(`${this.base}/structures/${id}/toggle`, {});
  }

  removeEquipmentFromStructure(structureId: number, equipmentId: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/structures/${structureId}/equipments/${equipmentId}`);
  }

  // Equipments
  getEquipments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/equipments`);
  }

  // Lab activity data (Ticket #14)
  getLabActivityData(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/lab-activity-data`);
  }

  createLabActivityData(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/lab-activity-data`, data);
  }

  updateLabActivityData(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/lab-activity-data/${id}`, data);
  }

  deleteLabActivityData(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/lab-activity-data/${id}`);
  }

  // Intrant movement data (Ticket #14)
  getIntrantMvtData(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/intrant-mvt-data`);
  }

  createIntrantMvtData(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/intrant-mvt-data`, data);
  }

  updateIntrantMvtData(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/intrant-mvt-data/${id}`, data);
  }

  deleteIntrantMvtData(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/intrant-mvt-data/${id}`);
  }

  // Adjustments (Ticket #14)
  getAdjustments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/adjustments`);
  }

  createAdjustment(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/adjustments`, data);
  }

  updateAdjustment(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/adjustments/${id}`, data);
  }

  deleteAdjustment(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/adjustments/${id}`);
  }

  // System / Logs
  getLoginAttempts(page: number, size: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/system/login-attempts`, {
      params: { page: page.toString(), size: size.toString() },
    });
  }

  getBlacklistStats(): Observable<any> {
    return this.http.get<any>(`${this.base}/system/blacklist-stats`);
  }

  purgeBlacklist(): Observable<any> {
    return this.http.delete<any>(`${this.base}/system/blacklist`);
  }

  // Audit log (Ticket #7) — lecture seule
  getAuditLogs(filter: AuditLogFilter, page: number, size: number): Observable<AuditLogPage> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (filter.entityType) {
      params = params.set('entityType', filter.entityType);
    }
    if (filter.action) {
      params = params.set('action', filter.action);
    }
    if (filter.accountId !== null && filter.accountId !== undefined) {
      params = params.set('accountId', filter.accountId.toString());
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    return this.http.get<AuditLogPage>(`${this.base}/audit-logs`, { params });
  }
}
