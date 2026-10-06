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
  TestRequest,
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
import { render, screen, within } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { EMPTY } from "rxjs";
import { fromPartial } from "src/test-helpers";
import { sleep, testQueryClient } from "../../../../setupJest";
import { UtilService } from "../../core/service/util.service";
import { FoutAfhandelingService } from "../../fout-afhandeling/fout-afhandeling.service";
import { VertrouwelijkaanduidingToTranslationKeyPipe } from "../../shared/pipes/vertrouwelijkaanduiding-to-translation-key.pipe";
import { GeneratedType } from "../../shared/utils/generated-types";
import { EPISTOLA_GENERATION_FINISHED_DISPLAY_MS } from "../epistola-generation-progress/epistola-generation-progress.component";
import { InformatieObjectCreateAttendedComponent } from "./informatie-object-create-attended.component";

const CREATE_URL = "/rest/document-creation/create-document-attended";
const TEMPLATES_URL =
  "/rest/zaakafhandelparameters/fakeZaaktypeUuid/smartdocuments-templates-mapping";
const INFORMATIEOBJECTTYPES_URL =
  "/rest/informatieobjecten/informatieobjecttypes/fakeZaaktypeUuid";
const EPISTOLA_CREATE_URL = "/rest/document-creation/epistola/create-document";
const EPISTOLA_TEMPLATES_URL =
  "/rest/zaakafhandelparameters/fakeZaaktypeUuid/epistola-templates-mapping";
