/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { inject, Injectable } from "@angular/core";
import { QueryClient } from "@tanstack/angular-query-experimental";
import { tap } from "rxjs/operators";
import { PostBody } from "../shared/http/http-client";
import { ZacHttpClient } from "../shared/http/zac-http-client";
import { ZacQueryClient } from "../shared/http/zac-query-client";

@Injectable({ providedIn: "root" })
export class EpistolaTemplatesService {
  private readonly zacHttpClient = inject(ZacHttpClient);
  private readonly zacQueryClient = inject(ZacQueryClient);
  private readonly queryClient = inject(QueryClient);

  listCatalogsQuery() {
    return this.zacQueryClient.GET(
      "/rest/zaakafhandelparameters/epistola-catalogs",
    );
  }

  /** Each template with the languages and variants Epistola gives it, which are absent while Epistola cannot be asked. */
  listCatalogTemplatesQuery(catalogId: string) {
    return this.zacQueryClient.GET(
      "/rest/zaakafhandelparameters/epistola-catalogs/{catalogId}/templates",
      { path: { catalogId } },
    );
  }

  getCatalogMappingQuery(zaaktypeUuid: string) {
    return this.zacQueryClient.GET(
      "/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-catalog-mapping",
      { path: { zaaktypeUuid } },
    );
  }

  /** The templates a zaak of this zaaktype can generate a document from. */
  listOfferedTemplatesQuery(zaaktypeUuid: string) {
    return this.zacQueryClient.GET(
      "/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-templates",
      { path: { zaaktypeUuid } },
    );
  }

  storeCatalogMapping(
    zaaktypeUuid: string,
    catalogMapping: PostBody<"/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-catalog-mapping">,
  ) {
    return this.zacHttpClient
      .POST(
        "/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-catalog-mapping",
        catalogMapping,
        { path: { zaaktypeUuid } },
      )
      .pipe(
        tap(() => {
          void this.queryClient.invalidateQueries({
            queryKey: this.getCatalogMappingQuery(zaaktypeUuid).queryKey,
          });
          void this.queryClient.invalidateQueries({
            queryKey: this.listOfferedTemplatesQuery(zaaktypeUuid).queryKey,
          });
        }),
      );
  }
}
