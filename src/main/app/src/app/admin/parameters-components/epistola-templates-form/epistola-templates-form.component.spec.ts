/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { TranslateModule, TranslateService } from "@ngx-translate/core";
import {
  provideQueryClient,
  queryOptions,
} from "@tanstack/angular-query-experimental";
import { render, screen, waitFor, within } from "@testing-library/angular";
import userEvent, { UserEvent } from "@testing-library/user-event";
import { of } from "rxjs";
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

const VERGUNNINGEN: GeneratedType<"RestEpistolaCatalog"> = {
  id: "fake-catalog-vergunningen",
  name: "Vergunningen",
};
const HANDHAVING: GeneratedType<"RestEpistolaCatalog"> = {
  id: "fake-catalog-handhaving",
  name: "Handhaving",
};

const BESLUIT_TEMPLATE: GeneratedType<"RestEpistolaTemplate"> = {
  id: "besluit-evenementenvergunning",
  name: "Besluit evenementenvergunning",
};
const ONTVANGSTBEVESTIGING_TEMPLATE: GeneratedType<"RestEpistolaTemplate"> = {
  id: "ontvangstbevestiging-aanvraag",
  name: "Ontvangstbevestiging aanvraag",
};
const HANDHAVING_TEMPLATES: GeneratedType<"RestEpistolaTemplate">[] = [
  {
    id: "besluit-evenementenvergunning",
    name: "Besluit evenementenvergunning",
  },
  {
    id: "vooraankondiging-last",
    name: "Vooraankondiging last",
  },
];

const TEMPLATES_BY_CATALOG: Record<
  string,
  GeneratedType<"RestEpistolaTemplate">[]
> = {
  [VERGUNNINGEN.id]: [BESLUIT_TEMPLATE, ONTVANGSTBEVESTIGING_TEMPLATE],
  [HANDHAVING.id]: HANDHAVING_TEMPLATES,
};

