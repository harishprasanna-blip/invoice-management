import { useParams } from 'react-router-dom'
import { useInvoice } from '@/hooks/api/use-invoices'
import { ArrowLeft, Download, Edit } from 'lucide-react'
import { Link } from 'react-router-dom'
import { formatCurrency, formatDate, getStatusColor } from '@/lib/utils'
import { cn } from '@/lib/utils'

export function InvoiceDetail() {
  const { id } = useParams<{ id: string }>()
  const { data: invoice, isLoading } = useInvoice(id!)

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="spinner h-8 w-8"></div>
      </div>
    )
  }

  if (!invoice) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-slate-500">Invoice not found</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link
            to="/dashboard"
            className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
          >
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
              Invoice {invoice.invoiceMetadata?.invoiceNumber || invoice.invoiceId}
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              Uploaded {formatDate(invoice.createdAt)}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button className="flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800">
            <Edit className="h-4 w-4" />
            Edit
          </button>
          <button className="flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700">
            <Download className="h-4 w-4" />
            Download
          </button>
        </div>
      </div>

      {/* Status Badge */}
      <div className="flex items-center gap-2">
        <span className={cn('status-badge', getStatusColor(invoice.ingestionStatus))}>
          {invoice.ingestionStatus}
        </span>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Invoice Details */}
        <div className="lg:col-span-2 space-y-6">
          {/* Vendor Information */}
          {invoice.vendorReference && (
            <div className="glass rounded-xl p-6">
              <h2 className="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
                Vendor Information
              </h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <p className="text-sm font-medium text-slate-500">Vendor Name</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {invoice.vendorReference.vendorName}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-500">Vendor ID</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {invoice.vendorReference.vendorId}
                  </p>
                </div>
                {invoice.vendorReference.taxId && (
                  <div>
                    <p className="text-sm font-medium text-slate-500">Tax ID</p>
                    <p className="mt-1 text-sm text-slate-900 dark:text-white">
                      {invoice.vendorReference.taxId}
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Invoice Metadata */}
          {invoice.invoiceMetadata && (
            <div className="glass rounded-xl p-6">
              <h2 className="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
                Invoice Details
              </h2>
              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <p className="text-sm font-medium text-slate-500">Invoice Number</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {invoice.invoiceMetadata.invoiceNumber}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-500">Invoice Date</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {formatDate(invoice.invoiceMetadata.invoiceDate)}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-500">Due Date</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {formatDate(invoice.invoiceMetadata.dueDate)}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-500">Total Amount</p>
                  <p className="mt-1 text-lg font-bold text-slate-900 dark:text-white">
                    {formatCurrency(
                      invoice.invoiceMetadata.totalAmount.amount,
                      invoice.invoiceMetadata.totalAmount.currency
                    )}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Line Items */}
          {invoice.lineItems && invoice.lineItems.length > 0 && (
            <div className="glass rounded-xl p-6">
              <h2 className="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
                Line Items ({invoice.lineItems.length})
              </h2>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pb-3 text-left font-medium text-slate-500">#</th>
                      <th className="pb-3 text-left font-medium text-slate-500">Description</th>
                      <th className="pb-3 text-right font-medium text-slate-500">Qty</th>
                      <th className="pb-3 text-right font-medium text-slate-500">Unit Price</th>
                      <th className="pb-3 text-right font-medium text-slate-500">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {invoice.lineItems.map((item) => (
                      <tr
                        key={item.lineNumber}
                        className="border-b border-slate-100 dark:border-slate-800"
                      >
                        <td className="py-3 text-slate-900 dark:text-white">{item.lineNumber}</td>
                        <td className="py-3 text-slate-900 dark:text-white">{item.description}</td>
                        <td className="py-3 text-right text-slate-900 dark:text-white">
                          {item.quantity}
                        </td>
                        <td className="py-3 text-right text-slate-900 dark:text-white">
                          {formatCurrency(item.unitPrice.amount, item.unitPrice.currency)}
                        </td>
                        <td className="py-3 text-right font-medium text-slate-900 dark:text-white">
                          {formatCurrency(item.lineTotal.amount, item.lineTotal.currency)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Extraction Info */}
          {invoice.extractionResult && (
            <div className="glass rounded-xl p-6">
              <h2 className="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
                AI Extraction
              </h2>
              <div className="space-y-3">
                <div>
                  <p className="text-sm font-medium text-slate-500">Method</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {invoice.extractionResult.extractionMethod}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-500">Confidence Score</p>
                  <p className="mt-1 text-sm font-bold text-success-600">
                    {invoice.extractionResult.confidenceScore}
                  </p>
                </div>
                <div>
                  <p className="text-sm font-medium text-slate-500">Extracted At</p>
                  <p className="mt-1 text-sm text-slate-900 dark:text-white">
                    {formatDate(invoice.extractionResult.extractedAt)}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Document Info */}
          <div className="glass rounded-xl p-6">
            <h2 className="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
              Document
            </h2>
            <div className="space-y-3">
              <div>
                <p className="text-sm font-medium text-slate-500">File Name</p>
                <p className="mt-1 text-sm text-slate-900 dark:text-white">
                  {invoice.documentReference.originalFileName}
                </p>
              </div>
              <div>
                <p className="text-sm font-medium text-slate-500">Format</p>
                <p className="mt-1 text-sm text-slate-900 dark:text-white">
                  {invoice.documentReference.documentFormat}
                </p>
              </div>
              <div>
                <p className="text-sm font-medium text-slate-500">Uploaded</p>
                <p className="mt-1 text-sm text-slate-900 dark:text-white">
                  {formatDate(invoice.documentReference.uploadTimestamp)}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
