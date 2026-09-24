import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatOptionModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { forkJoin, Subject, takeUntil } from 'rxjs';
import { AgGridAngular } from 'ag-grid-angular'; // Angular Data Grid Component
import { AllCommunityModule, ColDef, ModuleRegistry } from 'ag-grid-community'; // Column Definition Type Interface

// Register required modules globally, before any grids are instantiated (voir app-table.ts).
ModuleRegistry.registerModules([AllCommunityModule]);

interface TransactionLineRow {
  refId: number | null;
  quantity: number;
}

/**
 * Écran Super Admin du CRUD Transferts (entité {@code Transaction}) — Ticket #8.
 * Grille ag-grid listant les Transferts (Structures origine/destination, Équipement, statut
 * d'approbation) avec formulaire de création/modification incluant les lignes filles
 * (produits sanguins / intrants). La suppression déclenche la cascade explicite côté backend
 * (voir {@code SuperAdminTransactionService.delete}).
 */
@Component({
  selector: 'app-transaction-admin',
  standalone: true,
  templateUrl: './transaction-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatOptionModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    AgGridAngular,
  ],
})
export class TransactionAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  transactions: any[]     = [];
  structures: any[]       = [];
  equipments: any[]       = [];
  sanguineProducts: any[] = [];
  intrants: any[]         = [];
  loading                 = true;

  showForm                  = false;
  editingId: number | null  = null;

  formOriginId: number | null                = null;
  formDestinationId: number | null           = null;
  formEquipmentId: number | null             = null;
  formEquipmentDestinataireId: number | null = null;
  formApproved                               = false;
  formIsRejected                             = false;
  sanguineLines: TransactionLineRow[]        = [];
  medicineLines: TransactionLineRow[]        = [];

  defaultColDef: ColDef = {
    filter: true,
    sortable: true,
    resizable: true,
    flex: 1,
  };

  colDefs: ColDef[] = [
    { field: 'id', headerName: 'ID', flex: 0, width: 90 },
    {
      colId: 'origin',
      headerName: 'Structure origine',
      valueGetter: (params: any) => params.data?.origin?.name ?? '',
    },
    {
      colId: 'destination',
      headerName: 'Structure destination',
      valueGetter: (params: any) => params.data?.destination?.name ?? '',
    },
    {
      colId: 'equipment',
      headerName: 'Équipement',
      valueGetter: (params: any) => params.data?.equipment?.name ?? '',
    },
    {
      colId: 'equipmentDestinataire',
      headerName: 'Équipement destinataire',
      valueGetter: (params: any) => params.data?.equipmentDestinataire?.name ?? '',
    },
    {
      colId: 'approved',
      headerName: 'Approuvé',
      flex: 0,
      width: 120,
      valueGetter: (params: any) => (params.data?.approved ? 'Oui' : 'Non'),
    },
    {
      colId: 'isRejected',
      headerName: 'Rejeté',
      flex: 0,
      width: 100,
      valueGetter: (params: any) => (params.data?.isRejected ? 'Oui' : 'Non'),
    },
    {
      colId: 'createdAt',
      headerName: 'Créé le',
      valueGetter: (params: any) =>
        params.data?.createdAt ? new Date(params.data.createdAt).toLocaleString('fr-FR') : '',
    },
    {
      field: 'actions',
      headerName: 'Actions',
      flex: 0,
      width: 140,
      sortable: false,
      filter: false,
      cellRenderer: () =>
        `<button type="button" data-action="edit" class="ag-grid-action-btn" title="Modifier">✏️</button>` +
        `<button type="button" data-action="delete" class="ag-grid-action-btn" title="Supprimer">🗑️</button>`,
    },
  ];

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading = true;
    forkJoin({
      transactions: this.service.getTransactions(),
      structures: this.service.getStructures(),
      equipments: this.service.getEquipments(),
      sanguineProducts: this.service.getTransactionSanguineProducts(),
      intrants: this.service.getTransactionIntrants(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ transactions, structures, equipments, sanguineProducts, intrants }) => {
        this.transactions     = transactions;
        this.structures        = structures;
        this.equipments        = equipments;
        this.sanguineProducts  = sanguineProducts;
        this.intrants           = intrants;
        this.loading            = false;
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des transferts');
        this.loading = false;
      },
    });
  }

  onCellClicked(event: any): void {
    if (event.colDef?.field !== 'actions') return;
    const target = event.event?.target as HTMLElement | null;
    const action = target?.closest?.('[data-action]')?.getAttribute('data-action');
    if (action === 'edit') {
      this.startEdit(event.data);
    } else if (action === 'delete') {
      this.deleteTransaction(event.data);
    }
  }

  openCreateForm(): void {
    this.editingId                   = null;
    this.formOriginId                = null;
    this.formDestinationId           = null;
    this.formEquipmentId             = null;
    this.formEquipmentDestinataireId = null;
    this.formApproved                = false;
    this.formIsRejected              = false;
    this.sanguineLines               = [];
    this.medicineLines               = [];
    this.showForm                    = true;
  }

  startEdit(transaction: any): void {
    this.editingId                   = transaction.id;
    this.formOriginId                = transaction.origin?.id ?? null;
    this.formDestinationId           = transaction.destination?.id ?? null;
    this.formEquipmentId             = transaction.equipment?.id ?? null;
    this.formEquipmentDestinataireId = transaction.equipmentDestinataire?.id ?? null;
    this.formApproved                = !!transaction.approved;
    this.formIsRejected              = !!transaction.isRejected;
    this.sanguineLines = (transaction.sanguineProductTransactions ?? []).map((l: any) => ({
      refId: l.sanguineProduct?.id ?? null,
      quantity: l.quantity,
    }));
    this.medicineLines = (transaction.medicinesTransactions ?? []).map((l: any) => ({
      refId: l.intrant?.id ?? null,
      quantity: l.quantity,
    }));
    this.showForm = true;
  }

  cancelForm(): void {
    this.showForm   = false;
    this.editingId  = null;
  }

  addSanguineLine(): void {
    this.sanguineLines.push({ refId: null, quantity: 1 });
  }

  removeSanguineLine(index: number): void {
    this.sanguineLines.splice(index, 1);
  }

  addMedicineLine(): void {
    this.medicineLines.push({ refId: null, quantity: 1 });
  }

  removeMedicineLine(index: number): void {
    this.medicineLines.splice(index, 1);
  }

  submitForm(): void {
    const payload = {
      originId: this.formOriginId,
      destinationId: this.formDestinationId,
      equipmentId: this.formEquipmentId,
      equipmentDestinataireId: this.formEquipmentDestinataireId,
      approved: this.formApproved,
      isRejected: this.formIsRejected,
      sanguineProductTransactions: this.sanguineLines
        .filter(l => l.refId != null)
        .map(l => ({ sanguine_product_id: l.refId, quantity: l.quantity })),
      medicinesTransactions: this.medicineLines
        .filter(l => l.refId != null)
        .map(l => ({ intrant_id: l.refId, quantity: l.quantity })),
    };

    const request = this.editingId
      ? this.service.updateTransaction(this.editingId, payload)
      : this.service.createTransaction(payload);

    request.subscribe({
      next: () => {
        this.toast.success(this.editingId ? 'Transfert mis à jour' : 'Transfert créé');
        this.cancelForm();
        this.loadAll();
      },
      error: () => this.toast.error(this.editingId ? 'Erreur lors de la mise à jour' : 'Erreur lors de la création'),
    });
  }

  deleteTransaction(transaction: any): void {
    if (!confirm(`Supprimer le transfert #${transaction.id} et ses lignes ?`)) return;
    this.service.deleteTransaction(transaction.id).subscribe({
      next: () => {
        this.toast.success('Transfert supprimé');
        this.loadAll();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
