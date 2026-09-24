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
  selector: 'app-intrant-type-admin',
  standalone: true,
  templateUrl: './intrant-type-admin.html',
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
export class IntrantTypeAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  intrantTypes: any[]          = [];
  paginatedIntrantTypes: any[] = [];
  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadIntrantTypes();
  }

  loadIntrantTypes(): void {
    this.loading = true;
    this.service.getIntrantTypes().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.intrantTypes = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des types d\'intrant');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedIntrantTypes = this.intrantTypes.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(intrantType: any): void {
    this.editingId   = intrantType.id;
    this.editingData = { ...intrantType };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(intrantType: any): void {
    const payload = { name: this.editingData.name };
    this.service.updateIntrantType(intrantType.id, payload).subscribe({
      next: () => {
        this.toast.success('Type d\'intrant mis à jour');
        this.cancelEdit();
        this.loadIntrantTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteIntrantType(intrantType: any): void {
    if (!confirm(`Supprimer le type d'intrant "${intrantType.name}" ?`)) return;
    this.service.deleteIntrantType(intrantType.id).subscribe({
      next: () => {
        this.toast.success('Type d\'intrant supprimé');
        this.loadIntrantTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createIntrantType({ name: val.name }).subscribe({
      next: () => {
        this.toast.success('Type d\'intrant créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadIntrantTypes();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
