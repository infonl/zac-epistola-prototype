/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { provideZonelessChangeDetection } from "@angular/core";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { provideRouter } from "@angular/router";
import { TranslateModule } from "@ngx-translate/core";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { UtilService } from "src/app/core/service/util.service";
import { fromPartial } from "src/test-helpers";
import { ButtonMenuItem } from "./menu-item/button-menu-item";
import { MenuItem } from "./menu-item/menu-item";
import { SideNavComponent } from "./side-nav.component";

describe(`${SideNavComponent.name} button menu items with an explanation`, () => {
  const user = userEvent.setup();

  async function setup(menu: MenuItem[]) {
    await render(SideNavComponent, {
      imports: [NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: UtilService,
          useValue: fromPartial<UtilService>({ setLoading: jest.fn() }),
        },
      ],
      inputs: { menu },
    });
  }

  function createButtonMenuItem(fn = jest.fn()) {
    return new ButtonMenuItem("actie.document.maken", fn, "note_add");
  }

  it("keeps a disabled item unclickable, and offers its explanation on a labelled, focusable group", async () => {
    const fn = jest.fn();
    const menuItem = createButtonMenuItem(fn);
    menuItem.disabled = true;
    menuItem.tooltip = "msg.document.maken.epistola.alleen-cmmn";
    await setup([menuItem]);

    const button = screen.getByRole("button", {
      name: "actie.document.maken",
    });
    await user.click(button);

    expect(button).toBeDisabled();
    expect(fn).not.toHaveBeenCalled();
    const group = screen.getByRole("group", { name: "actie.document.maken" });
    expect(group).toHaveAttribute("tabindex", "0");
    await waitFor(() =>
      expect(group).toHaveAccessibleDescription(
        "msg.document.maken.epistola.alleen-cmmn",
      ),
    );
  });

  it("renders an item without an explanation as a plain button, without a group around it", async () => {
    const fn = jest.fn();
    await setup([createButtonMenuItem(fn)]);

    await user.click(
      screen.getByRole("button", { name: "actie.document.maken" }),
    );

    expect(screen.queryByRole("group")).not.toBeInTheDocument();
    expect(fn).toHaveBeenCalledTimes(1);
  });
});
