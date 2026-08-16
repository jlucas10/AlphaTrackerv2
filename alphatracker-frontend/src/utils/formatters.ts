// Shared display formatting.
// Kept out of the component files so Fast Refresh keeps working — a module that
// exports both a component and a helper breaks React's refresh boundary.

// Signed dollar amount, e.g. "+$68.16" / "-$412.50".
export function formatUsd(value: number): string {
  const sign = value >= 0 ? '+' : '-';
  return `${sign}$${Math.abs(value).toFixed(2)}`;
}
