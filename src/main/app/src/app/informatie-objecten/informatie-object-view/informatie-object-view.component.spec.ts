/*
 * SPDX-FileCopyrightText: 2025 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 *
 */

import { HarnessLoader } from "@angular/cdk/testing";
import { TestbedHarnessEnvironment } from "@angular/cdk/testing/testbed";
import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { Component, input } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatNavListItemHarness } from "@angular/material/list/testing";
import { MatDrawer } from "@angular/material/sidenav";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ActivatedRoute, provideRouter, Router } from "@angular/router";
import { TranslateModule } from "@ngx-translate/core";
import { provideQueryClient } from "@tanstack/angular-query-experimental";
import { config, of, ReplaySubject, Subject, throwError } from "rxjs";
import { fromPartial } from "src/test-helpers";
import { sleep, testQueryClient } from "../../../../setupJest";
import { UtilService } from "../../core/service/util.service";
import { FoutAfhandelingService } from "../../fout-afhandeling/fout-afhandeling.service";
import { RedenDialogData } from "../../shared/dialog/reden-dialog-form/reden-dialog-form.component";
import { GeneratedType } from "../../shared/utils/generated-types";
import { ZakenService } from "../../zaken/zaken.service";
import { EpistolaDocumentenService } from "../epistola-documenten.service";
import { EpistolaGenerationDialogComponent } from "../epistola-generation-dialog/epistola-generation-dialog.component";
import { EPISTOLA_GENERATION_FINISHED_DISPLAY_MS } from "../epistola-generation-progress/epistola-generation-progress.component";
import { InformatieObjectEditComponent } from "../informatie-object-edit/informatie-object-edit.component";
import { InformatieObjectenService } from "../informatie-objecten.service";
import { FileFormat } from "../model/file-format";
import { InformatieObjectViewComponent } from "./informatie-object-view.component";

@Component({
  selector: "zac-informatie-object-edit",
  template: "",
  standalone: true,
})
class InformatieObjectEditStubComponent {
  readonly infoObject =
    input<GeneratedType<"RestEnkelvoudigInformatieObjectVersieGegevens">>();
  readonly sideNav = input.required<MatDrawer>();
  readonly zaakUuid = input.required<string>();
}

