import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatOptionModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
import { MtxGridModule } from '@ng-matero/extensions/grid';
import { TranslateModule } from '@ngx-translate/core';
import { FormBaseComponent } from '@shared';
import { forkJoin, takeUntil } from 'rxjs';
import { MatDividerModule } from '@angular/material/divider';
import { SynthesisService } from '@shared/services/synthesis.service';
import { ReportHistoryService } from '@shared/services/report-history.service';
import { LoadingComponent } from '@shared/components/loading/loading';

const SPECIFIC_PRIMARY_INTRANT = 1;
const SPECIFIC_SECONDARY_INTRANT = 2;
const CONSOMMABLES_GENERAUX = 8;

@Component({
  selector: '[app-cmm-settings]',
  templateUrl: './cmm-settings.html',
  styleUrls: ['./cmm-settings.scss', '../../../public/report/lab-report/lab.scss'],
  imports: [
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatOptionModule,
    MatSelectModule,
    TranslateModule,
    MatStepperModule,
    MtxGridModule,
    MatRadioModule,
    MatDividerModule,
    LoadingComponent
  ],
})
export class CmmSettings extends FormBaseComponent implements OnInit, OnDestroy {
  private readonly service = inject(SynthesisService);
  private readonly reportHistoryService = inject(ReportHistoryService);
  equipmentInfo: { id: string; name: string; intrants: [] } = { id: '', name: '', intrants: [] };
  equipmentIntrants: any;
  selectedEquipmentName: any;
  equipmentSecondaryIntrants: any;
  pharmInputs: Record<string, number> = {};
  disabledConfirm = false;
  home_form: FormGroup | undefined;
  equipments: any[] = [];
  structure: any;
  cmmConfigInstance: any[] | null = null;

  account: any;
  structure_list: any;
  structure_by_equipment: any;

  loading = true;
  cmm_loading = false;

  constructor() {
    super();
    this.home_form = this.buildFormFromArray([
      { key: 'equipment', defaultValue: '', validators: [Validators.required] },
      { key: 'structure', defaultValue: '', validators: [Validators.required] },
    ]);
  }

  ngOnInit(): void {
    this.home_form?.get('equipment')?.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(value => {
      this.selectedEquipmentName = this.equipmentList.find((e: any) => e.id === value)?.name;
      this.structure_by_equipment = this.structure_list.filter((s: any) =>
        s.equipments.some((e: any) => e.id === value)
      );
    });

    this.home_form?.get('structure')?.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(value => {
      this.disabledConfirm = false;
      this.equipmentIntrants = null;
      this.cmmConfigInstance = null;
      this.pharmInputs = {};
    });

    forkJoin([this.reportHistoryService.getEquipments()])
      .pipe(takeUntil(this.destroy$))
      .subscribe(([response]) => {
      this.account = response.data?.account;
      this.structure_list = response.data?.account?.structures;
      this.loading = false;
    });
  }

  get equipmentList() {
    return this.reportHistoryService.getEquipmentList(this.account);
  }

  handleCmmInstance(structure_Id: any, equipmentId: any) {
    forkJoin([
      this.service.getCmmConfig({ structureId: structure_Id, equipmentId: Number(equipmentId) }),
      this.service.getEquipmentById(equipmentId),
    ]).subscribe(([config, equipment]: [any, any]) => {
      if (equipment?.data) {
        this.equipmentInfo = equipment.data.equipment;
        this.equipmentIntrants = this.equipmentInfo.intrants.filter(
          (e: any) => Number(e.intrantType.id) === SPECIFIC_PRIMARY_INTRANT
        ) as any[];
        this.equipmentSecondaryIntrants = this.equipmentInfo.intrants.filter(
          (e: any) => Number(e.intrantType.id) === SPECIFIC_SECONDARY_INTRANT
        ) as any[];
      }

      const existing = config?.data?.intrantCmmConfigByStructureAndEquipment;
      if (existing?.length > 0) {
        this.cmmConfigInstance = existing
          .filter((element: any) =>
            Number(element?.intrant?.intrantType.id) === SPECIFIC_PRIMARY_INTRANT
          );
        this.pharmInputs = this.cmmConfigInstance!.reduce(
          (acc: Record<string, number>, element: any) => {
            acc['cmm_qty_for_intrant_' + element.intrant.id] = element.cmm;
            return acc;
          }, {});
      }
      this.cmm_loading = false;
    });
  }

  onSubmit() {
    if (this.home_form?.valid) {
      this.cmm_loading = true;
      const value = this.home_form?.value;
      this.disabledConfirm = false;
      this.equipmentIntrants = null;
      this.cmmConfigInstance = null;
      this.handleCmmInstance(value.structure, value.equipment);
    }
  }

  ngOnDestroy(): void {
    this.service.clearList();
  }

  onConfirm() {
    this.disabledConfirm = true;
    const value = this.home_form?.value;
    const dto = this.service.handleCmmInputToDTO(
      { structureId: value.structure, equipmentId: value.equipment },
      this.pharmInputs
    );
    const cmm_config = this.normalizeIntrantCmm(dto, this.equipmentSecondaryIntrants);
    this.service
      .handleCreateCmmConfig(cmm_config)
      .subscribe(r => {
        this.toast.success('CMM configurées avec succès.');
      });
  }

  normalizeIntrantCmm(dto: any[], secondaryIntrant: any[]) {
    if (!dto?.length) return dto;
    const result = structuredClone(dto);
    const test_realisable_array = result.map((e: any) => {
      const intrant = this.equipmentIntrants?.find((i: any) => Number(i.id) === e.intrantId);
      const factor = intrant?.convertionFactor ?? 0;
      const base = e.test_realisable ?? e.cmm ?? 0;
      return {
        code: intrant?.code,
        test_realisable: base * factor,
      };
    });

    const { structureId, equipmentId } = dto[0];
    const out_put = secondaryIntrant.map(intrant => ({
      structureId,
      equipmentId,
      intrantId: Number(intrant.id),
      cmm: this.service.handleNormalizeSecondaryIntrantCmm(
        intrant,
        test_realisable_array,
        this.selectedEquipmentName),
    }));

    return [...dto, ...out_put];
  }
}
