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
  selector: 'app-region-admin',
  standalone: true,
  templateUrl: './region-admin.html',
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
export class RegionAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  regions: any[]            = [];
  paginatedRegions: any[]   = [];
  pageSize                  = 10;
  pageIndex                 = 0;
  pageSizeOptions           = [5, 10, 25, 50];
  loading                   = true;
  showAddForm               = false;
  editingId: number | null  = null;
  editingName               = '';

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadRegions();
  }

  loadRegions(): void {
    this.loading = true;
    this.service.getRegions().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.regions = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des régions');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedRegions = this.regions.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(region: any): void {
    this.editingId   = region.id;
    this.editingName = region.name;
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingName = '';
  }

  saveEdit(region: any): void {
    this.service.updateRegion(region.id, { name: this.editingName }).subscribe({
      next: () => {
        this.toast.success('Région mise à jour');
        this.cancelEdit();
        this.loadRegions();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteRegion(region: any): void {
    if (!confirm(`Supprimer la région "${region.name}" ?`)) return;
    this.service.deleteRegion(region.id).subscribe({
      next: () => {
        this.toast.success('Région supprimée');
        this.loadRegions();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createRegion({ name: val.name }).subscribe({
      next: () => {
        this.toast.success('Région créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadRegions();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
