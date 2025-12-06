import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ingestionApi, handleApiError } from '@/lib/api-client'
import type {
  Invoice,
  InvoiceUploadForm,
  MetadataCorrectionForm,
} from '@/types/domain'
import toast from 'react-hot-toast'

/**
 * React Query hooks for Invoice Ingestion Context
 */

// Query keys
export const invoiceKeys = {
  all: ['invoices'] as const,
  lists: () => [...invoiceKeys.all, 'list'] as const,
  list: (filters: Record<string, unknown>) => [...invoiceKeys.lists(), filters] as const,
  details: () => [...invoiceKeys.all, 'detail'] as const,
  detail: (id: string) => [...invoiceKeys.details(), id] as const,
  document: (id: string) => [...invoiceKeys.all, 'document', id] as const,
}

// ============================================================================
// Queries
// ============================================================================

/**
 * Get invoice by ID
 */
export function useInvoice(invoiceId: string) {
  return useQuery({
    queryKey: invoiceKeys.detail(invoiceId),
    queryFn: async (): Promise<Invoice> => {
      const response = await ingestionApi.get(`/invoices/${invoiceId}`)
      return response.data
    },
    enabled: !!invoiceId,
    staleTime: 10000, // 10 seconds
    retry: 2,
  })
}

/**
 * Get all invoices (with optional filters)
 */
export function useInvoices(filters?: {
  status?: string
  vendorId?: string
  dateFrom?: string
  dateTo?: string
}) {
  return useQuery({
    queryKey: invoiceKeys.list(filters || {}),
    queryFn: async (): Promise<Invoice[]> => {
      const response = await ingestionApi.get('/invoices', { params: filters })
      return response.data
    },
    staleTime: 5000, // 5 seconds
  })
}

/**
 * Download invoice document
 */
export function useInvoiceDocument(invoiceId: string) {
  return useQuery({
    queryKey: invoiceKeys.document(invoiceId),
    queryFn: async (): Promise<Blob> => {
      const response = await ingestionApi.get(`/invoices/${invoiceId}/document`, {
        responseType: 'blob',
      })
      return response.data
    },
    enabled: false, // Manual trigger only
    staleTime: Infinity,
  })
}

// ============================================================================
// Mutations
// ============================================================================

/**
 * Submit invoice for processing
 */
export function useSubmitInvoice() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (data: InvoiceUploadForm) => {
      const formData = new FormData()
      formData.append('file', data.file)
      if (data.vendorId) formData.append('vendorId', data.vendorId)
      if (data.vendorName) formData.append('vendorName', data.vendorName)

      const response = await ingestionApi.post('/invoices', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      })
      return response.data
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: invoiceKeys.lists() })
      toast.success(`Invoice submitted successfully: ${data.invoiceId}`, {
        duration: 5000,
      })
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to submit invoice: ${message}`)
    },
  })
}

/**
 * Correct invoice metadata
 */
export function useCorrectMetadata() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      invoiceId,
      data,
    }: {
      invoiceId: string
      data: MetadataCorrectionForm
    }) => {
      const response = await ingestionApi.put(`/invoices/${invoiceId}/metadata`, data)
      return response.data
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: invoiceKeys.detail(variables.invoiceId) })
      queryClient.invalidateQueries({ queryKey: invoiceKeys.lists() })
      toast.success('Invoice metadata corrected successfully')
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to correct metadata: ${message}`)
    },
  })
}

/**
 * Download invoice document (action)
 */
export function useDownloadInvoiceDocument() {
  return useMutation({
    mutationFn: async (invoiceId: string) => {
      const response = await ingestionApi.get(`/invoices/${invoiceId}/document`, {
        responseType: 'blob',
      })
      return {
        blob: response.data as Blob,
        filename:
          response.headers['content-disposition']?.split('filename=')[1]?.replace(/"/g, '') ||
          `invoice-${invoiceId}.pdf`,
      }
    },
    onSuccess: ({ blob, filename }) => {
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      toast.success('Document downloaded successfully')
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to download document: ${message}`)
    },
  })
}
