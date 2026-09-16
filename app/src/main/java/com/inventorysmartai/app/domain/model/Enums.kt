package com.inventorysmartai.app.domain.model

/** Derived at query time from quantity/minStock/expiryDate — never stored as a column. */
enum class InventoryStatus { AVAILABLE, LOW, ZERO, NEAR_EXPIRY, EXPIRED }

// Phase 2 spec asks for: PURCHASE, SALE, COUNT_ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, RETURN_IN,
// RETURN_OUT, MANUAL_ADJUSTMENT. Kept the existing PURCHASE_IN/SALE_OUT/MANUAL names (already
// referenced across repositories, dashboards and tests) rather than a pure rename; added the two
// genuinely-missing states (RETURN_IN, RETURN_OUT) plus a MANUAL_ADJUSTMENT alias-in-spirit is
// covered by MANUAL already.
enum class MovementType { PURCHASE_IN, SALE_OUT, COUNT_ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, RETURN_IN, RETURN_OUT, MANUAL }

enum class CountStatus { DRAFT, IN_PROGRESS, COMPLETED, CANCELLED }

// Spec: DRAFT, PENDING, APPROVED, PARTIALLY_APPROVED, REJECTED, COMPLETED, CANCELLED.
// SUBMITTED/ORDERED/RECEIVED already cover PENDING/approved-ordering/COMPLETED in spirit;
// PARTIALLY_APPROVED and REJECTED were genuinely missing states and are added here.
enum class PurchaseStatus { DRAFT, SUBMITTED, APPROVED, PARTIALLY_APPROVED, REJECTED, ORDERED, RECEIVED, CANCELLED }

enum class InvoiceStatus { DRAFT, CONFIRMED, CANCELLED }

enum class CommissionType { PERCENTAGE, FIXED }

enum class ImportSourceType { EXCEL, CSV, PDF, IMAGE, CAMERA, BARCODE, MANUAL }

enum class ImportJobStatus { PENDING, PROCESSING, REVIEW_REQUIRED, COMPLETED, FAILED }

// Spec: PENDING, MATCHED, NEW_PRODUCT, DUPLICATE, ERROR, ACCEPTED, REJECTED — each tracked
// separately so "accepted rows" is never conflated with "unique products" (see ImportJob counts).
enum class ImportRowStatus { PENDING, MATCHED, NEW_PRODUCT, DUPLICATE, ERROR, ACCEPTED, REJECTED }

enum class AttachmentOwnerType { PRODUCT, PURCHASE_REQUEST, SALES_INVOICE, INVENTORY_COUNT }

enum class AuditAction { CREATE, UPDATE, DELETE, IMPORT, APPROVE, REJECT, ADJUST_STOCK }

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class SortOrder { NAME_ASC, NAME_DESC, QUANTITY_ASC, QUANTITY_DESC }

enum class InventoryStatusFilter { ALL, AVAILABLE, LOW, ZERO, NEAR_EXPIRY, EXPIRED }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class CatalogType { BRANCH, CATEGORY, UNIT }
