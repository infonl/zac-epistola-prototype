/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse } from "@angular/common/http";
import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  OnInit,
  output,
  signal,
} from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatDialog } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatDrawer } from "@angular/material/sidenav";
import { MatToolbarModule } from "@angular/material/toolbar";
import { TranslateModule, TranslateService } from "@ngx-translate/core";
import { injectQuery, QueryClient } from "@tanstack/angular-query-experimental";
import {
  catchError,
  EMPTY,
  from,
  map,
  of,
  ReplaySubject,
  switchMap,
  take,
  timer,
} from "rxjs";
import { EpistolaTemplatesService } from "../../admin/epistola-templates.service";
import { UtilService } from "../../core/service/util.service";
import { IdentityService } from "../../identity/identity.service";
import { ZacAutoComplete } from "../../shared/form/auto-complete/auto-complete";
import { ZacFormActions } from "../../shared/form/form-actions/form-actions.component";
import { ZacInput } from "../../shared/form/input/input";
import { injectMutation } from "../../shared/http/inject-mutation";
import { VertrouwelijkaanduidingToTranslationKeyPipe } from "../../shared/pipes/vertrouwelijkaanduiding-to-translation-key.pipe";
import { GeneratedType } from "../../shared/utils/generated-types";
import { EpistolaDocumentenService } from "../epistola-documenten.service";
import {
  EPISTOLA_GENERATION_FINISHED_DISPLAY_MS,
  EpistolaGenerationProgressComponent,
} from "../epistola-generation-progress/epistola-generation-progress.component";
import {
  EpistolaPreviewDialogComponent,
  EpistolaPreviewDialogData,
} from "../epistola-preview-dialog/epistola-preview-dialog.component";
import { InformatieObjectenService } from "../informatie-objecten.service";

type EpistolaTemplate = GeneratedType<"RestOfferedEpistolaTemplate">;

/**
 * Epistola renders the document while ZAC waits, so there is no wizard to choose a date or an author in:
 * the document is dated today and written by the logged-in user.
 */
@Component({
  selector: "zac-epistola-document-create",
  templateUrl: "./epistola-document-create.component.html",
  standalone: true,
  providers: [VertrouwelijkaanduidingToTranslationKeyPipe],
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDividerModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
    TranslateModule,
    ZacAutoComplete,
    ZacInput,
    ZacFormActions,
    EpistolaGenerationProgressComponent,
  ],
})
export class EpistolaDocumentCreateComponent implements OnInit {
  readonly zaak = input.required<GeneratedType<"RestZaak">>();
  readonly taak = input<GeneratedType<"RestTask">>();
  readonly sideNav = input.required<MatDrawer>();
  readonly document = output<void>();

  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queryClient = inject(QueryClient);
  private readonly dialog = inject(MatDialog);
  private readonly translateService = inject(TranslateService);
  private readonly utilService = inject(UtilService);
  private readonly identityService = inject(IdentityService);
  private readonly informatieObjectenService = inject(
    InformatieObjectenService,
  );
  private readonly epistolaDocumentenService = inject(
    EpistolaDocumentenService,
  );
  private readonly epistolaTemplatesService = inject(EpistolaTemplatesService);
  private readonly vertrouwelijkaanduidingToTranslationKeyPipe = inject(
    VertrouwelijkaanduidingToTranslationKeyPipe,
  );

  private readonly informatieObjectTypes$ = new ReplaySubject<
    GeneratedType<"RestInformatieobjecttype">[]
  >(1);

  protected readonly form = this.formBuilder.group({
    template: this.formBuilder.control<EpistolaTemplate | null>(
      { value: null, disabled: true },
      [Validators.required],
    ),
    title: this.formBuilder.control<string | null>(null, [
      Validators.required,
      Validators.maxLength(100),
    ]),
    description: this.formBuilder.control<string | null>(null, [
      Validators.maxLength(100),
    ]),
    informationObjectType: this.formBuilder.control<string | null>({
      value: null,
      disabled: true,
    }),
    confidentiality: this.formBuilder.control<string | null>({
      value: null,
      disabled: true,
    }),
    format: this.formBuilder.control<string | null>({
      value: "PDF",
      disabled: true,
    }),
    author: this.formBuilder.control<string | null>({
      value: null,
      disabled: true,
    }),
  });

  protected templates: EpistolaTemplate[] = [];
  protected readonly templatesError = signal<string | null>(null);

  private readonly loggedInUserQuery = injectQuery(() =>
    this.identityService.readLoggedInUser(),
  );

  protected readonly createEpistolaDocumentMutation = injectMutation(() =>
    this.epistolaDocumentenService.createEpistolaDocumentMutation(),
  );

  protected readonly previewEpistolaDocumentMutation = injectMutation(() =>
    this.epistolaDocumentenService.previewEpistolaDocumentMutation(),
  );

