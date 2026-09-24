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

  listTemplatesQuery() {
    return this.zacQueryClient.GET(
      "/rest/zaakafhandelparameters/epistola-templates",
    );
  }

  getTemplatesMappingQuery(zaaktypeUuid: string) {
    return this.zacQueryClient.GET(
      "/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-templates-mapping",
      { path: { zaaktypeUuid } },
    );
  }

  storeTemplatesMapping(
    zaaktypeUuid: string,
    templateGroups: PostBody<"/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-templates-mapping">,
  ) {
    return this.zacHttpClient
      .POST(
        "/rest/zaakafhandelparameters/{zaaktypeUuid}/epistola-templates-mapping",
        templateGroups,
        { path: { zaaktypeUuid } },
      )
      .pipe(
        tap(() =>
          this.queryClient.invalidateQueries({
            queryKey: this.getTemplatesMappingQuery(zaaktypeUuid).queryKey,
          }),
        ),
      );
  }
}
