import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ToastrService } from 'ngx-toastr';
import { AuditLog, AuditLogFilter, SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil, forkJoin } from 'rxjs';

@Component({
  selector: 'app-system-logs',
  standalone: true,
  templateUrl: './system-logs.html',
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
})
export class SystemLogs implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  loginAttempts: any[]   = [];
  blacklistStats: any    = null;
  loading                = true;
  loadingBlacklist       = true;
  purgingBlacklist       = false;

  page     = 1;
  pageSize = 20;

  // Journal d'audit (Ticket #7)
  auditLogs: AuditLog[]      = [];
  auditLoading                = true;
  auditPage                   = 0;
  auditPageSize                = 20;
  auditTotalElements           = 0;
  auditFilter = {
    entityType: '',
    action: '',
    accountId: '',
    from: '',
    to: '',
  };
  readonly auditEntityTypes = ['Account', 'User', 'Report', 'Period', 'Role', 'Structure'];
  readonly auditActions     = ['CREATE', 'UPDATE', 'DELETE'];
  expandedAuditLogId: number | null = null;

  ngOnInit(): void {
    this.loadAll();
    this.loadAuditLogs();
  }

  loadAll(): void {
    this.loading         = true;
    this.loadingBlacklist = true;

    forkJoin([
      this.service.getLoginAttempts(this.page - 1, this.pageSize),
      this.service.getBlacklistStats(),
    ]).pipe(takeUntil(this.destroy$)).subscribe({
      next: ([attempts, stats]) => {
        this.loginAttempts   = Array.isArray(attempts) ? attempts : [];
        this.blacklistStats  = stats;
        this.loading         = false;
        this.loadingBlacklist = false;
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des logs système');
        this.loading         = false;
        this.loadingBlacklist = false;
      },
    });
  }

  loadAttempts(): void {
    this.loading = true;
    this.service.getLoginAttempts(this.page - 1, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: data => {
          this.loginAttempts = Array.isArray(data) ? data : [];
          this.loading       = false;
        },
        error: () => {
          this.toast.error('Erreur lors du chargement des tentatives de connexion');
          this.loading = false;
        },
      });
  }

  prevPage(): void {
    if (this.page <= 1) return;
    this.page--;
    this.loadAttempts();
  }

  nextPage(): void {
    this.page++;
    this.loadAttempts();
  }

  purgeBlacklist(): void {
    if (!confirm('Voulez-vous vraiment purger la blacklist des tokens ? Cette action est irréversible.')) return;
    this.purgingBlacklist = true;
    this.service.purgeBlacklist().subscribe({
      next: () => {
        this.toast.success('Blacklist purgée avec succès');
        this.purgingBlacklist = false;
        this.service.getBlacklistStats().subscribe({
          next: stats => { this.blacklistStats = stats; },
        });
      },
      error: () => {
        this.toast.error('Erreur lors de la purge de la blacklist');
        this.purgingBlacklist = false;
      },
    });
  }

  // ---- Journal d'audit (Ticket #7) ----

  loadAuditLogs(): void {
    this.auditLoading = true;
    const filter: AuditLogFilter = {
      entityType: this.auditFilter.entityType || null,
      action: this.auditFilter.action || null,
      accountId: this.auditFilter.accountId ? Number(this.auditFilter.accountId) : null,
      from: this.auditFilter.from ? new Date(this.auditFilter.from).toISOString() : null,
      to: this.auditFilter.to ? new Date(this.auditFilter.to).toISOString() : null,
    };
    this.service.getAuditLogs(filter, this.auditPage, this.auditPageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.auditLogs         = result?.content ?? [];
          this.auditTotalElements = result?.totalElements ?? 0;
          this.auditLoading       = false;
        },
        error: () => {
          this.toast.error("Erreur lors du chargement du journal d'audit");
          this.auditLoading = false;
        },
      });
  }

  applyAuditFilters(): void {
    this.auditPage = 0;
    this.loadAuditLogs();
  }

  resetAuditFilters(): void {
    this.auditFilter = { entityType: '', action: '', accountId: '', from: '', to: '' };
    this.applyAuditFilters();
  }

  prevAuditPage(): void {
    if (this.auditPage <= 0) return;
    this.auditPage--;
    this.loadAuditLogs();
  }

  nextAuditPage(): void {
    if ((this.auditPage + 1) * this.auditPageSize >= this.auditTotalElements) return;
    this.auditPage++;
    this.loadAuditLogs();
  }

  toggleAuditSnapshot(log: AuditLog): void {
    this.expandedAuditLogId = this.expandedAuditLogId === log.id ? null : log.id;
  }

  formattedSnapshot(log: AuditLog): string {
    try {
      return JSON.stringify(JSON.parse(log.snapshot), null, 2);
    } catch {
      return log.snapshot ?? '';
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
