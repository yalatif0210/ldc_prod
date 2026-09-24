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

/**
 * CRUD Super Admin sur l'entité structurelle Equipment (Ticket #13). Suit le motif établi par
 * PeriodAdmin (période) : liste paginée + formulaire d'ajout + édition inline + suppression.
 * La suppression est bloquée côté backend (409) tant que des enregistrements dépendent encore de
 * l'équipement ; le message d'erreur détaillé renvoyé par l'API est affiché tel quel.
 */
@Component({
  selector: 'app-equipment-admin',
  standalone: true,
  templateUrl: './equipment-admin.html',
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
export class EquipmentAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  equipments: any[]          = [];
  paginatedEquipments: any[] = [];
  pageSize                   = 10;
  pageIndex                  = 0;
  pageSizeOptions            = [5, 10, 25, 50];
  loading                    = true;
  showAddForm                = false;
  editingId: number | null   = null;
  editingData: any           = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadEquipments();
  }

  loadEquipments(): void {
    this.loading = true;
    this.service.getEquipments().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.equipments = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des équipements');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedEquipments = this.equipments.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(equipment: any): void {
    this.editingId   = equipment.id;
    this.editingData = { ...equipment };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(equipment: any): void {
    this.service.updateEquipment(equipment.id, { name: this.editingData.name }).subscribe({
      next: () => {
        this.toast.success('Équipement mis à jour');
        this.cancelEdit();
        this.loadEquipments();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteEquipment(equipment: any): void {
    if (!confirm(`Supprimer l'équipement "${equipment.name}" ?`)) return;
    this.service.deleteEquipment(equipment.id).subscribe({
      next: () => {
        this.toast.success('Équipement supprimé');
        this.loadEquipments();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createEquipment({ name: this.addForm.value.name }).subscribe({
      next: () => {
        this.toast.success('Équipement créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadEquipments();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
