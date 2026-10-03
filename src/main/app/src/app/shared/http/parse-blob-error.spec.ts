/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse } from "@angular/common/http";
import { parseBlobError } from "./parse-blob-error";

describe(parseBlobError.name, () => {
  it("makes the JSON ZAC answered with readable, with the rest of the response as it was", async () => {
    const error = new HttpErrorResponse({
      error: new Blob(['{"message":"fakeMessage","exception":"fakeDetail"}']),
      status: 500,
      statusText: "fakeStatusText",
      url: "https://example.com/rest/fake",
    });

    const parsedError = await parseBlobError(error);

    expect(parsedError.error).toEqual({
      message: "fakeMessage",
      exception: "fakeDetail",
    });
    expect(parsedError.status).toBe(500);
    expect(parsedError.statusText).toBe("fakeStatusText");
    expect(parsedError.url).toBe("https://example.com/rest/fake");
  });

  it("leaves a failure whose body is not JSON as it was", async () => {
    const error = new HttpErrorResponse({
      error: new Blob(["fakeNotJson"]),
      status: 502,
    });

    expect(await parseBlobError(error)).toBe(error);
  });

  it("leaves a failure that carries no Blob as it was", async () => {
    const error = new HttpErrorResponse({
      error: { message: "fakeMessage" },
      status: 400,
    });

    expect(await parseBlobError(error)).toBe(error);
  });
});
