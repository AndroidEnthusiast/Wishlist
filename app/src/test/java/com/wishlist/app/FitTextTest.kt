package com.wishlist.app

import android.Manifest
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config as Robo

/**
 * Amounts are never cut off (2.209, owner 15 November 2026: "€1,434.56" was cut off on Home in Gen Z chaos,
 * "unacceptable"). The real app on the smallest common phone (360 dp) with big text (130 %) and a total of
 * €12,345.67: Home's "Total value" must fit in every style.
 */
@RunWith(RobolectricTestRunner::class)
@Robo(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi", fontScale = 1.3f)
abstract class AmountFitsBase(private val style: String) {
    private val setup = object : TestRule {
        override fun apply(base: Statement, d: Description) = object : Statement() {
            override fun evaluate() { prepare(); base.evaluate() }
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(setup).around(compose)

    private fun prepare() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.getSharedPreferences("wishlist", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("asked_background", true).putString("sh_me", "Sam").commit()
        ListStore.reset(); WishlistDb.reset()
        Repo.load(app)
        Repo.setLook(app, style, "dark", Repo.palette)
        Repo.addMine(app, Wish(1, "https://example.com/a", "Sofa", "", "", 12345.67, null, "EUR", "Home"))
    }

    @Test fun totalValueOnHomeFits() {
        compose.waitForIdle()
        val node = compose.onAllNodes(hasText("12,345.67", substring = true)).onFirst().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val r = layouts.single()
        assertFalse("$style: the total is cut off", r.didOverflowWidth || r.didOverflowHeight || r.isLineEllipsized(0))
    }
}

class AmountFitsQuiet : AmountFitsBase("quiet")
class AmountFitsChaos : AmountFitsBase("chaos")
