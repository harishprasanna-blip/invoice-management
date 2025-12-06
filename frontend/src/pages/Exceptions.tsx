import { useOpenExceptions } from '@/hooks/api/use-exceptions'
import { ExceptionCard } from '@/components/dashboard/ExceptionCard'
import { AlertTriangle } from 'lucide-react'

export function Exceptions() {
  const { data: exceptions, isLoading } = useOpenExceptions()

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="spinner h-8 w-8"></div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
            Exception Management
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            AI-powered resolution recommendations with human-in-the-loop
          </p>
        </div>
      </div>

      {/* Exception List */}
      <div className="glass rounded-xl p-6">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
            Open Cases ({exceptions?.length || 0})
          </h2>
        </div>

        {exceptions && exceptions.length > 0 ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {exceptions.map((exception) => (
              <ExceptionCard key={exception.caseId} exception={exception} />
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-12">
            <AlertTriangle className="h-16 w-16 text-slate-300 dark:text-slate-700" />
            <p className="mt-4 text-lg font-medium text-slate-900 dark:text-white">
              No open exceptions
            </p>
            <p className="mt-1 text-sm text-slate-500">
              All invoices are processing smoothly!
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
