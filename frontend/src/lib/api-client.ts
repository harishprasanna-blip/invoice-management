import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'

/**
 * API Client Configuration
 * Handles all HTTP requests to the backend services
 */

// Service ports configuration
const SERVICE_PORTS = {
  ingestion: 8080,
  validation: 8082,
  exception: 8083,
  payment: 8084,
} as const

// Base URLs for each service
const BASE_URLS = {
  ingestion: `http://localhost:${SERVICE_PORTS.ingestion}/api/v1`,
  validation: `http://localhost:${SERVICE_PORTS.validation}/api/v1`,
  exception: `http://localhost:${SERVICE_PORTS.exception}/api/v1`,
  payment: `http://localhost:${SERVICE_PORTS.payment}/api/v1`,
} as const

/**
 * Create axios instance with default configuration
 */
function createAxiosInstance(baseURL: string): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 30000,
    headers: {
      'Content-Type': 'application/json',
    },
  })

  // Request interceptor - Add tenant ID and auth token
  instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      // Get tenant ID from localStorage or context
      const tenantId = localStorage.getItem('tenantId')
      if (tenantId && config.headers) {
        config.headers['X-Tenant-ID'] = tenantId
      }

      // Get auth token from localStorage
      const token = localStorage.getItem('authToken')
      if (token && config.headers) {
        config.headers['Authorization'] = `Bearer ${token}`
      }

      return config
    },
    (error) => {
      return Promise.reject(error)
    }
  )

  // Response interceptor - Handle errors globally
  instance.interceptors.response.use(
    (response) => response,
    (error: AxiosError) => {
      // Handle common errors
      if (error.response) {
        const status = error.response.status

        switch (status) {
          case 401:
            // Unauthorized - redirect to login
            console.error('Unauthorized access - redirecting to login')
            localStorage.removeItem('authToken')
            window.location.href = '/login'
            break
          case 403:
            // Forbidden
            console.error('Access forbidden:', error.response.data)
            break
          case 404:
            // Not found
            console.error('Resource not found:', error.config?.url)
            break
          case 500:
            // Server error
            console.error('Server error:', error.response.data)
            break
          default:
            console.error('API error:', error.message)
        }
      } else if (error.request) {
        // Network error
        console.error('Network error - no response received')
      } else {
        console.error('Error:', error.message)
      }

      return Promise.reject(error)
    }
  )

  return instance
}

// Create instances for each service
export const ingestionApi = createAxiosInstance(BASE_URLS.ingestion)
export const validationApi = createAxiosInstance(BASE_URLS.validation)
export const exceptionApi = createAxiosInstance(BASE_URLS.exception)
export const paymentApi = createAxiosInstance(BASE_URLS.payment)

/**
 * API Error Handler
 */
export function handleApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: string }>
    return axiosError.response?.data?.message || axiosError.message || 'An error occurred'
  }
  return 'An unexpected error occurred'
}

/**
 * Tenant Context Management
 */
export const tenantContext = {
  setTenantId: (tenantId: string) => {
    localStorage.setItem('tenantId', tenantId)
  },
  getTenantId: (): string | null => {
    return localStorage.getItem('tenantId')
  },
  clearTenantId: () => {
    localStorage.removeItem('tenantId')
  },
}

/**
 * Auth Token Management
 */
export const authToken = {
  setToken: (token: string) => {
    localStorage.setItem('authToken', token)
  },
  getToken: (): string | null => {
    return localStorage.getItem('authToken')
  },
  clearToken: () => {
    localStorage.removeItem('authToken')
  },
}
