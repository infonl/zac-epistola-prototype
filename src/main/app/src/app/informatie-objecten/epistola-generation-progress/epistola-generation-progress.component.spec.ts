/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */

import { TranslateModule } from "@ngx-translate/core";
import { render, screen, within } from "@testing-library/angular";
import { GeneratedType } from "../../shared/utils/generated-types";
import { EpistolaGenerationProgressComponent } from "./epistola-generation-progress.component";

describe(EpistolaGenerationProgressComponent.name, () => {
  async function setup(
    status?: GeneratedType<"EpistolaDocumentCreationStatus"> | null,
  ) {
    await render(EpistolaGenerationProgressComponent, {
      imports: [TranslateModule.forRoot()],
      inputs: { status },
    });
  }

  function steps() {
    return within(
      screen.getByRole("list", { name: "epistola.voortgang" }),
    ).getAllByRole("listitem");
  }

  function currentStep() {
    return steps().find((step) => step.getAttribute("aria-current") === "step");
  }

  it("shows the four steps of a generation in order", async () => {
    await setup();

    expect(steps().map((step) => step.textContent)).toEqual([
      expect.stringContaining("epistola.voortgang.voorbereiden"),
      expect.stringContaining("epistola.voortgang.in-wachtrij"),
      expect.stringContaining("epistola.voortgang.maken"),
      expect.stringContaining("epistola.voortgang.opslaan"),
    ]);
  });

  describe("given Epistola has not reported a status yet", () => {
    it("marks preparing as the current step, with nothing done yet", async () => {
      await setup(null);

      expect(currentStep()).toHaveTextContent(
        "epistola.voortgang.voorbereiden",
      );
      expect(currentStep()).toHaveTextContent("epistola.voortgang.bezig");
      expect(screen.queryByText("epistola.voortgang.klaar")).toBeNull();
    });

    it("says the document is being generated", async () => {
      await setup(null);

      expect(screen.getByRole("status")).toHaveTextContent(
        "msg.document.genereren.bezig",
      );
    });

    it("shows the bar at the start of the first step", async () => {
      await setup(null);

      expect(screen.getByRole("progressbar")).toHaveAttribute(
        "aria-valuenow",
        "12.5",
      );
    });
  });

  describe.each<{
    status: GeneratedType<"EpistolaDocumentCreationStatus">;
    step: string;
    done: number;
    message: string;
    percentage: string;
  }>([
    {
      status: "WAITING_IN_QUEUE",
      step: "epistola.voortgang.in-wachtrij",
      done: 1,
      message: "msg.document.genereren.in-wachtrij",
      percentage: "37.5",
    },
    {
      status: "RENDERING",
      step: "epistola.voortgang.maken",
      done: 2,
      message: "msg.document.genereren.wordt-gemaakt",
      percentage: "62.5",
    },
    {
      status: "STORING",
      step: "epistola.voortgang.opslaan",
      done: 3,
      message: "msg.document.genereren.opslaan",
      percentage: "87.5",
    },
  ])(
    "given Epistola reports $status",
    ({ status, step, done, message, percentage }) => {
      it(`marks ${step} as the current step and the ones before it as done`, async () => {
        await setup(status);

        expect(currentStep()).toHaveTextContent(step);
        expect(currentStep()).toHaveTextContent("epistola.voortgang.bezig");
        expect(screen.getAllByText("epistola.voortgang.klaar")).toHaveLength(
          done,
        );
      });

      it("says what is happening now", async () => {
        await setup(status);

        expect(screen.getByRole("status")).toHaveTextContent(message);
      });

      it("moves the bar along with the steps", async () => {
        await setup(status);

        expect(screen.getByRole("progressbar")).toHaveAttribute(
          "aria-valuenow",
          percentage,
        );
      });
    },
  );

  describe.each<{
    status: GeneratedType<"EpistolaDocumentCreationStatus">;
    step: string;
    message: string;
  }>([
    {
      status: "HELD_UP_IN_QUEUE",
      step: "epistola.voortgang.in-wachtrij",
      message: "msg.document.genereren.lang-in-wachtrij",
    },
    {
      status: "HELD_UP_IN_RENDERING",
      step: "epistola.voortgang.maken",
      message: "msg.document.genereren.duurt-lang",
    },
  ])("given Epistola reports $status", ({ status, step, message }) => {
    it("marks the current step as taking longer than usual", async () => {
      await setup(status);

      expect(currentStep()).toHaveTextContent(step);
      expect(currentStep()).toHaveTextContent("epistola.voortgang.vertraagd");
    });

    it("says that it takes longer than usual", async () => {
      await setup(status);

      expect(screen.getByRole("status")).toHaveTextContent(message);
    });
  });
});
