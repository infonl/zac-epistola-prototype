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
import { MatDrawer } from "@angular/material/sidenav";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { provideRouter } from "@angular/router";
import { TranslateModule } from "@ngx-translate/core";
import {
  provideQueryClient,
  provideTanStackQuery,
} from "@tanstack/angular-query-experimental";
import { render, screen, waitFor, within } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { EMPTY } from "rxjs";
import { fromPartial } from "src/test-helpers";
import { sleep, testQueryClient } from "../../../../setupJest";
import { UtilService } from "../../core/service/util.service";
import { FoutAfhandelingService } from "../../fout-afhandeling/fout-afhandeling.service";
import { GeneratedType } from "../../shared/utils/generated-types";
import { EPISTOLA_GENERATION_FINISHED_DISPLAY_MS } from "../epistola-generation-progress/epistola-generation-progress.component";
import { EpistolaDocumentCreateComponent } from "./epistola-document-create.component";

const INFORMATIEOBJECTTYPES_URL =
  "/rest/informatieobjecten/informatieobjecttypes/fakeZaaktypeUuid";
const EPISTOLA_CREATE_URL = "/rest/document-creation/epistola/create-document";
const EPISTOLA_TEMPLATES_URL =
  "/rest/zaakafhandelparameters/fakeZaaktypeUuid/epistola-templates";
const EPISTOLA_STATUS_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/status";
const EPISTOLA_VARIANTEN_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/template/fake-epistola-template-1/varianten";
const EPISTOLA_PREVIEW_URL =
  "/rest/document-creation/epistola/preview-document";
const EPISTOLA_OTHER_VARIANTEN_URL =
  "/rest/document-creation/epistola/create-document/fakeZaakUuid/template/fake-epistola-template-2/varianten";

const zaak = fromPartial<GeneratedType<"RestZaak">>({
  uuid: "fakeZaakUuid",
  zaaktype: { uuid: "fakeZaaktypeUuid" },
});

const epistolaTemplates: GeneratedType<"RestOfferedEpistolaTemplate">[] = [
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
];

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

