package com.gaozay.smartflight.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/** Resource access for runtime/permission formatters that do not have a Compose context.
 * Installed before Hilt creates models. Never caches translated text or holds an Activity.
 * Compose screens use stringResource/pluralStringResource directly.
 */
object AppStrings {
    @Volatile
    private var resolver: StringResolver? = null

    internal fun initialize(context: Context) {
        resolver = AndroidStringResolver(context.applicationContext ?: context)
    }

    internal fun installResolver(resolver: StringResolver) {
        this.resolver = resolver
    }

    fun get(@StringRes id: Int, vararg args: Any): String = current().get(id, *args)

    fun quantity(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        current().quantity(id, count, *args)

    // Stored logs may have been written before a language change. This is only for
    // displaying historical probe labels; execution decisions use structured fields.
    fun hasTranslatedPrefix(value: String?, @StringRes id: Int): Boolean =
        value != null && listOf(Locale.CHINESE, Locale.ENGLISH).any {
            value.startsWith(current().inLocale(id, it))
        }

    private fun current(): StringResolver = checkNotNull(resolver) {
        "AppStrings must be initialized before constructing app state"
    }
}

internal interface StringResolver {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun quantity(@PluralsRes id: Int, count: Int, vararg args: Any): String
    fun inLocale(@StringRes id: Int, locale: Locale): String
}

private class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    override fun quantity(id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)

    override fun inLocale(id: Int, locale: Locale): String {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration).getString(id)
    }
}

interface ResourceLabel {
    @get:StringRes
    val labelRes: Int
    val label: String get() = AppStrings.get(labelRes)
}
