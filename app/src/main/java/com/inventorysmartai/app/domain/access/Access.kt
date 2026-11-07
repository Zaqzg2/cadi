package com.inventorysmartai.app.domain.access

/** ما يمكن للمستخدم فعله في التطبيق. الأدوار ([AppRole]) مجموعات جاهزة منها. */
enum class Permission(val titleAr: String) {
    VIEW_STOCK("عرض المخزون"),
    MANAGE_STOCK("تعديل المخزون والجرد والتحويل والمرتجعات"),
    MANAGE_SALES("المبيعات والعملاء"),
    MANAGE_PURCHASES("المشتريات والموردون"),
    VIEW_GOALS("عرض الأهداف"),
    MANAGE_GOALS("تعديل الأهداف والعمولات"),
    VIEW_REPORTS("التقارير"),
    IMPORT_DATA("مركز البيانات والاستيراد"),
    USE_ASSISTANT("المساعد الذكي"),
    MANAGE_SETTINGS("الإعدادات"),
    MANAGE_BACKUP("النسخ الاحتياطي والاستعادة"),
    MANAGE_USERS("المستخدمون والصلاحيات")
}

enum class AppRole(val titleAr: String, val permissions: Set<Permission>) {
    OWNER("مدير", Permission.entries.toSet()),
    SALES(
        "مبيعات",
        setOf(Permission.VIEW_STOCK, Permission.MANAGE_SALES, Permission.VIEW_GOALS, Permission.VIEW_REPORTS, Permission.USE_ASSISTANT)
    ),
    STOREKEEPER(
        "أمين مخزن",
        setOf(Permission.VIEW_STOCK, Permission.MANAGE_STOCK, Permission.MANAGE_PURCHASES, Permission.VIEW_REPORTS, Permission.USE_ASSISTANT)
    ),
    VIEWER("مشاهد", setOf(Permission.VIEW_STOCK, Permission.VIEW_GOALS, Permission.VIEW_REPORTS))
}

/** مستخدم محلي. الرمز لا يُخزَّن أبدًا؛ فقط ملح وتجزئة PBKDF2 بصيغة Base64 ([PinHasher]). */
data class UserProfile(
    val id: String,
    val name: String,
    val role: AppRole,
    val pinSalt: String,
    val pinHash: String
)

/**
 * حالة الوصول الحالية. [enabled] = false (الافتراضي) تعني أن التطبيق مفتوح لكل شيء كما كان قبل هذه الميزة.
 * إن كان مفعّلًا ولا مستخدم داخل فهو [locked].
 */
data class AccessState(
    val loaded: Boolean = false,
    val enabled: Boolean = false,
    val profiles: List<UserProfile> = emptyList(),
    val currentUserId: String? = null
) {
    val currentUser: UserProfile? get() = profiles.firstOrNull { it.id == currentUserId }
    val locked: Boolean get() = enabled && currentUser == null

    fun can(permission: Permission): Boolean = !enabled || currentUser?.role?.permissions?.contains(permission) == true

    /** هل يُسمح بفتح هذه الوجهة؟ مسار بلا قاعدة (لم يُصنَّف) يُفتح كي لا تتعطل شاشة جديدة؛ والاختبار يمنع وجود مسار غير مصنَّف. */
    fun canOpen(route: String?): Boolean {
        if (!enabled) return true
        val needed = RoutePermissions.required(route) ?: return true
        if (needed.isEmpty()) return true
        val permissions = currentUser?.role?.permissions ?: return false
        return needed.any { it in permissions }
    }
}

/** قواعد صرفة لإدارة المستخدمين (تُستعمل في المدير والشاشة، وتُختبر وحدها). كل دالة تعيد رسالة الخطأ أو null إن صحّ الطلب. */
object AccessRules {
    const val MIN_PIN = 4
    const val MAX_PIN = 8

    fun isValidPin(pin: String): Boolean = pin.length in MIN_PIN..MAX_PIN && pin.all { it in '0'..'9' }

