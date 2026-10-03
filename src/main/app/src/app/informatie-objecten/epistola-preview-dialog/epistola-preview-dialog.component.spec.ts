/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { TranslateModule } from "@ngx-translate/core";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { fromPartial } from "src/test-helpers";
import {
  EpistolaPreviewDialogComponent,
  EpistolaPreviewDialogData,
} from "./epistola-preview-dialog.component";

describe(EpistolaPreviewDialogComponent.name, () => {
  const createObjectURL = jest.fn().mockReturnValue("blob:fakeObjectUrl");
  const revokeObjectURL = jest.fn();
  const originalCreateObjectURL = URL.createObjectURL;
  const originalRevokeObjectURL = URL.revokeObjectURL;
  const pdf = new Blob(["fakePdfContent"], { type: "application/pdf" });

  beforeAll(() => {
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
  });

  afterAll(() => {
    URL.createObjectURL = originalCreateObjectURL;
    URL.revokeObjectURL = originalRevokeObjectURL;
  });

  const user = userEvent.setup();

  beforeEach(() => jest.clearAllMocks());

  async function setup(
    data: EpistolaPreviewDialogData = { pdf, templateName: "fakeTemplateName" },
  ) {
    const close = jest.fn();
    const rendered = await render(EpistolaPreviewDialogComponent, {
      imports: [TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        {
          provide: MatDialogRef,
          useValue: fromPartial<MatDialogRef<EpistolaPreviewDialogComponent>>({
            close,
          }),
        },
      ],
    });
    return { ...rendered, close };
  }

  it("shows the document Epistola rendered, named after its template", async () => {
    await setup();

    const preview = screen.getByTitle("fakeTemplateName");
    expect(preview).toHaveAttribute("type", "application/pdf");
    expect(preview).toHaveAttribute("data", "blob:fakeObjectUrl");
    expect(createObjectURL).toHaveBeenCalledWith(pdf);
    expect(
      screen.getByText("fakeTemplateName", { selector: "p" }),
    ).toBeVisible();
  });

  it("says that it is a preview that is not yet in the zaak", async () => {
    await setup();

    expect(
      screen.getByRole("heading", { name: /epistola\.voorbeeld\.titel/ }),
    ).toBeVisible();
    expect(screen.getByText("epistola.voorbeeld.hint")).toBeVisible();
  });

  it("closes when asked to", async () => {
    const { close } = await setup();

    await user.click(screen.getByRole("button"));

    expect(close).toHaveBeenCalled();
  });

  it("lets go of the PDF in the browser's memory when it is closed", async () => {
    const { fixture } = await setup();
    expect(revokeObjectURL).not.toHaveBeenCalled();

    fixture.destroy();

    expect(revokeObjectURL).toHaveBeenCalledWith("blob:fakeObjectUrl");
  });
});
