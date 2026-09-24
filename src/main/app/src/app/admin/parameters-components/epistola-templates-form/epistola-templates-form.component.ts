/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { Component, computed, effect, inject, input } from "@angular/core";
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatSelectChange, MatSelectModule } from "@angular/material/select";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { TranslateModule } from "@ngx-translate/core";
import { injectQuery } from "@tanstack/angular-query-experimental";
import { InformatieObjectenService } from "src/app/informatie-objecten/informatie-objecten.service";
import { GeneratedType } from "src/app/shared/utils/generated-types";
import { EpistolaTemplatesService } from "../../epistola-templates.service";

type TemplateForm = FormGroup<{
  id: FormControl<string>;
  informatieObjectTypeUUID: FormControl<string | null>;
}>;

type TemplateGroupForm = FormGroup<{
  name: FormControl<string>;
  templates: FormArray<TemplateForm>;
}>;

const DUPLICATE_NAME_ERROR = "duplicateName";

const normalizedName = (name: string) => name.trim().toLowerCase();

const nonBlank: ValidatorFn = (control: AbstractControl<string>) =>
  control.value.trim() ? null : { required: true };

/**
 * Sets the error on each duplicated name control rather than on the list, so the field itself shows it.
 */
const uniqueTemplateGroupNames: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const nameControls = (control as FormArray<TemplateGroupForm>).controls.map(
    ({ controls }) => controls.name,
  );
  const nameCounts = new Map<string, number>();
  nameControls.forEach(({ value }) =>
    nameCounts.set(
      normalizedName(value),
      (nameCounts.get(normalizedName(value)) ?? 0) + 1,
    ),
  );

  let hasDuplicates = false;
  nameControls.forEach((nameControl) => {
    const name = normalizedName(nameControl.value);
    const isDuplicate = name !== "" && (nameCounts.get(name) ?? 0) > 1;
    hasDuplicates ||= isDuplicate;
    const { [DUPLICATE_NAME_ERROR]: _, ...otherErrors } =
      nameControl.errors ?? {};
    const errors = isDuplicate
      ? { ...otherErrors, [DUPLICATE_NAME_ERROR]: true }
      : otherErrors;
    nameControl.setErrors(Object.keys(errors).length ? errors : null);
  });

  return hasDuplicates ? { [DUPLICATE_NAME_ERROR]: true } : null;
};

@Component({
  selector: "epistola-templates-form",
  templateUrl: "./epistola-templates-form.component.html",
  styleUrl: "./epistola-templates-form.component.less",
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
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

  protected readonly templatesQuery = injectQuery(() =>
    this.epistolaTemplatesService.listTemplatesQuery(),
  );
  private readonly templateMappingQuery = injectQuery(() =>
    this.epistolaTemplatesService.getTemplatesMappingQuery(this.zaaktypeUuid()),
  );
  protected readonly informatieobjecttypesQuery = injectQuery(() =>
    this.informatieObjectenService.listInformatieobjecttypesQuery(
      this.zaaktypeUuid(),
    ),
  );

  private readonly templateNamesById = computed(
    () =>
      new Map(
        [
          ...(this.templateMappingQuery.data() ?? []).flatMap(
            ({ templates }) => templates,
          ),
          ...(this.templatesQuery.data() ?? []),
        ].map(({ id, name }) => [id, name]),
      ),
  );

  protected readonly form = new FormGroup({
    enabledForZaaktype: new FormControl(false, { nonNullable: true }),
    templateGroups: new FormArray<TemplateGroupForm>([], {
      validators: uniqueTemplateGroupNames,
    }),
  });

  private isTemplateMappingLoaded = false;

  constructor() {
    effect(() =>
      this.form.controls.enabledForZaaktype.setValue(this.enabledForZaaktype()),
    );
    effect(() => {
      const templateMapping = this.templateMappingQuery.data();
      if (!templateMapping || this.isTemplateMappingLoaded) return;
      this.isTemplateMappingLoaded = true;
      templateMapping.forEach(({ name, templates }) =>
        this.addTemplateGroup(
          name,
          templates.map(({ id, informatieObjectTypeUUID }) => ({
            id,
            informatieObjectTypeUUID,
          })),
        ),
      );
    });
  }

  get enabledForZaaktypeValue() {
    return this.form.controls.enabledForZaaktype.value;
  }

  isValid() {
    return !this.enabledForZaaktypeValue || this.form.valid;
  }

  saveEpistolaTemplatesMapping() {
    return this.epistolaTemplatesService.storeTemplatesMapping(
      this.zaaktypeUuid(),
      this.form.controls.templateGroups.controls.map(({ controls }) => ({
        name: controls.name.value.trim(),
        templates: controls.templates.controls.map(({ controls }) => ({
          id: controls.id.value,
          name: this.templateName(controls.id.value),
          informatieObjectTypeUUID: controls.informatieObjectTypeUUID.value!,
        })),
      })),
    );
  }

  protected addTemplateGroup(
    name = "",
    templates: { id: string; informatieObjectTypeUUID: string | null }[] = [],
  ) {
    this.form.controls.templateGroups.push(
      new FormGroup({
        name: new FormControl(name, {
          nonNullable: true,
          validators: nonBlank,
        }),
        templates: new FormArray(
          templates.map((template) => this.createTemplateForm(template)),
        ),
      }),
    );
  }

  protected removeTemplateGroup(templateGroupIndex: number) {
    this.form.controls.templateGroups.removeAt(templateGroupIndex);
  }

  protected addTemplate(
    templateGroup: TemplateGroupForm,
    selectChange: MatSelectChange<string>,
  ) {
    const templateForm = this.createTemplateForm({
      id: selectChange.value,
      informatieObjectTypeUUID: null,
    });
    // Touched straight away, so the missing document type shows why saving is disabled.
    templateForm.controls.informatieObjectTypeUUID.markAsTouched();
    templateGroup.controls.templates.push(templateForm);
    selectChange.source.value = null;
  }

  protected removeTemplate(
    templateGroup: TemplateGroupForm,
    templateIndex: number,
  ) {
    templateGroup.controls.templates.removeAt(templateIndex);
  }

  /** A template can be in only one group of a zaaktype, so a template already placed is not offered again. */
  protected unplacedTemplates(): GeneratedType<"RestEpistolaTemplate">[] {
    const placedTemplateIds = new Set(
      this.form.controls.templateGroups.controls.flatMap(({ controls }) =>
        controls.templates.controls.map(({ controls }) => controls.id.value),
      ),
    );
    return (this.templatesQuery.data() ?? []).filter(
      ({ id }) => !placedTemplateIds.has(id),
    );
  }

  protected templateName(templateId: string) {
    return this.templateNamesById().get(templateId) ?? templateId;
  }

  protected vertrouwelijkheidaanduiding(
    informatieObjectTypeUuid: string | null,
  ) {
    return this.informatieobjecttypesQuery
      .data()
      ?.find(({ uuid }) => uuid === informatieObjectTypeUuid)
      ?.vertrouwelijkheidaanduiding;
  }

  private createTemplateForm({
    id,
    informatieObjectTypeUUID,
  }: {
    id: string;
    informatieObjectTypeUUID: string | null;
  }): TemplateForm {
    return new FormGroup({
      id: new FormControl(id, { nonNullable: true }),
      informatieObjectTypeUUID: new FormControl(informatieObjectTypeUUID, {
        validators: Validators.required,
      }),
    });
  }
}
