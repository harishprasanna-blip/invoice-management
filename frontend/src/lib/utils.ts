import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Merge Tailwind CSS classes with clsx
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Format currency amount
 */
export function formatCurrency(amount: string | number, currency: string = 'USD'): string {
  const numAmount = typeof amount === 'string' ? parseFloat(amount) : amount
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency,
  }).format(numAmount)
}

/**
 * Format date to readable string
 */
export function formatDate(dateString: string, options?: Intl.DateTimeFormatOptions): string {
  const date = new Date(dateString)
  return new Intl.DateTimeFormat('en-US', options || {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  }).format(date)
}

/**
 * Format relative time (e.g., "2 hours ago")
 */
export function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSecs = Math.floor(diffMs / 1000)
  const diffMins = Math.floor(diffSecs / 60)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffSecs < 60) return 'just now'
  if (diffMins < 60) return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`
  return formatDate(dateString)
}

/**
 * Calculate percentage
 */
export function calculatePercentage(value: number, total: number): number {
  if (total === 0) return 0
  return Math.round((value / total) * 100)
}

/**
 * Format file size
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

/**
 * Debounce function
 */
export function debounce<T extends (...args: unknown[]) => void>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeout: NodeJS.Timeout | null = null
  return (...args: Parameters<T>) => {
    if (timeout) clearTimeout(timeout)
    timeout = setTimeout(() => func(...args), wait)
  }
}

/**
 * Generate random ID
 */
export function generateId(): string {
  return Math.random().toString(36).substring(2) + Date.now().toString(36)
}

/**
 * Parse confidence score to percentage
 */
export function parseConfidenceScore(score: string): number {
  const numScore = parseFloat(score)
  if (numScore <= 1) return Math.round(numScore * 100)
  return Math.round(numScore)
}

/**
 * Get status color class
 */
export function getStatusColor(status: string): string {
  const statusLower = status.toLowerCase()

  if (statusLower.includes('fail') || statusLower.includes('error') || statusLower === 'critical') {
    return 'text-error-600 bg-error-100 dark:bg-error-900/30 dark:text-error-300'
  }
  if (statusLower.includes('pending') || statusLower === 'medium') {
    return 'text-warning-600 bg-warning-100 dark:bg-warning-900/30 dark:text-warning-300'
  }
  if (statusLower.includes('progress') || statusLower.includes('processing')) {
    return 'text-primary-600 bg-primary-100 dark:bg-primary-900/30 dark:text-primary-300'
  }
  if (statusLower.includes('pass') || statusLower.includes('success') || statusLower === 'validated') {
    return 'text-success-600 bg-success-100 dark:bg-success-900/30 dark:text-success-300'
  }
  if (statusLower === 'low') {
    return 'text-slate-600 bg-slate-100 dark:bg-slate-800 dark:text-slate-300'
  }
  if (statusLower === 'high') {
    return 'text-orange-600 bg-orange-100 dark:bg-orange-900/30 dark:text-orange-300'
  }

  return 'text-slate-600 bg-slate-100 dark:bg-slate-800 dark:text-slate-300'
}

/**
 * Get priority score color
 */
export function getPriorityColor(score: number): string {
  if (score >= 90) return 'text-error-600'
  if (score >= 70) return 'text-orange-600'
  if (score >= 50) return 'text-warning-600'
  return 'text-slate-600'
}

/**
 * Truncate text
 */
export function truncate(text: string, length: number): string {
  if (text.length <= length) return text
  return text.substring(0, length) + '...'
}

/**
 * Sleep utility
 */
export function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * Copy to clipboard
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

/**
 * Download blob as file
 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * Check if date is overdue
 */
export function isOverdue(dateString: string): boolean {
  return new Date(dateString) < new Date()
}

/**
 * Calculate time remaining until deadline
 */
export function getTimeRemaining(deadline: string): {
  isOverdue: boolean
  hours: number
  minutes: number
  formatted: string
} {
  const now = new Date()
  const deadlineDate = new Date(deadline)
  const diffMs = deadlineDate.getTime() - now.getTime()

  const isOverdue = diffMs < 0
  const absDiffMs = Math.abs(diffMs)

  const hours = Math.floor(absDiffMs / (1000 * 60 * 60))
  const minutes = Math.floor((absDiffMs % (1000 * 60 * 60)) / (1000 * 60))

  let formatted = ''
  if (hours > 24) {
    const days = Math.floor(hours / 24)
    formatted = `${days}d ${hours % 24}h`
  } else if (hours > 0) {
    formatted = `${hours}h ${minutes}m`
  } else {
    formatted = `${minutes}m`
  }

  if (isOverdue) {
    formatted = `${formatted} overdue`
  }

  return { isOverdue, hours, minutes, formatted }
}
