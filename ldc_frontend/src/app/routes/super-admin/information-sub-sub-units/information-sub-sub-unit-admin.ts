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
 * CRUD Super Admin pour les sous-sous-unités d'information (niveau 3) — Ticket #11.
 * Calqué sur le motif de {@link PeriodAdmin} (period-admin.ts).
 *
 * <p>La suppression est refusée côté backend (409) tant que des Informations référencent
 * encore la sous-sous-unité ; le message renvoyé par l'API est affiché tel quel.</p>
 */
@Component({
  selector: 'app-information-sub-sub-unit-admin',
  standalone: true,
  templateUrl: './information-sub-sub-unit-admin.html',
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
export class InformationSubSubUnitAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  subSubUnits: any[]          = [];
  paginatedSubSubUnits: any[] = [];
  pageSize                    = 10;
  pageIndex                   = 0;
  pageSizeOptions             = [5, 10, 25, 50];
  loading                     = true;
  showAddForm                 = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadSubSubUnits();
  }

  loadSubSubUnits(): void {
    this.loading = true;
    this.service.getInformationSubSubUnits().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.subSubUnits = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error("Erreur lors du chargement des sous-sous-unités d'information");
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedSubSubUnits = this.subSubUnits.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(subSubUnit: any): void {
    this.editingId   = subSubUnit.id;
    this.editingData = { ...subSubUnit };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(subSubUnit: any): void {
    const payload = { name: this.editingData.name };
    this.service.updateInformationSubSubUnit(subSubUnit.id, payload).subscribe({
      next: () => {
        this.toast.success('Sous-sous-unité mise à jour');
        this.cancelEdit();
        this.loadSubSubUnits();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteSubSubUnit(subSubUnit: any): void {
    if (!confirm(`Supprimer la sous-sous-unité d'information "${subSubUnit.name}" ?`)) return;
    this.service.deleteInformationSubSubUnit(subSubUnit.id).subscribe({
      next: () => {
        this.toast.success('Sous-sous-unité supprimée');
        this.loadSubSubUnits();
      },
      error: (err: HttpErrorResponse) => {
        const message = err.status === 409
          ? (err.error?.description ?? "Suppression impossible : des éléments dépendent encore de cette sous-sous-unité.")
          : "Erreur lors de la suppression";
        this.toast.error(message);
      },
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createInformationSubSubUnit({ name: val.name }).subscribe({
      next: () => {
        this.toast.success('Sous-sous-unité créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadSubSubUnits();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
