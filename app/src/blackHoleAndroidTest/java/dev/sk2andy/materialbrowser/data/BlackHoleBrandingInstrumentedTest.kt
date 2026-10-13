package dev.sk2andy.materialbrowser.data

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Release-compatible tests, run only on a dedicated disposable emulator. */
@RunWith(AndroidJUnit4::class)
class BlackHoleBrandingInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val selection = AppIconSelection(context)
    private val initialSelection = selection.selected()

    @After
    fun restoreLauncherChoice() {
        selection.select(initialSelection)
    }

    @Test
    fun identityAndAttributionResolveInEnglishAndPortuguese() {
        assertTrue(BuildConfig.BLACK_HOLE_BRANDING)
        assertEquals("dev.sk2andy.materialbrowser.blackholedev", context.packageName)
        for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("pt-BR"))) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val localized = context.createConfigurationContext(configuration)
            assertEquals("Black Hole Browser", localized.getString(R.string.app_name))
            assertEquals("Browse Beyond", localized.getString(R.string.black_hole_tagline))
            assertTrue(localized.getString(R.string.onboarding_welcome_title).contains("Black Hole"))
            assertTrue(localized.getString(R.string.about_imprint_body, "sk2andy").contains("Candy"))
        }
        assertEquals(
            listOf("Classic", "Neon", "Minimal", "Dark", "Cosmic", "Luxury", "Blue", "Red Shift", "Solar Flare"),
            listOf(R.string.bh_icon_classic, R.string.bh_icon_neon, R.string.bh_icon_minimal,
                R.string.bh_icon_dark, R.string.bh_icon_cosmic, R.string.bh_icon_luxury,
                R.string.bh_icon_blue, R.string.bh_icon_redshift, R.string.bh_icon_solar_flare)
                .map(context::getString),
        )
    }

    @Test
    fun eachOfNineIconsEnablesExactlyOneLauncherAndPersistsSelection() {
        assertEquals(9, selection.options.size)
        for (option in selection.options) {
            selection.select(option)
            assertEquals(option, AppIconSelection(context).selected())
            val launchers = context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(context.packageName),
                0,
            )
            assertEquals(1, launchers.size)
            assertEquals(option.componentName(context).className, launchers.single().activityInfo.name)
            val declared = context.packageManager.getActivityInfo(option.componentName(context), 0)
            assertTrue(declared.icon != 0)
            assertEquals("Black Hole Browser", declared.loadLabel(context.packageManager).toString())
        }
    }

    @Test
    fun legacyJellyBeansAliasRemainsLaunchableAndCanSwitchToNeon() {
        context.packageManager.setComponentEnabledSettings(
            AppIconOption.entries.map { option ->
                PackageManager.ComponentEnabledSetting(
                    option.componentName(context),
                    if (option == AppIconOption.JellyBeans) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            },
        )
        assertEquals(AppIconOption.Classic, selection.selected())
        selection.select(AppIconOption.Cookie)
        assertEquals(AppIconOption.Cookie, AppIconSelection(context).selected())
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            context.packageManager.getComponentEnabledSetting(AppIconOption.JellyBeans.componentName(context)),
        )
    }
}
