# Quick Reference Guide

## 🚀 Common Commands

```bash
# Development
npm run dev                  # Start dev server (localhost:3000)
npm run build               # Build for production
npm run preview             # Preview production build

# Code Quality
npm run type-check          # Check TypeScript types
npm run lint                # Check for linting errors
npm run lint:fix            # Auto-fix linting errors
npm run format              # Format code with Prettier

# Testing
npm test                    # Run tests
npm run test:ui             # Run tests with UI
npm run test:coverage       # Generate coverage report

# Analysis
npm run analyze             # Analyze bundle size
```

## 📂 Key File Locations

| What | Where |
|------|-------|
| **API Hooks** | `src/hooks/api/` |
| **Components** | `src/components/` |
| **Pages** | `src/pages/` |
| **Types** | `src/types/domain.ts` |
| **Utilities** | `src/lib/utils.ts` |
| **API Client** | `src/lib/api-client.ts` |
| **Styles** | `src/index.css` |
| **Config** | `vite.config.ts`, `tailwind.config.js` |

## 🔌 Backend Endpoints

| Service | Port | Health Check |
|---------|------|--------------|
| Invoice Ingestion | 8080 | `http://localhost:8080/actuator/health` |
| Validation | 8082 | `http://localhost:8082/api/v1/validation/health` |
| Exception Handling | 8083 | `http://localhost:8083/api/v1/exceptions/health` |
| Payment | 8084 | `http://localhost:8084/api/v1/payments/health` |

## 🎯 Common Tasks

### Add a New Page
```typescript
// 1. Create page component
// src/pages/NewPage.tsx
export function NewPage() {
  return <div>New Page</div>
}

// 2. Add route
// src/App.tsx
<Route path="new-page" element={<NewPage />} />

// 3. Add navigation
// src/components/layout/Sidebar.tsx
{
  name: 'New Page',
  href: '/new-page',
  icon: IconComponent,
}
```

### Add a New API Hook
```typescript
// src/hooks/api/use-new-feature.ts
import { useQuery, useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'

export function useNewFeature() {
  return useQuery({
    queryKey: ['new-feature'],
    queryFn: async () => {
      const response = await api.get('/new-endpoint')
      return response.data
    },
  })
}
```

### Add a New Type
```typescript
// src/types/domain.ts
export interface NewType {
  id: string
  name: string
  // ... other fields
}
```

### Add a New Component
```typescript
// src/components/NewComponent.tsx
import { cn } from '@/lib/utils'

interface NewComponentProps {
  title: string
  className?: string
}

export function NewComponent({ title, className }: NewComponentProps) {
  return (
    <div className={cn('rounded-lg bg-white p-4', className)}>
      <h2>{title}</h2>
    </div>
  )
}
```

## 🎨 Styling Reference

### Tailwind Classes
```typescript
// Layout
'flex items-center justify-between'
'grid gap-4 md:grid-cols-2 lg:grid-cols-3'

// Spacing
'p-4'        // padding: 1rem
'mt-6'       // margin-top: 1.5rem
'gap-2'      // gap: 0.5rem

// Colors
'bg-primary-600'
'text-slate-900 dark:text-white'
'border-slate-200 dark:border-slate-700'

// Effects
'glass'                  // Glassmorphism
'rounded-lg'            // Border radius
'shadow-md hover:shadow-lg'  // Shadows
```

### Custom CSS Classes
```css
.glass              /* Glassmorphism effect */
.status-badge       /* Status badge base */
.status-pending     /* Pending status */
.status-validated   /* Validated status */
.status-exception   /* Exception status */
.custom-scrollbar   /* Custom scrollbar */
.spinner            /* Loading spinner */
```

## 🔧 Utility Functions

```typescript
// Formatting
formatCurrency(1000, 'USD')        // "$1,000.00"
formatDate('2024-01-01')           // "Jan 1, 2024"
formatRelativeTime('2024-01-01')   // "2 hours ago"
formatFileSize(1024)               // "1 KB"

// Status Helpers
getStatusColor('VALIDATED')        // "text-success-600 bg-success-100"
getPriorityColor(85)               // "text-error-600"
parseConfidenceScore('0.95')       // 95

// Time
getTimeRemaining(deadline)         // { isOverdue, hours, minutes, formatted }
isOverdue('2024-01-01')           // true/false

// Misc
cn('class1', 'class2')            // Merge Tailwind classes
debounce(fn, 500)                 // Debounce function
copyToClipboard(text)             // Copy to clipboard
```

