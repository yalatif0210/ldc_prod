import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, forkJoin, takeUntil } from 'rxjs';

/**
 * CRUD Super Admin (Ticket #15) sur l'entité feuille Information : création, modification,
 * suppression directe (pas de vérification de dépendants). Suit le modèle de period-admin.ts.
 */
@Component({
  selector: 'app-information-admin',
  standalone: true,
  templateUrl: './information-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class InformationAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  informations: any[]          = [];
  paginatedInformations: any[] = [];
  informationUnits: any[]      = [];
  informationSubUnits: any[]   = [];
  informationSubSubUnits: any[] = [];
  equipments: any[]            = [];

  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    informationUnitId: new FormControl<number | null>(null, [Validators.required]),
    informationSubUnitId: new FormControl<number | null>(null, [Validators.required]),
    informationSubSubUnitId: new FormControl<number | null>(null, [Validators.required]),
    equipmentId: new FormControl<number | null>(null, [Validators.required]),
    isActive: new FormControl(true),
  });

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadInformations();
  }

  loadReferenceData(): void {
    forkJoin({
      informationUnits: this.service.getInformationUnits(),
      informationSubUnits: this.service.getInformationSubUnits(),
      informationSubSubUnits: this.service.getInformationSubSubUnits(),
      equipments: this.service.getEquipments(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ informationUnits, informationSubUnits, informationSubSubUnits, equipments }) => {
        this.informationUnits = informationUnits;
        this.informationSubUnits = informationSubUnits;
        this.informationSubSubUnits = informationSubSubUnits;
        this.equipments = equipments;
      },
      error: () => this.toast.error('Erreur lors du chargement des référentiels'),
    });
  }

  loadInformations(): void {
    this.loading = true;
    this.service.getInformations().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.informations = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des informations');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedInformations = this.informations.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(info: any): void {
    this.editingId   = info.id;
    this.editingData = {
      informationUnitId: info.informationUnit?.id ?? null,
      informationSubUnitId: info.informationSubUnit?.id ?? null,
      informationSubSubUnitId: info.informationSubSubUnit?.id ?? null,
      equipmentId: info.equipment?.id ?? null,
      isActive: info.isActive,
    };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(info: any): void {
    this.service.updateInformation(info.id, this.editingData).subscribe({
      next: () => {
        this.toast.success('Information mise à jour');
        this.cancelEdit();
        this.loadInformations();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteInformation(info: any): void {
    if (!confirm(`Supprimer l'information #${info.id} ?`)) return;
    this.service.deleteInformation(info.id).subscribe({
      next: () => {
        this.toast.success('Information supprimée');
        this.loadInformations();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createInformation(this.addForm.value).subscribe({
      next: () => {
        this.toast.success('Information créée');
        this.addForm.reset({ isActive: true });
        this.showAddForm = false;
        this.loadInformations();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
