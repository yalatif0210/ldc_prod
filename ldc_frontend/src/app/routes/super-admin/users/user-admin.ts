import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatOptionModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil } from 'rxjs';
import { ResetPasswordDialog } from './reset-password-dialog';

@Component({
  selector: 'app-user-admin',
  standalone: true,
  templateUrl: './user-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatOptionModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSelectModule,
    ResetPasswordDialog,
  ],
})
export class UserAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private readonly dialog  = inject(MatDialog);
  private destroy$         = new Subject<void>();

  accounts: any[]          = [];
  filteredAccounts: any[]  = [];
  paginatedAccounts: any[] = [];
  pageSize                 = 10;
  pageIndex                = 0;
  pageSizeOptions          = [5, 10, 25, 50];
  roles: any[]             = [];
  loading                  = true;
  searchTerm               = '';
  editingId: number | null = null;
  editingRoleId: number | null = null;

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.service.getAccounts().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.accounts = data.sort((a, b) => a.id - b.id);
        this.loading  = false;
        this.applyFilter();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des utilisateurs');
        this.loading = false;
      },
    });
    this.service.getRoles().subscribe({
      next: data => {
        this.roles = data; },
    });
  }


  applyFilter(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) {
      this.filteredAccounts = [...this.accounts];
    } else {
      this.filteredAccounts = this.accounts.filter(a =>
        (a.user?.name ?? a.name ?? '').toLowerCase().includes(term) ||
        (a.user?.username ?? a.username ?? '').toLowerCase().includes(term) ||
        (a.user?.phone ?? a.phone ?? '').toLowerCase().includes(term) ||
        (a.role?.role ?? '').toLowerCase().includes(term) ||
        (a.structures ?? []).some((s: any) => s.name?.toLowerCase().includes(term))
      );
    }
    this.pageIndex = 0;
    this.paginate();
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedAccounts = this.filteredAccounts.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  toggleActive(account: any): void {
    const action = account.isActive
      ? this.service.deactivateAccount(account.id)
      : this.service.activateAccount(account.id);
    action.subscribe({
      next: () => {
        this.toast.success(account.isActive ? 'Compte désactivé' : 'Compte activé');
        this.loadData();
      },
      error: () => this.toast.error('Erreur lors du changement de statut'),
    });
  }

  startRoleEdit(account: any): void {
    this.editingId     = account.id;
    this.editingRoleId = account.roleId;
  }

  saveRole(account: any): void {
    if (!this.editingRoleId) return;
    this.service.changeRole(account.id, this.editingRoleId).subscribe({
      next: () => {
        this.toast.success('Rôle mis à jour');
        this.editingId     = null;
        this.editingRoleId = null;
        this.loadData();
      },
      error: () => this.toast.error('Erreur lors du changement de rôle'),
    });
  }

  cancelRoleEdit(): void {
    this.editingId     = null;
    this.editingRoleId = null;
  }

  openResetPassword(account: any): void {
    const ref = this.dialog.open(ResetPasswordDialog, {
      width: '400px',
      data: { account },
    });
    ref.afterClosed().subscribe((newPassword: string | undefined) => {
      if (!newPassword) return;
      const userId = account.user?.id ?? account.userId ?? account.id;
      this.service.resetPassword(userId, newPassword).subscribe({
        next: () => this.toast.success('Mot de passe réinitialisé'),
        error: () => this.toast.error('Erreur lors de la réinitialisation'),
      });
    });
  }

  deleteUser(account: any): void {
    if (!confirm(`Supprimer l'utilisateur "${account.user?.name ?? account.name}" ? Cette action est irréversible.`)) return;
    const userId = account.user?.id ?? account.userId ?? account.id;
    this.service.deleteUser(userId).subscribe({
      next: () => {
        this.toast.success('Utilisateur supprimé');
        this.loadData();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  getAccountZone(account: any): string {
    if (account.structures?.length === 1) {
      const structure = account.structures[0];
      return `${structure.name}`;
     } else {
      return 'Non applicable';
     }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
