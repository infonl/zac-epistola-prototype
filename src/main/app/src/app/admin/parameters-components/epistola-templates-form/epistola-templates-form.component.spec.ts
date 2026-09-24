/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { TranslateModule } from "@ngx-translate/core";
import {
  provideQueryClient,
  queryOptions,
} from "@tanstack/angular-query-experimental";
import { render, screen, waitFor, within } from "@testing-library/angular";
import userEvent, { UserEvent } from "@testing-library/user-event";
import { of } from "rxjs";
import { InformatieObjectenService } from "src/app/informatie-objecten/informatie-objecten.service";
import { GeneratedType } from "src/app/shared/utils/generated-types";
import { fromPartial } from "src/test-helpers";
import { testQueryClient } from "../../../../../setupJest";
import { EpistolaTemplatesService } from "../../epistola-templates.service";
import { EpistolaTemplatesFormComponent } from "./epistola-templates-form.component";

const ZAAKTYPE_UUID = "fake-zaaktype-uuid";

const BESLUIT: GeneratedType<"RestInformatieobjecttype"> = {
  uuid: "fake-informatieobjecttype-besluit",
  omschrijving: "Besluit",
  vertrouwelijkheidaanduiding: "VERTROUWELIJK",
};
const BIJLAGE: GeneratedType<"RestInformatieobjecttype"> = {
  uuid: "fake-informatieobjecttype-bijlage",
  omschrijving: "Bijlage",
  vertrouwelijkheidaanduiding: "OPENBAAR",
};

const BESLUIT_EVENEMENTENVERGUNNING: GeneratedType<"RestEpistolaTemplate"> = {
  id: "besluit-evenementenvergunning",
  name: "Besluit evenementenvergunning",
};
const ONTVANGSTBEVESTIGING: GeneratedType<"RestEpistolaTemplate"> = {
  id: "ontvangstbevestiging-aanvraag",
  name: "Ontvangstbevestiging aanvraag",
};

const STORED_TEMPLATE_GROUP: GeneratedType<"RestMappedEpistolaTemplateGroup"> =
  {
    name: "Vergunningen",
    templates: [
      {
        ...BESLUIT_EVENEMENTENVERGUNNING,
        informatieObjectTypeUUID: BESLUIT.uuid,
      },
    ],
  };

