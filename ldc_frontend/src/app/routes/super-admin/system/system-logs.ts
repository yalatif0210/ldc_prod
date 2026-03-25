import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil, forkJoin } from 'rxjs';

@Component({
  selector: 'app-system-logs',
  standalone: true,
  templateUrl: './system-logs.html',
  imports: [
    CommonModule,
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

  ngOnInit(): void {
    this.loadAll();
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
