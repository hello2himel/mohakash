package app.mohakash.apod.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.mohakash.apod.MohakashApplication
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.domain.wallpaper.WallpaperGeometry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The live wallpaper service.
 *
 * The wallpaper is a still picture — there is no animation and no continuous redraw. The engine
 * draws once per surface change and once per new picture.
 */
class MohakashWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = MohakashEngine()

    /**
     * Draws the current picture on the wallpaper surface.
     */
    inner class MohakashEngine : Engine() {

        /**
         * All drawing runs on one single thread, and every engine callback is posted onto it.
         *
         * This matters for stability: `lockCanvas`/`unlockCanvasAndPost` must never be called
         * from two threads at once and the surface must not be touched after it was destroyed.
         * A dedicated handler with [surfaceAlive] checks makes that impossible.
         */
        private val renderThread = HandlerThread("MohakashWallpaperRender").apply { start() }
        private val renderHandler = Handler(renderThread.looper)

        private var holder: SurfaceHolder? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var bitmap: Bitmap? = null
        private var decodedFile: String? = null
        private var decodedStamp: Long = 0L
        private var scalingMode = WallpaperScaling.FILL
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        /** True while a valid surface exists; written by every callback, read on the render thread. */
        @Volatile
        private var surfaceAlive = false

        /** Set while the framework requires a synchronous redraw of the current frame. */
        @Volatile
        private var awaitingSyncRedraw = false

        /** Bumped by [WallpaperSignal]; observed without ever blocking a callback. */
        private var signalRevision = -1L
        private var observeJob: Job? = null

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            this.holder = holder
            loadScalingAndObserveSignals()
        }

        /**
         * Reads the scaling setting once, then re-renders whenever the picture changes.
         *
         * `settingsStore.current()` is suspending, so it must not run on the render thread. The
         * coroutine reads it off the main thread and hands the result back via [postRender].
         */
        private fun loadScalingAndObserveSignals() {
            observeJob?.cancel()
            observeJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val mode = runCatching {
                    (applicationContext as? MohakashApplication)
                        ?.container
                        ?.settingsStore
                        ?.current()
                        ?.wallpaperScaling
                }.getOrNull() ?: WallpaperScaling.FILL
                postRender {
                    scalingMode = mode
                    render(redecode = false)
                }
                WallpaperSignal.revision.collectLatest { revision ->
                    // Never block in the collector: hand the work back to the render thread.
                    postRender {
                        if (revision != signalRevision) {
                            signalRevision = revision
                            render(redecode = true)
                        }
                    }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            this.holder = holder
            postRender {
                surfaceAlive = true
                render(redecode = true)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.holder = holder
            postRender {
                surfaceWidth = width
                surfaceHeight = height
                surfaceAlive = true
                render(redecode = true)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            // The framework requires this callback to return *before* the surface may be torn
            // down. So we must NOT merely post the cleanup and return — a queued draw could
            // still lock a dead canvas and crash the process that hosts us (the system settings
            // wallpaper screen). Instead: block until the render thread has finished its current
            // frame, then drop everything synchronously.
            surfaceAlive = false
            awaitingSyncRedraw = false
            renderHandler.removeCallbacksAndMessages(null)
            val drained = CompletableDeferred<Unit>()
            val posted = runCatching {
                renderHandler.post {
                    releaseBitmap()
                    surfaceWidth = 0
                    surfaceHeight = 0
                    this.holder = null
                    drained.complete(Unit)
                }
            }.isSuccess
            if (posted) {
                runBlocking {
                    withTimeoutOrNull(SYNC_REDRAW_TIMEOUT_MS) { drained.await() }
                }
            }
            if (!drained.isCompleted) {
                // The drain did not finish in time; keep only the cheap parts synchronous so we
                // never hold the main thread hostage.
                bitmap = null
                decodedFile = null
                decodedStamp = 0L
                surfaceWidth = 0
                surfaceHeight = 0
                this.holder = null
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) postRender { render(redecode = false) }
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            this.holder = holder
            // The framework blocks until this method returns and expects the surface to be
            // redrawn. Reuse the already decoded bitmap here — decoding a large photo inside this
            // callback is what made the preview janky and could ANR the host process. The bitmap
            // is only decoded on surface creation/change and when the picture actually changes.
            if (!surfaceAlive) return
            awaitingSyncRedraw = true
            val drawn = CompletableDeferred<Unit>()
            val posted = runCatching {
                renderHandler.post {
                    if (surfaceAlive && awaitingSyncRedraw) {
                        render(redecode = false)
                    }
                    drawn.complete(Unit)
                }
            }.isSuccess
            if (posted) {
                runBlocking {
                    withTimeoutOrNull(SYNC_REDRAW_TIMEOUT_MS) { drawn.await() }
                }
            }
            awaitingSyncRedraw = false
        }

        override fun onDestroy() {
            super.onDestroy()
            observeJob?.cancel()
            renderHandler.removeCallbacksAndMessages(null)
            renderThread.quitSafely()
        }

        private fun postRender(action: () -> Unit) {
            runCatching { renderHandler.post(action) }
        }

        /** Decodes the current picture (when needed) and draws it. Runs on the render thread. */
        private fun render(redecode: Boolean): Boolean {
            if (!surfaceAlive) return false
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return false
            return runCatching { renderNow(redecode) }.getOrDefault(false)
        }

        private fun renderNow(redecode: Boolean): Boolean {
            val storage = (applicationContext as? MohakashApplication)?.container?.wallpaperStorage
                ?: return false
            val file: File = storage.currentImageFile() ?: return false
            val stamp = file.lastModified() * 31 + file.length()
            val changed = decodedFile != file.absolutePath ||
                decodedStamp != stamp ||
                bitmap?.isRecycled != false
            if (redecode || changed) {
                val decoded = decode(file, surfaceWidth, surfaceHeight) ?: return false
                val previous = bitmap
                bitmap = decoded
                decodedFile = file.absolutePath
                decodedStamp = stamp
                previous?.takeIf { it !== decoded && !it.isRecycled }?.recycle()
            }
            return draw()
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
                    inSampleSize = WallpaperGeometry.computeSampleSize(
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

        /**
         * Draws the picture on the surface. Runs on the render thread only.
         *
         * @return true when a frame was posted.
         */
        private fun draw(): Boolean {
            val current = bitmap ?: return false
            if (current.isRecycled) return false
            val surface = holder ?: return false
            if (!surfaceAlive) return false
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return false

            var canvas: Canvas? = null
            try {
                canvas = surface.lockCanvas() ?: return false
                canvas.drawColor(BACKGROUND_COLOR)
                val source = WallpaperGeometry.computeSourceRect(
                    current.width,
                    current.height,
                    surfaceWidth,
                    surfaceHeight,
                    scalingMode,
                )
                val destination = WallpaperGeometry.computeDestinationRect(
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
                return true
            } catch (error: Throwable) {
                // A surface can disappear while we are drawing — simply skip this frame.
                return false
            } finally {
                if (canvas != null) {
                    runCatching { surface.unlockCanvasAndPost(canvas) }
                }
            }
        }

        private fun releaseBitmap() {
            val current = bitmap ?: return
            bitmap = null
            decodedFile = null
            decodedStamp = 0L
            if (!current.isRecycled) current.recycle()
        }
    }

    companion object {
        /** The app background colour, used for the letterbox bars. */
        val BACKGROUND_COLOR: Int = Color.parseColor("#0B0D12")

        /** Upper bound for waiting on a synchronous redraw before giving up. */
        private const val SYNC_REDRAW_TIMEOUT_MS = 2_000L
    }
}
