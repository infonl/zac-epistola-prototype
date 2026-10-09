/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { fromPartial } from "../../../test-helpers";
import { ButtonMenuItem } from "../../shared/side-nav/menu-item/button-menu-item";
import { HeaderMenuItem } from "../../shared/side-nav/menu-item/header-menu-item";
import { GeneratedType } from "../../shared/utils/generated-types";
import { buildEpistolaTaskMenuItems } from "./epistola-task-menu.builder";

function createZaak({
  isEnabledGlobally = true,
  isEnabledForZaaktype = true,
  isProcesGestuurd = false,
  canCreerenDocument = true,
} = {}) {
  return fromPartial<GeneratedType<"RestZaak">>({
    uuid: "fakeZaakUuid",
    isProcesGestuurd,
    rechten: fromPartial({ canCreerenDocument }),
    zaaktype: fromPartial({
      zaakafhandelparameters: fromPartial({
        epistola: { isEnabledGlobally, isEnabledForZaaktype },
      }),
    }),
  });
}

const taskMenu = [new HeaderMenuItem("taak")];

describe(buildEpistolaTaskMenuItems.name, () => {
  it("offers document maken on a task of a zaak whose zaaktype has Epistola switched on, and opens the side action", () => {
    const openSideAction = jest.fn();

    const [documentMaken] = buildEpistolaTaskMenuItems(
      createZaak(),
      taskMenu,
      openSideAction,
    ) as ButtonMenuItem[];
    documentMaken.fn();

    expect(documentMaken.title).toBe("actie.document.maken");
    expect(documentMaken.disabled).toBe(false);
    expect(openSideAction).toHaveBeenCalled();
  });

  it("offers nothing when the zaaktype has Epistola switched off", () => {
    expect(
      buildEpistolaTaskMenuItems(
        createZaak({ isEnabledForZaaktype: false }),
        taskMenu,
        jest.fn(),
      ),
    ).toEqual([]);
  });

  it("offers nothing while Epistola is not the active provider, so SmartDocuments keeps its own action", () => {
    expect(
      buildEpistolaTaskMenuItems(
        createZaak({ isEnabledGlobally: false }),
        taskMenu,
        jest.fn(),
      ),
    ).toEqual([]);
  });

  it("offers nothing to a user who may not create documents in the zaak, as the backend would refuse it", () => {
    expect(
      buildEpistolaTaskMenuItems(
        createZaak({ canCreerenDocument: false }),
        taskMenu,
        jest.fn(),
      ),
    ).toEqual([]);
  });

  it("offers nothing while the zaak is still loading", () => {
    expect(buildEpistolaTaskMenuItems(undefined, taskMenu, jest.fn())).toEqual(
      [],
    );
  });

  it("adds no second document maken when the task view already offers one", () => {
    const menuWithDocumentMaken = [
      ...taskMenu,
      new ButtonMenuItem("actie.document.maken", jest.fn(), "note_add"),
    ];

    expect(
      buildEpistolaTaskMenuItems(
        createZaak(),
        menuWithDocumentMaken,
        jest.fn(),
      ),
    ).toEqual([]);
  });

  it("shows document maken disabled with the CMMN-only explanation on a task of a BPMN zaak", () => {
    const [documentMaken] = buildEpistolaTaskMenuItems(
      createZaak({ isProcesGestuurd: true, isEnabledForZaaktype: false }),
      taskMenu,
      jest.fn(),
    );

    expect(documentMaken.disabled).toBe(true);
    expect(documentMaken.tooltip).toBe(
      "msg.document.maken.epistola.alleen-cmmn",
    );
  });
});
