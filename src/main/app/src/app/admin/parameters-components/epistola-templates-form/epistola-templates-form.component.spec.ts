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
  locales: ["en-GB", "nl-NL"],
  kanalen: ["post", "digitaal"],
  variants: [
    {
      id: "initial",
      title: "Initial",
      isDefault: true,
      attributes: [
        { key: "locale", value: "nl-NL" },
        { key: "kanaal", value: "post" },
      ],
    },
    {
      id: "groot-lettertype",
      title: "Groot lettertype",
      isDefault: false,
      attributes: [
        { key: "locale", value: "nl-NL" },
        { key: "kanaal", value: "post" },
        { key: "weergave", value: "groot" },
      ],
    },
    {
      id: "english",
      title: "English",
      isDefault: false,
      attributes: [
        { key: "locale", value: "en-GB" },
        { key: "kanaal", value: "digitaal" },
      ],
    },
  ],
};
const ONTVANGSTBEVESTIGING_TEMPLATE: GeneratedType<"RestEpistolaTemplate"> = {
  id: "ontvangstbevestiging-aanvraag",
  name: "Ontvangstbevestiging aanvraag",
  locales: ["en-GB", "nl-NL"],
  kanalen: [],
  variants: [],
};
const HANDHAVING_TEMPLATES: GeneratedType<"RestEpistolaTemplate">[] = [
  {
    id: "besluit-evenementenvergunning",
    name: "Besluit evenementenvergunning",
    locales: ["de-DE", "nl-NL"],
    kanalen: ["post"],
  },
  {
    id: "vooraankondiging-last",
    name: "Vooraankondiging last",
    locales: ["de-DE", "nl-NL"],
    kanalen: ["post"],
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
      locale: null,
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
  const languagePicker = () =>
    screen.getByRole("combobox", { name: /epistola.taal$/ });
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
        locale: null,
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

    it("shows the id and the languages by name of a template once its item is opened", async () => {
      const { user } = await setup();

      await openTemplate(user, "Besluit evenementenvergunning");

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
        templatePanel("Besluit evenementenvergunning").getByText(
          "Engels (Verenigd Koninkrijk), Nederlands (Nederland)",
        ),
      ).toBeVisible();
    });

    it("lists every variant of a template by its title, once its item is opened", async () => {
      const { user } = await setup();

      await openTemplate(user, "Besluit evenementenvergunning");

      const variants = await within(
        await screen.findByRole("region", {
          name: /Besluit evenementenvergunning/,
        }),
      ).findAllByRole("listitem");
      expect(variants).toHaveLength(3);
      expect(within(variants[0]).getByText("Initial")).toBeVisible();
      expect(within(variants[1]).getByText("Groot lettertype")).toBeVisible();
      expect(within(variants[2]).getByText("English")).toBeVisible();
    });

    it("names the language and the channel of a variant, and shows any other attribute as received", async () => {
      const { user } = await setup();

      await openTemplate(user, "Besluit evenementenvergunning");

      const [initial, largePrint, english] = await within(
        await screen.findByRole("region", {
          name: /Besluit evenementenvergunning/,
        }),
      ).findAllByRole("listitem");
      expect(within(initial).getByText("Nederlands (Nederland)")).toBeVisible();
      expect(within(initial).getByText("epistola.variant.post")).toBeVisible();
      expect(
        within(largePrint).getByText("Nederlands (Nederland)"),
      ).toBeVisible();
      expect(
        within(largePrint).getByText("epistola.variant.post"),
      ).toBeVisible();
      expect(within(largePrint).getByText("weergave: groot")).toBeVisible();
      expect(
        within(english).getByText("Engels (Verenigd Koninkrijk)"),
      ).toBeVisible();
      expect(
        within(english).getByText("epistola.variant.digitaal"),
      ).toBeVisible();
    });

    it("marks only the default variant", async () => {
      const { user } = await setup();

      await openTemplate(user, "Besluit evenementenvergunning");

      const [initial, largePrint, english] = await within(
        await screen.findByRole("region", {
          name: /Besluit evenementenvergunning/,
        }),
      ).findAllByRole("listitem");
      expect(
        within(initial).getByText("epistola.template.variant.standaard"),
      ).toBeVisible();
      expect(
        within(largePrint).queryByText("epistola.template.variant.standaard"),
      ).not.toBeInTheDocument();
      expect(
        within(english).queryByText("epistola.template.variant.standaard"),
      ).not.toBeInTheDocument();
    });

    it("shows a variant without attributes by its title alone", async () => {
      const { user } = await setup({
        templatesByCatalog: {
          [VERGUNNINGEN.id]: [
            {
              id: "brief",
              name: "Brief",
              locales: [],
              kanalen: [],
              variants: [
                {
                  id: "plain",
                  title: "Plain",
                  isDefault: true,
                  attributes: [],
                },
              ],
            },
          ],
        },
      });

      await openTemplate(user, "Brief");

      const [plain] = await within(
        await screen.findByRole("region", { name: /Brief/ }),
      ).findAllByRole("listitem");
      expect(within(plain).getByText("Plain")).toBeVisible();
      expect(plain).toHaveTextContent(
        /^\s*Plain\s*epistola.template.variant.standaard\s*$/,
      );
    });

    it("says so briefly when a template has no variants", async () => {
      const { user } = await setup();

      await openTemplate(user, "Ontvangstbevestiging aanvraag");

      expect(
        await within(
          await screen.findByRole("region", {
            name: /Ontvangstbevestiging aanvraag/,
          }),
        ).findByText("epistola.template.varianten.geen"),
      ).toBeVisible();
      expect(
        templatePanel("Ontvangstbevestiging aanvraag").queryByRole("listitem"),
      ).not.toBeInTheDocument();
      expect(
        templatePanel("Ontvangstbevestiging aanvraag").queryByText(
          "epistola.template.talen.geen",
        ),
      ).not.toBeInTheDocument();
    });

    it("says so briefly when a template has no languages", async () => {
      const { user } = await setup({
        templatesByCatalog: {
          [VERGUNNINGEN.id]: [
            { id: "brief", name: "Brief", locales: [], kanalen: ["post"] },
          ],
        },
      });

      await openTemplate(user, "Brief");

      expect(
        templatePanel("Brief").getByText("epistola.template.talen.geen"),
      ).toBeVisible();
    });

    it("shows the id, the document type and the offering of a template whose details Epistola could not give, but no languages or variants", async () => {
      const { user } = await setup({
        templatesByCatalog: {
          [VERGUNNINGEN.id]: [{ id: "brief", name: "Brief" }],
        },
      });

      await openTemplate(user, "Brief");

      expect(
        templatePanel("Brief").getByRole("combobox", {
          name: /informatieobject-type-omschrijving/,
        }),
      ).toBeVisible();
      expect(
        templatePanel("Brief").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      ).toBeVisible();
      expect(templatePanel("Brief").getAllByRole("term")).toHaveLength(1);
      expect(
        templatePanel("Brief").getByText("epistola.template.id"),
      ).toBeVisible();
      expect(
        templatePanel("Brief").queryByText("epistola.template.talen"),
      ).not.toBeInTheDocument();
      expect(
        templatePanel("Brief").queryByRole("listitem"),
      ).not.toBeInTheDocument();
      expect(
        templatePanel("Brief").queryByText("epistola.template.varianten.geen"),
      ).not.toBeInTheDocument();
      expect(templatePanel("Brief").getByText("brief")).toBeVisible();
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
          locale: null,
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
          locale: null,
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
          locale: null,
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
          locale: null,
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

  describe("given a catalog whose templates all offer Dutch and British English", () => {
    const optionNames = () =>
      screen
        .getAllByRole("option")
        .map(({ textContent }) => textContent?.trim());

    it("offers each language by its name in the language ZAC is shown in, after the choice to leave it to ZAC", async () => {
      const { user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );

      await user.click(languagePicker());

      await waitFor(() =>
        expect(optionNames()).toEqual([
          "epistola.taal.standaard",
          "Engels (Verenigd Koninkrijk)",
          "Nederlands (Nederland)",
        ]),
      );
    });

    it("shows no hint when the catalog has languages to choose from", async () => {
      await setup();

      expect(
        await screen.findByRole("combobox", { name: /epistola\.taal/ }),
      ).toBeVisible();
      expect(
        screen.queryByText("msg.epistola.talen.geen"),
      ).not.toBeInTheDocument();
    });

    it("shows the language stored with the zaaktype", async () => {
      await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: "en-GB",
          templateSettings: [],
        },
      });

      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent(
          "Engels (Verenigd Koninkrijk)",
        ),
      );
    });

    it("saves the chosen language with the catalog and the document type", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );
      await user.click(languagePicker());
      await waitFor(() => expect(optionNames()).toHaveLength(3));
      await user.keyboard("{Escape}");

      await chooseOption(
        user,
        languagePicker(),
        "Engels (Verenigd Koninkrijk)",
      );
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(ZAAKTYPE_UUID, {
        catalogId: VERGUNNINGEN.id,
        informatieObjectTypeUUID: BESLUIT.uuid,
        locale: "en-GB",
        templateSettings: [],
      });
    });

    it("saves no language once the choice is left to ZAC again", async () => {
      const { component, storeCatalogMapping, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: "en-GB",
          templateSettings: [],
        },
      });
      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent(
          "Engels (Verenigd Koninkrijk)",
        ),
      );

      await chooseOption(user, languagePicker(), "epistola.taal.standaard");
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({ locale: null }),
      );
    });
  });

  describe("given a catalog with a template that is only in Dutch", () => {
    const TEMPLATES = [
      BESLUIT_TEMPLATE,
      {
        id: "brief-nederlands",
        name: "Brief Nederlands",
        locales: ["nl-NL"],
        kanalen: ["post"],
      },
    ];

    it("offers only Dutch while that template is offered", async () => {
      const { user } = await setup({
        templatesByCatalog: { [VERGUNNINGEN.id]: TEMPLATES },
      });
      expect(
        await screen.findByRole("button", { name: /Brief Nederlands/ }),
      ).toBeVisible();

      await user.click(languagePicker());

      expect(
        screen
          .getAllByRole("option")
          .map(({ textContent }) => textContent?.trim()),
      ).toEqual(["epistola.taal.standaard", "Nederlands (Nederland)"]);
    });

    it("offers the languages of the offered templates while a switched off template has no details", async () => {
      const { user } = await setup({
        templatesByCatalog: {
          [VERGUNNINGEN.id]: [
            BESLUIT_TEMPLATE,
            { id: "zonder-details", name: "Zonder details" },
          ],
        },
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: null,
          templateSettings: [
            {
              templateId: "zonder-details",
              informatieObjectTypeUUID: null,
              isEnabled: false,
            },
          ],
        },
      });
      expect(
        await screen.findByRole("button", { name: /Zonder details/ }),
      ).toBeVisible();

      await user.click(languagePicker());

      expect(
        screen
          .getAllByRole("option")
          .map(({ textContent }) => textContent?.trim()),
      ).toEqual([
        "epistola.taal.standaard",
        "Engels (Verenigd Koninkrijk)",
        "Nederlands (Nederland)",
      ]);
    });

    it("offers English again once that template is switched off, because no document is generated from it", async () => {
      const { user } = await setup({
        templatesByCatalog: { [VERGUNNINGEN.id]: TEMPLATES },
      });
      await openTemplate(user, "Brief Nederlands");

      await user.click(
        templatePanel("Brief Nederlands").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );

      await user.click(languagePicker());
      expect(
        await screen.findByRole("option", {
          name: "Engels (Verenigd Koninkrijk)",
        }),
      ).toBeVisible();
    });

    it("clears a chosen language that only the switched off template lacks, once it is switched on", async () => {
      const { component, storeCatalogMapping, user } = await setup({
        templatesByCatalog: { [VERGUNNINGEN.id]: TEMPLATES },
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: "en-GB",
          templateSettings: [
            {
              templateId: "brief-nederlands",
              informatieObjectTypeUUID: null,
              isEnabled: false,
            },
          ],
        },
      });
      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent(
          "Engels (Verenigd Koninkrijk)",
        ),
      );
      await openTemplate(user, "Brief Nederlands");

      await user.click(
        templatePanel("Brief Nederlands").getByRole("switch", {
          name: "epistola.template.aangeboden",
        }),
      );

      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent("epistola.taal.standaard"),
      );
      component.saveEpistolaTemplatesMapping().subscribe();
      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({ locale: null }),
      );
    });
  });

  describe("given templates whose languages Epistola could not give", () => {
    it("keeps the language stored with the zaaktype, which is then not known to be wrong", async () => {
      const { component, storeCatalogMapping } = await setup({
        templatesByCatalog: {
          [VERGUNNINGEN.id]: [{ id: "brief", name: "Brief" }],
        },
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: "en-GB",
          templateSettings: [],
        },
      });

      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent(
          "Engels (Verenigd Koninkrijk)",
        ),
      );
      await waitFor(() => expect(component.isValid()).toBe(true));
      component.saveEpistolaTemplatesMapping().subscribe();
      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({ locale: "en-GB" }),
      );
      expect(
        screen.queryByText("msg.epistola.talen.geen"),
      ).not.toBeInTheDocument();
    });
  });

  describe("given the beheerder chooses another catalog", () => {
    it("offers the languages of that catalog instead", async () => {
      const { user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");
      expect(
        await screen.findByRole("button", { name: /Vooraankondiging last/ }),
      ).toBeVisible();

      await user.click(languagePicker());
      expect(
        await screen.findByRole("option", { name: "Duits (Duitsland)" }),
      ).toBeVisible();
      expect(
        screen.queryByRole("option", { name: "Engels (Verenigd Koninkrijk)" }),
      ).not.toBeInTheDocument();
    });

    it("clears a chosen language that the other catalog does not offer", async () => {
      const { component, storeCatalogMapping, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: "en-GB",
          templateSettings: [],
        },
      });
      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent(
          "Engels (Verenigd Koninkrijk)",
        ),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");

      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent("epistola.taal.standaard"),
      );
      component.saveEpistolaTemplatesMapping().subscribe();
      expect(storeCatalogMapping).toHaveBeenCalledWith(
        ZAAKTYPE_UUID,
        expect.objectContaining({ catalogId: HANDHAVING.id, locale: null }),
      );
    });

    it("keeps a chosen language that the other catalog offers too", async () => {
      const { user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: BESLUIT.uuid,
          locale: "nl-NL",
          templateSettings: [],
        },
      });
      await waitFor(() =>
        expect(languagePicker()).toHaveTextContent("Nederlands (Nederland)"),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");
      expect(
        await screen.findByRole("button", { name: /Vooraankondiging last/ }),
      ).toBeVisible();

      await user.click(languagePicker());
      expect(
        await screen.findByRole("option", { name: "Duits (Duitsland)" }),
      ).toBeVisible();
      await user.keyboard("{Escape}");
      expect(languagePicker()).toHaveTextContent("Nederlands (Nederland)");
    });
  });

  describe("given a catalog whose templates share no language", () => {
    it("says so, and offers only the choice to leave it to ZAC", async () => {
      const { user } = await setup({
        templatesByCatalog: {
          [VERGUNNINGEN.id]: [
            { id: "een", name: "Een", locales: ["nl-NL"], kanalen: [] },
            { id: "twee", name: "Twee", locales: ["en-GB"], kanalen: [] },
          ],
        },
      });

      expect(await screen.findByText("msg.epistola.talen.geen")).toBeVisible();
      await user.click(languagePicker());
      expect(screen.getAllByRole("option")).toHaveLength(1);
    });
  });

  describe("given a zaaktype without a document type for its Epistola documents", () => {
    it("says the document type is required, and is valid once one is chosen", async () => {
      const { component, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: null,
          locale: null,
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
