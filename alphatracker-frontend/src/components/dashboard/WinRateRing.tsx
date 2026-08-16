import React from 'react';

interface WinRateRingProps {
  winRate: number;    // 0-100
  totalTrades: number;
}

// Geometry for the donut. The arc length of a circle is 2*PI*r, so setting
// strokeDasharray to "<portion> <circumference>" draws exactly that fraction of
// the ring and leaves the rest blank. That is what makes the arc track the
// percentage instead of being decorative.
const SIZE = 128;
const STROKE = 10;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

const WinRateRing: React.FC<WinRateRingProps> = ({ winRate, totalTrades }) => {
  // Guard the geometry against bad input: a NaN or out-of-range value would
  // produce an invalid dash array and silently render nothing.
  const safeRate = Number.isFinite(winRate) ? Math.min(100, Math.max(0, winRate)) : 0;
  const hasTrades = totalTrades > 0;

  const progress = (safeRate / 100) * CIRCUMFERENCE;

  // Grey until there is data, so an empty account does not read as 0% "losing".
  const arcColor = !hasTrades
    ? 'text-gray-200'
    : safeRate >= 50
    ? 'text-emerald-500'
    : 'text-red-500';

  return (
    <div className="relative" style={{ width: SIZE, height: SIZE }}>
      {/* -90deg rotation starts the arc at 12 o'clock rather than 3 o'clock. */}
      <svg width={SIZE} height={SIZE} className="-rotate-90">
        {/* Track */}
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          fill="none"
          stroke="currentColor"
          strokeWidth={STROKE}
          className="text-gray-100"
        />
        {/* Progress arc */}
        {hasTrades && (
          <circle
            cx={SIZE / 2}
            cy={SIZE / 2}
            r={RADIUS}
            fill="none"
            stroke="currentColor"
            strokeWidth={STROKE}
            strokeLinecap="round"
            strokeDasharray={`${progress} ${CIRCUMFERENCE}`}
            className={`${arcColor} transition-all duration-500 ease-out`}
          />
        )}
      </svg>

      {/* Centred label, positioned over the un-rotated box so the text stays upright. */}
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-2xl font-black text-gray-900">{safeRate}%</span>
      </div>
    </div>
  );
};

export default WinRateRing;
