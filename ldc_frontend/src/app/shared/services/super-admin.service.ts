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
}
