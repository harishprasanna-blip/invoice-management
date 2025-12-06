import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { exceptionApi, handleApiError } from '@/lib/api-client'
import type {
  ExceptionCase,
  ResolutionActionForm,
  ResolveExceptionForm,
  EscalateExceptionForm,
  DashboardStats,
} from '@/types/domain'
import toast from 'react-hot-toast'

/**
 * React Query hooks for Exception Handling Context
 */

// Query keys
export const exceptionKeys = {
  all: ['exceptions'] as const,
  lists: () => [...exceptionKeys.all, 'list'] as const,
  list: (filters: Record<string, unknown>) => [...exceptionKeys.lists(), filters] as const,
  details: () => [...exceptionKeys.all, 'detail'] as const,
  detail: (id: string) => [...exceptionKeys.details(), id] as const,
  assigned: (assignee: string) => [...exceptionKeys.all, 'assigned', assignee] as const,
  stats: () => [...exceptionKeys.all, 'stats'] as const,
}

// ============================================================================
// Queries
// ============================================================================

/**
 * Get exception case by ID
 */
export function useExceptionCase(caseId: string) {
  return useQuery({
    queryKey: exceptionKeys.detail(caseId),
    queryFn: async (): Promise<ExceptionCase> => {
      const response = await exceptionApi.get(`/exceptions/${caseId}`)
      return response.data
    },
    enabled: !!caseId,
    staleTime: 10000,
    retry: 2,
  })
}

/**
 * Get all open exception cases
 */
export function useOpenExceptions() {
  return useQuery({
    queryKey: exceptionKeys.list({ status: 'open' }),
    queryFn: async (): Promise<ExceptionCase[]> => {
      const response = await exceptionApi.get('/exceptions')
      return response.data
    },
    staleTime: 5000,
    refetchInterval: 30000, // Refresh every 30 seconds
  })
}

/**
 * Get cases assigned to resolver
 */
export function useAssignedExceptions(assignee: string) {
  return useQuery({
    queryKey: exceptionKeys.assigned(assignee),
    queryFn: async (): Promise<ExceptionCase[]> => {
      const response = await exceptionApi.get(`/exceptions/assigned/${assignee}`)
      return response.data
    },
    enabled: !!assignee,
    staleTime: 5000,
  })
}

/**
 * Get dashboard statistics
 */
export function useDashboardStats() {
  return useQuery({
    queryKey: exceptionKeys.stats(),
    queryFn: async (): Promise<DashboardStats> => {
      const response = await exceptionApi.get('/exceptions/dashboard/stats')
      return response.data
    },
    staleTime: 10000,
    refetchInterval: 60000, // Refresh every minute
  })
}

// ============================================================================
// Mutations
// ============================================================================

/**
 * Add resolution action to exception case
 */
export function useAddResolutionAction() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      caseId,
      data,
      userId,
    }: {
      caseId: string
      data: ResolutionActionForm
      userId: string
    }) => {
      const response = await exceptionApi.post(`/exceptions/${caseId}/actions`, {
        ...data,
        userId,
      })
      return response.data
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: exceptionKeys.detail(variables.caseId) })
      queryClient.invalidateQueries({ queryKey: exceptionKeys.lists() })
      toast.success('Resolution action added')
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to add action: ${message}`)
    },
  })
}

/**
 * Resolve exception case
 */
export function useResolveException() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      caseId,
      data,
      userId,
    }: {
      caseId: string
      data: ResolveExceptionForm
      userId: string
    }) => {
      const response = await exceptionApi.post(`/exceptions/${caseId}/resolve`, {
        resolverUserId: userId,
        resolutionNotes: data.resolutionNotes,
      })
      return response.data
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: exceptionKeys.detail(variables.caseId) })
      queryClient.invalidateQueries({ queryKey: exceptionKeys.lists() })
      queryClient.invalidateQueries({ queryKey: exceptionKeys.stats() })
      toast.success('Exception resolved successfully')
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to resolve exception: ${message}`)
    },
  })
}

/**
 * Escalate exception case
 */
export function useEscalateException() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async ({
      caseId,
      data,
    }: {
      caseId: string
      data: EscalateExceptionForm
    }) => {
      const response = await exceptionApi.post(`/exceptions/${caseId}/escalate`, data)
      return response.data
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: exceptionKeys.detail(variables.caseId) })
      queryClient.invalidateQueries({ queryKey: exceptionKeys.lists() })
      toast.success('Exception escalated successfully')
    },
    onError: (error) => {
      const message = handleApiError(error)
      toast.error(`Failed to escalate exception: ${message}`)
    },
  })
}
