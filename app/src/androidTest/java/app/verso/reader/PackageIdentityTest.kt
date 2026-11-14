package app.verso.reader

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageIdentityTest {
    @Test fun installedApplicationLabelMatchesBuildVariant() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = if (context.packageName.endsWith(".testing")) "Verso Test" else "Verso"
        assertEquals(expected, context.packageManager.getApplicationLabel(context.applicationInfo).toString())
    }
}
