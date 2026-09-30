/*
 * SPDX-FileCopyrightText: 2021 Atos, 2025 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

export abstract class MenuItem {
  abstract readonly type: MenuItemType;
  abstract readonly title: string;
  abstract readonly icon?: string;
  activated = false;
  disabled = false;
  /** Translation key of the explanation shown on hover and focus, for example of why an item is disabled. */
  tooltip?: string;
}

export enum MenuItemType {
  HEADER = "HEADER",
  LINK = "LINK",
  HREF = "HREF",
  BUTTON = "BUTTON",
}