describe(InformatieObjectViewComponent.name, () => {
  let component: InformatieObjectViewComponent;
  let fixture: ComponentFixture<typeof component>;
  let loader: HarnessLoader;

  let informatieObjectenService: InformatieObjectenService;
  let epistolaDocumentenService: EpistolaDocumentenService;
  let zakenService: ZakenService;

  const mockActivatedRoute = {
    data: new ReplaySubject<{
      informatieObject: GeneratedType<"RestEnkelvoudigInformatieobject">;
    }>(1),
  };

  const zaak = fromPartial<GeneratedType<"RestZaak">>({
    uuid: "zaak-001",
    identificatie: "test",
    indicaties: [],
    omschrijving: "test omschrijving",
    vertrouwelijkheidaanduiding: "OPENBAAR",
    rechten: fromPartial<GeneratedType<"RestZaakRechten">>({}),
    zaaktype: fromPartial<GeneratedType<"RestZaaktype">>({
      uuid: "zaaktype-001",
    }),
  });

  const zaakInformatieobject = fromPartial<
    GeneratedType<"RestZaakInformatieobject">
  >({
    zaakIdentificatie: zaak.identificatie,
  });

  const enkelvoudigInformatieobject = fromPartial<
    GeneratedType<"RestEnkelvoudigInformatieobject">
  >({
    uuid: "enkelvoudig-informatieobject-001",
    informatieobjectTypeUUID: "test-uuid",
    indicaties: [],
    titel: "test informatieobject",
    vertrouwelijkheidaanduiding: "OPENBAAR",
    rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({}),
    formaat: FileFormat.DOCX,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        InformatieObjectViewComponent,
        TranslateModule.forRoot(),
        NoopAnimationsModule,
      ],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideQueryClient(testQueryClient),
        {
          provide: ActivatedRoute,
          useValue: mockActivatedRoute,
        },
      ],
    })
      .overrideComponent(InformatieObjectViewComponent, {
        remove: { imports: [InformatieObjectEditComponent] },
        add: { imports: [InformatieObjectEditStubComponent] },
      })
      .compileComponents();

    informatieObjectenService = TestBed.inject(InformatieObjectenService);
    epistolaDocumentenService = TestBed.inject(EpistolaDocumentenService);
    jest
      .spyOn(epistolaDocumentenService, "readEpistolaDocument")
      .mockReturnValue(
        of(
          fromPartial<GeneratedType<"RestEpistolaDocument">>({
            isNewVersionAvailable: false,
          }),
        ),
      );
    jest
      .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
      .mockReturnValue(of(enkelvoudigInformatieobject));

    jest
      .spyOn(
        informatieObjectenService,
        "readHuidigeVersieEnkelvoudigInformatieObject",
      )
      .mockReturnValue(
        of({
          uuid: "enkelvoudig-informatieobject-001",
          informatieobjectTypeUUID: "test-uuid",
          titel: "test informatieobject",
          vertrouwelijkheidaanduiding: "OPENBAAR",
          rechten: {},
        }),
      );

    jest
      .spyOn(informatieObjectenService, "listZaakInformatieobjecten")
      .mockReturnValue(of([zaakInformatieobject]));

    jest
      .spyOn(informatieObjectenService, "listHistorie")
      .mockReturnValue(of([]));

    zakenService = TestBed.inject(ZakenService);
    jest.spyOn(zakenService, "readZaakByID").mockReturnValue(of(zaak));

    fixture = TestBed.createComponent(InformatieObjectViewComponent);
    component = fixture.componentInstance;
    loader = TestbedHarnessEnvironment.loader(fixture);

    mockActivatedRoute.data.next({
      informatieObject: enkelvoudigInformatieobject,
    });

    fixture.detectChanges();
  });

  describe("actie.nieuwe.versie.toevoegen", () => {
    it("should not have a button when the user does not have the right to add a new version", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canToevoegenNieuweVersie: false,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({ title: "actie.nieuwe.versie.toevoegen" }),
      );

      expect(button).toBeNull();
    });

    it("should open the sidebar when clicked", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canToevoegenNieuweVersie: true,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarness(
        MatNavListItemHarness.with({ title: "actie.nieuwe.versie.toevoegen" }),
      );
      await button.click();

      const sidebar = component.actionsSidenav;
      expect(sidebar.opened).toBe(true);
    });
  });

  describe("actie.epistola.nieuwe-versie.genereren", () => {
    const epistolaZaak = fromPartial<GeneratedType<"RestZaak">>({
      ...zaak,
      rechten: fromPartial<GeneratedType<"RestZaakRechten">>({
        canCreerenDocument: true,
      }),
    });

    function givenADocument({
      canAddVersion = true,
      canCreateDocument = true,
      isNewVersionAvailable = true,
    } = {}) {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canToevoegenNieuweVersie: canAddVersion,
            }),
          }),
        );
      jest.spyOn(zakenService, "readZaakByID").mockReturnValue(
        of(
          canCreateDocument
            ? epistolaZaak
            : fromPartial<GeneratedType<"RestZaak">>({
                ...zaak,
                rechten: fromPartial<GeneratedType<"RestZaakRechten">>({
                  canCreerenDocument: false,
                }),
              }),
        ),
      );
      const readEpistolaDocument = jest
        .spyOn(epistolaDocumentenService, "readEpistolaDocument")
        .mockReturnValue(
          of(
            fromPartial<GeneratedType<"RestEpistolaDocument">>({
              isNewVersionAvailable,
            }),
          ),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });
      return readEpistolaDocument;
    }

    it("should have a button for a document Epistola generated when the user may add a version and create documents", async () => {
      givenADocument();

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({
          title: "actie.epistola.nieuwe-versie.genereren",
        }),
      );

      expect(button).toBeTruthy();
    });

    it("should not have a button for a document that Epistola did not generate", async () => {
      givenADocument({ isNewVersionAvailable: false });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({
          title: "actie.epistola.nieuwe-versie.genereren",
        }),
      );

      expect(button).toBeNull();
    });

    it("should not have a button when the user may not create documents for the zaak", async () => {
      givenADocument({ canCreateDocument: false });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({
          title: "actie.epistola.nieuwe-versie.genereren",
        }),
      );

      expect(button).toBeNull();
    });

    it("should not have a button, and not ask whether Epistola generated the document, when the user may not add a version", async () => {
      const readEpistolaDocument = givenADocument({ canAddVersion: false });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({
          title: "actie.epistola.nieuwe-versie.genereren",
        }),
      );

      expect(button).toBeNull();
      expect(readEpistolaDocument).not.toHaveBeenCalled();
    });

    it("should generate the version and show it when clicked", async () => {
      givenADocument();
      const createEpistolaDocumentVersion = jest
        .spyOn(epistolaDocumentenService, "createEpistolaDocumentVersion")
        .mockReturnValue(of(undefined));
      const navigate = jest
        .spyOn(TestBed.inject(Router), "navigate")
        .mockResolvedValue(true);
      const openSnackbar = jest.spyOn(
        TestBed.inject(UtilService),
        "openSnackbar",
      );
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(of({ ...enkelvoudigInformatieobject, versie: 2 }));

      const button = await loader.getHarness(
        MatNavListItemHarness.with({
          title: "actie.epistola.nieuwe-versie.genereren",
        }),
      );
      await button.click();
      await sleep(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS);

      expect(createEpistolaDocumentVersion).toHaveBeenCalledWith(
        enkelvoudigInformatieobject.uuid,
      );
      expect(openSnackbar).toHaveBeenCalledWith(
        "msg.document.epistola.nieuwe-versie.gegenereerd",
        { document: enkelvoudigInformatieobject.titel },
      );
      expect(navigate).toHaveBeenCalledWith([
        "/informatie-objecten",
        enkelvoudigInformatieobject.uuid,
        2,
      ]);
    });

    describe("while the version is generated", () => {
      afterEach(() => {
        config.onUnhandledError = null;
      });

      function givenAProgressDialog() {
        const close = jest.fn();
        const markFinished = jest.fn();
        const open = jest.spyOn(component["dialog"], "open").mockReturnValue(
          fromPartial<MatDialogRef<unknown>>({
            close,
            componentInstance: { markFinished },
          }),
        );
        return { open, close, markFinished };
      }

      async function clickGenerateNewVersion() {
        const button = await loader.getHarness(
          MatNavListItemHarness.with({
            title: "actie.epistola.nieuwe-versie.genereren",
          }),
        );
        await button.click();
      }

      it("shows the generation's progress for the document's zaak until the version is there", async () => {
        givenADocument();
        const { open, close } = givenAProgressDialog();
        const versionCreated = new Subject<void>();
        jest
          .spyOn(epistolaDocumentenService, "createEpistolaDocumentVersion")
          .mockReturnValue(versionCreated);
        jest.spyOn(TestBed.inject(Router), "navigate").mockResolvedValue(true);

        await clickGenerateNewVersion();

        expect(open).toHaveBeenCalledWith(
          EpistolaGenerationDialogComponent,
          expect.objectContaining({
            data: {
              zaakUuid: zaak.uuid,
              documentTitle: enkelvoudigInformatieobject.titel,
            },
            disableClose: true,
          }),
        );
        expect(close).not.toHaveBeenCalled();

        versionCreated.next();
        versionCreated.complete();
        await sleep(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS);

        expect(close).toHaveBeenCalled();
      });

      it("shows that the version is there for a moment before it closes the progress", async () => {
        givenADocument();
        const { close, markFinished } = givenAProgressDialog();
        const versionCreated = new Subject<void>();
        jest
          .spyOn(epistolaDocumentenService, "createEpistolaDocumentVersion")
          .mockReturnValue(versionCreated);
        jest.spyOn(TestBed.inject(Router), "navigate").mockResolvedValue(true);

        await clickGenerateNewVersion();
        versionCreated.next();
        versionCreated.complete();

        expect(markFinished).toHaveBeenCalled();
        expect(close).not.toHaveBeenCalled();

        await sleep(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS);

        expect(close).toHaveBeenCalled();
      });

      it("closes the progress when generating the version fails", async () => {
        givenADocument();
        const { close, markFinished } = givenAProgressDialog();
        const error = new Error("fakeError");
        jest
          .spyOn(epistolaDocumentenService, "createEpistolaDocumentVersion")
          .mockReturnValue(throwError(() => error));
        const onUnhandledError = jest.fn();
        config.onUnhandledError = onUnhandledError;

        await clickGenerateNewVersion();
        await sleep();

        expect(markFinished).not.toHaveBeenCalled();
        expect(close).toHaveBeenCalled();
        expect(onUnhandledError).toHaveBeenCalledWith(error);
      });
    });
  });

  describe("actie.converteren", () => {
    it("should have a button when the document is of format DOCX and the user has the right to convert a document", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canConverteren: true,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarness(
        MatNavListItemHarness.with({ title: "actie.converteren" }),
      );

      expect(button).toBeTruthy();
    });

    it("should not have a button when the document is of format DOCX and the user does not have the right to convert a document", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canConverteren: false,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({ title: "actie.converteren" }),
      );

      expect(button).toBeNull();
    });

    it("should not have a button when the document is of format TEXT and the user has the right to convert a document", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canConverteren: true,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: {
          ...enkelvoudigInformatieobject,
          formaat: FileFormat.TEXT,
        },
      });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({ title: "actie.converteren" }),
      );

      expect(button).toBeNull();
    });
  });

  describe("actie.unlock", () => {
    it("should not have a button when the document is not locked", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            gelockedDoor: undefined,
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canOntgrendelen: true,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({ title: "actie.unlock" }),
      );

      expect(button).toBeNull();
    });

    it("should not have a button when the document is locked but the user does not have the right to unlock", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            gelockedDoor: { id: "user-001", naam: "Test User" },
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canOntgrendelen: false,
            }),
          }),
        );
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarnessOrNull(
        MatNavListItemHarness.with({ title: "actie.unlock" }),
      );

      expect(button).toBeNull();
    });

    it("should call unlockInformatieObject with zaakUuid when clicked and a zaak is present", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            gelockedDoor: { id: "user-001", naam: "Test User" },
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canOntgrendelen: true,
            }),
          }),
        );
      const unlockSpy = jest
        .spyOn(informatieObjectenService, "unlockInformatieObject")
        .mockReturnValue(of({}));
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarness(
        MatNavListItemHarness.with({ title: "actie.unlock" }),
      );
      await button.click();

      expect(unlockSpy).toHaveBeenCalledWith(
        enkelvoudigInformatieobject.uuid,
        zaak.uuid,
      );
    });

    it("should call unlockInformatieObject without zaakUuid when clicked and no zaak is present", async () => {
      jest
        .spyOn(informatieObjectenService, "readEnkelvoudigInformatieobject")
        .mockReturnValue(
          of({
            ...enkelvoudigInformatieobject,
            gelockedDoor: { id: "user-001", naam: "Test User" },
            rechten: fromPartial<GeneratedType<"RestDocumentRechten">>({
              canOntgrendelen: true,
            }),
          }),
        );
      const unlockSpy = jest
        .spyOn(informatieObjectenService, "unlockInformatieObject")
        .mockReturnValue(of({}));
      jest
        .spyOn(informatieObjectenService, "listZaakInformatieobjecten")
        .mockReturnValue(of([]));
      mockActivatedRoute.data.next({
        informatieObject: enkelvoudigInformatieobject,
      });

      const button = await loader.getHarness(
        MatNavListItemHarness.with({ title: "actie.unlock" }),
      );
      await button.click();

      expect(unlockSpy).toHaveBeenCalledWith(
        enkelvoudigInformatieobject.uuid,
        undefined,
      );
    });
  });

  describe("actie.verwijderen", () => {
    const deleteUrl = `/rest/informatieobjecten/informatieobject/${enkelvoudigInformatieobject.uuid}`;

    let httpTestingController: HttpTestingController;
    let dialog: MatDialog;

    beforeEach(() => {
      httpTestingController = TestBed.inject(HttpTestingController);
      dialog = TestBed.inject(MatDialog);
      jest
        .spyOn(dialog, "open")
        .mockReturnValue(
          fromPartial<MatDialogRef<unknown>>({ afterClosed: () => of(false) }),
        );
    });

    describe("a document without a zaak", () => {
      it("does not delete it while the confirmation dialog is still open", () => {
        component.zaak = undefined;

        component["openDocumentVerwijderenDialog"]();

        httpTestingController.expectNone(deleteUrl);
      });
    });

    describe("a document belonging to a zaak", () => {
      it("reports a failing delete through the error handler", async () => {
        const foutAfhandelingService = TestBed.inject(FoutAfhandelingService);
        const foutAfhandelen = jest
          .spyOn(foutAfhandelingService, "foutAfhandelen")
          .mockReturnValue(of());
        component.zaak = zaak;

        component["openDocumentVerwijderenDialog"]();
        const { callback } = jest.mocked(dialog.open).mock.calls.at(-1)![1]!
          .data as RedenDialogData;
        callback!("fakeReden").subscribe({ error: () => undefined });
        await new Promise(requestAnimationFrame);
        httpTestingController
          .expectOne(deleteUrl)
          .flush(null, { status: 500, statusText: "Server Error" });
        await new Promise(requestAnimationFrame);

        expect(foutAfhandelen).toHaveBeenCalled();
      });
    });
  });
});
