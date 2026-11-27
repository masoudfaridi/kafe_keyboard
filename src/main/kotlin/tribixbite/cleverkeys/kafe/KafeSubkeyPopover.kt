package tribixbite.cleverkeys.kafe

/**
 * Kafe Keyboard: size of the hold-for-subkeys popover.
 *
 * CleverKeys makes each popover cell exactly one key in size, which leaves little room to aim
 * on a phone. Kafe draws (and hit-tests) every cell [SCALE] times larger. The popover still
 * stays inside the keyboard; CleverKeys clamps the grid to the view.
 *
 * Hooked from one line in Keyboard2View.subkeyPopoverMetrics.
 */
object KafeSubkeyPopover {
    /** Kafe's default enlargement of a popover cell over the key's own size. */
    const val SCALE = 1.3f
}
