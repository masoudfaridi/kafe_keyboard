package tribixbite.cleverkeys.kafe

import kotlin.math.min

/**
 * Kafe Keyboard: size of the hold-for-subkeys popover.
 *
 * CleverKeys makes each popover cell exactly one key in size, which leaves little room to aim
 * on a phone. Kafe draws (and hit-tests) every cell [SCALE] times larger, but never larger than
 * a third of the keyboard ([FIT]): the popover is a 3 × 3 grid, and a wide key (the 4-key space
 * bar: 3 × 4 × 1.3 = 15.6 keys) would otherwise run off both sides of a 10-key keyboard.
 *
 * Hooked from Keyboard2View.subkeyPopoverMetrics (two marked lines).
 */
object KafeSubkeyPopover {
    /** Kafe's default enlargement of a popover cell over the key's own size. */
    private const val SCALE = 1.3f
    /** Share of the keyboard the 3 cells of a row/column may fill. */
    private const val FIT = 0.96f

    /** Cell size for a key [keySize] px wide (or tall) on a keyboard [viewSize] px wide (or tall). */
    fun cell(keySize: Float, viewSize: Float): Float =
        if (viewSize <= 0f) keySize * SCALE else min(keySize * SCALE, viewSize * FIT / 3f)
}
