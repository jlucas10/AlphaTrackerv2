import React, { useMemo } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { Trade } from '../../types/Trade';
import { computeEquityCurve } from '../../utils/pnlAggregations';

interface EquityCurveChartProps {
  trades: Trade[];
}

const tooltipFormatter = (value: unknown) =>
  [`$${Number(value).toFixed(2)}`, 'Cumulative P/L'] as [string, string];

const EquityCurveChart: React.FC<EquityCurveChartProps> = ({ trades }) => {
  const data = useMemo(() => computeEquityCurve(trades), [trades]);

  if (data.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center border-b border-dashed border-gray-200 mb-6">
        <span className="text-xs text-gray-300 font-medium">No performance data compiled</span>
      </div>
    );
  }

  return (
    <div className="flex-1">
      <ResponsiveContainer width="100%" height={140}>
        <AreaChart data={data} margin={{ top: 5, right: 5, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="date" tick={{ fontSize: 10 }} />
          <YAxis tick={{ fontSize: 10 }} width={40} />
          <Tooltip formatter={tooltipFormatter} />
          <Area
            type="monotone"
            dataKey="cumulativePnl"
            stroke="#10b981"
            fill="#10b981"
            fillOpacity={0.15}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
};

export default EquityCurveChart;
