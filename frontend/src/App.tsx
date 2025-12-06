import { Routes, Route, Navigate } from 'react-router-dom'
import { MainLayout } from './components/layout/MainLayout'
import { Dashboard } from './pages/Dashboard'
import { InvoiceDetail } from './pages/InvoiceDetail'
import { Exceptions } from './pages/Exceptions'
import { ExceptionDetail } from './pages/ExceptionDetail'
import { Analytics } from './pages/Analytics'
import { Settings } from './pages/Settings'

function App() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="invoices/:id" element={<InvoiceDetail />} />
        <Route path="exceptions" element={<Exceptions />} />
        <Route path="exceptions/:id" element={<ExceptionDetail />} />
        <Route path="analytics" element={<Analytics />} />
        <Route path="settings" element={<Settings />} />
      </Route>
    </Routes>
  )
}

export default App
