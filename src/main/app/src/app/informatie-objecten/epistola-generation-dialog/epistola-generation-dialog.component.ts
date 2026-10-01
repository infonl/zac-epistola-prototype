/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, computed, inject, signal } from "@angular/core";
import { MAT_DIALOG_DATA } from "@angular/material/dialog";
import { injectQuery } from "@tanstack/angular-query-experimental";
import { GenericDialogComponent } from "../../shared/dialog/generic-dialog/generic-dialog.component";
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
  imports: [GenericDialogComponent, EpistolaGenerationProgressComponent],
})
export class EpistolaGenerationDialogComponent {
  protected readonly data =
    inject<EpistolaGenerationDialogData>(MAT_DIALOG_DATA);
  private readonly informatieObjectenService = inject(
    InformatieObjectenService,
  );

  protected readonly finished = signal(false);

  private readonly statusQuery = injectQuery(() => ({
    ...this.informatieObjectenService.readEpistolaDocumentCreationStatusQuery(
      this.data.zaakUuid,
    ),
    enabled: !this.finished(),
  }));

  protected readonly status = computed(() =>
    this.statusQuery.isError() ? undefined : this.statusQuery.data()?.status,
  );

  markFinished() {
    this.finished.set(true);
  }
}
