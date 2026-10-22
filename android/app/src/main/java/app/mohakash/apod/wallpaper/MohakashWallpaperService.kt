package app.mohakash.apod.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.mohakash.apod.MohakashApplication
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.domain.wallpaper.WallpaperScaling as ScalingMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The live wallpaper service.
 *
 * The wallpaper is a still picture — there is no animation and no continuous redraw. The engine
 * draws exactly once per surface change and once per new picture.
 */
class MohakashWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = MohakashEngine()

    /**
     * Draws the current picture on the wallpaper surface.
     */
    inner class MohakashEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private var holder: SurfaceHolder? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var bitmap: Bitmap? = null
        private var scalingMode = WallpaperScaling.FILL
        private var renderJob: Job? = null
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            this.holder = holder
            scope.launch {
                val mode = runCatching {
                    (applicationContext as? MohakashApplication)
                        ?.container
                        ?.settingsStore
                        ?.current()
                        ?.wallpaperScaling
                }.getOrNull() ?: WallpaperScaling.FILL
                scalingMode = mode
                // Redraw whenever a new picture was stored — no polling involved.
                WallpaperSignal.revision.collectLatest {
                    render()
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            this.holder = holder
            render()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.holder = holder
            this.surfaceWidth = width
            this.surfaceHeight = height
            render()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            this.holder = null
            releaseBitmap()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) render()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            this.holder = holder
            render()
        }

        override fun onDestroy() {
            super.onDestroy()
            scope.cancel()
            releaseBitmap()
        }

        /** Decodes the current picture off the main thread and draws it. */
        private suspend fun renderNow() {
            val storage = (applicationContext as? MohakashApplication)?.container?.wallpaperStorage
                ?: return
            val file: File = storage.currentImageFile() ?: return
            val width = surfaceWidth
            val height = surfaceHeight
            val decoded = decode(file, width, height) ?: return
            withContext(Dispatchers.Main) {
                val previous = bitmap
                bitmap = decoded
                draw()
                previous?.takeIf { it !== decoded && !it.isRecycled }?.recycle()
            }
        }

        /** Starts a render pass; safe to call from any of the engine callbacks. */
        private fun render() {
            renderJob?.cancel()
            renderJob = scope.launch { renderNow() }
        }

        /**
         * Decodes the picture with an `inSampleSize` so a huge photo never has to fit into
         * memory completely.
         */
        private fun decode(file: File, destinationWidth: Int, destinationHeight: Int): Bitmap? {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                val options = BitmapFactory.Options().apply {
                    inSampleSize = ScalingMath.computeSampleSize(
                        sourceWidth = bounds.outWidth,
                        sourceHeight = bounds.outHeight,
                        destinationWidth = destinationWidth,
                        destinationHeight = destinationHeight,
                    )
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            } catch (error: Throwable) {
                null
            }
        }

        /** Draws the picture on the surface. Must run on the thread that owns the surface. */
        private fun draw() {
            val current = bitmap ?: return
            val surface = holder ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            var canvas: Canvas? = null
            try {
                canvas = surface.lockCanvas() ?: return
                canvas.drawColor(BACKGROUND_COLOR)
                val source = ScalingMath.computeSourceRect(
                    current.width,
                    current.height,
                    surfaceWidth,
                    surfaceHeight,
                    scalingMode,
                )
                val destination = ScalingMath.computeDestinationRect(
                    current.width,
                    current.height,
                    surfaceWidth,
                    surfaceHeight,
                    scalingMode,
                )
                if (!source.isEmpty && !destination.isEmpty) {
                    canvas.drawBitmap(
                        current,
                        Rect(source.left, source.top, source.right, source.bottom),
                        Rect(destination.left, destination.top, destination.right, destination.bottom),
                        paint,
                    )
                }
            } catch (error: Throwable) {
                // A surface can disappear while we are drawing — simply skip this frame.
            } finally {
                if (canvas != null) {
                    runCatching { surface.unlockCanvasAndPost(canvas) }
                }
            }
        }

        private fun releaseBitmap() {
            val current = bitmap ?: return
            bitmap = null
            if (!current.isRecycled) current.recycle()
        }
    }

    companion object {
        /** The app background colour, used for the letterbox bars. */
        val BACKGROUND_COLOR: Int = Color.parseColor("#0B0D12")
    }
}
