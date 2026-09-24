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

  // Information units (Ticket #11)
  getInformationUnits(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/information-units`);
  }

  createInformationUnit(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/information-units`, data);
  }

  updateInformationUnit(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/information-units/${id}`, data);
  }

  deleteInformationUnit(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/information-units/${id}`);
  }

  // Information sub-units (Ticket #11)
  getInformationSubUnits(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/information-sub-units`);
  }

  createInformationSubUnit(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/information-sub-units`, data);
  }

  updateInformationSubUnit(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/information-sub-units/${id}`, data);
  }

  deleteInformationSubUnit(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/information-sub-units/${id}`);
  }

  // Information sub-sub-units (Ticket #11)
  getInformationSubSubUnits(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/information-sub-sub-units`);
  }

  createInformationSubSubUnit(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/information-sub-sub-units`, data);
  }

  updateInformationSubSubUnit(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/information-sub-sub-units/${id}`, data);
  }

  deleteInformationSubSubUnit(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/information-sub-sub-units/${id}`);
  }

  // Statuses (referentiel)
  getStatuses(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/statuses`);
  }

  createStatus(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/statuses`, data);
  }

  updateStatus(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/statuses/${id}`, data);
  }

  deleteStatus(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/statuses/${id}`);
  }

  // Months (referentiel)
  getMonths(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/months`);
  }

  createMonth(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/months`, data);
  }

  updateMonth(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/months/${id}`, data);
  }

  deleteMonth(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/months/${id}`);
  }

  // Adjustment types (referentiel)
  getAdjustmentTypes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/adjustment-types`);
  }

  createAdjustmentType(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/adjustment-types`, data);
  }

  updateAdjustmentType(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/adjustment-types/${id}`, data);
  }

  deleteAdjustmentType(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/adjustment-types/${id}`);
  }

  // Intrant types (referentiel)
  getIntrantTypes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/intrant-types`);
  }

  createIntrantType(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/intrant-types`, data);
  }

  updateIntrantType(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/intrant-types/${id}`, data);
  }

  deleteIntrantType(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/intrant-types/${id}`);
  }

  // Synthesis types (referentiel)
  getSynthesisTypes(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/synthesis-types`);
  }

  createSynthesisType(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/synthesis-types`, data);
  }

  updateSynthesisType(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/synthesis-types/${id}`, data);
  }

  deleteSynthesisType(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/synthesis-types/${id}`);
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

  // Equipments (Ticket #12)
  getEquipments(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/equipments`);
  }

  createEquipment(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/equipments`, data);
  }

  updateEquipment(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/equipments/${id}`, data);
  }

  deleteEquipment(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/equipments/${id}`);
  }

  // Intrants (Ticket #12)
  getIntrants(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/intrants`);
  }

  createIntrant(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/intrants`, data);
  }

  updateIntrant(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/intrants/${id}`, data);
  }

  deleteIntrant(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/intrants/${id}`);
  }

  // Sanguine products / comptes sanguins (Ticket #12)
  getSanguineProducts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/sanguine-products`);
  }

  createSanguineProduct(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/sanguine-products`, data);
  }

  updateSanguineProduct(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/sanguine-products/${id}`, data);
  }

  deleteSanguineProduct(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/sanguine-products/${id}`);
  }

  // Notifications (Ticket #9)
  getNotifications(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/notifications`);
  }

  getNotificationIntrants(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/notifications/intrants`);
  }

  createNotification(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/notifications`, data);
  }

  updateNotification(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/notifications/${id}`, data);
  }

  deleteNotification(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/notifications/${id}`);
  }

  // Regions (Ticket #10)
  getRegions(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/regions`);
  }

  createRegion(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/regions`, data);
  }

  updateRegion(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/regions/${id}`, data);
  }

  deleteRegion(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/regions/${id}`);
  }

  // Districts (Ticket #10)
  getDistricts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/districts`);
  }

  createDistrict(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/districts`, data);
  }

  updateDistrict(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/districts/${id}`, data);
  }

  deleteDistrict(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/districts/${id}`);
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

  // Transactions (Transferts) — Ticket #8
  getTransactions(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/transactions`);
  }

  getTransaction(id: number): Observable<any> {
    return this.http.get<any>(`${this.base}/transactions/${id}`);
  }

  createTransaction(data: any): Observable<any> {
    return this.http.post<any>(`${this.base}/transactions`, data);
  }

  updateTransaction(id: number, data: any): Observable<any> {
    return this.http.put<any>(`${this.base}/transactions/${id}`, data);
  }

  deleteTransaction(id: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/transactions/${id}`);
  }

  getTransactionSanguineProducts(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/transactions/reference/sanguine-products`);
  }

  getTransactionIntrants(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/transactions/reference/intrants`);
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
