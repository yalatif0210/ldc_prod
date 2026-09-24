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
  selector: 'app-adjustment-type-admin',
  standalone: true,
  templateUrl: './adjustment-type-admin.html',
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
export class AdjustmentTypeAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  adjustmentTypes: any[]          = [];
  paginatedAdjustmentTypes: any[] = [];
  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
    type: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadAdjustmentTypes();
  }

  loadAdjustmentTypes(): void {
    this.loading = true;
    this.service.getAdjustmentTypes().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.adjustmentTypes = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des types d\'ajustement');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedAdjustmentTypes = this.adjustmentTypes.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(adjustmentType: any): void {
    this.editingId   = adjustmentType.id;
    this.editingData = { ...adjustmentType };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(adjustmentType: any): void {
    const payload = { name: this.editingData.name, type: this.editingData.type };
    this.service.updateAdjustmentType(adjustmentType.id, payload).subscribe({
      next: () => {
        this.toast.success('Type d\'ajustement mis à jour');
        this.cancelEdit();
        this.loadAdjustmentTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteAdjustmentType(adjustmentType: any): void {
    if (!confirm(`Supprimer le type d'ajustement "${adjustmentType.name}" ?`)) return;
    this.service.deleteAdjustmentType(adjustmentType.id).subscribe({
      next: () => {
        this.toast.success('Type d\'ajustement supprimé');
        this.loadAdjustmentTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createAdjustmentType({ name: val.name, type: val.type }).subscribe({
      next: () => {
        this.toast.success('Type d\'ajustement créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadAdjustmentTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
