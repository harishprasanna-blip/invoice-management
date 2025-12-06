import { ExceptionCase } from '@/types/domain'
import { AlertTriangle, User, Clock } from 'lucide-react'
import { formatRelativeTime, getStatusColor, getPriorityColor, getTimeRemaining } from '@/lib/utils'
import { cn } from '@/lib/utils'

interface ExceptionCardProps {
  exception: ExceptionCase
}

export function ExceptionCard({ exception }: ExceptionCardProps) {
  const timeRemaining = getTimeRemaining(exception.slaDeadline)

  return (
    <div className="group rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-800">
      {/* Header */}
      <div className="mb-3 flex items-start justify-between">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-error-100 dark:bg-error-900/30">
            <AlertTriangle className="h-4 w-4 text-error-600 dark:text-error-400" />
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="truncate text-sm font-semibold text-slate-900 dark:text-white">
              {exception.title}
            </p>
            <p className="text-xs text-slate-500">{exception.exceptionType}</p>
          </div>
        </div>
        <span className={cn('status-badge text-xs', getStatusColor(exception.severity))}>
          {exception.severity}
        </span>
      </div>

      {/* Description */}
      <p className="mb-3 line-clamp-2 text-xs text-slate-600 dark:text-slate-400">
        {exception.description}
      </p>

      {/* Details */}
      <div className="space-y-2">
        {exception.assignedTo && (
          <div className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-400">
            <User className="h-3.5 w-3.5" />
            <span>{exception.assignedTo}</span>
          </div>
        )}
        <div className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-400">
          <Clock className="h-3.5 w-3.5" />
          <span>{formatRelativeTime(exception.createdAt)}</span>
        </div>
      </div>

      {/* Priority & SLA */}
      <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3 dark:border-slate-700">
        <div className="flex items-center gap-1 text-xs">
          <span className="text-slate-500">Priority:</span>
          <span className={cn('font-semibold', getPriorityColor(exception.priorityScore))}>
            {exception.priorityScore}
          </span>
        </div>
        <div
          className={cn(
            'text-xs font-medium',
            timeRemaining.isOverdue ? 'text-error-600' : 'text-slate-500'
          )}
        >
          {timeRemaining.formatted}
        </div>
      </div>

      {/* ML Recommendations Badge */}
      {exception.mlRecommendations.length > 0 && (
        <div className="mt-2 flex items-center gap-1 text-xs">
          <div className="flex h-5 items-center rounded-full bg-primary-100 px-2 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
            <span className="font-medium">AI: {exception.mlRecommendations.length} suggestions</span>
          </div>
        </div>
      )}
    </div>
  )
}
