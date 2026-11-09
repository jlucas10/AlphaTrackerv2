import type { ImportPreviewRow } from '../types/Import';
import type { PlanAnswers } from '../api/imports';

// The trader's "did you follow your plan" answer per imported trade, keyed by the broker's trade id:
// true = followed, false = did not, null/absent = not answered (saved as unspecified, never assumed).
export type PlanChoices = Record<string, boolean | null>;

// Rows that will actually create a trade: the only ones a plan answer means anything for.
export function isImportable(row: ImportPreviewRow): boolean {
  return row.status === 'NEW' && row.externalId !== null;
}

// "Set all": the same answer for every importable row (null clears them all).
export function answerAll(rows: ImportPreviewRow[], value: boolean | null): PlanChoices {
  const choices: PlanChoices = {};
  rows.filter(isImportable).forEach((row) => {
    choices[row.externalId as string] = value;
  });
  return choices;
}

// What goes to the server. Built from the importable rows only, so a stale answer for a row that turned out
// to be a duplicate or an error is never sent, and unanswered rows are simply left out.
export function toPlanAnswers(rows: ImportPreviewRow[], choices: PlanChoices): PlanAnswers {
  const answers: PlanAnswers = { yes: [], no: [] };
  rows.filter(isImportable).forEach((row) => {
    const choice = choices[row.externalId as string];
    if (choice === true) answers.yes.push(row.externalId as string);
    if (choice === false) answers.no.push(row.externalId as string);
  });
  return answers;
}

// How many importable rows the trader has not answered yet (shown as a hint, never a blocker).
export function unansweredCount(rows: ImportPreviewRow[], choices: PlanChoices): number {
  return rows.filter(isImportable).filter((row) => {
    const choice = choices[row.externalId as string];
    return choice !== true && choice !== false;
  }).length;
}
