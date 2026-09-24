import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
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

/**
 * CRUD Super Admin pour les unités d'information (niveau 1) — Ticket #11.
 * Calqué sur le motif de {@link PeriodAdmin} (period-admin.ts) : liste paginée,
 * formulaire d'ajout, édition inline, suppression.
 *
 * <p>La suppression est refusée côté backend (409) tant que des Informations ou des
 * Synthèses référencent encore l'unité ; le message renvoyé par l'API est affiché tel quel.</p>
 */
@Component({
  selector: 'app-information-unit-admin',
  standalone: true,
  templateUrl: './information-unit-admin.html',
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
export class InformationUnitAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  units: any[]          = [];
  paginatedUnits: any[] = [];
  pageSize              = 10;
  pageIndex             = 0;
  pageSizeOptions       = [5, 10, 25, 50];
  loading               = true;
  showAddForm           = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadUnits();
  }

  loadUnits(): void {
    this.loading = true;
    this.service.getInformationUnits().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.units = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error("Erreur lors du chargement des unités d'information");
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedUnits = this.units.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(unit: any): void {
    this.editingId   = unit.id;
    this.editingData = { ...unit };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(unit: any): void {
    const payload = { name: this.editingData.name };
    this.service.updateInformationUnit(unit.id, payload).subscribe({
      next: () => {
        this.toast.success('Unité mise à jour');
        this.cancelEdit();
        this.loadUnits();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteUnit(unit: any): void {
    if (!confirm(`Supprimer l'unité d'information "${unit.name}" ?`)) return;
    this.service.deleteInformationUnit(unit.id).subscribe({
      next: () => {
        this.toast.success('Unité supprimée');
        this.loadUnits();
      },
      error: (err: HttpErrorResponse) => {
        const message = err.status === 409
          ? (err.error?.description ?? "Suppression impossible : des éléments dépendent encore de cette unité.")
          : "Erreur lors de la suppression";
        this.toast.error(message);
      },
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createInformationUnit({ name: val.name }).subscribe({
      next: () => {
        this.toast.success('Unité créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadUnits();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
