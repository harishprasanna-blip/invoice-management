import { useInvoices } from '@/hooks/api/use-invoices'
import { useOpenExceptions } from '@/hooks/api/use-exceptions'
import { KanbanColumn } from './KanbanColumn'
import { IngestionStatus, ExceptionStatus } from '@/types/domain'

export function KanbanBoard() {
  const { data: invoices, isLoading: invoicesLoading } = useInvoices()
  const { data: exceptions, isLoading: exceptionsLoading } = useOpenExceptions()

  const isLoading = invoicesLoading || exceptionsLoading

  // Organize invoices by status
  const pendingInvoices = invoices?.filter(
    (inv) =>
      inv.ingestionStatus === IngestionStatus.RECEIVED ||
      inv.ingestionStatus === IngestionStatus.EXTRACTING ||
      inv.ingestionStatus === IngestionStatus.EXTRACTED
  )

  const validatedInvoices = invoices?.filter(
    (inv) => inv.ingestionStatus === IngestionStatus.VALIDATION_PENDING
  )

  const exceptionInvoices = exceptions?.filter(
    (exc) => exc.status === ExceptionStatus.OPEN || exc.status === ExceptionStatus.IN_PROGRESS
  )

  const paidInvoices = invoices?.filter((inv) => inv.ingestionStatus === 'PAID' as IngestionStatus)

  const columns = [
    {
      id: 'ingestion',
      title: 'Ingestion',
      count: pendingInvoices?.length || 0,
      color: 'border-primary-500',
      items: pendingInvoices || [],
    },
    {
      id: 'validation',
      title: 'Validation',
      count: validatedInvoices?.length || 0,
      color: 'border-warning-500',
      items: validatedInvoices || [],
    },
    {
      id: 'exceptions',
      title: 'Exceptions',
      count: exceptionInvoices?.length || 0,
      color: 'border-error-500',
      items: exceptionInvoices || [],
    },
    {
      id: 'payment',
      title: 'Payment',
      count: paidInvoices?.length || 0,
      color: 'border-success-500',
      items: paidInvoices || [],
    },
  ]

  if (isLoading) {
    return (
      <div className="grid gap-4 lg:grid-cols-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="animate-pulse rounded-lg border-2 border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800">
            <div className="mb-4 h-6 w-32 rounded bg-slate-200 dark:bg-slate-700"></div>
            <div className="space-y-3">
              {[...Array(3)].map((_, j) => (
                <div key={j} className="h-24 rounded-lg bg-slate-200 dark:bg-slate-700"></div>
              ))}
            </div>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="grid gap-4 lg:grid-cols-4">
      {columns.map((column) => (
        <KanbanColumn
          key={column.id}
          title={column.title}
          count={column.count}
          color={column.color}
          items={column.items}
        />
      ))}
    </div>
  )
}
