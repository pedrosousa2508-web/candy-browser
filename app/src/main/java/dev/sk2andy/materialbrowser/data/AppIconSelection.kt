package dev.sk2andy.materialbrowser.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.sk2andy.materialbrowser.BuildConfig

/** The enabled launcher alias is the persisted icon choice. */
internal enum class AppIconOption(
    val aliasName: String,
) {
    Classic("LauncherIconClassic"),
    Cookie("LauncherIconCookie"),
    CookieMaxed("LauncherIconCookieMaxed"),
    CottonCandy("LauncherIconCottonCandy"),
    CottonCandyFreeform("LauncherIconCottonCandyFreeform"),
    Lemon("LauncherIconLemon"),
    LemonMaxed("LauncherIconLemonMaxed"),
    Donut("LauncherIconDonut"),
    DonutMaxed("LauncherIconDonutMaxed"),
    JellyBeans("LauncherIconJellyBeans"),
    ;

    companion object {
        fun availableOptions(blackHole: Boolean): List<AppIconOption> = if (blackHole) {
            listOf(
                Classic,
                Cookie,
                CottonCandy,
                Lemon,
                Donut,
                CookieMaxed,
                CottonCandyFreeform,
                LemonMaxed,
                DonutMaxed,
            )
        } else {
            entries
        }
    }

    fun componentName(context: Context): ComponentName = ComponentName(
        context.packageName,
        "dev.sk2andy.materialbrowser.$aliasName",
    )
}

internal class AppIconSelection(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    val options: List<AppIconOption> = AppIconOption.availableOptions(BuildConfig.BLACK_HOLE_BRANDING)

    fun selected(): AppIconOption = options.firstOrNull { option ->
        option != AppIconOption.Classic &&
            packageManager.getComponentEnabledSetting(option.componentName(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } ?: AppIconOption.Classic

    fun select(option: AppIconOption) {
        require(option in options) { "Icon is not available in this build." }
        packageManager.setComponentEnabledSettings(
            AppIconOption.entries.map { entry ->
                PackageManager.ComponentEnabledSetting(
                    entry.componentName(context),
                    if (entry == option) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            },
        )
    }
}
