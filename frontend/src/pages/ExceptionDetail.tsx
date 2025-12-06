import { useParams, Link } from 'react-router-dom'
import { useExceptionCase } from '@/hooks/api/use-exceptions'
import { ArrowLeft } from 'lucide-react'

export function ExceptionDetail() {
  const { id } = useParams<{ id: string }>()
  const { data: exception, isLoading } = useExceptionCase(id!)

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="spinner h-8 w-8"></div>
      </div>
    )
  }

  if (!exception) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-slate-500">Exception not found</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link
          to="/exceptions"
          className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
        >
          <ArrowLeft className="h-5 w-5" />
        </Link>
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
            {exception.title}
          </h1>
          <p className="mt-1 text-sm text-slate-500">{exception.exceptionType}</p>
        </div>
      </div>

      <div className="glass rounded-xl p-6">
        <p className="text-slate-700 dark:text-slate-300">{exception.description}</p>
      </div>
    </div>
  )
}
