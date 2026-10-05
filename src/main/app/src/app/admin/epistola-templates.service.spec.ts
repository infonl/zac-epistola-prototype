/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import {
  provideHttpClient,
  withInterceptorsFromDi,
} from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { TranslateModule } from "@ngx-translate/core";
import { provideQueryClient } from "@tanstack/angular-query-experimental";
import { firstValueFrom } from "rxjs";
import { testQueryClient } from "../../../setupJest";
import { EpistolaTemplatesService } from "./epistola-templates.service";

const ZAAKTYPE_UUID = "fake-zaaktype-uuid";

describe(EpistolaTemplatesService.name, () => {
  let epistolaTemplatesService: EpistolaTemplatesService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideQueryClient(testQueryClient),
      ],
    });
    epistolaTemplatesService = TestBed.inject(EpistolaTemplatesService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  it("reads the templates a zaaktype offers, without asking for a template group", async () => {
    const offeredTemplates = testQueryClient.fetchQuery(
      epistolaTemplatesService.listOfferedTemplatesQuery(ZAAKTYPE_UUID),
    );

    httpTestingController
      .expectOne(
        `/rest/zaakafhandelparameters/${ZAAKTYPE_UUID}/epistola-templates`,
      )
      .flush([
        {
          id: "fake-template",
          name: "Standaardbrief",
          informatieObjectTypeUUID: "fake-informatieobjecttype-uuid",
        },
      ]);

    expect(await offeredTemplates).toEqual([
      {
        id: "fake-template",
        name: "Standaardbrief",
        informatieObjectTypeUUID: "fake-informatieobjecttype-uuid",
      },
    ]);
  });

  it("reads the templates of the catalog the beheerder is looking at", async () => {
    const catalogTemplates = testQueryClient.fetchQuery(
      epistolaTemplatesService.listCatalogTemplatesQuery("fake-catalog"),
    );

    httpTestingController
      .expectOne(
        "/rest/zaakafhandelparameters/epistola-catalogs/fake-catalog/templates",
      )
      .flush([{ id: "fake-template", name: "Standaardbrief" }]);

    expect(await catalogTemplates).toEqual([
      { id: "fake-template", name: "Standaardbrief" },
    ]);
  });

  it("stores the catalog and document type, and refreshes what the admin card and Document maken read of them", async () => {
    const invalidateQueries = jest
      .spyOn(testQueryClient, "invalidateQueries")
      .mockResolvedValue(undefined);

    const stored = firstValueFrom(
      epistolaTemplatesService.storeCatalogMapping(ZAAKTYPE_UUID, {
        catalogId: "fake-catalog",
        informatieObjectTypeUUID: "fake-informatieobjecttype-uuid",
      }),
    );
    const postedRequest = httpTestingController.expectOne(
      `/rest/zaakafhandelparameters/${ZAAKTYPE_UUID}/epistola-catalog-mapping`,
    );
    expect(postedRequest.request.method).toBe("POST");
    expect(postedRequest.request.body).toEqual({
      catalogId: "fake-catalog",
      informatieObjectTypeUUID: "fake-informatieobjecttype-uuid",
    });
    postedRequest.flush(null);
    await stored;

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey:
        epistolaTemplatesService.getCatalogMappingQuery(ZAAKTYPE_UUID).queryKey,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey:
        epistolaTemplatesService.listOfferedTemplatesQuery(ZAAKTYPE_UUID)
          .queryKey,
    });
    invalidateQueries.mockRestore();
  });
});
