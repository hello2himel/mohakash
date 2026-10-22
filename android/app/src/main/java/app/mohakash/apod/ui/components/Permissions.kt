package app.mohakash.apod.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests the legacy storage permission when it is really needed.
 *
 * Android 10 and newer store the picture through `MediaStore`, which needs no permission at all.
 * Only Android 7 to 9 write into the public *Pictures* directory and therefore need
 * `WRITE_EXTERNAL_STORAGE` — the permission is declared with `maxSdkVersion="28"`.
 *
 * @param onGranted called when the picture may be saved.
 * @param onDenied called when the user refuses the permission.
 * @return a lambda that starts the save.
 */
@Composable
fun rememberLegacyStoragePermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onGranted() else onDenied()
    }

    return remember(context, onGranted, onDenied) {
        {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                onGranted()
            }
        }
    }
}
