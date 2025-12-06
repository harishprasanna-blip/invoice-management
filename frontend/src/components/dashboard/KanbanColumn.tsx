import { useNavigate } from 'react-router-dom'
import { Invoice, ExceptionCase } from '@/types/domain'
import { InvoiceCard } from './InvoiceCard'
import { ExceptionCard } from './ExceptionCard'
import { cn } from '@/lib/utils'

interface KanbanColumnProps {
  title: string
  count: number
  color: string
  items: (Invoice | ExceptionCase)[]
}

export function KanbanColumn({ title, count, color, items }: KanbanColumnProps) {
  const navigate = useNavigate()

  const handleItemClick = (item: Invoice | ExceptionCase) => {
    if ('invoiceId' in item) {
      navigate(`/invoices/${item.invoiceId}`)
    } else {
      navigate(`/exceptions/${item.caseId}`)
    }
  }

  return (
    <div className={cn('flex flex-col rounded-lg border-2 bg-slate-50 dark:bg-slate-800/50', color)}>
      {/* Column Header */}
      <div className="border-b border-slate-200 p-4 dark:border-slate-700">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-slate-900 dark:text-white">{title}</h3>
          <span className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700 dark:bg-slate-700 dark:text-slate-300">
            {count}
          </span>
        </div>
      </div>

      {/* Column Items */}
      <div className="custom-scrollbar flex-1 space-y-3 overflow-y-auto p-4" style={{ maxHeight: '600px' }}>
        {items.length === 0 ? (
          <div className="flex h-32 items-center justify-center text-sm text-slate-400">
            No items
          </div>
        ) : (
          items.map((item) => (
            <div
              key={'invoiceId' in item ? item.invoiceId : item.caseId}
              onClick={() => handleItemClick(item)}
              className="cursor-pointer transition-transform hover:scale-[1.02]"
            >
              {'invoiceId' in item ? (
                <InvoiceCard invoice={item} />
              ) : (
                <ExceptionCard exception={item} />
              )}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
