package app.silverbox.ui.art

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.silverbox.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture at full size, decoded off the main thread; its small copy stands in meanwhile. */
@Composable
fun rememberFull(name: String, max: Int = 1600): Bitmap? {
    val vault = LocalContext.current.container.vault
    return produceState(initialValue = vault.small(name), name, max) {
        withContext(Dispatchers.IO) { vault.full(name, max) }?.let { value = it }
    }.value
}
