import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { validationApi, handleApiError } from '@/lib/api-client'
import type { PayableTransaction } from '@/types/domain'
import toast from 'react-hot-toast'

/**
 * React Query hooks for Validation Context
 */

// Query keys
export const validationKeys = {
  all: ['validation'] as const,
  invoices: () => [...validationKeys.all, 'invoices'] as const,
  invoice: (id: string) => [...validationKeys.invoices(), id] as const,
  transactions: () => [...validationKeys.all, 'transactions'] as const,
  transaction: (id: string) => [...validationKeys.transactions(), id] as const,
}

// ============================================================================
// Queries
// ============================================================================

/**
 * Get validation status for invoice
 */
export function useValidationStatus(invoiceId: string) {
  return useQuery({
    queryKey: validationKeys.invoice(invoiceId),
    queryFn: async () => {
      const response = await validationApi.get(`/validation/invoices/${invoiceId}`)
      return response.data
    },
    enabled: !!invoiceId,
    staleTime: 10000,
    retry: 2,
  })
}

/**
 * Get payable transaction details
 */
export function useTransaction(transactionId: string) {
  return useQuery({
    queryKey: validationKeys.transaction(transactionId),
    queryFn: async (): Promise<PayableTransaction> => {
      const response = await validationApi.get(`/validation/transactions/${transactionId}`)
      return response.data
    },
    enabled: !!transactionId,
    staleTime: 10000,
  })
}

// ============================================================================
// Mutations
// ============================================================================

/**
 * Re-validate transaction
 */
export function useRevalidateTransaction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (transactionId: string) => {
      const response = await validationApi.post(
        `/validation/transactions/${transactionId}/revalidate`
      )
      return response.data
    },
    onSuccess: (_data, transactionId) => {
      queryClient.invalidateQueries({ queryKey: validationKeys.transaction(transactionId) })
      toast.success('Re-validation initiated')
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to re-validate: ${message}`)
    },
  })
}
