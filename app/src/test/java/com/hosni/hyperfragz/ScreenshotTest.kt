package com.hosni.hyperfragz

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hosni.hyperfragz.ui.DetailScreen
import com.hosni.hyperfragz.ui.IndexScreen
import com.hosni.hyperfragz.ui.LocalUpdater
import com.hosni.hyperfragz.ui.RefineSheet
import com.hosni.hyperfragz.ui.SavedScreen
import com.hosni.hyperfragz.ui.VitrineTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One image per screen, rendered on the JVM from [Samples] (no network, no WebView
 * data). Phone size matches the design/ PNGs: 390 × 844 dp, rendered at 2×.
 *
 *   ./gradlew :app:verifyRoborazziDebug    fail if any screen changed
 *   ./gradlew :app:compareRoborazziDebug   write before/after/diff images to app/build/outputs/roborazzi
 *   ./gradlew :app:recordRoborazziDebug    accept the changes as the new golden images
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val site by lazy { SiteController(ApplicationProvider.getApplicationContext<Application>()) }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { VitrineTheme(content) }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", options)

    @Test fun index_results() {
        site.state = Samples.results
        show { IndexScreen(site, onOpenFilters = {}, onOpenSaved = {}) }
        shot("1-index-results")
    }

    /** Debug app: a test build from .github/workflows/test-build.yml is waiting. */
    @Test fun index_test_build_banner() {
        site.state = Samples.results
        val updater = Updater(ApplicationProvider.getApplicationContext<Application>())
        updater.state = Updater.State.Available(
            version = "1.2.0-test.14", apkUrl = "", size = 0,
            label = "Test build 14 is ready", note = "New filter chips: check the Notes row",
        )
        show { CompositionLocalProvider(LocalUpdater provides updater) { IndexScreen(site, onOpenFilters = {}, onOpenSaved = {}) } }
        shot("1-index-test-build-banner")
    }

    @Test fun filter_sheet() {
        site.state = Samples.results
        show { RefineSheet(site, onDismiss = {}) }
        // The sheet is a separate window: capture the whole screen, not the root node.
        captureScreenRoboImage("src/test/screenshots/2-filter-sheet.png", options)
    }

    @Test fun index_no_results() {
        site.state = Samples.noResults
        show { IndexScreen(site, onOpenFilters = {}, onOpenSaved = {}) }
        shot("3-index-no-results")
    }

    /** Tall viewport so the whole page fits in one image, like design/4. */
    @Config(qualifiers = TALL)
    @Test fun detail_full() {
        show { DetailScreen(site, Samples.detail) }
        shot("4-detail-full")
    }

    @Test fun detail_loading() {
        show { DetailScreen(site, Samples.detailLoading) }
        shot("4-detail-loading")
    }

    @Test fun saved_empty() {
        show { SavedScreen(site, onBack = {}) }
        shot("5-saved-empty")
    }

    @Test fun saved_items() {
        Samples.hits.take(3).forEach { site.favorites.toggle(Detail.preview(it)) }
        show { SavedScreen(site, onBack = {}) }
        shot("5-saved-items")
    }

    private companion object {
        // Small rendering differences between machines (anti-aliasing) aren't changes.
        val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))
    }
}

private const val PHONE = "w390dp-h844dp-xhdpi"
private const val TALL = "w390dp-h4480dp-xhdpi"
