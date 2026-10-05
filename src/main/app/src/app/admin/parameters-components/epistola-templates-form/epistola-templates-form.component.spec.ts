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

const VERGUNNINGEN: GeneratedType<"RestEpistolaCatalog"> = {
  id: "fake-catalog-vergunningen",
  name: "Vergunningen",
};
const HANDHAVING: GeneratedType<"RestEpistolaCatalog"> = {
  id: "fake-catalog-handhaving",
  name: "Handhaving",
};

const TEMPLATES_BY_CATALOG: Record<
  string,
  GeneratedType<"RestEpistolaTemplate">[]
> = {
  [VERGUNNINGEN.id]: [
    {
      id: "besluit-evenementenvergunning",
      name: "Besluit evenementenvergunning",
    },
    {
      id: "ontvangstbevestiging-aanvraag",
      name: "Ontvangstbevestiging aanvraag",
    },
  ],
  [HANDHAVING.id]: [
    { id: "vooraankondiging-last", name: "Vooraankondiging last" },
  ],
};

describe(EpistolaTemplatesFormComponent.name, () => {
  async function setup({
    enabledForZaaktype = true,
    catalogs = [VERGUNNINGEN, HANDHAVING],
    templatesByCatalog = TEMPLATES_BY_CATALOG,
    catalogMapping = {
      catalogId: VERGUNNINGEN.id,
      informatieObjectTypeUUID: BESLUIT.uuid,
    },
    readCatalogMapping = () => Promise.resolve(catalogMapping),
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
                queryFn: () =>
                  Promise.resolve(templatesByCatalog[catalogId] ?? []),
              }),
            getCatalogMappingQuery: (zaaktypeUuid: string) =>
              queryOptions({
                queryKey: ["epistola-catalog-mapping", zaaktypeUuid],
                queryFn: readCatalogMapping,
              }),
            storeCatalogMapping,
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
      storeCatalogMapping,
      user: userEvent.setup(),
    };
  }

  const catalogPicker = () =>
    screen.getByRole("combobox", { name: /epistola.catalog/ });
  const informatieobjecttypePicker = () =>
    screen.getByRole("combobox", { name: /informatieobjectTypeOmschrijving/ });

  async function chooseOption(
    user: UserEvent,
    combobox: HTMLElement,
    option: string,
  ) {
    await user.click(combobox);
    await user.click(screen.getByRole("option", { name: option }));
  }

  function catalogTemplateNames() {
    return within(
      screen.getByRole("list", { name: "epistola.catalog.templates" }),
    )
      .getAllByRole("listitem")
      .map(({ textContent }) => textContent?.trim());
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

    it("labels the switch visibly with what it switches", async () => {
      await setup({ enabledForZaaktype: false });

      expect(screen.getByText("epistola.form.schakelaar")).toBeVisible();
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
      ).toHaveValue("vertrouwelijkheidaanduiding.VERTROUWELIJK");
      expect(component.isValid()).toBe(true);
    });

    it("lists every template of the catalog, which the zaaktype then offers, without a way to pick them one by one", async () => {
      await setup();

      await waitFor(() =>
        expect(catalogTemplateNames()).toEqual([
          "Besluit evenementenvergunning",
          "Ontvangstbevestiging aanvraag",
        ]),
      );
      expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    });

    it("lists the templates of another catalog once the beheerder chooses it", async () => {
      const { user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");

      await waitFor(() =>
        expect(catalogTemplateNames()).toEqual(["Vooraankondiging last"]),
      );
    });

    it("saves the chosen catalog with the document type", async () => {
      const { component, storeCatalogMapping, user } = await setup();
      await waitFor(() =>
        expect(catalogPicker()).toHaveTextContent("Vergunningen"),
      );

      await chooseOption(user, catalogPicker(), "Handhaving");
      await chooseOption(user, informatieobjecttypePicker(), "Bijlage");
      component.saveEpistolaTemplatesMapping().subscribe();

      expect(storeCatalogMapping).toHaveBeenCalledWith(ZAAKTYPE_UUID, {
        catalogId: HANDHAVING.id,
        informatieObjectTypeUUID: BIJLAGE.uuid,
      });
    });
  });

  describe("given a zaaktype without a document type for its Epistola documents", () => {
    it("says the document type is required, and is valid once one is chosen", async () => {
      const { component, user } = await setup({
        catalogMapping: {
          catalogId: VERGUNNINGEN.id,
          informatieObjectTypeUUID: null,
        },
      });

      expect(await screen.findByText("verplicht")).toBeVisible();
      expect(component.isValid()).toBe(false);

      await chooseOption(user, informatieobjecttypePicker(), "Bijlage");

      expect(
        screen.getByRole("textbox", { name: "vertrouwelijkheidaanduiding" }),
      ).toHaveValue("vertrouwelijkheidaanduiding.OPENBAAR");
      expect(component.isValid()).toBe(true);
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
