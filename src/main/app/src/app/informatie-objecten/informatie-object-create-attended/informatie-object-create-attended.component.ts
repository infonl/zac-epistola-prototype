/*
 * SPDX-FileCopyrightText: 2024 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse } from "@angular/common/http";
import {
  Component,
  computed,
  effect,
  EventEmitter,
  inject,
  Input,
  OnDestroy,
  OnInit,
  Output,
  signal,
} from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
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
import moment, { Moment } from "moment";
import {
  catchError,
  EMPTY,
  from,
  map,
  Observable,
  of,
  ReplaySubject,
  startWith,
  Subject,
  switchMap,
  take,
  takeUntil,
  tap,
  timer,
} from "rxjs";
import { EpistolaTemplatesService } from "src/app/admin/epistola-templates.service";
import { SmartDocumentsService } from "src/app/admin/smart-documents.service";
import { VertrouwelijkaanduidingToTranslationKeyPipe } from "src/app/shared/pipes/vertrouwelijkaanduiding-to-translation-key.pipe";
import { UtilService } from "../../core/service/util.service";
import { IdentityService } from "../../identity/identity.service";
import { ZacAutoComplete } from "../../shared/form/auto-complete/auto-complete";
import { ZacDate } from "../../shared/form/date/date";
import { ZacFormActions } from "../../shared/form/form-actions/form-actions.component";
import { ZacInput } from "../../shared/form/input/input";
import { ZacSelect } from "../../shared/form/select/select";
import { injectMutation } from "../../shared/http/inject-mutation";
import {
  NotificationDialogComponent,
  NotificationDialogData,
} from "../../shared/notification-dialog/notification-dialog.component";
import { epistolaVariantLabel } from "../../shared/utils/epistola-variant-label";
import { GeneratedType } from "../../shared/utils/generated-types";
import {
  EPISTOLA_GENERATION_FINISHED_DISPLAY_MS,
  EpistolaGenerationProgressComponent,
} from "../epistola-generation-progress/epistola-generation-progress.component";
import {
  EpistolaPreviewDialogComponent,
  EpistolaPreviewDialogData,
} from "../epistola-preview-dialog/epistola-preview-dialog.component";
import { InformatieObjectenService } from "../informatie-objecten.service";

type TemplateOption = {
  id: string;
  name: string;
  informatieObjectTypeUUID?: string | null;
};
/** Only SmartDocuments groups its templates; Epistola offers those of the zaaktype's catalog directly. */
type TemplateGroupOption = {
  id: string;
  name: string;
  templates: TemplateOption[];
};

