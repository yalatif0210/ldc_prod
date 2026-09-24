import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, forkJoin, takeUntil } from 'rxjs';

@Component({
  selector: 'app-notification-admin',
  standalone: true,
  templateUrl: './notification-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSelectModule,
  ],
})
export class NotificationAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  notifications: any[]          = [];
  paginatedNotifications: any[] = [];
  pageSize                      = 10;
  pageIndex                     = 0;
  pageSizeOptions                = [5, 10, 25, 50];
  loading                        = true;
  showAddForm                    = false;
  editingId: number | null       = null;
  editingData: any                = {};

  structures: any[] = [];
  equipments: any[] = [];
  intrants: any[]   = [];

  addForm = new FormGroup({
    emitterId:   new FormControl<number | null>(null, [Validators.required]),
    equipmentId: new FormControl<number | null>(null, [Validators.required]),
    intrantId:   new FormControl<number | null>(null, [Validators.required]),
    quantity:    new FormControl<number | null>(null, [Validators.required, Validators.min(0)]),
    isResolved:  new FormControl(false),
    isRejected:  new FormControl(false),
  });

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadNotifications();
  }

  loadReferenceData(): void {
    forkJoin({
      structures: this.service.getStructures(),
      equipments: this.service.getEquipments(),
      intrants:   this.service.getNotificationIntrants(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ structures, equipments, intrants }) => {
        this.structures = structures;
        this.equipments = equipments;
        this.intrants   = intrants;
      },
      error: () => this.toast.error('Erreur lors du chargement des données de référence'),
    });
  }

  loadNotifications(): void {
    this.loading = true;
    this.service.getNotifications().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.notifications = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des notifications');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedNotifications = this.notifications.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(notification: any): void {
    this.editingId   = notification.id;
    this.editingData = {
      emitterId:   notification.emitterId,
      equipmentId: notification.equipmentId,
      intrantId:   notification.intrantId,
      quantity:    notification.quantity,
      isResolved:  notification.isResolved,
      isRejected:  notification.isRejected,
    };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(notification: any): void {
    const payload = {
      emitterId:   this.editingData.emitterId,
      equipmentId: this.editingData.equipmentId,
      intrantId:   this.editingData.intrantId,
      quantity:    this.editingData.quantity,
      isResolved:  this.editingData.isResolved,
      isRejected:  this.editingData.isRejected,
    };
    this.service.updateNotification(notification.id, payload).subscribe({
      next: () => {
        this.toast.success('Notification mise à jour');
        this.cancelEdit();
        this.loadNotifications();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deleteNotification(notification: any): void {
    if (!confirm(`Supprimer la notification #${notification.id} ?`)) return;
    this.service.deleteNotification(notification.id).subscribe({
      next: () => {
        this.toast.success('Notification supprimée');
        this.loadNotifications();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createNotification({
      emitterId:   val.emitterId,
      equipmentId: val.equipmentId,
      intrantId:   val.intrantId,
      quantity:    val.quantity,
      isResolved:  val.isResolved ?? false,
      isRejected:  val.isRejected ?? false,
    }).subscribe({
      next: () => {
        this.toast.success('Notification créée');
        this.addForm.reset({ isResolved: false, isRejected: false });
        this.showAddForm = false;
        this.loadNotifications();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
