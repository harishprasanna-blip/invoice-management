import { useState } from 'react'
import { Plus, Upload, TrendingUp, AlertTriangle, CheckCircle, Clock } from 'lucide-react'
import { KanbanBoard } from '@/components/dashboard/KanbanBoard'
import { StatsCards } from '@/components/dashboard/StatsCards'
import { UploadInvoiceModal } from '@/components/modals/UploadInvoiceModal'
import { useDashboardStats } from '@/hooks/api/use-exceptions'

export function Dashboard() {
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false)
  const { data: stats, isLoading: statsLoading } = useDashboardStats()

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
            Invoice Processing Dashboard
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            AI-powered invoice workflow with real-time automation
          </p>
        </div>
        <button
          onClick={() => setIsUploadModalOpen(true)}
          className="flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
        >
          <Upload className="h-4 w-4" />
          Upload Invoice
        </button>
      </div>

      {/* Stats Cards */}
      <StatsCards stats={stats} isLoading={statsLoading} />

      {/* Kanban Board */}
      <div className="glass rounded-xl p-6">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
            Workflow Pipeline
          </h2>
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <div className="flex items-center gap-1">
              <div className="h-2 w-2 animate-pulse-slow rounded-full bg-success-500"></div>
              <span>Live Updates</span>
            </div>
          </div>
        </div>
        <KanbanBoard />
      </div>

      {/* Upload Modal */}
      <UploadInvoiceModal
        isOpen={isUploadModalOpen}
        onClose={() => setIsUploadModalOpen(false)}
      />
    </div>
  )
}
