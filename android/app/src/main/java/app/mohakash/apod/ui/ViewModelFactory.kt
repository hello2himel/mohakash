package app.mohakash.apod.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mohakash.apod.AppContainer
import app.mohakash.apod.MohakashApplication

/**
 * Creates a [ViewModel] with the dependencies from the application container.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as MohakashApplication).container
    }
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container) }
        },
    )
}
