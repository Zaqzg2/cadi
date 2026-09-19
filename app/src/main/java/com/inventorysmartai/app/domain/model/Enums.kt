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

// Phase 3 spec: PROCESSING, REVIEW, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED.
// PENDING (job row created, file not analyzed yet) and REVIEW_REQUIRED (== spec's "REVIEW") were
// already in use from Phase 1/2 and are kept as-is; PARTIALLY_COMPLETED and CANCELLED were
// genuinely missing and are added here.
enum class ImportJobStatus { PENDING, PROCESSING, REVIEW_REQUIRED, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED }

// Spec: PENDING, MATCHED, NEW_PRODUCT, DUPLICATE, ERROR, ACCEPTED, REJECTED — each tracked
// separately so "accepted rows" is never conflated with "unique products" (see ImportJob counts).
// AMBIGUOUS is Phase 3's addition for ProductMatcher's AMBIGUOUS match status (spec section 10) —
// distinct from ERROR (a validation failure) and from PENDING (not analyzed yet / needs a manual
// decision but isn't inherently a problem).
enum class ImportRowStatus { PENDING, MATCHED, NEW_PRODUCT, AMBIGUOUS, DUPLICATE, ERROR, ACCEPTED, REJECTED }

/** Structured error codes, verbatim from the Phase 3 spec's "Error Management" section — stored
 *  against an ImportRow (see ImportRowEntity.errorCode) instead of only a free-text message, so
 *  the review UI can group/filter/localize without string-matching. */
enum class ImportErrorCode {
    MISSING_REQUIRED_FIELD,
    INVALID_NUMBER,
    INVALID_DATE,
    INVALID_BARCODE,
    AMBIGUOUS_PRODUCT,
    DUPLICATE_ROW,
    UNSUPPORTED_FORMAT,
    EMPTY_FILE,
    INVALID_HEADER,
    DATABASE_ERROR
}

enum class AttachmentOwnerType { PRODUCT, PURCHASE_REQUEST, SALES_INVOICE, INVENTORY_COUNT }

enum class AuditAction { CREATE, UPDATE, DELETE, IMPORT, APPROVE, REJECT, ADJUST_STOCK }

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class SortOrder { NAME_ASC, NAME_DESC, QUANTITY_ASC, QUANTITY_DESC }

enum class InventoryStatusFilter { ALL, AVAILABLE, LOW, ZERO, NEAR_EXPIRY, EXPIRED }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class CatalogType { BRANCH, CATEGORY, UNIT }
