/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { ButtonMenuItem } from "../../shared/side-nav/menu-item/button-menu-item";
import { MenuItem } from "../../shared/side-nav/menu-item/menu-item";
import { GeneratedType } from "../../shared/utils/generated-types";

const DOCUMENT_MAKEN = "actie.document.maken";

/**
 * Epistola's Document maken on a task, kept out of the task view, which is ZAC's own, so that the prototype stays
 * apart from the upstream code it is kept in sync with. The task view only offers it for SmartDocuments.
 */
export function buildEpistolaTaskMenuItems(
  zaak: GeneratedType<"RestZaak"> | undefined,
  menu: MenuItem[],
  openSideAction: () => void,
): MenuItem[] {
  const epistola = zaak?.zaaktype.zaakafhandelparameters?.epistola;
  if (
    !epistola?.isEnabledGlobally ||
    !zaak?.rechten.canCreerenDocument ||
    menu.some((menuItem) => menuItem.title === DOCUMENT_MAKEN)
  ) {
    return [];
  }

  const documentMaken = new ButtonMenuItem(
    DOCUMENT_MAKEN,
    openSideAction,
    "note_add",
  );
  if (zaak.isProcesGestuurd) {
    documentMaken.disabled = true;
    documentMaken.tooltip = "msg.document.maken.epistola.alleen-cmmn";
    return [documentMaken];
  }
  return epistola.isEnabledForZaaktype ? [documentMaken] : [];
}
