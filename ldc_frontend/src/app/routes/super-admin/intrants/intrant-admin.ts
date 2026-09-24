import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, forkJoin, takeUntil } from 'rxjs';

/**
 * CRUD Super Admin sur l'entité structurelle Intrant (Ticket #13). Remplace l'ancien écran
 * "Référentiel Intrants" en lecture seule : celui-ci appelait déjà GET /api/super-admin/intrants,
 * endpoint désormais servi par SuperAdminIntrantController, complété ici avec création, édition
 * et suppression (bloquée côté backend, 409, tant que des enregistrements dépendent encore de
 * l'intrant — message d'erreur détaillé affiché tel quel).
 */
@Component({
  selector: 'app-intrant-admin',
  standalone: true,
  templateUrl: './intrant-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class IntrantAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  intrants: any[]          = [];
  filtered: any[]          = [];
  paginatedFiltered: any[] = [];
  intrantTypes: any[]      = [];
  equipments: any[]        = [];
  pageSize                 = 10;
  pageIndex                = 0;
  pageSizeOptions          = [5, 10, 25, 50];
  loading                  = true;
  searchTerm               = '';
  showAddForm              = false;
  editingId: number | null = null;
  editingData: any         = {};

  addForm = new FormGroup({
    name:          new FormControl('', [Validators.required]),
    code:          new FormControl<number | null>(null, [Validators.required]),
    sku:           new FormControl('', [Validators.required]),
    intrantTypeId: new FormControl<number | null>(null, [Validators.required]),
    equipmentId:   new FormControl<number | null>(null, [Validators.required]),
    conversionFactor: new FormControl<number>(1),
    roundFactor:      new FormControl<number>(1),
    otherFactor:      new FormControl<number>(1),
  });

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadIntrants();
  }

  loadReferenceData(): void {
    forkJoin({
      intrantTypes: this.service.getIntrantTypes(),
      equipments: this.service.getEquipments(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ intrantTypes, equipments }) => {
        this.intrantTypes = intrantTypes;
        this.equipments   = equipments;
      },
      error: () => this.toast.error('Erreur lors du chargement des référentiels (types, équipements)'),
    });
  }

  loadIntrants(): void {
    this.loading = true;
    this.service.getIntrants().pipe(takeUntil(this.destroy$)).subscribe({
      next: (data) => {
        this.intrants = Array.isArray(data) ? data : [];
        this.applySearch();
        this.loading  = false;
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des intrants');
        this.loading = false;
      },
    });
  }

  applySearch(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) {
      this.filtered = [...this.intrants];
    } else {
      this.filtered = this.intrants.filter(
        i => (i.name ?? '').toLowerCase().includes(term)
          || String(i.code ?? '').toLowerCase().includes(term)
      );
    }
    this.pageIndex = 0;
    this.paginate();
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedFiltered = this.filtered.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(intrant: any): void {
    this.editingId   = intrant.id;
    this.editingData = {
      ...intrant,
      intrantTypeId: intrant.intrantType?.id ?? null,
      equipmentId:   intrant.equipment?.id ?? null,
    };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  private toPayload(source: any): any {
    return {
      name: source.name,
      code: source.code,
      sku: source.sku,
      intrantTypeId: source.intrantTypeId,
      equipmentId: source.equipmentId,
      conversionFactor: source.conversionFactor ?? 1,
      roundFactor: source.roundFactor ?? 1,
      otherFactor: source.otherFactor ?? 1,
    };
  }

  saveEdit(intrant: any): void {
    this.service.updateIntrant(intrant.id, this.toPayload(this.editingData)).subscribe({
      next: () => {
        this.toast.success('Intrant mis à jour');
        this.cancelEdit();
        this.loadIntrants();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteIntrant(intrant: any): void {
    if (!confirm(`Supprimer l'intrant "${intrant.name}" ?`)) return;
    this.service.deleteIntrant(intrant.id).subscribe({
      next: () => {
        this.toast.success('Intrant supprimé');
        this.loadIntrants();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createIntrant(this.toPayload(this.addForm.value)).subscribe({
      next: () => {
        this.toast.success('Intrant créé');
        this.addForm.reset({ conversionFactor: 1, roundFactor: 1, otherFactor: 1 });
        this.showAddForm = false;
        this.loadIntrants();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
