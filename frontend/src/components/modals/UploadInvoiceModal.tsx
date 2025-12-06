import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { X, Upload, FileText, CheckCircle } from 'lucide-react'
import { useSubmitInvoice } from '@/hooks/api/use-invoices'
import { formatFileSize } from '@/lib/utils'
import { cn } from '@/lib/utils'

interface UploadInvoiceModalProps {
  isOpen: boolean
  onClose: () => void
}

export function UploadInvoiceModal({ isOpen, onClose }: UploadInvoiceModalProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [vendorId, setVendorId] = useState('')
  const [vendorName, setVendorName] = useState('')

  const submitMutation = useSubmitInvoice()

  const onDrop = useCallback((acceptedFiles: File[]) => {
    if (acceptedFiles.length > 0) {
      setSelectedFile(acceptedFiles[0])
    }
  }, [])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/pdf': ['.pdf'],
      'application/xml': ['.xml'],
      'text/plain': ['.txt', '.edi'],
    },
    maxFiles: 1,
    maxSize: 50 * 1024 * 1024, // 50MB
  })

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedFile) return

    await submitMutation.mutateAsync({
      file: selectedFile,
      vendorId: vendorId || undefined,
      vendorName: vendorName || undefined,
    })

    // Reset and close
    setSelectedFile(null)
    setVendorId('')
    setVendorName('')
    onClose()
  }

  const handleClose = () => {
    setSelectedFile(null)
    setVendorId('')
    setVendorName('')
    onClose()
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full max-w-2xl rounded-xl bg-white p-6 shadow-2xl dark:bg-slate-800">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-xl font-bold text-slate-900 dark:text-white">Upload Invoice</h2>
          <button
            onClick={handleClose}
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* File Dropzone */}
          <div>
            <label className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300">
              Invoice Document
            </label>
            <div
              {...getRootProps()}
              className={cn(
                'dropzone',
                isDragActive && 'active',
                selectedFile && 'border-success-500 bg-success-50 dark:bg-success-900/20'
              )}
            >
              <input {...getInputProps()} />
              {selectedFile ? (
                <div className="flex flex-col items-center">
                  <CheckCircle className="mb-2 h-12 w-12 text-success-600" />
                  <p className="text-sm font-medium text-slate-900 dark:text-white">
                    {selectedFile.name}
                  </p>
                  <p className="text-xs text-slate-500">{formatFileSize(selectedFile.size)}</p>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      setSelectedFile(null)
                    }}
                    className="mt-2 text-sm text-primary-600 hover:text-primary-700"
                  >
                    Choose different file
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-center">
                  <Upload className="mb-2 h-12 w-12 text-slate-400" />
                  <p className="text-sm font-medium text-slate-900 dark:text-white">
                    {isDragActive ? 'Drop file here' : 'Drag & drop invoice or click to browse'}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    Supported: PDF, XML, EDI (max 50MB)
                  </p>
                </div>
              )}
            </div>
          </div>

          {/* Optional Vendor Information */}
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label
                htmlFor="vendorId"
                className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Vendor ID (Optional)
              </label>
              <input
                type="text"
                id="vendorId"
                value={vendorId}
                onChange={(e) => setVendorId(e.target.value)}
                placeholder="e.g., VENDOR-001"
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              />
            </div>
            <div>
              <label
                htmlFor="vendorName"
                className="mb-2 block text-sm font-medium text-slate-700 dark:text-slate-300"
              >
                Vendor Name (Optional)
              </label>
              <input
                type="text"
                id="vendorName"
                value={vendorName}
                onChange={(e) => setVendorName(e.target.value)}
                placeholder="e.g., Acme Corp"
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-slate-700 dark:bg-slate-900 dark:text-white"
              />
            </div>
          </div>

          {/* Info Note */}
          <div className="rounded-lg bg-primary-50 p-4 dark:bg-primary-900/20">
            <p className="text-xs text-primary-800 dark:text-primary-300">
              <strong>AI Processing:</strong> Your invoice will be automatically extracted using GPT-4
              Vision and validated against procurement data. Average processing time: 15-30 seconds.
            </p>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={handleClose}
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!selectedFile || submitMutation.isPending}
              className="flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitMutation.isPending ? (
                <>
                  <span className="spinner"></span>
                  Uploading...
                </>
              ) : (
                <>
                  <Upload className="h-4 w-4" />
                  Submit Invoice
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
