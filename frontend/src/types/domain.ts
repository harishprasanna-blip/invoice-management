/**
 * Domain Types - TypeScript interfaces matching backend domain models
 * Aligned with DDD bounded contexts: InvoiceIngestion, Validation, ExceptionHandling, PaymentOrchestration
 */

// ============================================================================
// Shared Kernel Types
// ============================================================================

export interface Money {
  amount: string
  currency: string
}

export interface TenantId {
  id: string
}

export interface AuditEntry {
  action: string
  userId: string
  timestamp: string
  details: string
}

// ============================================================================
// Invoice Ingestion Context
// ============================================================================

export enum IngestionStatus {
  RECEIVED = 'RECEIVED',
  EXTRACTING = 'EXTRACTING',
  EXTRACTED = 'EXTRACTED',
  VALIDATION_PENDING = 'VALIDATION_PENDING',
  EXTRACTION_FAILED = 'EXTRACTION_FAILED',
}

export enum DocumentFormat {
  PDF = 'PDF',
  EDI_X12 = 'EDI_X12',
  EDIFACT = 'EDIFACT',
  XML_UBL = 'XML_UBL',
  EMAIL = 'EMAIL',
}

export enum ExtractionMethod {
  GPT4_VISION = 'GPT4_VISION',
  EDI_PARSER = 'EDI_PARSER',
  XML_PARSER = 'XML_PARSER',
  OCR = 'OCR',
}

export interface VendorReference {
  vendorId: string
  vendorName: string
  taxId?: string
  address?: string
  email?: string
}

export interface DocumentReference {
  originalFileName: string
  s3Bucket: string
  s3Key: string
  documentFormat: DocumentFormat
  uploadTimestamp: string
  fileSizeBytes: number
}

export interface InvoiceMetadata {
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  currency: Money
  totalAmount: Money
  taxAmount?: Money
  subtotalAmount?: Money
  poNumber?: string
}

export interface LineItem {
  lineNumber: number
  description: string
  quantity: number
  unitPrice: Money
  lineTotal: Money
  taxRate?: number
  poLineNumber?: number
  productCode?: string
}

export interface ExtractionResult {
  extractionId: string
  confidenceScore: string
  extractionMethod: ExtractionMethod
  extractedAt: string
  retryCount: number
  errorMessage?: string
}

export interface Invoice {
  invoiceId: string
  tenantId: string
  ingestionStatus: IngestionStatus
  vendorReference?: VendorReference
  invoiceMetadata?: InvoiceMetadata
  lineItems: LineItem[]
  extractionResult?: ExtractionResult
  documentReference: DocumentReference
  createdAt: string
  updatedAt: string
}

// ============================================================================
// Validation Context
// ============================================================================

export enum ValidationStatus {
  PENDING = 'PENDING',
  IN_PROGRESS = 'IN_PROGRESS',
  PASSED = 'PASSED',
  FAILED_MATCHING = 'FAILED_MATCHING',
  FAILED_COMPLIANCE = 'FAILED_COMPLIANCE',
}

export enum MatchType {
  TWO_WAY = 'TWO_WAY',
  THREE_WAY = 'THREE_WAY',
}

export enum MatchStatus {
  MATCHED = 'MATCHED',
  PRICE_VARIANCE = 'PRICE_VARIANCE',
  QUANTITY_VARIANCE = 'QUANTITY_VARIANCE',
  TOTAL_VARIANCE = 'TOTAL_VARIANCE',
  MISSING_PO = 'MISSING_PO',
  MISSING_GR = 'MISSING_GR',
}

export enum ComplianceCheckType {
  GDPR_CONSENT = 'GDPR_CONSENT',
  TAX_VALIDATION = 'TAX_VALIDATION',
  DUPLICATE_CHECK = 'DUPLICATE_CHECK',
  SANCTIONS_SCREENING = 'SANCTIONS_SCREENING',
  DATA_RETENTION = 'DATA_RETENTION',
}

export enum ComplianceStatus {
  PASSED = 'PASSED',
  FAILED = 'FAILED',
  WARNING = 'WARNING',
  NOT_APPLICABLE = 'NOT_APPLICABLE',
}

export interface PurchaseOrderReference {
  poNumber: string
  poDate: string
  vendorId: string
  poAmount: Money
  poLineItems: POLineItem[]
  erpSystem: string
}

export interface POLineItem {
  lineNumber: number
  description: string
  quantity: number
  unitPrice: Money
  lineTotal: Money
  productCode?: string
}

export interface GoodsReceiptReference {
  grNumber: string
  grDate: string
  poNumber: string
  grLineItems: GRLineItem[]
  receivedBy: string
}

