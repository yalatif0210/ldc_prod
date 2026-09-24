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
 * CRUD Super Admin sur l'entité structurelle SanguineProduct / "compte sanguin" (Ticket #13).
 * Suit le motif établi par PeriodAdmin. La suppression est bloquée côté backend (409) tant que
 * des transactions dépendent encore du produit sanguin ; le message d'erreur détaillé renvoyé
 * par l'API est affiché tel quel.
 */
@Component({
  selector: 'app-sanguine-product-admin',
  standalone: true,
  templateUrl: './sanguine-product-admin.html',
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
export class SanguineProductAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  sanguineProducts: any[]          = [];
  paginatedSanguineProducts: any[] = [];
  pageSize                         = 10;
  pageIndex                        = 0;
  pageSizeOptions                  = [5, 10, 25, 50];
  loading                          = true;
  showAddForm                      = false;
  editingId: number | null         = null;
  editingData: any                 = {};

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadSanguineProducts();
  }

  loadSanguineProducts(): void {
    this.loading = true;
    this.service.getSanguineProducts().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.sanguineProducts = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des comptes sanguins');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedSanguineProducts = this.sanguineProducts.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(sanguineProduct: any): void {
    this.editingId   = sanguineProduct.id;
    this.editingData = { ...sanguineProduct };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(sanguineProduct: any): void {
    const data = { name: this.editingData.name };
    this.service.updateSanguineProduct(sanguineProduct.id, data).subscribe({
      next: () => {
        this.toast.success('Compte sanguin mis à jour');
        this.cancelEdit();
        this.loadSanguineProducts();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteSanguineProduct(sanguineProduct: any): void {
    if (!confirm(`Supprimer le compte sanguin "${sanguineProduct.name}" ?`)) return;
    this.service.deleteSanguineProduct(sanguineProduct.id).subscribe({
      next: () => {
        this.toast.success('Compte sanguin supprimé');
        this.loadSanguineProducts();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    this.service.createSanguineProduct({ name: this.addForm.value.name }).subscribe({
      next: () => {
        this.toast.success('Compte sanguin créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadSanguineProducts();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