## 🌐 Environment Variables

```env
# API Endpoints
VITE_API_BASE_URL=http://localhost:8080
VITE_VALIDATION_API_URL=http://localhost:8082
VITE_EXCEPTION_API_URL=http://localhost:8083
VITE_PAYMENT_API_URL=http://localhost:8084

# WebSocket
VITE_WS_URL=ws://localhost:8080

# Multi-tenancy
VITE_TENANT_ID=your-tenant-uuid

# Feature Flags
VITE_ENABLE_DARK_MODE=true
VITE_ENABLE_ANALYTICS=true
```

## 🐛 Debugging

### Check API Calls
```javascript
// Open Browser DevTools > Network tab
// Filter by XHR/Fetch
// Look for:
- Status codes (200, 201, 400, 404, 500)
- Request headers (X-Tenant-ID, Authorization)
- Response data
```

### Check React Query
```javascript
// Click React Query DevTools icon (bottom right)
// View:
- Queries and their status
- Cache contents
- Mutations in progress
- Invalidation events
```

### Check Console
```javascript
// Common errors:
"Module not found" → npm install missing-package
"Type error" → npm run type-check
"CORS error" → Check backend CORS config
"401 Unauthorized" → Check auth token
```

## 🔐 Multi-Tenancy

### Set Tenant ID
```typescript
import { tenantContext } from '@/lib/api-client'

// Set tenant ID (do this on login)
tenantContext.setTenantId('your-tenant-uuid')

// Get tenant ID
const tenantId = tenantContext.getTenantId()

// Clear tenant ID (on logout)
tenantContext.clearTenantId()
```

### Headers Automatically Added
```typescript
// Every API call includes:
{
  'X-Tenant-ID': 'your-tenant-uuid',
  'Authorization': 'Bearer your-jwt-token',
  'Content-Type': 'application/json'
}
```

## 🎭 State Management

### React Query (Server State)
```typescript
// Fetching data
const { data, isLoading, error } = useQuery({
  queryKey: ['key'],
  queryFn: fetchData,
})

// Mutating data
const mutation = useMutation({
  mutationFn: createData,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['key'] })
  },
})
```

### Zustand (Client State) - Optional
```typescript
// Create store
import { create } from 'zustand'

const useStore = create((set) => ({
  count: 0,
  increment: () => set((state) => ({ count: state.count + 1 })),
}))

// Use in component
const count = useStore((state) => state.count)
const increment = useStore((state) => state.increment)
```

## 🚨 Common Errors & Fixes

| Error | Fix |
|-------|-----|
| Module not found | `npm install <package>` |
| Port 3000 in use | `npm run dev -- --port 3001` |
| TypeScript errors | `npm run type-check` |
| Tailwind not working | `rm -rf node_modules/.cache && npm run dev` |
| API 401 error | Check auth token and tenant ID |
| CORS error | Configure backend CORS |
| WebSocket fails | Check backend WebSocket config |

## 📦 Key Dependencies

| Package | Purpose |
|---------|---------|
| react | UI library |
| typescript | Type safety |
| vite | Build tool |
| tailwindcss | Styling |
| @tanstack/react-query | Server state |
| react-router-dom | Routing |
| axios | HTTP client |
| zod | Schema validation |
| react-hook-form | Forms |
| react-hot-toast | Notifications |
| lucide-react | Icons |
| framer-motion | Animations |

## 🎓 Learning Resources

- [React Query Docs](https://tanstack.com/query/latest)
- [Tailwind CSS Docs](https://tailwindcss.com/docs)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/intro.html)
- [Vite Guide](https://vitejs.dev/guide/)
- [React Hook Form](https://react-hook-form.com/)

## 💡 Pro Tips

1. **Use React Query DevTools** - Essential for debugging
2. **Enable TypeScript strict mode** - Catch errors early
3. **Leverage Vite HMR** - Instant updates without refresh
4. **Use cn() for classes** - Better than template literals
5. **Invalidate queries** - Keep UI in sync with backend
6. **Add loading states** - Better UX
7. **Handle errors gracefully** - Toast notifications
8. **Use semantic HTML** - Better accessibility
9. **Optimize images** - Lazy load with loading="lazy"
10. **Check bundle size** - Run `npm run analyze`

---

**Need help? Check:**
- [README.md](./README.md) - Full documentation
- [SETUP_GUIDE.md](./SETUP_GUIDE.md) - Setup instructions
- [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) - What's built

**Happy coding! 🎉**