describe(EpistolaTemplatesFormComponent.name, () => {
  async function setup({
    enabledForZaaktype = true,
    templates = [BESLUIT_EVENEMENTENVERGUNNING, ONTVANGSTBEVESTIGING],
    templateMapping = [],
  }: {
    enabledForZaaktype?: boolean;
    templates?: GeneratedType<"RestEpistolaTemplate">[];
    templateMapping?: GeneratedType<"RestMappedEpistolaTemplateGroup">[];
  } = {}) {
    const storeTemplatesMapping = jest.fn().mockReturnValue(of(undefined));

    const rendered = await render(EpistolaTemplatesFormComponent, {
      imports: [TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideQueryClient(testQueryClient),
        {
          provide: EpistolaTemplatesService,
          useValue: fromPartial<EpistolaTemplatesService>({
            listTemplatesQuery: () =>
              queryOptions({
                queryKey: ["epistola-templates"],
                queryFn: () => Promise.resolve(templates),
              }),
            getTemplatesMappingQuery: (zaaktypeUuid: string) =>
              queryOptions({
                queryKey: ["epistola-templates-mapping", zaaktypeUuid],
                queryFn: () => Promise.resolve(templateMapping),
              }),
            storeTemplatesMapping,
          }),
        },
        {
          provide: InformatieObjectenService,
          useValue: fromPartial<InformatieObjectenService>({
            listInformatieobjecttypesQuery: (zaakTypeUuid: string) =>
              queryOptions({
                queryKey: ["informatieobjecttypes", zaakTypeUuid],
                queryFn: () => Promise.resolve([BESLUIT, BIJLAGE]),
              }),
          }),
        },
      ],
      inputs: { zaaktypeUuid: ZAAKTYPE_UUID, enabledForZaaktype },
    });

    await rendered.fixture.whenStable();
    rendered.fixture.detectChanges();

    return {
      ...rendered,
      component: rendered.fixture.componentInstance,
      storeTemplatesMapping,
      user: userEvent.setup(),
    };
  }

  async function addTemplateGroup(user: UserEvent, name: string) {
    await user.click(
      screen.getByRole("button", { name: "actie.sjabloongroep.toevoegen" }),
    );
    const newTemplateGroups = screen.getAllByRole("group", {
      name: "epistola.sjabloongroep.nieuw",
    });
    const newTemplateGroup = newTemplateGroups[newTemplateGroups.length - 1];
    await user.type(
      within(newTemplateGroup).getByRole("textbox", {
        name: "epistola.sjabloongroep",
      }),
      name,
    );
    return newTemplateGroup;
  }

  async function chooseOption(
    user: UserEvent,
    combobox: HTMLElement,
    option: string,
  ) {
    await user.click(combobox);
    await user.click(screen.getByRole("option", { name: option }));
  }

  describe("given Epistola is switched off for the zaaktype", () => {
    it("says so and offers no template groups", async () => {
      await setup({ enabledForZaaktype: false });

      expect(
        screen.getByRole("switch", { name: "title.epistola.form" }),
      ).not.toBeChecked();
      expect(screen.getByText("msg.epistola.form.disabled")).toBeVisible();
      expect(
        screen.queryByRole("button", { name: "actie.sjabloongroep.toevoegen" }),
      ).not.toBeInTheDocument();
    });

    it("is valid, because nothing it holds would be saved", async () => {
      const { component } = await setup({ enabledForZaaktype: false });

      expect(component.isValid()).toBe(true);
      expect(component.enabledForZaaktypeValue).toBe(false);
    });
  });

  describe("given a zaaktype with a stored template group", () => {
    it("shows the group with its template, the template's document type and the confidentiality that follows from it", async () => {
      await setup({ templateMapping: [STORED_TEMPLATE_GROUP] });

      const templateGroup = await screen.findByRole("group", {
        name: "Vergunningen",
      });
      expect(
        within(templateGroup).getByRole("textbox", {
          name: "epistola.sjabloongroep",
        }),
      ).toHaveValue("Vergunningen");
      const template = within(templateGroup).getByRole("group", {
        name: "Besluit evenementenvergunning",
      });
      await waitFor(() =>
        expect(
          within(template).getByRole("combobox", {
            name: /informatieobjectTypeOmschrijving/,
          }),
        ).toHaveTextContent("Besluit"),
      );
      expect(
        within(template).getByRole("textbox", {
          name: "vertrouwelijkheidaanduiding",
        }),
      ).toHaveValue("vertrouwelijkheidaanduiding.VERTROUWELIJK");
    });

    it("does not offer a template that is already in a group", async () => {
      const { user } = await setup({
        templateMapping: [STORED_TEMPLATE_GROUP],
      });

      await user.click(
        within(
          await screen.findByRole("group", { name: "Vergunningen" }),
        ).getByRole("combobox", { name: /actie.sjabloon.toevoegen/ }),
      );

      expect(
        screen.getByRole("option", { name: "Ontvangstbevestiging aanvraag" }),
      ).toBeVisible();
      expect(
        screen.queryByRole("option", { name: "Besluit evenementenvergunning" }),
      ).not.toBeInTheDocument();
    });

    it("says a group is empty once its last template is removed, and drops the group when it is removed", async () => {
      const { user } = await setup({
        templateMapping: [STORED_TEMPLATE_GROUP],
      });
      const templateGroup = await screen.findByRole("group", {
        name: "Vergunningen",
      });

      await user.click(
        within(templateGroup).getByRole("button", {
          name: "actie.sjabloon.verwijderen",
        }),
      );

      expect(
        within(templateGroup).getByText("msg.epistola.sjabloongroep.leeg"),
      ).toBeVisible();

      await user.click(
        within(templateGroup).getByRole("button", {
          name: "actie.sjabloongroep.verwijderen",
        }),
      );

      expect(
        screen.queryByRole("group", { name: "Vergunningen" }),
      ).not.toBeInTheDocument();
    });
  });

  describe("given a zaaktype without template groups", () => {
    it("lets the beheerder create a group and put a template in it, which needs a document type before it is valid", async () => {
      const { component, user } = await setup();

      const templateGroup = await addTemplateGroup(user, "Handhaving");
      await chooseOption(
        user,
        within(templateGroup).getByRole("combobox", {
          name: /actie.sjabloon.toevoegen/,
        }),
        "Ontvangstbevestiging aanvraag",
      );

      const template = within(templateGroup).getByRole("group", {
        name: "Ontvangstbevestiging aanvraag",
      });
      expect(within(template).getByText("verplicht")).toBeVisible();
      expect(component.isValid()).toBe(false);

      await chooseOption(
        user,
        within(template).getByRole("combobox", {
          name: /informatieobjectTypeOmschrijving/,
        }),
        "Bijlage",
      );

      expect(
        within(template).getByRole("textbox", {
          name: "vertrouwelijkheidaanduiding",
        }),
      ).toHaveValue("vertrouwelijkheidaanduiding.OPENBAAR");
      expect(component.isValid()).toBe(true);
    });

    it("rejects two groups whose names differ only in case and surrounding spaces", async () => {
      const { component, user } = await setup();

      await addTemplateGroup(user, "Vergunningen");
      await addTemplateGroup(user, " vergunningen ");
      await user.tab();

      expect(
        screen.getAllByText("msg.epistola.sjabloongroep.naam-bestaat-al"),
      ).toHaveLength(2);
      expect(component.isValid()).toBe(false);
    });

    it("rejects a group without a name", async () => {
      const { component, user } = await setup();

      await addTemplateGroup(user, "   ");
      await user.tab();

      expect(screen.getByText("verplicht")).toBeVisible();
      expect(component.isValid()).toBe(false);
    });

    it("saves each group with its name trimmed and its templates with their document type", async () => {
      const { component, storeTemplatesMapping, user } = await setup();

      const templateGroup = await addTemplateGroup(user, "  Handhaving ");
      await chooseOption(
        user,
        within(templateGroup).getByRole("combobox", {
          name: /actie.sjabloon.toevoegen/,
        }),
        "Ontvangstbevestiging aanvraag",
      );
      await chooseOption(
        user,
        within(templateGroup).getByRole("combobox", {
          name: /informatieobjectTypeOmschrijving/,
        }),
        "Bijlage",
      );

      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeTemplatesMapping).toHaveBeenCalledWith(ZAAKTYPE_UUID, [
        {
          name: "Handhaving",
          templates: [
            {
              ...ONTVANGSTBEVESTIGING,
              informatieObjectTypeUUID: BIJLAGE.uuid,
            },
          ],
        },
      ]);
    });
  });

  describe("given Epistola's catalog holds no templates", () => {
    it("says so and offers no template to add to a group", async () => {
      const { user } = await setup({ templates: [] });

      const templateGroup = await addTemplateGroup(user, "Handhaving");

      expect(screen.getByText("msg.epistola.sjablonen.geen")).toBeVisible();
      expect(
        within(templateGroup).queryByRole("combobox", {
          name: /actie.sjabloon.toevoegen/,
        }),
      ).not.toBeInTheDocument();
    });
  });
});
