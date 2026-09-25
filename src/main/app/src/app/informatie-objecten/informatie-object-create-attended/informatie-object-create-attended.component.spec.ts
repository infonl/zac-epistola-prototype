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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideMomentDateAdapter } from "@angular/material-moment-adapter";
import { MatDrawer } from "@angular/material/sidenav";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { provideRouter } from "@angular/router";
import { TranslateModule } from "@ngx-translate/core";
import {
  provideQueryClient,
  provideTanStackQuery,
} from "@tanstack/angular-query-experimental";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { EMPTY } from "rxjs";
import { fromPartial } from "src/test-helpers";
import { sleep, testQueryClient } from "../../../../setupJest";
import { UtilService } from "../../core/service/util.service";
import { FoutAfhandelingService } from "../../fout-afhandeling/fout-afhandeling.service";
import { VertrouwelijkaanduidingToTranslationKeyPipe } from "../../shared/pipes/vertrouwelijkaanduiding-to-translation-key.pipe";
import { GeneratedType } from "../../shared/utils/generated-types";
import { InformatieObjectCreateAttendedComponent } from "./informatie-object-create-attended.component";

const CREATE_URL = "/rest/document-creation/create-document-attended";
const TEMPLATES_URL =
  "/rest/zaakafhandelparameters/fakeZaaktypeUuid/smartdocuments-templates-mapping";
const INFORMATIEOBJECTTYPES_URL =
  "/rest/informatieobjecten/informatieobjecttypes/fakeZaaktypeUuid";
const EPISTOLA_CREATE_URL = "/rest/document-creation/epistola/create-document";
const EPISTOLA_TEMPLATES_URL =
  "/rest/zaakafhandelparameters/fakeZaaktypeUuid/epistola-templates-mapping";

const zaak = fromPartial<GeneratedType<"RestZaak">>({
  uuid: "fakeZaakUuid",
  zaaktype: { uuid: "fakeZaaktypeUuid" },
});

const templateGroup = fromPartial<
  GeneratedType<"RestMappedSmartDocumentsTemplateGroup">
>({
  id: "fakeGroupId1",
  name: "Group One",
  templates: [
    {
      id: "fakeTemplateId1",
      name: "Template One",
      informatieObjectTypeUUID: "fakeInformatieobjectTypeUuid",
    },
    {
      id: "fakeTemplateId2",
      name: "Template Two",
      informatieObjectTypeUUID: undefined,
    },
  ],
  groups: null,
});

const singleTemplateGroup = fromPartial<
  GeneratedType<"RestMappedSmartDocumentsTemplateGroup">
>({
  id: "fakeGroupId2",
  name: "Group Two",
  templates: [{ id: "fakeTemplateId3", name: "Template Three" }],
  groups: null,
});

const epistolaZaak = fromPartial<GeneratedType<"RestZaak">>({
  uuid: "fakeZaakUuid",
  zaaktype: {
    uuid: "fakeZaaktypeUuid",
    zaakafhandelparameters: {
      epistola: { enabledGlobally: true, enabledForZaaktype: true },
    },
  },
});

const epistolaTemplateGroup = fromPartial<
  GeneratedType<"RestMappedEpistolaTemplateGroup">
>({
  name: "Brieven",
  templates: [
    {
      id: "fake-epistola-template-1",
      name: "Standaardbrief",
      informatieObjectTypeUUID: "fakeInformatieobjectTypeUuid",
    },
    {
      id: "fake-epistola-template-2",
      name: "Ontvangstbevestiging",
      informatieObjectTypeUUID: "fakeInformatieobjectTypeUuid",
    },
  ],
});

const loggedInUser = fromPartial<GeneratedType<"RestUser">>({
  id: "fakeUserId1",
  naam: "fakeUserName1",
});

const informatieobjecttype = fromPartial<
  GeneratedType<"RestInformatieobjecttype">
>({
  uuid: "fakeInformatieobjectTypeUuid",
  omschrijving: "Bijlage",
  vertrouwelijkheidaanduiding: "OPENBAAR",
});

