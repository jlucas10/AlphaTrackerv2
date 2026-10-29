// Shared display formatting.
// Kept out of the component files so Fast Refresh keeps working — a module that
// exports both a component and a helper breaks React's refresh boundary.

// The letter shown in an avatar circle: first character of the name, upper-cased. Array.from splits by
// whole characters, so a name starting with an emoji or another multi-unit character isn't cut in half.
// "?" when there is no name yet.
export function initialOf(name: string | null | undefined): string {
  const first = Array.from((name ?? '').trim())[0];
  return first ? first.toUpperCase() : '?';
}

// Signed dollar amount, e.g. "+$68.16" / "-$412.50". Exactly 0 is "$0.00" with
// no sign: a neutral result is neither a gain nor a loss.
export function formatUsd(value: number): string {
  if (value === 0) return '$0.00';
  const sign = value > 0 ? '+' : '-';
  return `${sign}$${Math.abs(value).toFixed(2)}`;
}
