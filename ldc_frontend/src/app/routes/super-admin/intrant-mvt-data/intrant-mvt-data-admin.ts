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
  selector: 'app-intrant-mvt-data-admin',
  standalone: true,
  templateUrl: './intrant-mvt-data-admin.html',
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
export class IntrantMvtDataAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  rows: any[]               = [];
  paginatedRows: any[]      = [];
  pageSize                  = 10;
  pageIndex                 = 0;
  pageSizeOptions           = [5, 10, 25, 50];
  loading                   = true;
  showAddForm               = false;
  editingId: number | null  = null;
  editingData: any          = {};

  addForm = new FormGroup({
    reportId:          new FormControl<number | null>(null, [Validators.required]),
    intrantId:         new FormControl<number | null>(null, [Validators.required]),
    entryStock:        new FormControl<number | null>(null),
    distributionStock: new FormControl<number | null>(null),
    availableStock:    new FormControl<number | null>(null),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.service.getIntrantMvtData().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.rows    = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error("Erreur lors du chargement des mouvements d'intrants");
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedRows = this.rows.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(row: any): void {
    this.editingId   = row.id;
    this.editingData = {
      reportId:          row.report?.id ?? null,
      intrantId:         row.intrant?.id ?? null,
      entryStock:        row.entryStock,
      distributionStock: row.distributionStock,
      availableStock:    row.availableStock,
    };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(row: any): void {
    this.service.updateIntrantMvtData(row.id, this.editingData).subscribe({
      next: () => {
        this.toast.success('Mouvement mis à jour');
        this.cancelEdit();
        this.load();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteRow(row: any): void {
    if (!confirm(`Supprimer ce mouvement d'intrant (id ${row.id}) ? Ses ajustements seront aussi supprimés.`)) return;
    this.service.deleteIntrantMvtData(row.id).subscribe({
      next: () => {
        this.toast.success('Mouvement supprimé (et ses ajustements)');
        this.load();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createIntrantMvtData(this.addForm.value).subscribe({
      next: () => {
        this.toast.success('Mouvement créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.load();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
