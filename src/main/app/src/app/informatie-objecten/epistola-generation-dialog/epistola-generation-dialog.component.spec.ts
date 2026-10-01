/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import {
  provideHttpClient,
  withInterceptorsFromDi,
} from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { MAT_DIALOG_DATA } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { TranslateModule } from "@ngx-translate/core";
import { provideQueryClient } from "@tanstack/angular-query-experimental";
import { render, screen } from "@testing-library/angular";
import { sleep, testQueryClient } from "../../../../setupJest";
import { GeneratedType } from "../../shared/utils/generated-types";
import {
  EpistolaGenerationDialogComponent,
  EpistolaGenerationDialogData,
} from "./epistola-generation-dialog.component";

const STATUS_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/status";

describe(EpistolaGenerationDialogComponent.name, () => {
  let httpTestingController: HttpTestingController;

  async function setup(
    data: EpistolaGenerationDialogData = {
      zaakUuid: "fakeZaakUuid",
      documentTitle: "fakeDocumentTitle",
    },
  ) {
    const rendered = await render(EpistolaGenerationDialogComponent, {
      imports: [TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideQueryClient(testQueryClient),
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    httpTestingController = TestBed.inject(HttpTestingController);
    return rendered;
  }

  async function answerStatus(
    fixture: Awaited<ReturnType<typeof setup>>["fixture"],
    status: GeneratedType<"EpistolaDocumentCreationStatus"> | null,
  ) {
    await sleep();
    httpTestingController.expectOne(STATUS_URL).flush({ status });
    await sleep(50);
    fixture.detectChanges();
  }

  afterEach(() => {
    httpTestingController
      .match(STATUS_URL)
      .forEach((request) => request.flush({ status: null }));
  });

  it("says which document gets a new version", async () => {
    await setup();

    expect(
      screen.getByRole("heading", {
        name: "actie.epistola.nieuwe-versie.genereren",
      }),
    ).toBeVisible();
    expect(screen.getByText("fakeDocumentTitle")).toBeVisible();
  });

  it("follows what Epistola reports on the job for the zaak", async () => {
    const { fixture } = await setup();

    await answerStatus(fixture, "RENDERING");

    expect(screen.getByRole("status")).toHaveTextContent(
      "msg.document.genereren.wordt-gemaakt",
    );
  });

  it("keeps the general message when Epistola's status cannot be read", async () => {
    const { fixture } = await setup();

    await sleep();
    httpTestingController
      .expectOne(STATUS_URL)
      .flush(null, { status: 500, statusText: "fakeStatusText" });
    await sleep(50);
    fixture.detectChanges();

    expect(screen.getByRole("status")).toHaveTextContent(
      "msg.document.genereren.bezig",
    );
  });
});
