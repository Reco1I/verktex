package com.reco1l.verktex.tests

import com.reco1l.verktex.android.UITestActivity
import com.reco1l.verktex.testing.VerktexUITests

/**
 * The UI test browser of Verktex's own components on Android.
 */
class VerktexUITestActivity : UITestActivity() {

    override fun createSuites() = listOf(VerktexUITests)
}
