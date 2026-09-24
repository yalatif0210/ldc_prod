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
 * CRUD Super Admin (Ticket #15) sur l'entité feuille IntrantCmmConfig : création, modification,
 * suppression directe (pas de vérification de dépendants). Suit le modèle de period-admin.ts.
 */
@Component({
  selector: 'app-intrant-cmm-config-admin',
  standalone: true,
  templateUrl: './intrant-cmm-config-admin.html',
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
export class IntrantCmmConfigAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  configs: any[]          = [];
  paginatedConfigs: any[] = [];
  structures: any[]       = [];
  intrants: any[]         = [];
  equipments: any[]       = [];

  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    structureId: new FormControl<number | null>(null, [Validators.required]),
    intrantId: new FormControl<number | null>(null, [Validators.required]),
    equipmentId: new FormControl<number | null>(null, [Validators.required]),
    cmm: new FormControl<number | null>(null, [Validators.required]),
  });

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadConfigs();
  }

  loadReferenceData(): void {
    forkJoin({
      structures: this.service.getStructures(),
      intrants: this.service.getIntrantsForCmmConfig(),
      equipments: this.service.getEquipments(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ structures, intrants, equipments }) => {
        this.structures = structures;
        this.intrants = intrants;
        this.equipments = equipments;
      },
      error: () => this.toast.error('Erreur lors du chargement des référentiels'),
    });
  }

  loadConfigs(): void {
    this.loading = true;
    this.service.getIntrantCmmConfigs().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.configs = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des configurations CMM');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedConfigs = this.configs.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(config: any): void {
    this.editingId   = config.id;
    this.editingData = {
      structureId: config.structure?.id ?? null,
      intrantId: config.intrant?.id ?? null,
      equipmentId: config.equipment?.id ?? null,
      cmm: config.cmm,
    };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(config: any): void {
    this.service.updateIntrantCmmConfig(config.id, this.editingData).subscribe({
      next: () => {
        this.toast.success('Configuration CMM mise à jour');
        this.cancelEdit();
        this.loadConfigs();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteConfig(config: any): void {
    if (!confirm(`Supprimer la configuration CMM #${config.id} ?`)) return;
    this.service.deleteIntrantCmmConfig(config.id).subscribe({
      next: () => {
        this.toast.success('Configuration CMM supprimée');
        this.loadConfigs();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createIntrantCmmConfig(this.addForm.value).subscribe({
      next: () => {
        this.toast.success('Configuration CMM créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadConfigs();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
