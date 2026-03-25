import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-role-admin',
  standalone: true,
  templateUrl: './role-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class RoleAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  roles: any[]             = [];
  paginatedRoles: any[]    = [];
  pageSize                 = 10;
  pageIndex                = 0;
  pageSizeOptions          = [5, 10, 25, 50];
  loading                  = true;
  editingId: number | null = null;
  editingName              = '';

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.loading = true;
    this.service.getRoles().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.roles   = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des rôles');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedRoles = this.roles.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(role: any): void {
    this.editingId   = role.id;
    this.editingName = role.role;
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingName = '';
  }

  saveEdit(role: any): void {
    this.service.updateRole(role.id, { role: this.editingName }).subscribe({
      next: () => {
        this.toast.success('Rôle mis à jour');
        this.cancelEdit();
        this.loadRoles();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour du rôle'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