describe(EpistolaTemplatesFormComponent.name, () => {
  async function setup({
    enabledForZaaktype = true,
    catalogs = [VERGUNNINGEN, HANDHAVING],
    templatesByCatalog = TEMPLATES_BY_CATALOG,
    catalogMapping = {
      catalogId: VERGUNNINGEN.id,
      informatieObjectTypeUUID: BESLUIT.uuid,
      templateSettings: [],
    },
    readCatalogMapping = () => Promise.resolve(catalogMapping),
    readCatalogTemplates = (catalogId: string) =>
      Promise.resolve(templatesByCatalog[catalogId] ?? []),
  }: {
    enabledForZaaktype?: boolean;
    catalogs?: GeneratedType<"RestEpistolaCatalog">[];
    templatesByCatalog?: Record<
      string,
      GeneratedType<"RestEpistolaTemplate">[]
    >;
    catalogMapping?: GeneratedType<"RestEpistolaCatalogMapping">;
    readCatalogMapping?: () => Promise<
      GeneratedType<"RestEpistolaCatalogMapping">
    >;
    readCatalogTemplates?: (
      catalogId: string,
    ) => Promise<GeneratedType<"RestEpistolaTemplate">[]>;
  } = {}) {
    const storeCatalogMapping = jest.fn().mockReturnValue(of(undefined));

    const rendered = await render(EpistolaTemplatesFormComponent, {
      imports: [TranslateModule.forRoot(), NoopAnimationsModule],
      providers: [
        provideQueryClient(testQueryClient),
        {
          provide: EpistolaTemplatesService,
          useValue: fromPartial<EpistolaTemplatesService>({
            listCatalogsQuery: () =>
              queryOptions({
                queryKey: ["epistola-catalogs"],
                queryFn: () => Promise.resolve(catalogs),
              }),
            listCatalogTemplatesQuery: (catalogId: string) =>
              queryOptions({
                queryKey: ["epistola-catalog-templates", catalogId],
                queryFn: () => readCatalogTemplates(catalogId),
              }),
            getCatalogMappingQuery: (zaaktypeUuid: string) =>
              queryOptions({
                queryKey: ["epistola-catalog-mapping", zaaktypeUuid],
                queryFn: readCatalogMapping,
              }),
            listInformatieobjecttypesQuery: (zaaktypeUuid: string) =>
              queryOptions({
                queryKey: ["informatieobjecttypes", zaaktypeUuid],
                queryFn: () => Promise.resolve([BESLUIT, BIJLAGE]),
              }),
            storeCatalogMapping,
          }),
        },
      ],
      inputs: { zaaktypeUuid: ZAAKTYPE_UUID, enabledForZaaktype },
    });

    const translateService =
      rendered.fixture.debugElement.injector.get(TranslateService);
    translateService.setTranslation("nl", {
      "epistola.template.documenttype.standaard":
        "Zaaktype-standaard: {{documenttype}}",
    });
    translateService.use("nl");

    await rendered.fixture.whenStable();
    rendered.fixture.detectChanges();

    return {
      ...rendered,
      component: rendered.fixture.componentInstance,
      storeCatalogMapping,
      user: userEvent.setup(),
    };
  }

  const catalogPicker = () =>
    screen.getByRole("combobox", { name: /epistola.catalog/ });
  const informatieobjecttypePicker = () =>
    screen.getByRole("combobox", {
      name: /informatieobject-type-omschrijving/,
    });
  const templateHeader = (name: string) =>
    screen.getByRole("button", { name: new RegExp(name) });
  const templatePanel = (name: string) =>
    within(screen.getByRole("region", { name: new RegExp(name) }));

  async function chooseOption(
    user: UserEvent,
    combobox: HTMLElement,
    option: string,
  ) {
    await user.click(combobox);
    await user.click(screen.getByRole("option", { name: option }));
  }

  async function openTemplate(user: UserEvent, name: string) {
    await user.click(
      await screen.findByRole("button", { name: new RegExp(name) }),
    );
  }

  async function expectTemplateHeaders(names: string[]) {
    await waitFor(() => {
      const headers = within(
        screen.getByRole("group", { name: "epistola.catalog.templates" }),
      ).getAllByRole("button");
      expect(headers).toHaveLength(names.length);
      names.forEach((name, index) =>
        expect(headers[index]).toHaveAccessibleName(new RegExp(`^${name}`)),
      );
    });
  }

  describe("given Epistola is switched off for the zaaktype", () => {
    it("says so and offers no catalog to choose", async () => {
      await setup({ enabledForZaaktype: false });

      expect(
        screen.getByRole("switch", { name: "epistola.form.schakelaar" }),
      ).not.toBeChecked();
      expect(screen.getByText("msg.epistola.form.disabled")).toBeVisible();
      expect(
        screen.queryByRole("combobox", { name: /epistola.catalog/ }),
      ).not.toBeInTheDocument();
    });

    it("names the switch for assistive technology without showing its name as text next to it", async () => {
      await setup({ enabledForZaaktype: false });

      expect(
        screen.getByRole("switch", { name: "epistola.form.schakelaar" }),
      ).toBeVisible();
      expect(
        screen.queryByText("epistola.form.schakelaar"),
      ).not.toBeInTheDocument();
    });

    it("switches Epistola on when the switch is used", async () => {
      const { component, user } = await setup({ enabledForZaaktype: false });

      await user.click(
        screen.getByRole("switch", { name: "epistola.form.schakelaar" }),
      );

      expect(component.enabledForZaaktypeValue).toBe(true);
    });

    it("is valid, because nothing it holds would be saved", async () => {
      const { component } = await setup({ enabledForZaaktype: false });

      expect(component.isValid()).toBe(true);
      expect(component.enabledForZaaktypeValue).toBe(false);
    });
  });

  describe("given the stored catalog and document type have not arrived", () => {
    it("is not valid while they are loading, so the empty form cannot be saved over them", async () => {
      const { component } = await setup({
        readCatalogMapping: () => new Promise(() => {}),
      });

      expect(component.isValid()).toBe(false);
    });

    it("is not valid when they could not be loaded", async () => {
      const { component } = await setup({
        readCatalogMapping: () => Promise.reject(new Error("fakeFailure")),
      });

      expect(component.isValid()).toBe(false);
    });

    it("is not valid while the templates of the catalog are loading, so their settings cannot be saved away", async () => {
      const { component } = await setup({
        readCatalogTemplates: () => new Promise(() => {}),
      });

      expect(component.isValid()).toBe(false);
    });
  });

  describe("given a zaaktype with a stored catalog and document type", () => {
    it("shows the catalog by its name, the document type and the confidentiality that follows from it", async () => {
      const { component } = await setup();

      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );
      expect(informatieobjecttypePicker()).toHaveTextContent("Besluit");
      expect(
        screen.getByRole("textbox", { name: "vertrouwelijkheidaanduiding" }),
      ).toHaveValue("vertrouwelijkheidaanduiding.vertrouwelijk");
      await waitFor(() => expect(component.isValid()).toBe(true));
    });

    it("shows one accordion item for every template of the catalog, by its name", async () => {
      await setup();

      await expectTemplateHeaders([
        "Besluit evenementenvergunning",
        "Ontvangstbevestiging aanvraag",
      ]);
    });

    it("shows the other templates once the beheerder chooses another catalog", async () => {
      const { user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");

      await expectTemplateHeaders([
        "Besluit evenementenvergunning",
        "Vooraankondiging last",
      ]);
    });

    it("saves the chosen catalog with the document type, and no setting for any template", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");
      await chooseOption(user, informatieobjecttypePicker(), "Bijlage");
      await waitFor(() => expect(component.isValid()).toBe(true));
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(ZAAKTYPE_UUID, {
        catalogId: HANDHAVING.id,
        informatieObjectTypeUUID: BIJLAGE.uuid,
        templateSettings: [],
      });
    });
  });

  describe("given the templates of the catalog", () => {
    it("tells in the header of each which document type applies: the zaaktype's by default", async () => {
      await setup();

      expect(
        await screen.findByRole("button", {
          name: /Besluit evenementenvergunning.*Zaaktype-standaard: Besluit/,
        }),
      ).toBeVisible();
      expect(
        templateHeader("Ontvangstbevestiging aanvraag"),
      ).toHaveAccessibleName(/Zaaktype-standaard: Besluit/);
    });

    it("keeps the details of a template out of sight until its item is opened", async () => {
      await setup();
      expect(
        await screen.findByRole("button", {
          name: /Besluit evenementenvergunning/,
        }),
      ).toBeVisible();

      expect(
        screen.queryByText("besluit-evenementenvergunning"),
      ).not.toBeInTheDocument();
      expect(templateHeader("Besluit evenementenvergunning")).toHaveAttribute(
        "aria-expanded",
        "false",
      );
    });

    it("shows the id, the document type and the offering of a template once its item is opened", async () => {
      const { user } = await setup();

      await openTemplate(user, "Besluit evenementenvergunning");

      expect(
        templatePanel("Besluit evenementenvergunning").getAllByRole("term"),
      ).toHaveLength(1);
      expect(
        templatePanel("Besluit evenementenvergunning").getByText(
          "epistola.template.id",
        ),
      ).toBeVisible();
      expect(
        templatePanel("Besluit evenementenvergunning").getByText(
          "besluit-evenementenvergunning",
        ),
      ).toBeVisible();
      expect(
        templatePanel("Besluit evenementenvergunning").getByRole("combobox", {
          name: /informatieobject-type-omschrijving/,
        }),
      ).toBeVisible();
      expect(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      ).toBeVisible();
    });
  });

  describe("given the beheerder chooses a document type for a template", () => {
    it("starts with the choice to take the zaaktype's, and offers the zaaktype's document types", async () => {
      const { user } = await setup();
      await openTemplate(user, "Besluit evenementenvergunning");

      const picker = templatePanel("Besluit evenementenvergunning").getByRole(
        "combobox",
        {
          name: /informatieobject-type-omschrijving/,
        },
      );
      await waitFor(() =>
        expect(picker).toHaveTextContent(
          "epistola.template.documenttype.zaaktype",
        ),
      );
      await user.click(picker);

      expect(
        screen
          .getAllByRole("option")
          .map(({ textContent }) => textContent?.trim()),
      ).toEqual([
        "epistola.template.documenttype.zaaktype",
        "Besluit",
        "Bijlage",
      ]);
    });

    it("saves it for that template alone, and shows it in the header", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await openTemplate(user, "Besluit evenementenvergunning");

      await chooseOption(
        user,
        templatePanel("Besluit evenementenvergunning").getByRole("combobox", {
          name: /informatieobject-type-omschrijving/,
        }),
        "Bijlage",
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(
        templateHeader("Besluit evenementenvergunning"),
      ).toHaveAccessibleName(/Bijlage/);
      expect(
        templateHeader("Besluit evenementenvergunning"),
      ).not.toHaveAccessibleName(/Zaaktype-standaard/);
      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({
          informatieObjectTypeUUID: BESLUIT.uuid,
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              isEnabled: true,
              informatieObjectTypeUUID: BIJLAGE.uuid,
            },
          ],
        }),
      );
    });

    it("shows and keeps a document type that is stored for a template", async () => {
      const { component, storeCatalogMapping, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          templateSettings: [
            {
              templateId: "ontvangstbevestiging-aanvraag",
              informatieObjectTypeUUID: BIJLAGE.uuid,
              isEnabled: true,
            },
          ],
        },
      });

      await openTemplate(user, "Ontvangstbevestiging aanvraag");

      await waitFor(() =>
        expect(
          templatePanel("Ontvangstbevestiging aanvraag").getByRole("combobox", {
            name: /informatieobject-type-omschrijving/,
          }),
        ).toHaveTextContent("Bijlage"),
      );
      component.saveEpistolaTemplatesMapping().subscribe();
      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({
          templateSettings: [
            {
              templateId: "ontvangstbevestiging-aanvraag",
              isEnabled: true,
              informatieObjectTypeUUID: BIJLAGE.uuid,
            },
          ],
        }),
      );
    });

    it("saves no setting for a template once it takes the zaaktype's document type again", async () => {
      const { component, storeCatalogMapping, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              informatieObjectTypeUUID: BIJLAGE.uuid,
              isEnabled: true,
            },
          ],
        },
      });
      await openTemplate(user, "Besluit evenementenvergunning");

      await chooseOption(
        user,
        templatePanel("Besluit evenementenvergunning").getByRole("combobox", {
          name: /informatieobject-type-omschrijving/,
        }),
        "epistola.template.documenttype.zaaktype",
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({ templateSettings: [] }),
      );
      expect(
        templateHeader("Besluit evenementenvergunning"),
      ).toHaveAccessibleName(/Zaaktype-standaard: Besluit/);
    });

    it("is valid without a document type for any template", async () => {
      const { component } = await setup();

      await waitFor(() => expect(component.isValid()).toBe(true));
    });
  });

  describe("given the beheerder switches a template off", () => {
    it("saves the template as not offered, and says in its header that it is hidden", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await openTemplate(user, "Besluit evenementenvergunning");

      await user.click(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(
        templateHeader("Besluit evenementenvergunning"),
      ).toHaveAccessibleName(/epistola.template.verborgen/);
      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              isEnabled: false,
              informatieObjectTypeUUID: null,
            },
          ],
        }),
      );
    });

    it("keeps the document type of the template for when it is switched on again", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await openTemplate(user, "Besluit evenementenvergunning");
      await chooseOption(
        user,
        templatePanel("Besluit evenementenvergunning").getByRole("combobox", {
          name: /informatieobject-type-omschrijving/,
        }),
        "Bijlage",
      );

      await user.click(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenLastCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              isEnabled: false,
              informatieObjectTypeUUID: BIJLAGE.uuid,
            },
          ],
        }),
      );

      await user.click(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(
        templateHeader("Besluit evenementenvergunning"),
      ).toHaveAccessibleName(/Bijlage/);
      expect(storeCatalogMapping).toHaveBeenLastCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              isEnabled: true,
              informatieObjectTypeUUID: BIJLAGE.uuid,
            },
          ],
        }),
      );
    });

    it("saves no setting for a template that is switched off and on again without another change", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await openTemplate(user, "Besluit evenementenvergunning");

      await user.click(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );
      await user.click(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({ templateSettings: [] }),
      );
    });

    it("shows a template that is stored as not offered as hidden, switched off", async () => {
      const { user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              informatieObjectTypeUUID: null,
              isEnabled: false,
            },
          ],
        },
      });

      expect(
        await screen.findByRole("button", {
          name: /Besluit evenementenvergunning.*epistola.template.verborgen/,
        }),
      ).toBeVisible();
      await openTemplate(user, "Besluit evenementenvergunning");
      expect(
        templatePanel("Besluit evenementenvergunning").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      ).not.toBeChecked();
    });
  });

  describe("given the beheerder chooses another catalog", () => {
    it("drops the settings of templates the other catalog does not have, and keeps those of templates it has too", async () => {
      const { component, storeCatalogMapping, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          templateSettings: [],
        },
      });
      await openTemplate(user, "Besluit evenementenvergunning");
      await chooseOption(
        user,
        templatePanel("Besluit evenementenvergunning").getByRole("combobox", {
          name: /informatieobject-type-omschrijving/,
        }),
        "Bijlage",
      );
      await openTemplate(user, "Ontvangstbevestiging aanvraag");
      await user.click(
        templatePanel("Ontvangstbevestiging aanvraag").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");
      expect(
        await screen.findByRole("button", { name: /Vooraankondiging last/ }),
      ).toBeVisible();
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({
          catalogId: HANDHAVING.id,
          templateSettings: [
            {
              templateId: "besluit-evenementenvergunning",
              isEnabled: true,
              informatieObjectTypeUUID: BIJLAGE.uuid,
            },
          ],
        }),
      );
    });
  });

  describe("given a zaaktype without a document type for its Epistola documents", () => {
    it("says the document type is required, and is valid once one is chosen", async () => {
      const { component, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: null,
          templateSettings: [],
        },
      });

      expect(await screen.findByText("verplicht")).toBeVisible();
      expect(component.isValid()).toBe(false);

      await chooseOption(user, informatieobjecttypePicker(), "Bijlage");

      expect(
        screen.getByRole("textbox", { name: "vertrouwelijkheidaanduiding" }),
      ).toHaveValue("vertrouwelijkheidaanduiding.openbaar");
      await waitFor(() => expect(component.isValid()).toBe(true));
    });
  });

  describe("given a catalog that holds no templates", () => {
    it("says so", async () => {
      await setup({ templatesByCatalog: { [VERGUNNINGEN.id]: [] } });

      expect(
        await screen.findByText("msg.epistola.templates.geen"),
      ).toBeVisible();
    });
  });

  describe("given a tenant without catalogs to choose from", () => {
    it("says so under the catalog picker", async () => {
      await setup({ catalogs: [] });

      expect(
        await screen.findByText("msg.epistola.catalogs.geen"),
      ).toBeVisible();
    });
  });
});
