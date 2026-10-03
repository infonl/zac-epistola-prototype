/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { HttpErrorResponse } from "@angular/common/http";

function readText(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

/**
 * A request that asks for a Blob gets its failure as a Blob too, so the reason the server gave is unreadable to
 * the error handler, which expects the JSON that ZAC answers with. A body that is not JSON is left as it was.
 */
export async function parseBlobError(
  error: HttpErrorResponse,
): Promise<HttpErrorResponse> {
  if (!(error.error instanceof Blob)) return error;
  try {
    return new HttpErrorResponse({
      error: JSON.parse(await readText(error.error)),
      headers: error.headers,
      status: error.status,
      statusText: error.statusText,
      url: error.url ?? undefined,
    });
  } catch {
    return error;
  }
}
