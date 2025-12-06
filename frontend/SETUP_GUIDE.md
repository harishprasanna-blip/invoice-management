# Frontend Setup Guide

Complete step-by-step guide to set up and run the Invoice Management Frontend.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js** >= 18.0.0 ([Download](https://nodejs.org/))
- **npm** >= 9.0.0 (comes with Node.js)
- **Git** ([Download](https://git-scm.com/))

Verify installations:
```bash
node --version  # Should output v18.x.x or higher
npm --version   # Should output 9.x.x or higher
```

## Step 1: Clone and Navigate

```bash
cd d:\invoice_management_hp\frontend
```

## Step 2: Install Dependencies

```bash
npm install
```

This will install all required packages (~5 minutes):
- React 18 + React DOM
- TypeScript
- Vite
- Tailwind CSS
- React Query
- Axios
- And 50+ other production dependencies

## Step 3: Configure Environment

Create a `.env.local` file in the `frontend` directory:

```bash
# Copy the example file
cp .env.example .env.local
```

Edit `.env.local` with your configuration:

```env
# Backend API endpoints
VITE_API_BASE_URL=http://localhost:8080
VITE_VALIDATION_API_URL=http://localhost:8082
VITE_EXCEPTION_API_URL=http://localhost:8083
VITE_PAYMENT_API_URL=http://localhost:8084

# WebSocket for real-time updates
VITE_WS_URL=ws://localhost:8080

# Your tenant ID (get from backend)
VITE_TENANT_ID=demo-tenant-uuid

# Feature flags
VITE_ENABLE_DARK_MODE=true
VITE_ENABLE_ANALYTICS=true
```

## Step 4: Verify Backend Services

Ensure all backend services are running:

```bash
# Test Invoice Ingestion Context
curl http://localhost:8080/actuator/health

# Test Validation Context
curl http://localhost:8082/api/v1/validation/health

# Test Exception Handling Context
curl http://localhost:8083/api/v1/exceptions/health

# Test Payment Orchestration Context (if implemented)
curl http://localhost:8084/api/v1/payments/health
```

If any service is down, start the backend first:
```bash
cd d:\invoice_management_hp
docker-compose up -d
```

## Step 5: Start Development Server

```bash
npm run dev
```

You should see:
```
VITE v5.0.11  ready in 1234 ms

➜  Local:   http://localhost:3000/
➜  Network: use --host to expose
➜  press h to show help
```

Open your browser and navigate to [http://localhost:3000](http://localhost:3000)

## Step 6: Verify Application

### 6.1 Dashboard Should Load
- You should see the Kanban board with 4 columns
- Stats cards at the top
- Upload Invoice button

### 6.2 Upload Test Invoice
1. Click "Upload Invoice" button
2. Drag & drop a PDF invoice or click to browse
3. Submit the invoice
4. Watch it appear in the Ingestion column

### 6.3 Check Real-time Updates
- The dashboard should automatically refresh
- Invoices move through workflow stages
- Stats update in real-time

## Development Commands

### Run Development Server
```bash
npm run dev
```

### Type Check
```bash
npm run type-check
```

### Lint Code
```bash
npm run lint
```

### Fix Lint Issues
```bash
npm run lint:fix
```

### Format Code
```bash
npm run format
```

### Run Tests
```bash
npm test
```

### Run Tests with UI
```bash
npm run test:ui
```

### Build for Production
```bash
npm run build
```

### Preview Production Build
```bash
npm run preview
```

### Analyze Bundle Size
```bash
npm run analyze
```

## Troubleshooting

### Issue: Module not found errors

**Solution**: Delete `node_modules` and reinstall
```bash
rm -rf node_modules package-lock.json
npm install
```

### Issue: Port 3000 already in use

**Solution**: Kill the process or use a different port
```bash
# Windows
netstat -ano | findstr :3000
taskkill /PID <PID> /F

# Use different port
npm run dev -- --port 3001
```

### Issue: Backend API not connecting

**Solution**: Check CORS configuration in backend
```java
// Add to backend application.properties
spring.web.cors.allowed-origins=http://localhost:3000
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*
```

### Issue: TypeScript errors

**Solution**: Update TypeScript and check tsconfig.json
```bash
npm install typescript@latest
npm run type-check
```

### Issue: Tailwind CSS not working

**Solution**: Rebuild and clear cache
```bash
rm -rf node_modules/.cache
npm run dev
```

### Issue: WebSocket connection fails

**Solution**: Check backend WebSocket configuration
```typescript
// Test WebSocket connection
const socket = new WebSocket('ws://localhost:8080')
socket.onopen = () => console.log('Connected')
socket.onerror = (err) => console.error('Error:', err)
```

## Production Deployment

### Option 1: Docker

```bash
# Build Docker image
docker build -t invoice-frontend:latest .

# Run container
docker run -p 80:80 invoice-frontend:latest
```

### Option 2: Vercel

```bash
# Install Vercel CLI
npm install -g vercel

# Deploy
vercel
```

### Option 3: Netlify

```bash
# Build
npm run build

# Deploy dist/ folder to Netlify
```

### Option 4: AWS S3 + CloudFront

```bash
# Build
npm run build

# Upload to S3
aws s3 sync dist/ s3://your-bucket-name

# Invalidate CloudFront cache
aws cloudfront create-invalidation --distribution-id YOUR_DIST_ID --paths "/*"
```

## Testing the Complete Workflow

### 1. Upload Invoice
```bash
# Navigate to dashboard
http://localhost:3000/dashboard

# Click "Upload Invoice"
# Upload a test PDF invoice
```

### 2. Watch AI Extraction
- Invoice appears in "Ingestion" column
- AI extracts metadata (GPT-4 Vision)
- Confidence score displays
- Click invoice to view details

### 3. Validation Stage
- Invoice moves to "Validation" column
- 2-way or 3-way matching runs
- Check validation results

### 4. Exception Handling
- If mismatch detected, invoice moves to "Exceptions"
- View ML recommendations
- Resolve or escalate exception

### 5. Payment Stage
- After validation passes, invoice moves to "Payment"
- Payment can be approved and executed

## Performance Optimization

### Enable Production Mode
```bash
npm run build
npm run preview
```

### Check Lighthouse Score
1. Open Chrome DevTools
2. Go to Lighthouse tab
3. Run audit
4. Target scores: 95+ for all metrics

### Optimize Bundle Size
```bash
npm run analyze
```

Check for:
- Large dependencies
- Duplicate packages
- Unnecessary imports

### Enable Caching
Configure React Query cache:
```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5000,
      cacheTime: 300000,
    },
  },
})
```

## Next Steps

### Implement Additional Features
1. **PDF Viewer**: Use react-pdf for inline document viewing
2. **Analytics Dashboard**: Add Recharts visualizations
3. **Advanced Filtering**: Implement search and filter
4. **Bulk Operations**: Multi-select and bulk actions
5. **Export Functionality**: CSV/Excel export

### Connect WebSocket
```typescript
// src/hooks/use-websocket.ts
import { useEffect } from 'react'
import { io } from 'socket.io-client'

export function useWebSocket() {
  useEffect(() => {
    const socket = io('ws://localhost:8080')

    socket.on('InvoiceExtractedEvent', (data) => {
      console.log('Invoice extracted:', data)
      // Invalidate queries to refresh UI
    })

    return () => socket.disconnect()
  }, [])
}
```

### Add Authentication
```typescript
// src/context/AuthContext.tsx
import { createContext, useContext } from 'react'

interface AuthContext {
  user: User | null
  login: (credentials: Credentials) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContext>(null!)
```

### Implement Testing
```typescript
// src/components/__tests__/Dashboard.test.tsx
import { render, screen } from '@testing-library/react'
import { Dashboard } from '../Dashboard'

test('renders dashboard with kanban board', () => {
  render(<Dashboard />)
  expect(screen.getByText('Ingestion')).toBeInTheDocument()
  expect(screen.getByText('Validation')).toBeInTheDocument()
})
```

## Support

For issues or questions:
1. Check [README.md](./README.md) for detailed documentation
2. Review backend [SYSTEM_OVERVIEW.md](../SYSTEM_OVERVIEW.md)
3. Check browser console for errors
4. Verify backend logs

## Success Checklist

- [ ] Node.js 18+ installed
- [ ] Dependencies installed successfully
- [ ] Environment variables configured
- [ ] Backend services running
- [ ] Development server starts
- [ ] Dashboard loads correctly
- [ ] Invoice upload works
- [ ] API calls successful
- [ ] No console errors
- [ ] Dark mode toggle works

---

**Happy coding! 🚀**
