/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse } from "@angular/common/http";
import { inject, Injectable } from "@angular/core";
import { mutationOptions } from "@tanstack/angular-query-experimental";
import { lastValueFrom, map, Observable } from "rxjs";
import { FoutAfhandelingService } from "../fout-afhandeling/fout-afhandeling.service";
import {
  HttpClient,
  PathParameters,
  PostBody,
} from "../shared/http/http-client";
import { parseBlobError } from "../shared/http/parse-blob-error";
import { SKIP_GLOBAL_ERROR_HANDLING } from "../shared/http/query-client";
import { ZacHttpClient } from "../shared/http/zac-http-client";
import { StaleTimes, ZacQueryClient } from "../shared/http/zac-query-client";
import { InformatieObjectenService } from "./informatie-objecten.service";

const EPISTOLA_STATUS_POLL_INTERVAL = 1000;
const EPISTOLA_PREVIEW_PATH =
  "/rest/document-creation/epistola/preview-document";

@Injectable({
  providedIn: "root",
})
export class EpistolaDocumentenService {
  private readonly zacHttpClient = inject(ZacHttpClient);
  private readonly zacQueryClient = inject(ZacQueryClient);
  private readonly httpClient = inject(HttpClient);
  private readonly foutAfhandelingService = inject(FoutAfhandelingService);
  private readonly informatieObjectenService = inject(
    InformatieObjectenService,
  );

  createEpistolaDocumentMutation() {
    return this.zacQueryClient.POST(
      "/rest/document-creation/epistola/create-document",
    );
  }

  /**
   * Epistola renders the preview at once and ZAC keeps nothing, so this changes no zaak. The mutation
   * is only used for its pending state. Bypasses the {@link ZacQueryClient} because a failure of a
   * request for a Blob must be parsed before the error handler can read the reason.
   */
  previewEpistolaDocumentMutation() {
    return mutationOptions<
      Blob,
      HttpErrorResponse,
      PostBody<typeof EPISTOLA_PREVIEW_PATH>,
      void
    >({
      mutationKey: [EPISTOLA_PREVIEW_PATH],
      mutationFn: async (body) => {
        try {
          return await lastValueFrom(
            this.httpClient.POST(EPISTOLA_PREVIEW_PATH, body, {
              responseType: "blob",
            } as PathParameters<typeof EPISTOLA_PREVIEW_PATH, "post"> &
              Record<string, unknown>) as unknown as Observable<Blob>,
          );
        } catch (error) {
          throw error instanceof HttpErrorResponse
            ? await parseBlobError(error)
            : error;
        }
      },
      onError: (error) => this.foutAfhandelingService.foutAfhandelen(error),
    });
  }

  /**
   * Polled while the request that generates the document waits. Kept for no time once nothing polls it, so the
   * next generation does not start from the last one's status.
   *
   * The status only adds detail to a request that reports its own outcome, so a failed poll is not reported,
   * and polling stops at the first failure rather than repeating it every second.
   */
  readEpistolaDocumentCreationStatusQuery(zaakUuid: string) {
    return {
      ...this.zacQueryClient.GET(
        "/rest/document-creation/epistola/create-document/{zaakUuid}/status",
        { path: { zaakUuid } },
      ),
      refetchInterval: (query: { state: { status: string } }) =>
        query.state.status === "error" ? false : EPISTOLA_STATUS_POLL_INTERVAL,
      meta: SKIP_GLOBAL_ERROR_HANDLING,
      staleTime: StaleTimes.Instant,
      gcTime: StaleTimes.Instant,
      retry: false,
    };
  }

  /**
   * Without an answer the behandelaar just has no variant to choose, and ZAC still picks one by the zaak's
   * communicatiekanaal, so a failure is not reported.
   *
   * The suggested variant follows the zaak's communicatiekanaal, which can be edited while this page stays open, so an
   * answer is never kept for a next time.
   */
  readEpistolaVariantenQuery(zaakUuid: string, templateId: string) {
    return {
      ...this.zacQueryClient.GET(
        "/rest/document-creation/epistola/create-document/{zaakUuid}/template/{templateId}/varianten",
        { path: { zaakUuid, templateId } },
      ),
      meta: SKIP_GLOBAL_ERROR_HANDLING,
      staleTime: StaleTimes.Instant,
      gcTime: StaleTimes.Instant,
      retry: false,
    };
  }

  readEpistolaDocument(uuid: string) {
    return this.zacHttpClient.GET("/rest/epistola-documents/{uuid}", {
      path: { uuid },
    });
  }

  /**
   * Returns once the new version is stored, as creating the document does: Epistola renders it while the request
   * waits.
   */
  createEpistolaDocumentVersion(uuid: string): Observable<void> {
    return this.zacHttpClient
      .POST("/rest/epistola-documents/{uuid}/versions", undefined as never, {
        path: { uuid },
      })
      .pipe(map(() => void 0));
  }

  /** Covers both lists of the zaak: with and without the documents of its linked zaken. */
  listEnkelvoudigInformatieobjectenQueryKeyOfZaak(zaakUUID: string) {
    return this.informatieObjectenService
      .listEnkelvoudigInformatieobjectenQuery({ zaakUUID })
      .queryKey.slice(0, 2);
  }
}
