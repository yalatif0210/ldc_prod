import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { HttpClient } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-intrant-admin',
  standalone: true,
  templateUrl: './intrant-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class IntrantAdmin implements OnInit, OnDestroy {
  private readonly http    = inject(HttpClient);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  intrants: any[]          = [];
  filtered: any[]          = [];
  paginatedFiltered: any[] = [];
  pageSize                 = 10;
  pageIndex                = 0;
  pageSizeOptions          = [5, 10, 25, 50];
  loading                  = true;
  searchTerm               = '';

  ngOnInit(): void {
    this.loadIntrants();
  }

  loadIntrants(): void {
    this.loading = true;
    this.http.get<any>('/api/super-admin/intrants').pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        this.intrants = Array.isArray(res) ? res : (res?.data ?? res?.intrants ?? []);
        this.filtered = [...this.intrants];
        this.loading  = false;
        this.paginate();
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
        i => (i.name ?? i.nom ?? '').toLowerCase().includes(term)
          || (i.code ?? '').toLowerCase().includes(term)
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
