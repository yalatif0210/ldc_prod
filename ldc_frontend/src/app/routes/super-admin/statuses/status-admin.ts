import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
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
  selector: 'app-status-admin',
  standalone: true,
  templateUrl: './status-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class StatusAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  statuses: any[]          = [];
  paginatedStatuses: any[] = [];
  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    status: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadStatuses();
  }

  loadStatuses(): void {
    this.loading = true;
    this.service.getStatuses().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.statuses = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des statuts');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedStatuses = this.statuses.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(status: any): void {
    this.editingId   = status.id;
    this.editingData = { ...status };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(status: any): void {
    const payload = { status: this.editingData.status };
    this.service.updateStatus(status.id, payload).subscribe({
      next: () => {
        this.toast.success('Statut mis à jour');
        this.cancelEdit();
        this.loadStatuses();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteStatus(status: any): void {
    if (!confirm(`Supprimer le statut "${status.status}" ?`)) return;
    this.service.deleteStatus(status.id).subscribe({
      next: () => {
        this.toast.success('Statut supprimé');
        this.loadStatuses();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createStatus({ status: val.status }).subscribe({
      next: () => {
        this.toast.success('Statut créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadStatuses();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
