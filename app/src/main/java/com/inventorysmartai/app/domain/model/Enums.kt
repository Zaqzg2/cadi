package com.inventorysmartai.app.domain.model

/** Derived at query time from quantity/minStock/expiryDate — never stored as a column. */
enum class InventoryStatus { AVAILABLE, LOW, ZERO, NEAR_EXPIRY, EXPIRED }

enum class MovementType { PURCHASE_IN, SALE_OUT, COUNT_ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, MANUAL }

enum class CountStatus { DRAFT, IN_PROGRESS, COMPLETED, CANCELLED }

enum class PurchaseStatus { DRAFT, SUBMITTED, APPROVED, ORDERED, RECEIVED, CANCELLED }

enum class InvoiceStatus { DRAFT, CONFIRMED, CANCELLED }

enum class CommissionType { PERCENTAGE, FIXED }

enum class ImportSourceType { EXCEL, CSV, PDF, IMAGE, CAMERA, BARCODE, MANUAL }

enum class ImportJobStatus { PENDING, PROCESSING, REVIEW_REQUIRED, COMPLETED, FAILED }

enum class ImportRowStatus { PENDING, MATCHED, NEW, REJECTED }

enum class AttachmentOwnerType { PRODUCT, PURCHASE_REQUEST, SALES_INVOICE, INVENTORY_COUNT }

enum class AuditAction { CREATE, UPDATE, DELETE }

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class SortOrder { NAME_ASC, NAME_DESC, QUANTITY_ASC, QUANTITY_DESC }

enum class InventoryStatusFilter { ALL, AVAILABLE, LOW, ZERO, NEAR_EXPIRY, EXPIRED }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class CatalogType { BRANCH, CATEGORY, UNIT }
