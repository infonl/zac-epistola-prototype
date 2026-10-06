/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, computed, effect, inject, input } from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { TranslateModule, TranslateService } from "@ngx-translate/core";
import { injectQuery } from "@tanstack/angular-query-experimental";
import { InformatieObjectenService } from "src/app/informatie-objecten/informatie-objecten.service";
import { EpistolaTemplatesService } from "../../epistola-templates.service";

@Component({
  selector: "epistola-templates-form",
  templateUrl: "./epistola-templates-form.component.html",
  styleUrl: "./epistola-templates-form.component.less",
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    TranslateModule,
  ],
})
export class EpistolaTemplatesFormComponent {
  readonly zaaktypeUuid = input.required<string>();
  readonly enabledForZaaktype = input(false);

  private readonly epistolaTemplatesService = inject(EpistolaTemplatesService);
  private readonly informatieObjectenService = inject(
    InformatieObjectenService,
  );
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
  });

  protected readonly catalogsQuery = injectQuery(() =>
    this.epistolaTemplatesService.listCatalogsQuery(),
  );
  private readonly catalogMappingQuery = injectQuery(() =>
    this.epistolaTemplatesService.getCatalogMappingQuery(this.zaaktypeUuid()),
  );
  protected readonly informatieobjecttypesQuery = injectQuery(() =>
    this.informatieObjectenService.listInformatieobjecttypesQuery(
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

  protected readonly catalogLocalesQuery = injectQuery(() => {
    const catalogId = this.chosenCatalogId();
    return {
      ...this.epistolaTemplatesService.listCatalogLocalesQuery(catalogId),
      enabled: Boolean(catalogId),
    };
  });

  private readonly chosenLocale = toSignal(
    this.form.controls.locale.valueChanges,
    { initialValue: this.form.controls.locale.value },
  );

  protected readonly localeOptions = computed(() =>
    (this.catalogLocalesQuery.data() ?? [])
      .map((locale) => ({ locale, name: this.languageName(locale) }))
      .sort((a, b) => a.name.localeCompare(b.name)),
  );

  private readonly chosenInformatieObjectTypeUuid = toSignal(
    this.form.controls.informatieObjectTypeUUID.valueChanges,
    { initialValue: this.form.controls.informatieObjectTypeUUID.value },
  );

  private isCatalogMappingLoaded = false;

  constructor() {
    effect(() =>
      this.form.controls.enabledForZaaktype.setValue(this.enabledForZaaktype()),
    );
    effect(() => {
      const offeredLocales = this.catalogLocalesQuery.data();
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
  }

  get enabledForZaaktypeValue() {
    return this.form.controls.enabledForZaaktype.value;
  }

  /** Before the stored mapping has arrived the form is empty, and saving it would replace that mapping. */
  isValid() {
    return (
      !this.enabledForZaaktypeValue ||
      (this.catalogMappingQuery.data() !== undefined && this.form.valid)
    );
  }

  saveEpistolaTemplatesMapping() {
    const { catalogId, locale, informatieObjectTypeUUID } =
      this.form.getRawValue();
    return this.epistolaTemplatesService.storeCatalogMapping(
      this.zaaktypeUuid(),
      { catalogId, locale: locale || null, informatieObjectTypeUUID },
    );
  }

  protected vertrouwelijkheidaanduiding() {
    return this.informatieobjecttypesQuery
      .data()
      ?.find(({ uuid }) => uuid === this.chosenInformatieObjectTypeUuid())
      ?.vertrouwelijkheidaanduiding;
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
