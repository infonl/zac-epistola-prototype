/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, computed, input, output } from "@angular/core";
import { MatDrawer } from "@angular/material/sidenav";
import { GeneratedType } from "../../shared/utils/generated-types";
import { EpistolaDocumentCreateComponent } from "../epistola-document-create/epistola-document-create.component";
import { InformatieObjectCreateAttendedComponent } from "../informatie-object-create-attended/informatie-object-create-attended.component";

/**
 * Keeps the Epistola form out of the SmartDocuments one, which is ZAC's own, so that the prototype stays apart
 * from the upstream code it is kept in sync with.
 */
@Component({
  selector: "zac-document-create",
  standalone: true,
  imports: [
    EpistolaDocumentCreateComponent,
    InformatieObjectCreateAttendedComponent,
  ],
  styles: ":host { display: contents; }",
  template: `
    @if (usesEpistola()) {
      <zac-epistola-document-create
        [zaak]="zaak()"
        [taak]="taak()"
        [sideNav]="sideNav()"
        (document)="document.emit()"
      />
    } @else {
      <zac-informatie-object-create-attended
        [zaak]="zaak()"
        [taak]="taak()"
        [sideNav]="sideNav()"
        [smartDocumentsGroupId]="smartDocumentsGroupId()"
        [smartDocumentsTemplateId]="smartDocumentsTemplateId()"
        (document)="document.emit()"
      />
    }
  `,
})
export class DocumentCreateComponent {
  readonly zaak = input.required<GeneratedType<"RestZaak">>();
  readonly taak = input<GeneratedType<"RestTask">>();
  readonly sideNav = input.required<MatDrawer>();
  readonly smartDocumentsGroupId = input<string>();
  readonly smartDocumentsTemplateId = input<string>();
  readonly document = output<void>();

  protected readonly usesEpistola = computed(
    () =>
      !!this.zaak().zaaktype.zaakafhandelparameters?.epistola
        ?.isEnabledGlobally,
  );
}
