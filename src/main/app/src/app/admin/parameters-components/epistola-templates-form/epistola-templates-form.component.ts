/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import {
  FormControl,
  FormGroup,
  FormRecord,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { TranslateModule, TranslateService } from "@ngx-translate/core";
import { injectQuery } from "@tanstack/angular-query-experimental";
import { VertrouwelijkaanduidingToTranslationKeyPipe } from "src/app/shared/pipes/vertrouwelijkaanduiding-to-translation-key.pipe";
import { GeneratedType } from "src/app/shared/utils/generated-types";
import { EpistolaTemplatesService } from "../../epistola-templates.service";

type TemplateSettingsForm = FormGroup<{
  isEnabled: FormControl<boolean>;
  informatieObjectTypeUUID: FormControl<string>;
}>;

@Component({
  selector: "epistola-templates-form",
  templateUrl: "./epistola-templates-form.component.html",
  styleUrl: "./epistola-templates-form.component.less",
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatDividerModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    TranslateModule,
    VertrouwelijkaanduidingToTranslationKeyPipe,
  ],
})
export class EpistolaTemplatesFormComponent {
  readonly zaaktypeUuid = input.required<string>();
  readonly enabledForZaaktype = input(false);

  private readonly epistolaTemplatesService = inject(EpistolaTemplatesService);
  private readonly translateService = inject(TranslateService);

  protected readonly form = new FormGroup({
    enabledForZaaktype: new FormControl(false, { nonNullable: true }),
    catalogId: new FormControl("", {
      nonNullable: true,
      validators: Validators.required,
    }),
    locale: new FormControl("", { nonNullable: true }),
    informatieObjectTypeUUID: new FormControl<string | null>(
      null,
      Validators.required,
    ),
    templateSettings: new FormRecord<TemplateSettingsForm>({}),
  });

  protected readonly catalogsQuery = injectQuery(() =>
    this.epistolaTemplatesService.listCatalogsQuery(),
  );
  private readonly catalogMappingQuery = injectQuery(() =>
    this.epistolaTemplatesService.getCatalogMappingQuery(this.zaaktypeUuid()),
  );
  protected readonly informatieobjecttypesQuery = injectQuery(() =>
    this.epistolaTemplatesService.listInformatieobjecttypesQuery(
      this.zaaktypeUuid(),
    ),
  );

  private readonly chosenCatalogId = toSignal(
    this.form.controls.catalogId.valueChanges,
    { initialValue: this.form.controls.catalogId.value },
  );
  protected readonly catalogTemplatesQuery = injectQuery(() => {
    const catalogId = this.chosenCatalogId();
    return {
      ...this.epistolaTemplatesService.listCatalogTemplatesQuery(catalogId),
      enabled: Boolean(catalogId),
    };
  });

  private readonly chosenLocale = toSignal(
    this.form.controls.locale.valueChanges,
    { initialValue: this.form.controls.locale.value },
  );
  private readonly chosenInformatieObjectTypeUuid = toSignal(
    this.form.controls.informatieObjectTypeUUID.valueChanges,
    { initialValue: this.form.controls.informatieObjectTypeUUID.value },
  );
  private readonly chosenTemplateSettings = toSignal(
    this.form.controls.templateSettings.valueChanges,
    { initialValue: this.form.controls.templateSettings.value },
  );

  /** The catalog whose templates the template settings of the form were last made for. */
  private readonly settledCatalogId = signal<string | null>(null);

  /**
   * The languages that every template Document maken offers has, as BCP-47 tags. A template that is switched off takes
   * no part, as no document is generated from it, and neither does a template without a language, which is generated
   * without one. Unknown while the settings are not yet made for the catalog's templates, or while Epistola could not
   * say which languages a template has.
   */
  private readonly catalogLocales = computed(() => {
    const templates = this.catalogTemplatesQuery.data();
    if (!templates || this.settledCatalogId() !== this.chosenCatalogId()) {
      return undefined;
    }
    const settings = this.chosenTemplateSettings();
    const enabledTemplates = templates.filter(
      ({ id }) => settings[id]?.isEnabled ?? true,
    );
    if (enabledTemplates.some(({ locales }) => !locales)) {
      return undefined;
    }
    const [firstLocales = [], ...otherLocales] = enabledTemplates
      .map(({ locales }) => locales ?? [])
      .filter((locales) => locales.length);
    return otherLocales
      .reduce(
        (common, locales) =>
          common.filter((locale) => locales.includes(locale)),
        [...firstLocales],
      )
      .sort();
  });

  protected readonly hasNoCommonLocale = computed(
    () => this.catalogLocales()?.length === 0,
  );

  protected readonly localeOptions = computed(() => {
    const chosenLocale = this.chosenLocale();
    const locales =
      this.catalogLocales() ?? (chosenLocale ? [chosenLocale] : []);
    return locales
      .map((locale) => ({ locale, name: this.languageName(locale) }))
      .sort((a, b) => a.name.localeCompare(b.name));
  });

