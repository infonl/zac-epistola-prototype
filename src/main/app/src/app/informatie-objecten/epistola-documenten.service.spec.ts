/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse, provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { fromPartial } from "src/test-helpers";
import { UtilService } from "../core/service/util.service";
import { FoutAfhandelingService } from "../fout-afhandeling/fout-afhandeling.service";
import { QUERY_CLIENT } from "../shared/http/query-client";
import { EpistolaDocumentenService } from "./epistola-documenten.service";

describe(EpistolaDocumentenService.name, () => {
  const foutAfhandelen = jest.fn().mockReturnValue(of());
  let epistolaDocumentenService: EpistolaDocumentenService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: FoutAfhandelingService,
          useValue: fromPartial<FoutAfhandelingService>({ foutAfhandelen }),
        },
        { provide: UtilService, useValue: fromPartial<UtilService>({}) },
      ],
    });
    epistolaDocumentenService = TestBed.inject(EpistolaDocumentenService);
  });

  describe("readEpistolaDocumentCreationStatusQuery", () => {
    it("does not report a poll that fails, so a poll cannot open an error dialog every second", async () => {
      const error = new HttpErrorResponse({ status: 500 });

      await expect(
        TestBed.inject(QUERY_CLIENT).fetchQuery({
          ...epistolaDocumentenService.readEpistolaDocumentCreationStatusQuery(
            "fakeZaakUuid",
          ),
          queryFn: () => Promise.reject(error),
        }),
      ).rejects.toBe(error);

      expect(foutAfhandelen).not.toHaveBeenCalled();
    });

    it("polls every second until a poll fails, and then stops", () => {
      const { refetchInterval } =
        epistolaDocumentenService.readEpistolaDocumentCreationStatusQuery(
          "fakeZaakUuid",
        );

      expect(refetchInterval({ state: { status: "success" } })).toBe(1000);
      expect(refetchInterval({ state: { status: "error" } })).toBe(false);
    });
  });
});