export interface GRLineItem {
  lineNumber: number
  poLineNumber: number
  quantityReceived: number
  receivedDate: string
}

export interface VarianceDetail {
  field: string
  invoiceValue: string
  poValue?: string
  grValue?: string
  varianceAmount?: string
  variancePercentage?: string
  withinTolerance: boolean
}

export interface MatchingResult {
  matchType: MatchType
  matchStatus: MatchStatus
  overallScore: string
  variances: VarianceDetail[]
  matchedAt: string
}

export interface ComplianceCheck {
  checkType: ComplianceCheckType
  status: ComplianceStatus
  checkResult: string
  checkedAt: string
  failureReason?: string
}

export interface PayableTransaction {
  transactionId: string
  tenantId: string
  invoiceReference: {
    invoiceId: string
    invoiceNumber: string
    vendorId: string
    totalAmount: Money
  }
  validationStatus: ValidationStatus
  poReference?: PurchaseOrderReference
  grReference?: GoodsReceiptReference
  matchingResult?: MatchingResult
  complianceChecks: ComplianceCheck[]
  readyForPayment: boolean
  validatedAt?: string
  createdAt: string
  updatedAt: string
}

// ============================================================================
// Exception Handling Context
// ============================================================================

export enum ExceptionType {
  MATCHING_MISMATCH = 'MATCHING_MISMATCH',
  COMPLIANCE_VIOLATION = 'COMPLIANCE_VIOLATION',
  EXTRACTION_ERROR = 'EXTRACTION_ERROR',
  PAYMENT_FAILURE = 'PAYMENT_FAILURE',
  DUPLICATE_INVOICE = 'DUPLICATE_INVOICE',
  MISSING_PO = 'MISSING_PO',
  VENDOR_ISSUE = 'VENDOR_ISSUE',
  DATA_QUALITY = 'DATA_QUALITY',
}

export enum ExceptionSeverity {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL',
}

export enum ExceptionStatus {
  OPEN = 'OPEN',
  IN_PROGRESS = 'IN_PROGRESS',
  RESOLVED = 'RESOLVED',
  ESCALATED = 'ESCALATED',
  CLOSED = 'CLOSED',
}

export enum EscalationLevel {
  L0_AUTOMATED = 'L0_AUTOMATED',
  L1_AP_CLERK = 'L1_AP_CLERK',
  L2_AP_MANAGER = 'L2_AP_MANAGER',
  L3_FINANCE_CONTROLLER = 'L3_FINANCE_CONTROLLER',
  L4_EXECUTIVE = 'L4_EXECUTIVE',
}

export enum ResolutionStrategy {
  ACCEPT_AND_PROCEED = 'ACCEPT_AND_PROCEED',
  REQUEST_VENDOR_CORRECTION = 'REQUEST_VENDOR_CORRECTION',
  MANUAL_ADJUSTMENT = 'MANUAL_ADJUSTMENT',
  CREDIT_MEMO = 'CREDIT_MEMO',
  HOLD_PENDING_INVESTIGATION = 'HOLD_PENDING_INVESTIGATION',
  REJECT_INVOICE = 'REJECT_INVOICE',
  COMPLIANCE_OVERRIDE = 'COMPLIANCE_OVERRIDE',
  UPDATE_PROCUREMENT_DATA = 'UPDATE_PROCUREMENT_DATA',
  PARTIAL_PAYMENT = 'PARTIAL_PAYMENT',
  ESCALATE_TO_VENDOR_MANAGEMENT = 'ESCALATE_TO_VENDOR_MANAGEMENT',
}

export enum ResolutionActionType {
  COMMENT = 'COMMENT',
  INVESTIGATION = 'INVESTIGATION',
  VENDOR_CONTACT = 'VENDOR_CONTACT',
  DATA_CORRECTION = 'DATA_CORRECTION',
  APPROVAL_REQUEST = 'APPROVAL_REQUEST',
  ESCALATION = 'ESCALATION',
  SYSTEM_ADJUSTMENT = 'SYSTEM_ADJUSTMENT',
}

export interface MLRecommendation {
  recommendationId: string
  strategy: ResolutionStrategy
  confidenceScore: string
  reasoning: string
  suggestedActions: string[]
  estimatedResolutionTime: string
  generatedAt: string
  modelVersion: string
}

export interface ResolutionAction {
  actionId: string
  actionType: ResolutionActionType
  description: string
  performedBy: string
  performedAt: string
  result?: string
}