const EPISTOLA_STATUS_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/status";
const EPISTOLA_KANALEN_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/template/fake-epistola-template-1/kanalen";
const EPISTOLA_OTHER_KANALEN_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/template/fake-epistola-template-2/kanalen";

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
      epistola: { isEnabledGlobally: true, isEnabledForZaaktype: true },
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
      answerTemplateGroups: (request: TestRequest) => void = (request) =>
        request.flush([epistolaTemplateGroup]),
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
      answerTemplateGroups(
        httpTestingController.expectOne(EPISTOLA_TEMPLATES_URL),
      );
      await sleep();
      fixture.detectChanges();
    }

    afterEach(() => answerStatusPolls(null));

    function answerStatusPolls(
      status: GeneratedType<"EpistolaDocumentCreationStatus"> | null,
    ) {
      httpTestingController
        .match(EPISTOLA_STATUS_URL)
        .forEach((request) => request.flush({ status }));
    }

    function generateButton() {
      return screen.getByRole("button", { name: "actie.genereren" });
    }

    const withoutChoice: GeneratedType<"RestEpistolaKanalen"> = {
      kanalen: ["post"],
      voorgesteldKanaal: "post",
      communicatiekanaal: null,
    };

    async function chooseTemplateAndFillInTitle() {
      await choose("sjabloonGroep", "Brieven");
      await choose("sjabloon", "Standaardbrief");
      await user.type(field("titel"), "Ontvangstbevestiging aanvraag");
    }

    async function fillInValidEpistolaForm(
      kanalen: GeneratedType<"RestEpistolaKanalen"> = withoutChoice,
    ) {
      await chooseTemplateAndFillInTitle();
      await sleep();
      httpTestingController.expectOne(EPISTOLA_KANALEN_URL).flush(kanalen);
      await sleep();
      fixture.detectChanges();
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
        kanaal: null,
      });
      request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
      await sleep(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS + 50);

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

    it("shows that the document is ready for a moment, offering no second generation, before it reports it was added", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      await sleep();
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
      await sleep();
      fixture.detectChanges();

      expect(screen.getByRole("status")).toHaveTextContent(
        "msg.document.genereren.klaar",
      );
      expect(screen.getByRole("progressbar")).toHaveAttribute(
        "aria-valuenow",
        "100",
      );
      expect(generateButton()).toBeDisabled();
      expect(openSnackbar).not.toHaveBeenCalled();
      expect(documentCreated).not.toHaveBeenCalled();

      await sleep(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS);

      expect(openSnackbar).toHaveBeenCalled();
      expect(documentCreated).toHaveBeenCalled();
    });

    describe("given a template with a variant by post and a digital one", () => {
      const postAndDigitaal: GeneratedType<"RestEpistolaKanalen"> = {
        kanalen: ["post", "digitaal"],
        voorgesteldKanaal: "digitaal",
        communicatiekanaal: "E-mail",
      };

      function kanaalPicker() {
        return screen.getByRole("combobox", { name: "epistola.kanaal" });
      }

      async function chooseTheTemplate(
        kanalen: GeneratedType<"RestEpistolaKanalen"> = postAndDigitaal,
      ) {
        await fillInValidEpistolaForm(kanalen);
        // zac-select sets its options only after it has rendered
        await sleep();
        fixture.detectChanges();
      }

      it("offers the kanaal the zaak's communicatiekanaal suggests, and names that communicatiekanaal", async () => {
        await setupEpistola();
        await chooseTheTemplate();

        expect(kanaalPicker()).toHaveTextContent("epistola.kanaal.digitaal");
        expect(
          screen.getByText("epistola.kanaal.hint.communicatiekanaal"),
        ).toBeVisible();
      });

      it("explains the kanaal without naming a communicatiekanaal when the zaak has none", async () => {
        await setupEpistola();
        await chooseTheTemplate({
          kanalen: ["post", "digitaal"],
          voorgesteldKanaal: "post",
          communicatiekanaal: null,
        });

        expect(kanaalPicker()).toHaveTextContent("epistola.kanaal.post");
        expect(screen.getByText("epistola.kanaal.hint")).toBeVisible();
      });

      it("generates the document in the kanaal the behandelaar chose instead", async () => {
        await setupEpistola();
        await chooseTheTemplate();

        await choose("epistola.kanaal", "epistola.kanaal.post");
        await user.click(generateButton());
        await sleep();

        const request = httpTestingController.expectOne(EPISTOLA_CREATE_URL);
        expect(request.request.body).toMatchObject({ kanaal: "post" });
        request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
      });

      it("stops saying the kanaal was suggested once another one is chosen, and keeps the hint's line so nothing below it moves", async () => {
        await setupEpistola();
        await chooseTheTemplate();
        const hint = screen.getByText(
          "epistola.kanaal.hint.communicatiekanaal",
        );

        await choose("epistola.kanaal", "epistola.kanaal.post");
        fixture.detectChanges();

        expect(hint).toBeInTheDocument();
        expect(hint).not.toBeVisible();

        await choose("epistola.kanaal", "epistola.kanaal.digitaal");
        fixture.detectChanges();

        expect(hint).toBeVisible();
      });

      it("keeps the picker in place, without a kanaal, until another template is chosen and its kanalen arrive", async () => {
        await setupEpistola();
        await chooseTheTemplate();

        await user.clear(field("sjabloon"));
        fixture.detectChanges();

        expect(kanaalPicker()).toHaveAttribute("aria-disabled", "true");
        expect(kanaalPicker()).not.toHaveTextContent(
          "epistola.kanaal.digitaal",
        );

        await user.click(
          screen.getByRole("option", { name: "Ontvangstbevestiging" }),
        );
        await sleep();
        httpTestingController.expectOne(EPISTOLA_OTHER_KANALEN_URL).flush({
          kanalen: ["post", "digitaal"],
          voorgesteldKanaal: "post",
          communicatiekanaal: "Post",
        });
        await sleep();
        fixture.detectChanges();

        expect(kanaalPicker()).toHaveAttribute("aria-disabled", "false");
        expect(kanaalPicker()).toHaveTextContent("epistola.kanaal.post");
      });

      it("offers no generation until the kanalen of another template have arrived, so that the picker cannot be skipped", async () => {
        await setupEpistola();
        await chooseTheTemplate();

        await user.clear(field("sjabloon"));
        await user.click(
          screen.getByRole("option", { name: "Ontvangstbevestiging" }),
        );
        await sleep();
        fixture.detectChanges();

        expect(generateButton()).toBeDisabled();

        httpTestingController.expectOne(EPISTOLA_OTHER_KANALEN_URL).flush({
          kanalen: ["post", "digitaal"],
          voorgesteldKanaal: "post",
          communicatiekanaal: "Post",
        });
        await sleep();
        fixture.detectChanges();

        expect(generateButton()).toBeEnabled();
      });

      it("suggests the kanaal of the zaak's communicatiekanaal as it is now, when the same template is chosen again after the communicatiekanaal was edited", async () => {
        await setupEpistola();
        await chooseTheTemplate();
        expect(kanaalPicker()).toHaveTextContent("epistola.kanaal.digitaal");

        await user.clear(field("sjabloon"));
        await user.click(
          screen.getByRole("option", { name: "Ontvangstbevestiging" }),
        );
        await sleep();
        httpTestingController.expectOne(EPISTOLA_OTHER_KANALEN_URL).flush({
          kanalen: ["post", "digitaal"],
          voorgesteldKanaal: "digitaal",
          communicatiekanaal: "E-mail",
        });
        await sleep();

        await user.clear(field("sjabloon"));
        await user.click(
          screen.getByRole("option", { name: "Standaardbrief" }),
        );
        await sleep();
        httpTestingController.expectOne(EPISTOLA_KANALEN_URL).flush({
          kanalen: ["post", "digitaal"],
          voorgesteldKanaal: "post",
          communicatiekanaal: "Balie",
        });
        await sleep();
        fixture.detectChanges();
        await sleep();
        fixture.detectChanges();

        expect(kanaalPicker()).toHaveTextContent("epistola.kanaal.post");
        expect(
          screen.getByText("epistola.kanaal.hint.communicatiekanaal"),
        ).toBeVisible();
      });

      it("shows a kanaal it has no label for as Epistola names it", async () => {
        await setupEpistola();
        await chooseTheTemplate({
          kanalen: ["post", "fakeKanaal"],
          voorgesteldKanaal: "fakeKanaal",
          communicatiekanaal: null,
        });

        expect(kanaalPicker()).toHaveTextContent("fakeKanaal");
      });
    });

    it("offers no kanaal for a template whose variants are made for one kanaal at most", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm({
        kanalen: ["post"],
        voorgesteldKanaal: "post",
        communicatiekanaal: "Post",
      });

      expect(
        screen.queryByRole("combobox", { name: "epistola.kanaal" }),
      ).not.toBeInTheDocument();
      await user.click(generateButton());
      await sleep();
      const request = httpTestingController.expectOne(EPISTOLA_CREATE_URL);
      expect(request.request.body).toMatchObject({ kanaal: null });
      request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("offers no kanaal, and still generates, when the template's kanalen cannot be read", async () => {
      await setupEpistola();
      await chooseTemplateAndFillInTitle();
      await sleep();
      httpTestingController
        .expectOne(EPISTOLA_KANALEN_URL)
        .flush(null, { status: 500, statusText: "fakeStatusText" });
      await sleep();
      fixture.detectChanges();

      expect(
        screen.queryByRole("combobox", { name: "epistola.kanaal" }),
      ).not.toBeInTheDocument();
      await user.click(generateButton());
      await sleep();
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("offers no generation until the kanalen of the chosen template have arrived", async () => {
      await setupEpistola();
      await chooseTemplateAndFillInTitle();
      await sleep();
      fixture.detectChanges();

      expect(generateButton()).toBeDisabled();

      httpTestingController
        .expectOne(EPISTOLA_KANALEN_URL)
        .flush(withoutChoice);
      await sleep();
      fixture.detectChanges();

      expect(generateButton()).toBeEnabled();
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

    it("shows what Epistola reports on the job while the document is generated", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      fixture.detectChanges();
      await sleep();
      answerStatusPolls("HELD_UP_IN_QUEUE");
      await sleep(50);
      fixture.detectChanges();

      expect(screen.getByRole("status")).toHaveTextContent(
        "msg.document.genereren.lang-in-wachtrij",
      );
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("shows the generation as steps, with the one Epistola is at marked as current", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      fixture.detectChanges();
      await sleep();
      answerStatusPolls("RENDERING");
      await sleep(50);
      fixture.detectChanges();

      const steps = within(
        screen.getByRole("list", { name: "epistola.voortgang" }),
      ).getAllByRole("listitem");
      expect(
        steps.find((step) => step.getAttribute("aria-current") === "step"),
      ).toHaveTextContent("epistola.voortgang.maken");
      expect(screen.getByRole("progressbar")).toBeInTheDocument();
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("stops asking Epistola's status once the document is generated", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      fixture.detectChanges();
      await sleep();
      answerStatusPolls("RENDERING");
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
      await sleep();
      fixture.detectChanges();
      await sleep(1_100);

      httpTestingController.expectNone(EPISTOLA_STATUS_URL);
    });

    it("falls back to the general message and stops polling when Epistola's status cannot be read", async () => {
      await setupEpistola();
      await fillInValidEpistolaForm();

      await user.click(generateButton());
      fixture.detectChanges();
      await sleep();
      answerStatusPolls("RENDERING");
      await sleep(1_100);
      httpTestingController
        .expectOne(EPISTOLA_STATUS_URL)
        .flush(null, { status: 500, statusText: "fakeStatusText" });
      await sleep(50);
      fixture.detectChanges();

      expect(screen.getByRole("status")).toHaveTextContent(
        "msg.document.genereren.bezig",
      );
      await sleep(1_100);
      httpTestingController.expectNone(EPISTOLA_STATUS_URL);
      httpTestingController
        .expectOne(EPISTOLA_CREATE_URL)
        .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("says why there are no template groups when they cannot be loaded", async () => {
      await setupEpistola({}, async (request) => {
        const unavailable = { message: "msg.error.epistola.unavailable" };
        const serverError = { status: 500, statusText: "Server Error" };
        request.flush(unavailable, serverError);
        for (const retryDelay of [1_000, 2_000, 4_000]) {
          await sleep(retryDelay + 100);
          httpTestingController
            .expectOne(EPISTOLA_TEMPLATES_URL)
            .flush(unavailable, serverError);
        }
      });
      await sleep(7_500);
      fixture.detectChanges();

      expect(screen.getByRole("alert")).toHaveTextContent(
        "msg.document.templates.niet-geladen msg.error.epistola.unavailable",
      );
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
