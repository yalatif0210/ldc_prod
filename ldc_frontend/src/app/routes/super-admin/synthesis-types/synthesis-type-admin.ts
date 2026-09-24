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
  selector: 'app-synthesis-type-admin',
  standalone: true,
  templateUrl: './synthesis-type-admin.html',
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
export class SynthesisTypeAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  synthesisTypes: any[]          = [];
  paginatedSynthesisTypes: any[] = [];
  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    type: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadSynthesisTypes();
  }

  loadSynthesisTypes(): void {
    this.loading = true;
    this.service.getSynthesisTypes().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.synthesisTypes = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des types de synthèse');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedSynthesisTypes = this.synthesisTypes.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(synthesisType: any): void {
    this.editingId   = synthesisType.id;
    this.editingData = { ...synthesisType };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(synthesisType: any): void {
    const payload = { type: this.editingData.type };
    this.service.updateSynthesisType(synthesisType.id, payload).subscribe({
      next: () => {
        this.toast.success('Type de synthèse mis à jour');
        this.cancelEdit();
        this.loadSynthesisTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteSynthesisType(synthesisType: any): void {
    if (!confirm(`Supprimer le type de synthèse "${synthesisType.type}" ?`)) return;
    this.service.deleteSynthesisType(synthesisType.id).subscribe({
      next: () => {
        this.toast.success('Type de synthèse supprimé');
        this.loadSynthesisTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createSynthesisType({ type: val.type }).subscribe({
      next: () => {
        this.toast.success('Type de synthèse créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadSynthesisTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