  protected readonly templateRows = computed(() => {
    const settings = this.chosenTemplateSettings();
    const defaultTypeName = this.informatieObjecttypeName(
      this.chosenInformatieObjectTypeUuid(),
    );
    return (this.catalogTemplatesQuery.data() ?? []).flatMap((template) => {
      const setting = Object.hasOwn(settings, template.id)
        ? settings[template.id]
        : undefined;
      if (!setting) return [];
      const { isEnabled, informatieObjectTypeUUID } = setting;
      return {
        id: template.id,
        name: template.name,
        isEnabled,
        ownTypeName: this.informatieObjecttypeName(informatieObjectTypeUUID),
        defaultTypeName,
        hasDetails: Boolean(template.locales),
        languages: (template.locales ?? [])
          .map((locale) => this.languageName(locale))
          .sort((a, b) => a.localeCompare(b))
          .join(", "),
      };
    });
  });

  private isCatalogMappingLoaded = false;

  constructor() {
    effect(() =>
      this.form.controls.enabledForZaaktype.setValue(this.enabledForZaaktype()),
    );
    effect(() => {
      const offeredLocales = this.catalogLocales();
      const chosenLocale = this.chosenLocale();
      if (
        offeredLocales &&
        chosenLocale &&
        !offeredLocales.includes(chosenLocale)
      ) {
        this.form.controls.locale.setValue("");
      }
    });
    effect(() => {
      const catalogMapping = this.catalogMappingQuery.data();
      if (!catalogMapping || this.isCatalogMappingLoaded) return;
      this.isCatalogMappingLoaded = true;
      this.form.patchValue({
        catalogId: catalogMapping.catalogId,
        locale: catalogMapping.locale ?? "",
        informatieObjectTypeUUID: catalogMapping.informatieObjectTypeUUID,
      });
      // Touched straight away, so a missing document type shows why saving is disabled.
      this.form.controls.informatieObjectTypeUUID.markAsTouched();
    });
    effect(() => {
      const templates = this.catalogTemplatesQuery.data();
      const catalogMapping = this.catalogMappingQuery.data();
      if (!templates || !catalogMapping) return;
      untracked(() =>
        this.settleTemplateSettings(
          templates.map(({ id }) => id),
          catalogMapping.templateSettings,
        ),
      );
    });
  }

  get enabledForZaaktypeValue() {
    return this.form.controls.enabledForZaaktype.value;
  }

  /**
   * Before the stored mapping and the templates of the chosen catalog have arrived the form is empty, and saving it
   * would replace what is stored.
   */
  isValid() {
    return (
      !this.enabledForZaaktypeValue ||
      (this.catalogMappingQuery.data() !== undefined &&
        this.settledCatalogId() === this.chosenCatalogId() &&
        this.form.valid)
    );
  }

  saveEpistolaTemplatesMapping() {
    const { catalogId, locale, informatieObjectTypeUUID, templateSettings } =
      this.form.getRawValue();
    return this.epistolaTemplatesService.storeCatalogMapping(
      this.zaaktypeUuid(),
      {
        catalogId,
        locale: locale || null,
        informatieObjectTypeUUID,
        templateSettings: Object.entries(templateSettings)
          .map(([templateId, setting]) => ({
            templateId,
            isEnabled: setting.isEnabled,
            informatieObjectTypeUUID: setting.informatieObjectTypeUUID || null,
          }))
          .filter(
            ({ isEnabled, informatieObjectTypeUUID }) =>
              !isEnabled || informatieObjectTypeUUID !== null,
          ),
      },
    );
  }

  protected vertrouwelijkheidaanduiding() {
    return this.informatieobjecttypesQuery
      .data()
      ?.find(({ uuid }) => uuid === this.chosenInformatieObjectTypeUuid())
      ?.vertrouwelijkheidaanduiding;
  }

  /**
   * A template of the catalog keeps what the beheerder set for it in the form, also when that is not yet saved, and
   * otherwise takes what is stored. A template the catalog does not have loses its settings.
   */
  private settleTemplateSettings(
    templateIds: string[],
    storedSettings: GeneratedType<"RestEpistolaTemplateSetting">[],
  ) {
    const { templateSettings } = this.form.controls;
    const settledIds = Object.keys(templateSettings.controls);
    settledIds
      .filter((templateId) => !templateIds.includes(templateId))
      .forEach((templateId) => templateSettings.removeControl(templateId));
    templateIds
      .filter((templateId) => !settledIds.includes(templateId))
      .forEach((templateId) => {
        const stored = storedSettings.find(
          (setting) => setting.templateId === templateId,
        );
        templateSettings.addControl(
          templateId,
          new FormGroup({
            isEnabled: new FormControl(stored?.isEnabled ?? true, {
              nonNullable: true,
            }),
            informatieObjectTypeUUID: new FormControl(
              stored?.informatieObjectTypeUUID ?? "",
              { nonNullable: true },
            ),
          }),
        );
      });
    this.settledCatalogId.set(this.chosenCatalogId());
  }

  private informatieObjecttypeName(uuid: string | null | undefined) {
    return uuid
      ? this.informatieobjecttypesQuery
          .data()
          ?.find((type) => type.uuid === uuid)?.omschrijving
      : undefined;
  }

  /** ZAC keeps no list of languages: the browser names the tag, in the language ZAC is shown in. */
  private languageName(locale: string) {
    try {
      return (
        new Intl.DisplayNames([this.uiLanguage()], {
          type: "language",
          languageDisplay: "standard",
        }).of(locale) ?? locale
      );
    } catch {
      return locale;
    }
  }

  /** Dutch is ZAC's own default language. */
  private uiLanguage() {
    return (
      this.translateService.getCurrentLang() ||
      this.translateService.getFallbackLang() ||
      "nl"
    );
  }
}
