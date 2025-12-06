import { Invoice } from '@/types/domain'
import { FileText, Building, Calendar, DollarSign } from 'lucide-react'
import { formatCurrency, formatDate, getStatusColor, parseConfidenceScore } from '@/lib/utils'
import { cn } from '@/lib/utils'

interface InvoiceCardProps {
  invoice: Invoice
}

export function InvoiceCard({ invoice }: InvoiceCardProps) {
  const confidenceScore = invoice.extractionResult
    ? parseConfidenceScore(invoice.extractionResult.confidenceScore)
    : 0

  return (
    <div className="group rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-slate-700 dark:bg-slate-800">
      {/* Header */}
      <div className="mb-3 flex items-start justify-between">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
            <FileText className="h-4 w-4 text-primary-600 dark:text-primary-400" />
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-900 dark:text-white">
              {invoice.invoiceMetadata?.invoiceNumber || 'Extracting...'}
            </p>
            <p className="text-xs text-slate-500">
              {invoice.documentReference.originalFileName}
            </p>
          </div>
        </div>
        <span className={cn('status-badge text-xs', getStatusColor(invoice.ingestionStatus))}>
          {invoice.ingestionStatus}
        </span>
      </div>

      {/* Details */}
      <div className="space-y-2">
        {invoice.vendorReference && (
          <div className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-400">
            <Building className="h-3.5 w-3.5" />
            <span className="truncate">{invoice.vendorReference.vendorName}</span>
          </div>
        )}
        {invoice.invoiceMetadata?.invoiceDate && (
          <div className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-400">
            <Calendar className="h-3.5 w-3.5" />
            <span>{formatDate(invoice.invoiceMetadata.invoiceDate)}</span>
          </div>
        )}
        {invoice.invoiceMetadata?.totalAmount && (
          <div className="flex items-center gap-2 text-xs font-medium text-slate-900 dark:text-white">
            <DollarSign className="h-3.5 w-3.5" />
            <span>
              {formatCurrency(
                invoice.invoiceMetadata.totalAmount.amount,
                invoice.invoiceMetadata.totalAmount.currency
              )}
            </span>
          </div>
        )}
      </div>

      {/* Confidence Score */}
      {invoice.extractionResult && (
        <div className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-700">
          <div className="flex items-center justify-between text-xs">
            <span className="text-slate-500">AI Confidence</span>
            <span
              className={cn(
                'font-medium',
                confidenceScore >= 90
                  ? 'text-success-600'
                  : confidenceScore >= 70
                    ? 'text-warning-600'
                    : 'text-error-600'
              )}
            >
              {confidenceScore}%
            </span>
          </div>
          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700">
            <div
              className={cn(
                'h-full transition-all',
                confidenceScore >= 90
                  ? 'bg-success-500'
                  : confidenceScore >= 70
                    ? 'bg-warning-500'
                    : 'bg-error-500'
              )}
              style={{ width: `${confidenceScore}%` }}
            />
          </div>
        </div>
      )}
    </div>
  )
}