export interface ExceptionCase {
  caseId: string
  tenantId: string
  exceptionType: ExceptionType
  severity: ExceptionSeverity
  status: ExceptionStatus
  escalationLevel: EscalationLevel
  sourceContext: string
  sourceEntityId: string
  title: string
  description: string
  impactedAmount?: Money
  mlRecommendations: MLRecommendation[]
  resolutionHistory: ResolutionAction[]
  assignedTo?: string
  priorityScore: number
  slaDeadline: string
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  resolutionNotes?: string
}

// ============================================================================
// Payment Orchestration Context
// ============================================================================

export enum PaymentStatus {
  PENDING = 'PENDING',
  SCHEDULED = 'SCHEDULED',
  APPROVED = 'APPROVED',
  EXECUTING = 'EXECUTING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED',
}

export interface Payment {
  paymentId: string
  invoiceId: string
  vendorId: string
  amount: Money
  paymentDate: string
  paymentMethod: string
  status: PaymentStatus
  referenceNumber?: string
}

export interface PaymentRun {
  paymentRunId: string
  tenantId: string
  runDate: string
  totalPayments: number
  totalAmount: Money
  status: PaymentStatus
  payments: Payment[]
  createdAt: string
  completedAt?: string
}

// ============================================================================
// Dashboard & Analytics Types
// ============================================================================

export interface DashboardStats {
  totalOpenCases: number
  criticalCases: number
  highPriorityCases: number
  averageResolutionTime: string
  slaBreach: number
  automationRate: string
  pendingInvoices: number
  validatedInvoices: number
  exceptionInvoices: number
  paidInvoices: number
}

export interface ProcessingMetrics {
  date: string
  totalInvoices: number
  touchlessRate: number
  averageProcessingTime: number
  extractionAccuracy: number
}

export interface WorkflowStage {
  stage: 'ingestion' | 'validation' | 'exception' | 'payment'
  count: number
  amount: Money
}

// ============================================================================
// API Response Types
// ============================================================================

export interface ApiResponse<T> {
  data?: T
  error?: {
    message: string
    code: string
    details?: unknown
  }
  meta?: {
    timestamp: string
    requestId: string
  }
}

export interface PaginatedResponse<T> {
  data: T[]
  pagination: {
    page: number
    pageSize: number
    totalItems: number
    totalPages: number
  }
}

// ============================================================================
// WebSocket Event Types
// ============================================================================

export enum EventType {
  INVOICE_RECEIVED = 'InvoiceReceivedEvent',
  INVOICE_EXTRACTED = 'InvoiceExtractedEvent',
  EXTRACTION_FAILED = 'ExtractionFailedEvent',
  VALIDATION_STARTED = 'ValidationStartedEvent',
  VALIDATION_PASSED = 'ValidationPassedEvent',
  MISMATCH_DETECTED = 'MismatchDetectedEvent',
  COMPLIANCE_VIOLATION = 'ComplianceViolationDetectedEvent',
  EXCEPTION_CREATED = 'ExceptionCreatedEvent',
  EXCEPTION_RESOLVED = 'ExceptionResolvedEvent',
  EXCEPTION_ESCALATED = 'ExceptionEscalatedEvent',
  SLA_BREACHED = 'SLABreachedEvent',
  PAYMENT_APPROVED = 'PaymentApprovedEvent',
  PAYMENT_EXECUTED = 'PaymentExecutedEvent',
  PAYMENT_FAILED = 'PaymentFailedEvent',
}

export interface DomainEvent {
  eventId: string
  eventType: EventType
  aggregateType: string
  aggregateId: string
  tenantId: string
  payload: unknown
  occurredAt: string
}

// ============================================================================
// Form Types (for React Hook Form)
// ============================================================================

export interface InvoiceUploadForm {
  file: File
  vendorId?: string
  vendorName?: string
}

export interface MetadataCorrectionForm {
  invoiceNumber: string
  invoiceDate: string
  dueDate: string
  currency: string
  totalAmount: string
  reason: string
}

export interface ResolutionActionForm {
  actionType: ResolutionActionType
  description: string
  result?: string
}

export interface ResolveExceptionForm {
  resolutionNotes: string
}

export interface EscalateExceptionForm {
  reason: string
}

// ============================================================================
// UI State Types
// ============================================================================

export interface ToastNotification {
  id: string
  type: 'success' | 'error' | 'warning' | 'info'
  title: string
  message?: string
  duration?: number
}

export interface LoadingState {
  isLoading: boolean
  message?: string
}

export interface FilterOptions {
  status?: string[]
  severity?: string[]
  dateRange?: {
    from: string
    to: string
  }
  assignee?: string
  vendor?: string
}