    fun pinError(pin: String): String? =
        if (isValidPin(pin)) null else "الرمز من $MIN_PIN إلى $MAX_PIN أرقام فقط"

    fun nameError(name: String, existing: List<UserProfile>, ignoreId: String? = null): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return "اكتب اسم المستخدم"
        if (existing.any { it.id != ignoreId && it.name.trim().equals(clean, ignoreCase = true) }) return "هذا الاسم مستعمل"
        return null
    }

    fun removeError(profiles: List<UserProfile>, targetId: String, currentUserId: String?): String? {
        val target = profiles.firstOrNull { it.id == targetId } ?: return "المستخدم غير موجود"
        if (target.id == currentUserId) return "لا يمكنك حذف المستخدم الذي دخلت به. ادخل بمستخدم آخر أولًا."
        if (target.role == AppRole.OWNER && profiles.count { it.role == AppRole.OWNER } <= 1) return "لا بدّ من وجود مدير واحد على الأقل"
        return null
    }

    fun roleChangeError(profiles: List<UserProfile>, targetId: String, newRole: AppRole): String? {
        val target = profiles.firstOrNull { it.id == targetId } ?: return "المستخدم غير موجود"
        if (target.role == AppRole.OWNER && newRole != AppRole.OWNER && profiles.count { it.role == AppRole.OWNER } <= 1) {
            return "لا بدّ من وجود مدير واحد على الأقل"
        }
        return null
    }
}

/**
 * أي صلاحية تفتح أي شاشة. المفتاح هو بداية المسار (قبل «؟»)، والأطول يسبق الأقصر. المجموعة الفارغة = مفتوحة للجميع،
 * وأي صلاحية واحدة من المجموعة تكفي. null = مسار غير مصنَّف (يمنع الاختبار وجود مثله).
 */
object RoutePermissions {
    private val open = emptySet<Permission>()

    private val rules: List<Pair<String, Set<Permission>>> = listOf(
        "home" to open,
        "more" to open,
        "stock/operations/new" to setOf(Permission.MANAGE_STOCK),
        "stock/operations" to setOf(Permission.VIEW_STOCK, Permission.MANAGE_STOCK),
        "stock" to setOf(Permission.VIEW_STOCK, Permission.MANAGE_STOCK),
        "inventory" to setOf(Permission.VIEW_STOCK, Permission.MANAGE_STOCK),
        // المخزون المفلتر الذي يفتحه المساعد: اسم مساره «inventory_view» لا يبدأ بـ«inventory/» فيحتاج قاعدة بنفسه.
        "inventory_view" to setOf(Permission.VIEW_STOCK, Permission.MANAGE_STOCK),
        "counting" to setOf(Permission.MANAGE_STOCK),
        "purchases" to setOf(Permission.MANAGE_PURCHASES),
        "sales" to setOf(Permission.MANAGE_SALES),
        "goals/detail" to setOf(Permission.MANAGE_GOALS),
        "goals" to setOf(Permission.VIEW_GOALS, Permission.MANAGE_GOALS),
        "reports" to setOf(Permission.VIEW_REPORTS),
        "assistant" to setOf(Permission.USE_ASSISTANT),
        "data_center/manual_entry" to setOf(Permission.MANAGE_STOCK),
        "data_center/parties" to setOf(Permission.MANAGE_SALES, Permission.MANAGE_PURCHASES),
        "data_center" to setOf(Permission.IMPORT_DATA),
        "import_flow" to setOf(Permission.IMPORT_DATA),
        "settings/backup" to setOf(Permission.MANAGE_BACKUP),
        "settings/permissions" to setOf(Permission.MANAGE_USERS),
        "settings" to setOf(Permission.MANAGE_SETTINGS)
    )

    fun required(route: String?): Set<Permission>? {
        val path = route?.substringBefore('?') ?: return open
        return rules.firstOrNull { (prefix, _) -> path == prefix || path.startsWith("$prefix/") }?.second
    }
}