describe(EpistolaDocumentCreateComponent.name, () => {
  let fixture: ComponentFixture<EpistolaDocumentCreateComponent>;
  let httpTestingController: HttpTestingController;
  let sideNav: MatDrawer;
  let documentCreated: jest.Mock;
  let foutAfhandelen: jest.SpyInstance;
  let openSnackbar: jest.SpyInstance;

  const user = userEvent.setup({ delay: null });

  jest.setTimeout(20_000);

  async function setupEpistola(
    inputs: { taak?: GeneratedType<"RestTask"> } = {},
    answerTemplates: (request: TestRequest) => void = (request) =>
      request.flush(epistolaTemplates),
  ) {
    testQueryClient.setQueryData(["/rest/identity/loggedInUser"], loggedInUser);
    sideNav = fromPartial<MatDrawer>({
      close: jest.fn().mockResolvedValue(undefined),
    });
    documentCreated = jest.fn();

    const { fixture: renderedFixture } = await render(
      EpistolaDocumentCreateComponent,
      {
        inputs: { zaak, sideNav, ...inputs },
        imports: [NoopAnimationsModule, TranslateModule.forRoot()],
        providers: [
          provideRouter([]),
          provideHttpClient(withInterceptorsFromDi()),
          provideHttpClientTesting(),
          provideTanStackQuery(testQueryClient),
          provideQueryClient(testQueryClient),
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
    answerTemplates(httpTestingController.expectOne(EPISTOLA_TEMPLATES_URL));
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

  function field(label: string) {
    return screen.getByLabelText(label);
  }

  async function choose(label: string, option: string) {
    await user.click(field(label));
    await user.click(screen.getByRole("option", { name: option }));
  }

  function generateButton() {
    return screen.getByRole("button", { name: "actie.genereren" });
  }

  const withoutChoice: GeneratedType<"RestEpistolaVarianten"> = {
    varianten: ["post"],
    voorgesteldeVariant: "post",
    communicatiekanaal: null,
  };

  async function chooseTemplateAndFillInTitle() {
    await choose("template", "Standaardbrief");
    await user.type(field("titel"), "Ontvangstbevestiging aanvraag");
  }

  async function fillInValidEpistolaForm(
    varianten: GeneratedType<"RestEpistolaVarianten"> = withoutChoice,
  ) {
    await chooseTemplateAndFillInTitle();
    await sleep();
    httpTestingController.expectOne(EPISTOLA_VARIANTEN_URL).flush(varianten);
    await sleep();
    fixture.detectChanges();
  }

  it("announces what the drawer is for", async () => {
    await setupEpistola();

    expect(screen.getByText("actie.document.maken")).toBeVisible();
  });

  it("closes the drawer when the generation is cancelled", async () => {
    await setupEpistola();

    await user.click(screen.getByRole("button", { name: "actie.annuleren" }));

    expect(sideNav.close).toHaveBeenCalled();
  });

  it("asks for no template group, since Epistola offers the templates of the zaaktype's catalog directly", async () => {
    await setupEpistola();

    expect(screen.queryByLabelText("templategroep")).not.toBeInTheDocument();
    expect(field("template")).toBeEnabled();
    expect(field("template")).toHaveValue("");
  });

  it("offers every template of the zaaktype's catalog", async () => {
    await setupEpistola();

    await user.click(field("template"));

    expect(
      screen.getByRole("option", { name: "Standaardbrief" }),
    ).toBeVisible();
    expect(
      screen.getByRole("option", { name: "Ontvangstbevestiging" }),
    ).toBeVisible();
  });

  it("chooses the template without asking when the catalog holds only one", async () => {
    await setupEpistola({}, (request) => request.flush([epistolaTemplates[0]]));
    await sleep();
    httpTestingController.expectOne(EPISTOLA_VARIANTEN_URL).flush({
      varianten: [],
    });
    await sleep();
    fixture.detectChanges();

    expect(field("template")).toHaveValue("Standaardbrief");
    expect(field("template")).toBeDisabled();
  });

  it("fills in the informatieobjecttype and vertrouwelijkheid of the template", async () => {
    await setupEpistola();

    await choose("template", "Standaardbrief");

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

  it("keeps Genereren disabled until a title is filled in", async () => {
    await setupEpistola();
    await choose("template", "Standaardbrief");
    await sleep();
    httpTestingController
      .expectOne(EPISTOLA_VARIANTEN_URL)
      .flush(withoutChoice);
    await sleep();
    fixture.detectChanges();

    expect(generateButton()).toBeDisabled();

    await user.type(field("titel"), "Ontvangstbevestiging aanvraag");

    expect(generateButton()).toBeEnabled();
  });

  it("generates the document, reports that it was added to the zaak, and opens no wizard", async () => {
    const windowOpen = jest.spyOn(window, "open").mockReturnValue(null);
    const invalidateQueries = jest.spyOn(testQueryClient, "invalidateQueries");
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
      variant: null,
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
    const postAndDigitaal: GeneratedType<"RestEpistolaVarianten"> = {
      varianten: ["post", "digitaal"],
      voorgesteldeVariant: "digitaal",
      communicatiekanaal: "E-mail",
    };

    function variantPicker() {
      return screen.getByRole("combobox", { name: "epistola.variant" });
    }

    async function chooseTheTemplate(
      varianten: GeneratedType<"RestEpistolaVarianten"> = postAndDigitaal,
    ) {
      await fillInValidEpistolaForm(varianten);
      // zac-select sets its options only after it has rendered
      await sleep();
      fixture.detectChanges();
    }

    it("offers the variant the zaak's communicatiekanaal suggests, and names that communicatiekanaal", async () => {
      await setupEpistola();
      await chooseTheTemplate();

      expect(variantPicker()).toHaveTextContent("epistola.variant.digitaal");
      expect(
        screen.getByText("epistola.variant.hint.communicatiekanaal"),
      ).toBeVisible();
    });

    it("explains the variant without naming a communicatiekanaal when the zaak has none", async () => {
      await setupEpistola();
      await chooseTheTemplate({
        varianten: ["post", "digitaal"],
        voorgesteldeVariant: "post",
        communicatiekanaal: null,
      });

      expect(variantPicker()).toHaveTextContent("epistola.variant.post");
      expect(screen.getByText("epistola.variant.hint")).toBeVisible();
    });

    it("generates the document in the variant the behandelaar chose instead", async () => {
      await setupEpistola();
      await chooseTheTemplate();

      await choose("epistola.variant", "epistola.variant.post");
      await user.click(generateButton());
      await sleep();

      const request = httpTestingController.expectOne(EPISTOLA_CREATE_URL);
      expect(request.request.body).toMatchObject({ variant: "post" });
      request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
    });

    it("stops saying the variant was suggested once another one is chosen, and keeps the hint's line so nothing below it moves", async () => {
      await setupEpistola();
      await chooseTheTemplate();
      const hint = screen.getByText("epistola.variant.hint.communicatiekanaal");

      await choose("epistola.variant", "epistola.variant.post");
      fixture.detectChanges();

      expect(hint).toBeInTheDocument();
      expect(hint).not.toBeVisible();

      await choose("epistola.variant", "epistola.variant.digitaal");
      fixture.detectChanges();

      expect(hint).toBeVisible();
    });

    it("keeps the picker in place, without a variant, until another template is chosen and its variants arrive", async () => {
      await setupEpistola();
      await chooseTheTemplate();

      await user.clear(field("template"));
      fixture.detectChanges();

      expect(variantPicker()).toHaveAttribute("aria-disabled", "true");
      expect(variantPicker()).not.toHaveTextContent(
        "epistola.variant.digitaal",
      );

      await user.click(
        screen.getByRole("option", { name: "Ontvangstbevestiging" }),
      );
      await sleep();
      httpTestingController.expectOne(EPISTOLA_OTHER_VARIANTEN_URL).flush({
        varianten: ["post", "digitaal"],
        voorgesteldeVariant: "post",
        communicatiekanaal: "Post",
      });
      await sleep();
      fixture.detectChanges();

      expect(variantPicker()).toHaveAttribute("aria-disabled", "false");
      expect(variantPicker()).toHaveTextContent("epistola.variant.post");
    });

    it("offers no generation until the variants of another template have arrived, so that the picker cannot be skipped", async () => {
      await setupEpistola();
      await chooseTheTemplate();

      await user.clear(field("template"));
      await user.click(
        screen.getByRole("option", { name: "Ontvangstbevestiging" }),
      );
      await sleep();
      fixture.detectChanges();

      expect(generateButton()).toBeDisabled();

      httpTestingController.expectOne(EPISTOLA_OTHER_VARIANTEN_URL).flush({
        varianten: ["post", "digitaal"],
        voorgesteldeVariant: "post",
        communicatiekanaal: "Post",
      });
      await sleep();
      fixture.detectChanges();

      expect(generateButton()).toBeEnabled();
    });

    it("suggests the variant for the zaak's communicatiekanaal as it is now, when the same template is chosen again after the communicatiekanaal was edited", async () => {
      await setupEpistola();
      await chooseTheTemplate();
      expect(variantPicker()).toHaveTextContent("epistola.variant.digitaal");

      await user.clear(field("template"));
      await user.click(
        screen.getByRole("option", { name: "Ontvangstbevestiging" }),
      );
      await sleep();
      httpTestingController.expectOne(EPISTOLA_OTHER_VARIANTEN_URL).flush({
        varianten: ["post", "digitaal"],
        voorgesteldeVariant: "digitaal",
        communicatiekanaal: "E-mail",
      });
      await sleep();

      await user.clear(field("template"));
      await user.click(screen.getByRole("option", { name: "Standaardbrief" }));
      await sleep();
      httpTestingController.expectOne(EPISTOLA_VARIANTEN_URL).flush({
        varianten: ["post", "digitaal"],
        voorgesteldeVariant: "post",
        communicatiekanaal: "Balie",
      });
      await sleep();
      fixture.detectChanges();
      await sleep();
      fixture.detectChanges();

      expect(variantPicker()).toHaveTextContent("epistola.variant.post");
      expect(
        screen.getByText("epistola.variant.hint.communicatiekanaal"),
      ).toBeVisible();
    });

    it("shows a variant it has no label for by its kanaal, as Epistola names it", async () => {
      await setupEpistola();
      await chooseTheTemplate({
        varianten: ["post", "fakeKanaal"],
        voorgesteldeVariant: "fakeKanaal",
        communicatiekanaal: null,
      });

      expect(variantPicker()).toHaveTextContent("fakeKanaal");
    });
  });

  it("offers no choice of variant for a template whose variants are made for one kanaal at most", async () => {
    await setupEpistola();
    await fillInValidEpistolaForm({
      varianten: ["post"],
      voorgesteldeVariant: "post",
      communicatiekanaal: "Post",
    });

    expect(
      screen.queryByRole("combobox", { name: "epistola.variant" }),
    ).not.toBeInTheDocument();
    await user.click(generateButton());
    await sleep();
    const request = httpTestingController.expectOne(EPISTOLA_CREATE_URL);
    expect(request.request.body).toMatchObject({ variant: null });
    request.flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
  });

  it("offers no choice of variant, and still generates, when the template's variants cannot be read", async () => {
    await setupEpistola();
    await chooseTemplateAndFillInTitle();
    await sleep();
    httpTestingController
      .expectOne(EPISTOLA_VARIANTEN_URL)
      .flush(null, { status: 500, statusText: "fakeStatusText" });
    await sleep();
    fixture.detectChanges();

    expect(
      screen.queryByRole("combobox", { name: "epistola.variant" }),
    ).not.toBeInTheDocument();
    await user.click(generateButton());
    await sleep();
    httpTestingController
      .expectOne(EPISTOLA_CREATE_URL)
      .flush({ informatieobjectUuid: "fakeInformatieobjectUuid" });
  });

  it("offers no generation until the variants of the chosen template have arrived", async () => {
    await setupEpistola();
    await chooseTemplateAndFillInTitle();
    await sleep();
    fixture.detectChanges();

    expect(generateButton()).toBeDisabled();

    httpTestingController
      .expectOne(EPISTOLA_VARIANTEN_URL)
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

  it("says why there are no templates when they cannot be loaded", async () => {
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

  describe("previewing the document before it is generated", () => {
    const createObjectURL = jest.fn().mockReturnValue("blob:fakeObjectUrl");
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    const postAndDigitaal: GeneratedType<"RestEpistolaVarianten"> = {
      varianten: ["post", "digitaal"],
      voorgesteldeVariant: "digitaal",
      communicatiekanaal: "E-mail",
    };

    beforeAll(() => {
      URL.createObjectURL = createObjectURL;
      URL.revokeObjectURL = jest.fn();
    });

    afterAll(() => {
      URL.createObjectURL = originalCreateObjectURL;
      URL.revokeObjectURL = originalRevokeObjectURL;
    });

    function previewButton() {
      return screen.getByRole("button", { name: "actie.epistola.voorbeeld" });
    }

    async function chooseTheTemplate(
      varianten: GeneratedType<"RestEpistolaVarianten"> = postAndDigitaal,
    ) {
      await fillInValidEpistolaForm(varianten);
      // zac-select sets its options only after it has rendered
      await sleep();
      fixture.detectChanges();
    }

    it("is not possible until a template is chosen, and its variants are known", async () => {
      await setupEpistola();
      expect(previewButton()).toBeDisabled();

      await chooseTemplateAndFillInTitle();
      await sleep();
      expect(previewButton()).toBeDisabled();

      httpTestingController
        .expectOne(EPISTOLA_VARIANTEN_URL)
        .flush(postAndDigitaal);
      await sleep();
      fixture.detectChanges();
      await sleep();
      fixture.detectChanges();

      expect(previewButton()).toBeEnabled();
    });

    it("is possible without touching the form when the zaaktype's catalog holds only one template, once its variants are known", async () => {
      await setupEpistola({}, (request) =>
        request.flush([epistolaTemplates[0]]),
      );
      expect(field("template")).toHaveValue("Standaardbrief");
      expect(previewButton()).toBeDisabled();

      const variantenRequest = await waitFor(() =>
        httpTestingController.expectOne(EPISTOLA_VARIANTEN_URL),
      );
      variantenRequest.flush(postAndDigitaal);
      await waitFor(() => {
        fixture.detectChanges();
        expect(previewButton()).toBeEnabled();
      });

      await user.click(previewButton());

      const previewRequest = await waitFor(() =>
        httpTestingController.expectOne(EPISTOLA_PREVIEW_URL),
      );
      expect(previewRequest.request.body).toMatchObject({
        templateId: "fake-epistola-template-1",
        variant: "digitaal",
      });
      previewRequest.flush(new Blob(["fakePdfContent"]));
    });

    it("asks Epistola for the template in the chosen variant, and shows what it renders, without generating anything", async () => {
      await setupEpistola();
      await chooseTheTemplate();
      await choose("epistola.variant", "epistola.variant.post");

      await user.click(previewButton());
      await sleep();

      const request = httpTestingController.expectOne(EPISTOLA_PREVIEW_URL);
      expect(request.request.method).toBe("POST");
      expect(request.request.responseType).toBe("blob");
      expect(request.request.body).toEqual({
        zaakUuid: "fakeZaakUuid",
        taskId: undefined,
        templateId: "fake-epistola-template-1",
        variant: "post",
      });
      const pdf = new Blob(["fakePdfContent"], { type: "application/pdf" });
      request.flush(pdf);

      expect(
        await screen.findByRole("heading", {
          name: /epistola\.voorbeeld\.titel/,
        }),
      ).toBeVisible();
      expect(screen.getByTitle("Standaardbrief")).toHaveAttribute(
        "data",
        "blob:fakeObjectUrl",
      );
      expect(createObjectURL).toHaveBeenCalledWith(pdf);
      httpTestingController.expectNone(EPISTOLA_CREATE_URL);
      expect(documentCreated).not.toHaveBeenCalled();
    });

    it("previews a template without a choice of variant, with no variant", async () => {
      await setupEpistola();
      await chooseTheTemplate({
        varianten: ["post"],
        voorgesteldeVariant: "post",
        communicatiekanaal: "Post",
      });

      await user.click(previewButton());
      await sleep();

      const request = httpTestingController.expectOne(EPISTOLA_PREVIEW_URL);
      expect(request.request.body).toMatchObject({ variant: null });
      request.flush(new Blob(["fakePdfContent"]));
    });

    it("previews with the data of the task the document is created from", async () => {
      await setupEpistola({
        taak: fromPartial<GeneratedType<"RestTask">>({ id: "fakeTaskId" }),
      });
      await chooseTheTemplate();

      await user.click(previewButton());
      await sleep();

      const request = httpTestingController.expectOne(EPISTOLA_PREVIEW_URL);
      expect(request.request.body).toMatchObject({ taskId: "fakeTaskId" });
      request.flush(new Blob(["fakePdfContent"]));
    });

    it("offers no second preview while Epistola renders one", async () => {
      await setupEpistola();
      await chooseTheTemplate();

      await user.click(previewButton());
      await sleep();

      fixture.detectChanges();

      expect(previewButton()).toBeDisabled();
      httpTestingController
        .expectOne(EPISTOLA_PREVIEW_URL)
        .flush(new Blob(["fakePdfContent"]));
    });

    it("hands the reason Epistola gave to the error handler, and keeps the form as it was", async () => {
      await setupEpistola();
      await chooseTheTemplate();

      await user.click(previewButton());
      await sleep();
      httpTestingController
        .expectOne(EPISTOLA_PREVIEW_URL)
        .flush(
          new Blob([
            '{"message":"msg.error.epistola.template.data-rejected","exception":"/aanvrager: is required"}',
          ]),
          { status: 500, statusText: "fakeStatusText" },
        );

      await waitFor(() => {
        fixture.detectChanges();
        expect(foutAfhandelen).toHaveBeenCalledWith(
          expect.objectContaining({
            error: {
              message: "msg.error.epistola.template.data-rejected",
              exception: "/aanvrager: is required",
            },
          }),
        );
        expect(previewButton()).toBeEnabled();
      });
      expect(
        screen.queryByRole("heading", { name: /epistola\.voorbeeld\.titel/ }),
      ).not.toBeInTheDocument();
      expect(field("titel")).toHaveValue("Ontvangstbevestiging aanvraag");
    });
  });
});
