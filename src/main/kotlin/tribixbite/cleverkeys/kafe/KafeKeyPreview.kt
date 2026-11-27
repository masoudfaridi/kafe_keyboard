package tribixbite.cleverkeys.kafe

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import tribixbite.cleverkeys.theme.LookPreview
import java.lang.ref.WeakReference

/**
 * Kafe Keyboard: draws the key-press preview on the keyboard window's overlay instead of
 * inside the keyboard view.
 *
 * Inside the keyboard view the preview is clamped to the view's top edge, so the top row's
 * bubble was pushed down onto the key, under the finger, and could not rise over the
 * suggestion bar. On the root view's [android.view.ViewOverlay] the same drawing (CleverKeys'
 * own `drawKeyPreview`, handed over as a lambda) is only clamped to the window's top, so it
 * rises over the suggestion bar like on other keyboards. Nothing is laid out or added to the
 * view tree: an overlay drawable draws after every child and takes no space.
 *
 * Kafe also draws the bubble a little larger and a little higher ([sized], [LIFT]), so it is
 * easier to read and clear of the finger.
 *
 * Hooked from two lines in Keyboard2View.onDraw ([beginFrame], [post]).
 */
object KafeKeyPreview {
    /** Kafe's default enlargement of the look's preview (width, height, label). */
    private const val SCALE = 1.2f
    /** Extra rise above the key, in key heights. */
    private const val LIFT = 0.18f

    private var host = WeakReference<View>(null)
    private var drawer: ((Canvas, Float) -> Unit)? = null
    private var keyHeight = 0f
    private val overlayDrawable = PreviewDrawable()
    private var attachedRoot = WeakReference<ViewGroup>(null)

    /** The look's preview, enlarged by Kafe's default. */
    fun sized(preview: LookPreview): LookPreview = preview.copy(
        widthScale = preview.widthScale * SCALE,
        heightScale = preview.heightScale * SCALE,
        labelScale = preview.labelScale * SCALE,
    )

    /** Start of a keyboard frame: forget the last frame's preview. */
    fun beginFrame(view: View) {
        if (drawer != null && host.get() === view) {
            drawer = null
            overlayDrawable.invalidateSelf()
        }
    }

    /**
     * Hands this frame's preview to the overlay. [draw] receives the overlay canvas and the
     * vertical offset to add to the key's y (the keyboard's position in the window, minus
     * Kafe's lift). Returns false when no overlay is available; the caller then draws the
     * preview itself, as before.
     */
    fun post(view: View, keyH: Float, draw: (Canvas, Float) -> Unit): Boolean {
        val root = view.rootView as? ViewGroup ?: return false
        if (root === view) return false
        if (attachedRoot.get() !== root) {
            attachedRoot.get()?.overlay?.remove(overlayDrawable)
            root.overlay.add(overlayDrawable)
            attachedRoot = WeakReference(root)
        }
        host = WeakReference(view)
        drawer = draw
        keyHeight = keyH
        overlayDrawable.invalidateSelf()
        return true
    }

    private class PreviewDrawable : Drawable() {
        private val viewPos = IntArray(2)
        private val rootPos = IntArray(2)

        override fun draw(canvas: Canvas) {
            val draw = drawer ?: return
            val view = host.get() ?: return
            val root = attachedRoot.get() ?: return
            if (bounds.width() != root.width || bounds.height() != root.height) {
                setBounds(0, 0, root.width, root.height)
            }
            view.getLocationInWindow(viewPos)
            root.getLocationInWindow(rootPos)
            val dx = (viewPos[0] - rootPos[0]).toFloat()
            val dy = (viewPos[1] - rootPos[1]).toFloat() - keyHeight * LIFT
            canvas.save()
            canvas.translate(dx, 0f)
            draw(canvas, dy)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
