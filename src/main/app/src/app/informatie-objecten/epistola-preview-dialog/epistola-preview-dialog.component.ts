/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, inject, OnDestroy } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { DomSanitizer } from "@angular/platform-browser";
import { TranslateModule } from "@ngx-translate/core";
import { GenericDialogComponent } from "../../shared/dialog/generic-dialog/generic-dialog.component";

export type EpistolaPreviewDialogData = {
  pdf: Blob;
  templateName: string;
};

@Component({
  selector: "zac-epistola-preview-dialog",
  templateUrl: "./epistola-preview-dialog.component.html",
  standalone: true,
  imports: [GenericDialogComponent, TranslateModule],
})
export class EpistolaPreviewDialogComponent implements OnDestroy {
  protected readonly data = inject<EpistolaPreviewDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<EpistolaPreviewDialogComponent>>(MatDialogRef);

  private readonly objectUrl = URL.createObjectURL(this.data.pdf);
  protected readonly pdfUrl = inject(
    DomSanitizer,
  ).bypassSecurityTrustResourceUrl(this.objectUrl);

  protected close() {
    this.dialogRef.close();
  }

  ngOnDestroy() {
    URL.revokeObjectURL(this.objectUrl);
  }
}
