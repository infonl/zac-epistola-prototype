/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

const EPISTOLA_VARIANT_LABELS: Record<string, string> = {
  post: "epistola.variant.post",
  digitaal: "epistola.variant.digitaal",
};

/** The translation key that names a variant by its kanaal, or the kanaal itself when ZAC has no name for it. */
export const epistolaVariantLabel = (variant: string) =>
  EPISTOLA_VARIANT_LABELS[variant] ?? variant;