describe(InformatieObjectCreateAttendedComponent.name, () => {
  let fixture: ComponentFixture<InformatieObjectCreateAttendedComponent>;
  let httpTestingController: HttpTestingController;
  let sideNav: MatDrawer;
  let documentCreated: jest.Mock;
  let foutAfhandelen: jest.SpyInstance;

  const user = userEvent.setup({ delay: null });

  jest.setTimeout(20_000);

  async function setup(
    inputs: {
      smartDocumentsGroupId?: string;
      smartDocumentsTemplateId?: string;
    } = {},
  ) {
    testQueryClient.setQueryData(["/rest/identity/loggedInUser"], loggedInUser);

    sideNav = fromPartial<MatDrawer>({
      close: jest.fn().mockResolvedValue(undefined),
    });
    documentCreated = jest.fn();

    const { fixture: renderedFixture } = await render(
      InformatieObjectCreateAttendedComponent,
      {
        inputs: { zaak, sideNav, ...inputs },
        imports: [NoopAnimationsModule, TranslateModule.forRoot()],
        providers: [
          provideRouter([]),
          provideHttpClient(withInterceptorsFromDi()),
          provideHttpClientTesting(),
          provideMomentDateAdapter(),
          provideTanStackQuery(testQueryClient),
          provideQueryClient(testQueryClient),
          VertrouwelijkaanduidingToTranslationKeyPipe,
        ],
      },
    );

    fixture = renderedFixture;
    fixture.componentInstance.document.subscribe(documentCreated);
    httpTestingController = TestBed.inject(HttpTestingController);
    foutAfhandelen = jest
      .spyOn(TestBed.inject(FoutAfhandelingService), "foutAfhandelen")
      .mockReturnValue(EMPTY);

    await sleep();
    httpTestingController
      .expectOne(INFORMATIEOBJECTTYPES_URL)
      .flush([informatieobjecttype]);
    httpTestingController
      .expectOne(TEMPLATES_URL)
      .flush([templateGroup, singleTemplateGroup]);
    await sleep();
    fixture.detectChanges();
  }

  function field(label: string) {
    return screen.getByLabelText(label);
  }

  function submitButton() {
    return screen.getByRole("button", { name: "actie.toevoegen" });
  }

  async function choose(label: string, option: string) {
    await user.click(field(label));
    await user.click(screen.getByRole("option", { name: option }));
  }

  async function fillInValidForm() {
    await choose("sjabloonGroep", "Group One");
    await choose("sjabloon", "Template One");
    await user.type(field("titel"), "Aanvraag formulier");
  }

  it("announces what the drawer is for", async () => {
    await setup();

    expect(screen.getByText("actie.document.maken")).toBeVisible();
  });

  it("offers the template groups configured for the zaaktype", async () => {
    await setup();

    await user.click(field("sjabloonGroep"));

    expect(screen.getByRole("option", { name: "Group One" })).toBeVisible();
    expect(screen.getByRole("option", { name: "Group Two" })).toBeVisible();
  });

  it("shows a loading indicator for the template groups while SmartDocuments is still answering", async () => {
    testQueryClient.setQueryData(["/rest/identity/loggedInUser"], loggedInUser);
    sideNav = fromPartial<MatDrawer>({
      close: jest.fn().mockResolvedValue(undefined),
    });

    const { fixture: renderedFixture } = await render(
      InformatieObjectCreateAttendedComponent,
      {
        inputs: { zaak, sideNav },
        imports: [NoopAnimationsModule, TranslateModule.forRoot()],
        providers: [
          provideRouter([]),
          provideHttpClient(withInterceptorsFromDi()),
          provideHttpClientTesting(),
          provideMomentDateAdapter(),
          provideTanStackQuery(testQueryClient),
          provideQueryClient(testQueryClient),
          VertrouwelijkaanduidingToTranslationKeyPipe,
        ],
      },
    );
    fixture = renderedFixture;
    httpTestingController = TestBed.inject(HttpTestingController);
    await sleep();
    httpTestingController
      .expectOne(INFORMATIEOBJECTTYPES_URL)
      .flush([informatieobjecttype]);

    // Deliberately not flushed yet: the SmartDocuments fetch for the template groups is still in flight.
    await user.click(field("sjabloonGroep"));

    expect(
      screen.queryByRole("option", { name: "Group One" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /laden/i })).toBeVisible();

    httpTestingController
      .expectOne(TEMPLATES_URL)
      .flush([templateGroup, singleTemplateGroup]);
    await sleep();
    fixture.detectChanges();

    expect(screen.getByRole("option", { name: "Group One" })).toBeVisible();
  });

  it("offers the templates of the chosen template group", async () => {
    await setup();

    await choose("sjabloonGroep", "Group One");
    await user.click(field("sjabloon"));

    expect(screen.getByRole("option", { name: "Template One" })).toBeVisible();
    expect(screen.getByRole("option", { name: "Template Two" })).toBeVisible();
  });

  it("chooses the only template of a group without asking", async () => {
    await setup();

    await choose("sjabloonGroep", "Group Two");

    expect(field("sjabloon")).toHaveValue("Template Three");
    expect(field("sjabloon")).toBeDisabled();
  });

  it("locks the template group it was opened for", async () => {
    await setup({ smartDocumentsGroupId: "fakeGroupId1" });

    expect(field("sjabloonGroep")).toHaveValue("Group One");
    expect(field("sjabloonGroep")).toBeDisabled();
    expect(field("sjabloon")).toHaveValue("");
  });

  it("locks the template it was opened for", async () => {
    await setup({
      smartDocumentsGroupId: "fakeGroupId1",
      smartDocumentsTemplateId: "fakeTemplateId1",
    });

    expect(field("sjabloonGroep")).toHaveValue("Group One");
    expect(field("sjabloon")).toHaveValue("Template One");
    expect(field("sjabloon")).toBeDisabled();
  });

  it("fills in the informatieobjecttype and vertrouwelijkheid of the template", async () => {
    await setup();

    await choose("sjabloonGroep", "Group One");
    await choose("sjabloon", "Template One");

    expect(field("informatieobjectType")).toHaveValue("Bijlage");
    expect(field("vertrouwelijkheidaanduiding")).toHaveValue(
      "vertrouwelijkheidaanduiding.OPENBAAR",
    );
  });

  it("fills in the logged in user as the author", async () => {
    await setup();

    expect(field("auteur")).toHaveValue("fakeUserName1");
  });

  it("keeps the submit disabled until the form is filled in", async () => {
    await setup();

    expect(submitButton()).toBeDisabled();

    await fillInValidForm();

    expect(submitButton()).toBeEnabled();
  });

  it("creates the document and opens it for editing", async () => {
    const windowOpen = jest.spyOn(window, "open").mockReturnValue(null);
    await setup();
    await fillInValidForm();

    await user.click(submitButton());
    await sleep();

    const request = httpTestingController.expectOne(CREATE_URL);
    expect(request.request.method).toBe("POST");
    expect(request.request.body).toMatchObject({
      author: "fakeUserName1",
      smartDocumentsTemplateGroupId: "fakeGroupId1",
      smartDocumentsTemplateId: "fakeTemplateId1",
      title: "Aanvraag formulier",
      zaakUuid: "fakeZaakUuid",
    });
    request.flush({ redirectURL: "https://example.com/doc", message: null });
    await sleep();

    expect(documentCreated).toHaveBeenCalled();
    expect(windowOpen).toHaveBeenCalledWith("https://example.com/doc");
  });

  it("reports the message when there is no document to open", async () => {
    await setup();
    await fillInValidForm();

    await user.click(submitButton());
    await sleep();
    httpTestingController.expectOne(CREATE_URL).flush({
      redirectURL: null,
      message: "Document created without redirect",
    });
    await sleep();
    fixture.detectChanges();

    expect(screen.getByText("Document created without redirect")).toBeVisible();
  });

  it("routes a failed creation through the error handler", async () => {
    await setup();
    await fillInValidForm();

    await user.click(submitButton());
    await sleep();
    httpTestingController
      .expectOne(CREATE_URL)
      .flush("boom", { status: 500, statusText: "Server Error" });
    await sleep();

    expect(foutAfhandelen).toHaveBeenCalled();
    expect(documentCreated).not.toHaveBeenCalled();
  });

  it("does not offer to create a second document while one is being created", async () => {
    await setup();
    await fillInValidForm();

    await user.click(submitButton());
    await sleep();
    fixture.detectChanges();

    expect(submitButton()).toBeDisabled();
    httpTestingController
      .expectOne(CREATE_URL)
      .flush({ redirectURL: null, message: "done" });
  });

  it("closes the drawer when the creation is cancelled", async () => {
    await setup();

    await user.click(screen.getByRole("button", { name: "actie.annuleren" }));

    expect(sideNav.close).toHaveBeenCalled();
  });

  describe("when Epistola is the active provider", () => {
    let openSnackbar: jest.SpyInstance;

    async function setupEpistola(
      inputs: { taak?: GeneratedType<"RestTask"> } = {},
    ) {
      testQueryClient.setQueryData(
        ["/rest/identity/loggedInUser"],
        loggedInUser,
      );
      sideNav = fromPartial<MatDrawer>({
        close: jest.fn().mockResolvedValue(undefined),
      });
      documentCreated = jest.fn();

      const { fixture: renderedFixture } = await render(
        InformatieObjectCreateAttendedComponent,
        {
          inputs: { zaak: epistolaZaak, sideNav, ...inputs },
          imports: [NoopAnimationsModule, TranslateModule.forRoot()],
          providers: [
            provideRouter([]),
            provideHttpClient(withInterceptorsFromDi()),
            provideHttpClientTesting(),
            provideMomentDateAdapter(),
            provideTanStackQuery(testQueryClient),
            provideQueryClient(testQueryClient),
            VertrouwelijkaanduidingToTranslationKeyPipe,
          ],
        },
      );

      fixture = renderedFixture;
      fixture.componentInstance.document.subscribe(documentCreated);
      httpTestingController = TestBed.inject(HttpTestingController);
      foutAfhandelen = jest
        .spyOn(TestBed.inject(FoutAfhandelingService), "foutAfhandelen")
        .mockReturnValue(EMPTY);
      openSnackbar = jest
        .spyOn(TestBed.inject(UtilService), "openSnackbar")
        .mockImplementation(() => undefined);

      await sleep();
      httpTestingController
        .expectOne(INFORMATIEOBJECTTYPES_URL)
        .flush([informatieobjecttype]);
      httpTestingController
        .expectOne(EPISTOLA_TEMPLATES_URL)
        .flush([epistolaTemplateGroup]);
      await sleep();
      fixture.detectChanges();
    }

    function generateButton() {
      return screen.getByRole("button", { name: "actie.genereren" });
    }

    async function fillInValidEpistolaForm() {
      await choose("sjabloonGroep", "Brieven");
      await choose("sjabloon", "Standaardbrief");
      await user.type(field("titel"), "Ontvangstbevestiging aanvraag");
    }

    it("offers the template groups the beheerder arranged for Epistola", async () => {
      await setupEpistola();

      await choose("sjabloonGroep", "Brieven");
      await user.click(field("sjabloon"));

      expect(
        screen.getByRole("option", { name: "Standaardbrief" }),
      ).toBeVisible();
      expect(
        screen.getByRole("option", { name: "Ontvangstbevestiging" }),
      ).toBeVisible();
    });

    it("fills in the informatieobjecttype and vertrouwelijkheid of the template", async () => {
      await setupEpistola();

      await choose("sjabloonGroep", "Brieven");
      await choose("sjabloon", "Standaardbrief");

      expect(field("informatieobjectType")).toHaveValue("Bijlage");
      expect(field("vertrouwelijkheidaanduiding")).toHaveValue(
        "vertrouwelijkheidaanduiding.OPENBAAR",
      );
    });

    it("states that the document will be a PDF and asks for no creation date, because Epistola dates it today", async () => {
      await setupEpistola();

      expect(field("formaat")).toHaveValue("PDF");
      expect(field("formaat")).toBeDisabled();
      expect(screen.queryByLabelText("creatiedatum")).not.toBeInTheDocument();
    });

    it("names the logged-in user as the author, who cannot be changed", async () => {
      await setupEpistola();

      expect(field("auteur")).toHaveValue("fakeUserName1");
      expect(field("auteur")).toBeDisabled();
    });

    it("generates the document, reports that it was added to the zaak, and opens no wizard", async () => {
      const windowOpen = jest.spyOn(window, "open").mockReturnValue(null);
      const invalidateQueries = jest.spyOn(
        testQueryClient,
        "invalidateQueries",
      );
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      await sleep();

      const request = httpTestingController.expectOne(EPISTOLA_CREATE_URL);
      expect(request.request.method).toBe("POST");
      expect(request.request.body).toEqual({
        zaakUuid: "fakeZaakUuid",
        taskId: undefined,
        templateId: "fake-epistola-template-1",
        title: "Ontvangstbevestiging aanvraag",
        description: null,
      });
      request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
      await sleep();

      expect(openSnackbar).toHaveBeenCalledWith(
        "msg.document.toegevoegd.aan.zaak",
        { document: "Ontvangstbevestiging aanvraag" },
      );
      expect(invalidateQueries).toHaveBeenCalledWith({
        queryKey: [
          "/rest/informatieobjecten/informatieobjectenList",
          "fakeZaakUuid",
        ],
      });
      expect(documentCreated).toHaveBeenCalled();
      expect(windowOpen).not.toHaveBeenCalled();
    });

    it("links the document to the task it was created from", async () => {
      await setupEpistola({
        taak: fromPartial<GeneratedType<"RestTask">>({ id: "fakeTaskId" }),
      });
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      await sleep();

      const request = httpTestingController.expectOne(EPISTOLA_CREATE_URL);
      expect(request.request.body).toMatchObject({ taskId: "fakeTaskId" });
      request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("says it is generating, and offers no second generation while it does", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      await sleep();
      fixture.detectChanges();

      expect(screen.getByRole("status")).toHaveTextContent(
        "msg.document.genereren.bezig",
      );
      expect(generateButton()).toBeDisabled();
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("routes a failed generation through the error handler and keeps the drawer open", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      await sleep();
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush("boom", { status: 500, statusText: "Server Error" });
      await sleep();

      expect(foutAfhandelen).toHaveBeenCalled();
      expect(documentCreated).not.toHaveBeenCalled();
      expect(openSnackbar).not.toHaveBeenCalled();
    });
  });
});
