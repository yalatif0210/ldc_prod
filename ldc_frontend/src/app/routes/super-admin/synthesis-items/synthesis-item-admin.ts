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
 * CRUD Super Admin (Ticket #15) sur l'entité feuille Synthesis. À ne pas confondre avec l'écran
 * synthesis-admin (route 'synthesis') déjà existant, qui gère en réalité des Report sous un nom
 * trompeur : cet écran vit sous la route distincte 'synthesis-items'. Suit le modèle de
 * period-admin.ts.
 */
@Component({
  selector: 'app-synthesis-item-admin',
  standalone: true,
  templateUrl: './synthesis-item-admin.html',
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
export class SynthesisItemAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  items: any[]          = [];
  paginatedItems: any[] = [];
  synthesisTypes: any[] = [];
  informationUnits: any[] = [];

  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    item: new FormControl('', [Validators.required]),
    synthesisTypeId: new FormControl<number | null>(null, [Validators.required]),
    informationUnitId: new FormControl<number | null>(null),
  });

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadItems();
  }

  loadReferenceData(): void {
    forkJoin({
      synthesisTypes: this.service.getSynthesisTypes(),
      informationUnits: this.service.getInformationUnitsForSynthesis(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ synthesisTypes, informationUnits }) => {
        this.synthesisTypes = synthesisTypes;
        this.informationUnits = informationUnits;
      },
      error: () => this.toast.error('Erreur lors du chargement des référentiels'),
    });
  }

  loadItems(): void {
    this.loading = true;
    this.service.getSynthesisItems().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.items = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des synthèses');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedItems = this.items.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(item: any): void {
    this.editingId   = item.id;
    this.editingData = {
      item: item.item,
      synthesisTypeId: item.synthesisType?.id ?? null,
      informationUnitId: item.informationUnit?.id ?? null,
    };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(item: any): void {
    this.service.updateSynthesisItem(item.id, this.editingData).subscribe({
      next: () => {
        this.toast.success('Synthèse mise à jour');
        this.cancelEdit();
        this.loadItems();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteItem(item: any): void {
    if (!confirm(`Supprimer la synthèse "${item.item}" ?`)) return;
    this.service.deleteSynthesisItem(item.id).subscribe({
      next: () => {
        this.toast.success('Synthèse supprimée');
        this.loadItems();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createSynthesisItem(this.addForm.value).subscribe({
      next: () => {
        this.toast.success('Synthèse créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadItems();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
