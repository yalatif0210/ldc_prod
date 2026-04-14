import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { jsPDF } from 'jspdf';
import * as ExcelJS from 'exceljs';
import { saveAs } from 'file-saver';
import { CompletenessDetail } from './dashboard.service';

export interface CompletenessDetailDialogData {
  details: CompletenessDetail[];
  period: string;
  equipment: string;
}

@Component({
  selector: 'completeness-detail-dialog',
  templateUrl: './completeness-detail-dialog.html',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
  ],
})
export class CompletenessDetailDialog {
  readonly data: CompletenessDetailDialogData = inject(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<CompletenessDetailDialog>);

  pageSize = 10;
  pageIndex = 0;
  exporting = false;

  get pageData(): CompletenessDetail[] {
    const start = this.pageIndex * this.pageSize;
    return this.data.details.slice(start, start + this.pageSize);
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
  }

  exportPdf(): void {
    const { details, period, equipment } = this.data;
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });

    const marginL = 14;
    const colSite = 100;
    const colLab = 39;
    const colPharma = 39;
    const rowH = 8;
    const headerH = 10;

    const generatedAt = new Date().toLocaleString('fr-FR');

    // ---- Titre ----
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(14);
    doc.setTextColor(30, 30, 30);
    doc.text(`Détails Complétude — ${period}`, marginL, 14);

    // ---- Équipement ----
    if (equipment) {
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(10);
      doc.setTextColor(30, 136, 229);
      doc.text(`Équipement : ${equipment}`, marginL, 21);
    }

    // ---- Date de génération ----
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9);
    doc.setTextColor(120, 120, 120);
    doc.text(`Généré le ${generatedAt}`, marginL, equipment ? 27 : 21);

    let y = equipment ? 36 : 30;

    const drawTableHeader = (startY: number): void => {
      // Fond bleu
      doc.setFillColor(30, 136, 229);
      doc.rect(marginL, startY, colSite + colLab + colPharma, headerH, 'F');

      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9);
      doc.setTextColor(255, 255, 255);

      // Site — aligné à gauche
      doc.text('Site', marginL + 2, startY + 6.5);

      // Rapport laboratoire — centré
      const labX = marginL + colSite + colLab / 2;
      doc.text('Rapport laboratoire', labX, startY + 6.5, { align: 'center' });

      // Rapport pharmacie — centré
      const pharmaX = marginL + colSite + colLab + colPharma / 2;
      doc.text('Rapport pharmacie', pharmaX, startY + 6.5, { align: 'center' });
    };

    drawTableHeader(y);
    y += headerH;

    details.forEach((row, i) => {
      // Gestion multi-pages
      if (y > 272) {
        doc.addPage();
        y = 14;
        drawTableHeader(y);
        y += headerH;
      }

      // Fond alterné
      if (i % 2 === 0) {
        doc.setFillColor(255, 255, 255);
      } else {
        doc.setFillColor(248, 249, 250);
      }
      doc.rect(marginL, y, colSite + colLab + colPharma, rowH, 'F');

      // Séparateur horizontal
      doc.setDrawColor(224, 224, 224);
      doc.setLineWidth(0.1);
      doc.line(marginL, y + rowH, marginL + colSite + colLab + colPharma, y + rowH);

      // Colonne Site — tronqué si nécessaire, aligné à gauche
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      doc.setTextColor(30, 30, 30);
      const siteLines = doc.splitTextToSize(row.site?.name ?? '', colSite - 4);
      doc.text(siteLines[0], marginL + 2, y + 5.5);

      // Colonne Rapport laboratoire — centré, coloré
      const labOk = row.labDataReported;
      doc.setFont('helvetica', 'bold');
      if (labOk) {
        doc.setTextColor(46, 125, 50);
        doc.text('OUI', marginL + colSite + colLab / 2, y + 5.5, { align: 'center' });
      } else {
        doc.setTextColor(198, 40, 40);
        doc.text('NON', marginL + colSite + colLab / 2, y + 5.5, { align: 'center' });
      }

      // Colonne Rapport pharmacie — centré, coloré
      const pharmaOk = row.pharmDataReported;
      if (pharmaOk) {
        doc.setTextColor(46, 125, 50);
        doc.text('OUI', marginL + colSite + colLab + colPharma / 2, y + 5.5, { align: 'center' });
      } else {
        doc.setTextColor(198, 40, 40);
        doc.text('NON', marginL + colSite + colLab + colPharma / 2, y + 5.5, { align: 'center' });
      }

      y += rowH;
    });

    const eqSlug = equipment ? `_${equipment.replace(/\s+/g, '_')}` : '';
    doc.save(`completude_${period.replace(/\s+/g, '_')}${eqSlug}.pdf`);
  }

  async exportExcel(): Promise<void> {
    this.exporting = true;
    try {
      const { details, period, equipment } = this.data;
      const generatedAt = new Date().toLocaleString('fr-FR');

      const workbook = new ExcelJS.Workbook();
      const sheet = workbook.addWorksheet('Complétude');

      // Largeurs de colonnes
      sheet.getColumn(1).width = 42;
      sheet.getColumn(2).width = 22;
      sheet.getColumn(3).width = 22;

      // ---- Ligne 1 : titre mergé A1:C1 ----
      sheet.mergeCells('A1:C1');
      const titleRow = sheet.getRow(1);
      titleRow.height = 28;
      const titleCell = sheet.getCell('A1');
      titleCell.value = `Détails Complétude — ${period}`;
      titleCell.font = { bold: true, size: 13 };
      titleCell.alignment = { horizontal: 'center', vertical: 'middle' };

      // ---- Ligne 2 : équipement mergé A2:C2 (si présent) ----
      let nextRow = 2;
      if (equipment) {
        sheet.mergeCells('A2:C2');
        const eqRow = sheet.getRow(2);
        eqRow.height = 18;
        const eqCell = sheet.getCell('A2');
        eqCell.value = `Équipement : ${equipment}`;
        eqCell.font = { bold: true, size: 10, color: { argb: 'FF1E88E5' } };
        eqCell.alignment = { horizontal: 'center', vertical: 'middle' };
        nextRow = 3;
      }

      // ---- Date de génération ----
      sheet.mergeCells(`A${nextRow}:C${nextRow}`);
      const dateRow = sheet.getRow(nextRow);
      dateRow.height = 16;
      const dateCell = sheet.getCell(`A${nextRow}`);
      dateCell.value = `Généré le ${generatedAt}`;
      dateCell.font = { italic: true, color: { argb: 'FF9E9E9E' } };
      dateCell.alignment = { horizontal: 'center', vertical: 'middle' };
      nextRow++;

      // ---- Spacer ----
      const spacerRow = sheet.getRow(nextRow);
      spacerRow.height = 6;
      nextRow++;

      // ---- En-têtes ----
      const headerRow = sheet.getRow(nextRow);
      headerRow.height = 22;
      const headers = ['Site', 'Rapport laboratoire', 'Rapport pharmacie'];
      headers.forEach((h, colIdx) => {
        const cell = headerRow.getCell(colIdx + 1);
        cell.value = h;
        cell.font = { bold: true, size: 10, color: { argb: 'FFFFFFFF' } };
        cell.fill = {
          type: 'pattern',
          pattern: 'solid',
          fgColor: { argb: 'FF1E88E5' },
        };
        cell.alignment = { horizontal: 'center', vertical: 'middle' };
      });

      // ---- Lignes de données ----
      details.forEach((row, i) => {
        const dataRow = sheet.addRow([
          row.site?.name ?? '',
          row.labDataReported ? '✓ Soumis' : '✗ Non soumis',
          row.pharmDataReported ? '✓ Soumis' : '✗ Non soumis',
        ]);
        dataRow.height = 18;

        const bgColor = i % 2 === 0 ? 'FFFFFFFF' : 'FFF3F8FE';

        // Colonne 1 : Site
        const siteCell = dataRow.getCell(1);
        siteCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: bgColor } };
        siteCell.alignment = { horizontal: 'left', vertical: 'middle' };

        // Colonne 2 : Rapport laboratoire
        const labCell = dataRow.getCell(2);
        labCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: bgColor } };
        labCell.font = {
          bold: true,
          color: { argb: row.labDataReported ? 'FF2E7D32' : 'FFC62828' },
        };
        labCell.alignment = { horizontal: 'center', vertical: 'middle' };

        // Colonne 3 : Rapport pharmacie
        const pharmaCell = dataRow.getCell(3);
        pharmaCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: bgColor } };
        pharmaCell.font = {
          bold: true,
          color: { argb: row.pharmDataReported ? 'FF2E7D32' : 'FFC62828' },
        };
        pharmaCell.alignment = { horizontal: 'center', vertical: 'middle' };
      });

      const buffer = await workbook.xlsx.writeBuffer();
      const blob = new Blob([buffer], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      const eqSlug = equipment ? `_${equipment.replace(/\s+/g, '_')}` : '';
      saveAs(blob, `completude_${period.replace(/\s+/g, '_')}${eqSlug}.xlsx`);
    } finally {
      this.exporting = false;
    }
  }
}
