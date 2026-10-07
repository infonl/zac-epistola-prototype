/*
 * SPDX-FileCopyrightText: 2021 Atos, 2025 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { DOCUMENT } from "@angular/common";
import { Component, inject } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { MatSidenavModule } from "@angular/material/sidenav";
import { Title } from "@angular/platform-browser";
import { RouterOutlet } from "@angular/router";
import { TranslateService } from "@ngx-translate/core";
import { QueryClient } from "@tanstack/angular-query-experimental";
import { LoadingComponent } from "./core/loading/loading.component";
import { ToolbarComponent } from "./core/toolbar/toolbar.component";
import { IdentityService } from "./identity/identity.service";
import { ZoekComponent } from "./zoeken/zoek/zoek.component";

@Component({
  selector: "zac-root",
  templateUrl: "./app.component.html",
  styleUrls: ["./app.component.less"],
  imports: [
    MatSidenavModule,
    ToolbarComponent,
    ZoekComponent,
    LoadingComponent,
    RouterOutlet,
  ],
})
export class AppComponent {
  private readonly queryClient = inject(QueryClient);
  private readonly identityService = inject(IdentityService);
  private readonly translateService = inject(TranslateService);
  private readonly document = inject(DOCUMENT);
  private readonly titleService = inject(Title);

  constructor() {
    this.titleService.setTitle("Zaakafhandelcomponent");
    this.translateService.addLangs(["nl", "en"]);
    this.translateService.setFallbackLang("nl");
    this.translateService.onLangChange
      .pipe(takeUntilDestroyed())
      .subscribe(({ lang }) => this.setPageLanguage(lang));
    const browserLanguage = this.translateService.getBrowserLang();
    this.translateService.use(
      browserLanguage?.match(/nl|en/) ? browserLanguage : "nl",
    );
    this.setPageLanguage(this.translateService.getCurrentLang());

    void this.queryClient.removeQueries({
      queryKey: this.identityService.readLoggedInUser().queryKey,
    });
  }

  /** A language that was set before this component existed raises no change, so the current one is applied too. */
  private setPageLanguage(lang?: string) {
    if (lang) this.document.documentElement.lang = lang;
  }
}
