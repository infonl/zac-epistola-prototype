/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, computed, input } from "@angular/core";
import { MatIconModule } from "@angular/material/icon";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { TranslateModule } from "@ngx-translate/core";
import { GeneratedType } from "../../shared/utils/generated-types";

type EpistolaDocumentCreationStatus =
  GeneratedType<"EpistolaDocumentCreationStatus">;

const STEPS = [
  "epistola.voortgang.voorbereiden",
  "epistola.voortgang.in-wachtrij",
  "epistola.voortgang.maken",
  "epistola.voortgang.opslaan",
] as const;

const STEP_OF_STATUS: Record<EpistolaDocumentCreationStatus, number> = {
  WAITING_IN_QUEUE: 1,
  HELD_UP_IN_QUEUE: 1,
  RENDERING: 2,
  HELD_UP_IN_RENDERING: 2,
  STORING: 3,
};

const MESSAGE_OF_STATUS: Record<EpistolaDocumentCreationStatus, string> = {
  WAITING_IN_QUEUE: "msg.document.genereren.in-wachtrij",
  HELD_UP_IN_QUEUE: "msg.document.genereren.lang-in-wachtrij",
  RENDERING: "msg.document.genereren.wordt-gemaakt",
  HELD_UP_IN_RENDERING: "msg.document.genereren.duurt-lang",
  STORING: "msg.document.genereren.opslaan",
};

@Component({
  selector: "zac-epistola-generation-progress",
  templateUrl: "./epistola-generation-progress.component.html",
  styleUrls: ["./epistola-generation-progress.component.less"],
  standalone: true,
  imports: [MatIconModule, MatProgressBarModule, TranslateModule],
})
export class EpistolaGenerationProgressComponent {
  /** Absent while ZAC is still preparing the request, and when Epistola's status cannot be read. */
  readonly status = input<EpistolaDocumentCreationStatus | null>();

  protected readonly steps = STEPS;

  protected readonly currentStep = computed(() => {
    const status = this.status();
    return status ? STEP_OF_STATUS[status] : 0;
  });

  protected readonly isHeldUp = computed(() => {
    const status = this.status();
    return status === "HELD_UP_IN_QUEUE" || status === "HELD_UP_IN_RENDERING";
  });

  protected readonly message = computed(() => {
    const status = this.status();
    return status ? MESSAGE_OF_STATUS[status] : "msg.document.genereren.bezig";
  });

  /** Halfway into the current step: ZAC knows which step a job is in, not how far along it is. */
  protected readonly percentage = computed(
    () => ((this.currentStep() + 0.5) / STEPS.length) * 100,
  );
}
