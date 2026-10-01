/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, computed, inject } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogModule } from "@angular/material/dialog";
import { TranslateModule } from "@ngx-translate/core";
import { injectQuery } from "@tanstack/angular-query-experimental";
import { EpistolaGenerationProgressComponent } from "../epistola-generation-progress/epistola-generation-progress.component";
import { InformatieObjectenService } from "../informatie-objecten.service";

export type EpistolaGenerationDialogData = {
  zaakUuid: string;
  documentTitle?: string | null;
};

@Component({
  selector: "zac-epistola-generation-dialog",
  templateUrl: "./epistola-generation-dialog.component.html",
  standalone: true,
  imports: [
    MatDialogModule,
    TranslateModule,
    EpistolaGenerationProgressComponent,
  ],
})
export class EpistolaGenerationDialogComponent {
  protected readonly data =
    inject<EpistolaGenerationDialogData>(MAT_DIALOG_DATA);
  private readonly informatieObjectenService = inject(
    InformatieObjectenService,
  );

  private readonly statusQuery = injectQuery(() =>
    this.informatieObjectenService.readEpistolaDocumentCreationStatusQuery(
      this.data.zaakUuid,
    ),
  );

  protected readonly status = computed(() =>
    this.statusQuery.isError() ? undefined : this.statusQuery.data()?.status,
  );
}
