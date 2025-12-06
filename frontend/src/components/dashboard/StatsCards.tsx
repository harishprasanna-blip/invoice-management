import { TrendingUp, AlertTriangle, CheckCircle, Clock } from 'lucide-react'
import { DashboardStats } from '@/types/domain'
import { cn } from '@/lib/utils'

interface StatsCardsProps {
  stats?: DashboardStats
  isLoading: boolean
}

export function StatsCards({ stats, isLoading }: StatsCardsProps) {
  const cards = [
    {
      title: 'Pending',
      value: stats?.pendingInvoices || 0,
      icon: Clock,
      color: 'text-warning-600 bg-warning-100 dark:bg-warning-900/30',
      trend: '+12%',
      trendUp: true,
    },
    {
      title: 'Validated',
      value: stats?.validatedInvoices || 0,
      icon: CheckCircle,
      color: 'text-success-600 bg-success-100 dark:bg-success-900/30',
      trend: '+8%',
      trendUp: true,
    },
    {
      title: 'Exceptions',
      value: stats?.exceptionInvoices || 0,
      icon: AlertTriangle,
      color: 'text-error-600 bg-error-100 dark:bg-error-900/30',
      trend: '-5%',
      trendUp: false,
    },
    {
      title: 'Automation Rate',
      value: stats?.automationRate || '0%',
      icon: TrendingUp,
      color: 'text-primary-600 bg-primary-100 dark:bg-primary-900/30',
      trend: '+15%',
      trendUp: true,
    },
  ]

  if (isLoading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="glass animate-pulse rounded-xl p-6">
            <div className="h-12 w-12 rounded-lg bg-slate-200 dark:bg-slate-700"></div>
            <div className="mt-4 h-4 w-20 rounded bg-slate-200 dark:bg-slate-700"></div>
            <div className="mt-2 h-8 w-16 rounded bg-slate-200 dark:bg-slate-700"></div>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <div key={card.title} className="glass rounded-xl p-6 transition-all hover:shadow-lg">
          <div className="flex items-center justify-between">
            <div className={cn('flex h-12 w-12 items-center justify-center rounded-lg', card.color)}>
              <card.icon className="h-6 w-6" />
            </div>
            <div
              className={cn(
                'text-sm font-medium',
                card.trendUp ? 'text-success-600' : 'text-error-600'
              )}
            >
              {card.trend}
            </div>
          </div>
          <p className="mt-4 text-sm font-medium text-slate-600 dark:text-slate-400">
            {card.title}
          </p>
          <p className="mt-1 text-2xl font-bold text-slate-900 dark:text-white">{card.value}</p>
        </div>
      ))}
    </div>
  )
}