  private readonly generatingForZaakUuid = signal<string | undefined>(
    undefined,
  );

  private readonly epistolaStatusQuery = injectQuery(() => {
    const uuid = this.generatingForZaakUuid();
    return {
      ...this.epistolaDocumentenService.readEpistolaDocumentCreationStatusQuery(
        uuid ?? "",
      ),
      enabled: Boolean(uuid),
    };
  });

  protected readonly generationFinished = signal(false);

  protected readonly generationStatus = computed(() =>
    this.epistolaStatusQuery.isError()
      ? undefined
      : this.epistolaStatusQuery.data()?.status,
  );

  constructor() {
    effect(() => {
      this.form.controls.author.setValue(
        this.loggedInUserQuery.data()?.naam ?? null,
      );
    });
  }

  protected get canPreviewEpistolaDocument() {
    return (
      !!this.form.controls.template.value &&
      !this.createEpistolaDocumentMutation.isPending() &&
      !this.previewEpistolaDocumentMutation.isPending()
    );
  }

  ngOnInit() {
    this.fetchInformatieobjecttypes();

    this.form.controls.template.valueChanges
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap((value) => {
          if (!value?.informatieObjectTypeUUID) {
            this.form.controls.informationObjectType.setValue(null);
            this.form.controls.confidentiality.setValue(null);
            return EMPTY;
          }
          return this.informatieObjectTypes$.pipe(
            take(1),
            map((types) => ({ value, types })),
          );
        }),
      )
      .subscribe(({ value, types }) => {
        const infoObjectType = types.find(
          (type) => type.uuid === value.informatieObjectTypeUUID,
        );

        if (!infoObjectType) return;

        this.form.controls.informationObjectType.setValue(
          infoObjectType.omschrijving ?? null,
        );
        this.form.controls.confidentiality.setValue(
          this.translateService.instant(
            this.vertrouwelijkaanduidingToTranslationKeyPipe.transform(
              infoObjectType.vertrouwelijkheidaanduiding,
            ),
          ),
        );
      });

    this.offerTemplates();
  }

  /** Empty, with the reason shown, when the templates cannot be loaded. */
  private offerTemplates() {
    from(
      this.queryClient.query(
        this.epistolaTemplatesService.listOfferedTemplatesQuery(
          this.zaak().zaaktype.uuid,
        ),
      ),
    )
      .pipe(
        catchError((error: HttpErrorResponse) => {
          this.templatesError.set(
            error.error?.message ?? "dialoog.error.body.technisch",
          );
          return of([]);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((templates) => {
        this.templates = templates;
        const { template } = this.form.controls;
        template.enable();

        if (templates.length !== 1) return;

        template.setValue(templates.at(0) ?? null);
        template.disable();
      });
  }

  private fetchInformatieobjecttypes() {
    this.informatieObjectenService
      .listInformatieobjecttypes(this.zaak().zaaktype.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((types) => {
        this.informatieObjectTypes$.next(types);
      });
  }

  protected onFormSubmit(formData?: typeof this.form) {
    const values = formData?.getRawValue();

    if (!formData?.valid || !values) {
      void this.sideNav().close();
      return;
    }

    this.createEpistolaDocument(
      values.template!.id,
      values.title!,
      values.description,
    );
  }

  protected previewEpistolaDocument() {
    const { template } = this.form.getRawValue();
    if (!template) return;

    this.previewEpistolaDocumentMutation.mutate(
      {
        zaakUuid: this.zaak().uuid,
        taskId: this.taak()?.id,
        templateId: template.id,
      },
      {
        onSuccess: (pdf) =>
          this.dialog.open<
            EpistolaPreviewDialogComponent,
            EpistolaPreviewDialogData
          >(EpistolaPreviewDialogComponent, {
            data: { pdf, templateName: template.name },
            width: "900px",
            maxWidth: "95vw",
          }),
      },
    );
  }

  private createEpistolaDocument(
    templateId: string,
    title: string,
    description?: string | null,
  ) {
    const zaakUuid = this.zaak().uuid;
    this.generatingForZaakUuid.set(zaakUuid);
    this.createEpistolaDocumentMutation.mutate(
      {
        zaakUuid,
        taskId: this.taak()?.id,
        templateId,
        title,
        description,
      },
      {
        onSuccess: () => {
          void this.queryClient.invalidateQueries({
            queryKey:
              this.epistolaDocumentenService.listEnkelvoudigInformatieobjectenQueryKeyOfZaak(
                zaakUuid,
              ),
          });
          this.generationFinished.set(true);
          timer(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
              this.utilService.openSnackbar(
                "msg.document.toegevoegd.aan.zaak",
                { document: title },
              );
              this.document.emit();
            });
        },
        onSettled: () => this.generatingForZaakUuid.set(undefined),
      },
    );
  }
}
