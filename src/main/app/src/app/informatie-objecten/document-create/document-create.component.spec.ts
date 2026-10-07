/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, input, output } from "@angular/core";
import { MatDrawer } from "@angular/material/sidenav";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { fromPartial } from "src/test-helpers";
import { GeneratedType } from "../../shared/utils/generated-types";
import { DocumentCreateComponent } from "./document-create.component";

@Component({
  selector: "zac-epistola-document-create",
  standalone: true,
  template: `<button type="button" (click)="document.emit()">
    fakeEpistolaForm {{ taak()?.id }}
  </button>`,
})
class EpistolaDocumentCreateStubComponent {
  readonly zaak = input<GeneratedType<"RestZaak">>();
  readonly taak = input<GeneratedType<"RestTask">>();
  readonly sideNav = input<MatDrawer>();
  readonly document = output<void>();
}

@Component({
  selector: "zac-informatie-object-create-attended",
  standalone: true,
  template: `<button type="button" (click)="document.emit()">
    fakeSmartDocumentsForm {{ smartDocumentsGroupId() }}
    {{ smartDocumentsTemplateId() }}
  </button>`,
})
class InformatieObjectCreateAttendedStubComponent {
  readonly zaak = input<GeneratedType<"RestZaak">>();
  readonly taak = input<GeneratedType<"RestTask">>();
  readonly sideNav = input<MatDrawer>();
  readonly smartDocumentsGroupId = input<string>();
  readonly smartDocumentsTemplateId = input<string>();
  readonly document = output<void>();
}

function zaakWithEpistola(isEnabledGlobally: boolean) {
  return fromPartial<GeneratedType<"RestZaak">>({
    uuid: "fakeZaakUuid",
    zaaktype: {
      uuid: "fakeZaaktypeUuid",
      zaakafhandelparameters: { epistola: { isEnabledGlobally } },
    },
  });
}

describe(DocumentCreateComponent.name, () => {
  const user = userEvent.setup({ delay: null });

  async function setup(inputs: {
    zaak: GeneratedType<"RestZaak">;
    taak?: GeneratedType<"RestTask">;
    smartDocumentsGroupId?: string;
    smartDocumentsTemplateId?: string;
  }) {
    const documentCreated = jest.fn();
    const { fixture } = await render(DocumentCreateComponent, {
      inputs: { sideNav: fromPartial<MatDrawer>({}), ...inputs },
      componentImports: [
        EpistolaDocumentCreateStubComponent,
        InformatieObjectCreateAttendedStubComponent,
      ],
    });
    fixture.componentInstance.document.subscribe(documentCreated);
    return documentCreated;
  }

  it("offers the Epistola form, for the task it was opened from, when Epistola is on", async () => {
    await setup({
      zaak: zaakWithEpistola(true),
      taak: fromPartial<GeneratedType<"RestTask">>({ id: "fakeTaskId" }),
    });

    expect(
      screen.getByRole("button", { name: "fakeEpistolaForm fakeTaskId" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /fakeSmartDocumentsForm/ }),
    ).not.toBeInTheDocument();
  });

  it("offers the SmartDocuments form, with the template it was opened for, when Epistola is off", async () => {
    await setup({
      zaak: zaakWithEpistola(false),
      smartDocumentsGroupId: "fakeGroupId",
      smartDocumentsTemplateId: "fakeTemplateId",
    });

    expect(
      screen.getByRole("button", {
        name: "fakeSmartDocumentsForm fakeGroupId fakeTemplateId",
      }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /fakeEpistolaForm/ }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ["Epistola", true, /fakeEpistolaForm/],
    ["SmartDocuments", false, /fakeSmartDocumentsForm/],
  ])(
    "passes on that the %s form created a document",
    async (_provider, isEnabledGlobally, form) => {
      const documentCreated = await setup({
        zaak: zaakWithEpistola(isEnabledGlobally),
      });

      await user.click(screen.getByRole("button", { name: form }));

      expect(documentCreated).toHaveBeenCalled();
    },
  );
});