@Component({
  selector: "zac-informatie-object-create-attended",
  templateUrl: "./informatie-object-create-attended.component.html",
  styleUrls: ["./informatie-object-create-attended.component.less"],
  standalone: true,
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
    ZacDate,
    ZacInput,
    ZacSelect,
    ZacFormActions,
    EpistolaGenerationProgressComponent,
  ],
})
export class InformatieObjectCreateAttendedComponent
  implements OnInit, OnDestroy
{
  @Input({ required: true }) zaak!: GeneratedType<"RestZaak">;
  @Input() taak?: GeneratedType<"RestTask">;
  @Input({ required: true }) sideNav!: MatDrawer;
  @Input({ required: false }) smartDocumentsGroupId?: string;
  @Input({ required: false }) smartDocumentsTemplateId?: string;
  @Output() document = new EventEmitter<void>();

  private readonly destroy$ = new Subject<void>();

  private readonly informatieObjectTypes$ = new ReplaySubject<
    GeneratedType<"RestInformatieobjecttype">[]
  >(1);

  protected readonly form = this.formBuilder.group({
    templateGroup: this.formBuilder.control<TemplateGroupOption | null>(null, [
      Validators.required,
    ]),
    template: this.formBuilder.control<TemplateOption | null>(null, [
      Validators.required,
    ]),
    title: this.formBuilder.control<string | null>(null, [
      Validators.required,
      Validators.maxLength(100),
    ]),
    description: this.formBuilder.control<string | null>(null, [
      Validators.maxLength(100),
    ]),
    informationObjectType: this.formBuilder.control<string | null>(null),
    confidentiality: this.formBuilder.control<string | null>(null),
    format: this.formBuilder.control<string | null>({
      value: "PDF",
      disabled: true,
    }),
    creationDate: this.formBuilder.control<Moment | null>(moment(), [
      Validators.required,
    ]),
    author: this.formBuilder.control<string | null>(null, [
      Validators.required,
      Validators.pattern("\\S.*"),
      Validators.maxLength(50),
    ]),
    taskId: this.formBuilder.control<string | null>(null),
    variant: this.formBuilder.control<string | null>(null),
  });

  protected templateGroups: Observable<TemplateGroupOption[]> = of([]);
  protected templates: TemplateOption[] = [];
  protected readonly templatesError = signal<string | null>(null);

  protected readonly varianten =
    signal<GeneratedType<"RestEpistolaVarianten"> | null>(null);
  protected readonly variantOptions = computed(() => {
    const varianten = this.varianten()?.varianten ?? [];
    return varianten.length > 1 ? varianten : [];
  });
  protected readonly variantLabel = epistolaVariantLabel;
  private readonly chosenVariant = toSignal(
    this.form.controls.variant.valueChanges,
    { initialValue: null },
  );
  protected readonly isSuggestedVariantChosen = computed(
    () =>
      !!this.chosenVariant() &&
      this.chosenVariant() === this.varianten()?.voorgesteldeVariant,
  );

  private readonly epistolaTemplatesService = inject(EpistolaTemplatesService);
  private readonly utilService = inject(UtilService);

  private readonly loggedInUserQuery = injectQuery(() =>
    this.identityService.readLoggedInUser(),
  );

  protected readonly createDocumentMutation = injectMutation(() =>
    this.informatieObjectenService.createDocumentAttendedMutation(),
  );

  protected readonly createEpistolaDocumentMutation = injectMutation(() =>
    this.informatieObjectenService.createEpistolaDocumentMutation(),
  );

  protected readonly previewEpistolaDocumentMutation = injectMutation(() =>
    this.informatieObjectenService.previewEpistolaDocumentMutation(),
  );

  private readonly generatingForZaakUuid = signal<string | undefined>(
    undefined,
  );

  private readonly epistolaStatusQuery = injectQuery(() => {
    const uuid = this.generatingForZaakUuid();
    return {
      ...this.informatieObjectenService.readEpistolaDocumentCreationStatusQuery(
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

  constructor(
    private readonly smartDocumentsService: SmartDocumentsService,
    private readonly informatieObjectenService: InformatieObjectenService,
    private readonly identityService: IdentityService,
    private readonly vertrouwelijkaanduidingToTranslationKeyPipe: VertrouwelijkaanduidingToTranslationKeyPipe,
    private readonly translateService: TranslateService,
    private readonly dialog: MatDialog,
    private readonly formBuilder: FormBuilder,
    private readonly queryClient: QueryClient,
  ) {
    effect(() => {
      this.form.controls.author.setValue(
        this.loggedInUserQuery.data()?.naam ?? null,
      );
    });
  }

  /**
   * Epistola renders the document while ZAC waits, so there is no wizard to choose a date or an author in:
   * the document is dated today and written by the logged-in user.
   */
  protected get usesEpistola() {
    return !!this.zaak.zaaktype.zaakafhandelparameters?.epistola
      ?.isEnabledGlobally;
  }

  /**
   * A preview in another variant than the one that is generated would mislead, so it waits for the variants of the
   * chosen template, and for a variant where the template asks for one.
   */
  protected get canPreviewEpistolaDocument() {
    const { template, variant } = this.form.controls;
    return (
      !!template.value &&
      variant.enabled &&
      variant.valid &&
      !this.createEpistolaDocumentMutation.isPending() &&
      !this.previewEpistolaDocumentMutation.isPending()
    );
  }

  async ngOnInit() {
    this.fetchInformatieobjecttypes();

    this.form.controls.template.disable();
    this.form.controls.informationObjectType.disable();
    this.form.controls.confidentiality.disable();
    if (this.usesEpistola) {
      this.form.controls.templateGroup.disable();
      this.form.controls.creationDate.disable();
      this.form.controls.author.disable();
    }

    this.form.controls.templateGroup.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((value) => this.offerTemplates(value?.templates));

    this.form.controls.template.valueChanges
      .pipe(
        takeUntil(this.destroy$),
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

    if (this.usesEpistola) {
      this.form.controls.template.valueChanges
        .pipe(
          takeUntil(this.destroy$),
          tap(() => this.awaitVarianten()),
          switchMap((template) =>
            template?.id
              ? from(
                  this.queryClient.query(
                    this.informatieObjectenService.readEpistolaVariantenQuery(
                      this.zaak.uuid,
                      template.id,
                    ),
                  ),
                ).pipe(catchError(() => of(null)))
              : EMPTY,
          ),
        )
        .subscribe((varianten) => this.offerVarianten(varianten));
    }

    if (this.usesEpistola) {
      this.offerEpistolaTemplates();
      return;
    }

    const templateGroupsFetcher = this.whenLoaded(
      this.queryClient.query(
        this.smartDocumentsService.getTemplatesMappingQuery(
          this.zaak.zaaktype.uuid,
        ),
      ),
    ).pipe(startWith([]));
    this.templateGroups = templateGroupsFetcher;
    templateGroupsFetcher
      .pipe(takeUntil(this.destroy$))
      .subscribe((templateGroups) => {
        if (this.smartDocumentsGroupId !== undefined) {
          const smartDocumentsTemplateGroup = templateGroups.find(
            ({ id }) => id === this.smartDocumentsGroupId,
          );
          if (smartDocumentsTemplateGroup) {
            this.form.controls.templateGroup.setValue(
              smartDocumentsTemplateGroup,
            );
            this.form.controls.templateGroup.disable();
            return;
          }
        }

        if (templateGroups.length === 1) {
          this.form.controls.templateGroup.setValue(templateGroups[0]);
        }
      });
  }

  /** Hiding the picker until another template is chosen and its variants arrive would make the form below it jump. */
  private awaitVarianten() {
    const { variant } = this.form.controls;
    variant.setValue(null);
    variant.disable();
  }

  private offerVarianten(
    varianten: GeneratedType<"RestEpistolaVarianten"> | null,
  ) {
    this.varianten.set(varianten);
    const { variant } = this.form.controls;
    const isChoiceOffered = this.variantOptions().length > 0;
    variant.setValidators(isChoiceOffered ? Validators.required : null);
    variant.setValue(
      isChoiceOffered ? (varianten?.voorgesteldeVariant ?? null) : null,
    );
    variant.enable();
  }

  private offerEpistolaTemplates() {
    this.whenLoaded(
      this.queryClient.query(
        this.epistolaTemplatesService.listOfferedTemplatesQuery(
          this.zaak.zaaktype.uuid,
        ),
      ),
    )
      .pipe(takeUntil(this.destroy$))
      .subscribe((templates) => this.offerTemplates(templates));
  }

  /** Empty, with the reason shown, when the templates cannot be loaded. */
  private whenLoaded<T>(templates: Promise<T[]>): Observable<T[]> {
    return from(templates).pipe(
      catchError((error: HttpErrorResponse) => {
        this.templatesError.set(
          error.error?.message ?? "dialoog.error.body.technisch",
        );
        return of([]);
      }),
    );
  }

  private offerTemplates(templates: TemplateOption[] | undefined) {
    this.templates = templates ?? [];

    if (this.smartDocumentsTemplateId !== undefined) {
      const smartDocumentsTemplate = this.templates.find(
        ({ id }) => id === this.smartDocumentsTemplateId,
      );
      if (smartDocumentsTemplate) {
        this.form.controls.template.setValue(smartDocumentsTemplate);
        this.form.controls.template.disable();
        return;
      }
    }

    if (!templates) {
      this.form.controls.template.setValue(null);
      this.form.controls.template.disable();
      return;
    }

    this.form.controls.template.enable();

    if (templates.length !== 1) return;

    this.form.controls.template.setValue(templates.at(0) ?? null);
    this.form.controls.template.disable();
  }

  private fetchInformatieobjecttypes() {
    this.informatieObjectenService
      .listInformatieobjecttypes(this.zaak.zaaktype.uuid)
      .pipe(takeUntil(this.destroy$))
      .subscribe((types) => {
        this.informatieObjectTypes$.next(types);
      });
  }

  protected onFormSubmit(formData?: typeof this.form) {
    const values = formData?.getRawValue();

    if (!formData?.valid || !values) {
      void this.sideNav.close();
      return;
    }

    if (this.usesEpistola) {
      this.createEpistolaDocument(
        values.template!.id,
        values.title!,
        values.description,
        values.variant,
      );
      return;
    }

    const data: GeneratedType<"RestDocumentCreationAttendedData"> = {
      author: values.author!,
      smartDocumentsTemplateGroupId: values.templateGroup!.id ?? null,
      smartDocumentsTemplateId: values.template!.id,
      title: values.title!,
      creationDate: values.creationDate!.toISOString(),
      description: values.description,
      zaakUuid: this.zaak.uuid,
      taskId: this.taak?.id,
    };

    this.createDocumentMutation.mutate(data, {
      onSuccess: ({ redirectURL, message }) => {
        if (!redirectURL) {
          this.dialog.open(NotificationDialogComponent, {
            data: new NotificationDialogData(message!),
          });
          return;
        }

        this.document.emit();
        window.open(redirectURL);
      },
    });
  }

  protected previewEpistolaDocument() {
    const { template, variant } = this.form.getRawValue();
    if (!template) return;

    this.previewEpistolaDocumentMutation.mutate(
      {
        zaakUuid: this.zaak.uuid,
        taskId: this.taak?.id,
        templateId: template.id,
        variant,
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
    variant?: string | null,
  ) {
    this.generatingForZaakUuid.set(this.zaak.uuid);
    this.createEpistolaDocumentMutation.mutate(
      {
        zaakUuid: this.zaak.uuid,
        taskId: this.taak?.id,
        templateId,
        title,
        description,
        variant,
      },
      {
        onSuccess: () => {
          void this.queryClient.invalidateQueries({
            queryKey:
              this.informatieObjectenService.listEnkelvoudigInformatieobjectenQueryKeyOfZaak(
                this.zaak.uuid,
              ),
          });
          this.generationFinished.set(true);
          timer(EPISTOLA_GENERATION_FINISHED_DISPLAY_MS)
            .pipe(takeUntil(this.destroy$))
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

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
