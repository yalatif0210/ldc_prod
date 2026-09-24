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
 * CRUD Super Admin pour les sous-unités d'information (niveau 2) — Ticket #11.
 * Calqué sur le motif de {@link PeriodAdmin} (period-admin.ts).
 *
 * <p>La suppression est refusée côté backend (409) tant que des Informations référencent
 * encore la sous-unité ; le message renvoyé par l'API est affiché tel quel.</p>
 */
@Component({
  selector: 'app-information-sub-unit-admin',
  standalone: true,
  templateUrl: './information-sub-unit-admin.html',
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
export class InformationSubUnitAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  subUnits: any[]          = [];
  paginatedSubUnits: any[] = [];
  pageSize                 = 10;
  pageIndex                = 0;
  pageSizeOptions          = [5, 10, 25, 50];
  loading                  = true;
  showAddForm              = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadSubUnits();
  }

  loadSubUnits(): void {
    this.loading = true;
    this.service.getInformationSubUnits().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.subUnits = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error("Erreur lors du chargement des sous-unités d'information");
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedSubUnits = this.subUnits.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(subUnit: any): void {
    this.editingId   = subUnit.id;
    this.editingData = { ...subUnit };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(subUnit: any): void {
    const payload = { name: this.editingData.name };
    this.service.updateInformationSubUnit(subUnit.id, payload).subscribe({
      next: () => {
        this.toast.success('Sous-unité mise à jour');
        this.cancelEdit();
        this.loadSubUnits();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteSubUnit(subUnit: any): void {
    if (!confirm(`Supprimer la sous-unité d'information "${subUnit.name}" ?`)) return;
    this.service.deleteInformationSubUnit(subUnit.id).subscribe({
      next: () => {
        this.toast.success('Sous-unité supprimée');
        this.loadSubUnits();
      },
      error: (err: HttpErrorResponse) => {
        const message = err.status === 409
          ? (err.error?.description ?? "Suppression impossible : des éléments dépendent encore de cette sous-unité.")
          : "Erreur lors de la suppression";
        this.toast.error(message);
      },
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createInformationSubUnit({ name: val.name }).subscribe({
      next: () => {
        this.toast.success('Sous-unité créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadSubUnits();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
