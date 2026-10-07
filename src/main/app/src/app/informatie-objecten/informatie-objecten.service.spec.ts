/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse, provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { fromPartial } from "src/test-helpers";
import { UtilService } from "../core/service/util.service";
import { FoutAfhandelingService } from "../fout-afhandeling/fout-afhandeling.service";
import { QUERY_CLIENT } from "../shared/http/query-client";
import { GeneratedType } from "../shared/utils/generated-types";
import { InformatieObjectenService } from "./informatie-objecten.service";

describe(InformatieObjectenService.name, () => {
  const foutAfhandelen = jest.fn().mockReturnValue(of());
  let informatieObjectenService: InformatieObjectenService;

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
    informatieObjectenService = TestBed.inject(InformatieObjectenService);
  });

  describe("readEpistolaKanalenQuery", () => {
    const KANALEN_URL =
      "/rest/document-creation/epistola/create-document/fakeZaakUuid/template/fake-template/kanalen";
    const emailKanalen: GeneratedType<"RestEpistolaKanalen"> = {
      kanalen: ["post", "digitaal"],
      voorgesteldKanaal: "digitaal",
      communicatiekanaal: "E-mail",
    };
    const balieKanalen: GeneratedType<"RestEpistolaKanalen"> = {
      kanalen: ["post", "digitaal"],
      voorgesteldKanaal: "post",
      communicatiekanaal: "Balie",
    };

    it("asks again every time, because the suggestion follows the zaak's communicatiekanaal, which can be edited in between", async () => {
      const queryClient = TestBed.inject(QUERY_CLIENT);
      const httpTestingController = TestBed.inject(HttpTestingController);
      const read = () =>
        queryClient.query(
          informatieObjectenService.readEpistolaKanalenQuery(
            "fakeZaakUuid",
            "fake-template",
          ),
        );

      const first = read();
      httpTestingController.expectOne(KANALEN_URL).flush(emailKanalen);
      expect(await first).toEqual(emailKanalen);

      const second = read();
      httpTestingController.expectOne(KANALEN_URL).flush(balieKanalen);
      expect(await second).toEqual(balieKanalen);
    });
  });

  describe("readEpistolaDocumentCreationStatusQuery", () => {
    it("does not report a poll that fails, so a poll cannot open an error dialog every second", async () => {
      const error = new HttpErrorResponse({ status: 500 });

      await expect(
        TestBed.inject(QUERY_CLIENT).fetchQuery({
          ...informatieObjectenService.readEpistolaDocumentCreationStatusQuery(
            "fakeZaakUuid",
          ),
          queryFn: () => Promise.reject(error),
        }),
      ).rejects.toBe(error);

      expect(foutAfhandelen).not.toHaveBeenCalled();
    });

    it("polls every second until a poll fails, and then stops", () => {
      const { refetchInterval } =
        informatieObjectenService.readEpistolaDocumentCreationStatusQuery(
          "fakeZaakUuid",
        );

      expect(refetchInterval({ state: { status: "success" } })).toBe(1000);
      expect(refetchInterval({ state: { status: "error" } })).toBe(false);
    });
  });
});
