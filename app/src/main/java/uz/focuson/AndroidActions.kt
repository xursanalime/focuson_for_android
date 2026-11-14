package uz.focuson

import android.content.Context
import uz.focuson.focus.BreakTimer
import uz.focuson.focus.FocusController
import uz.focuson.ui.FocusActions

/** The phone's way of doing what the shared focus screen asks. */
class AndroidActions(context: Context) : FocusActions {
    private val ctx = context.applicationContext
    override fun start(minutes: Int, speciesId: String, tagId: String, stopwatch: Boolean) =
        FocusController.start(ctx, minutes, speciesId, tagId, stopwatch)
    override fun giveUp() = FocusController.giveUp(ctx)
    override fun stopStopwatch() = FocusController.stopStopwatch(ctx)
    override fun check() = FocusController.check(ctx)
    override fun startBreak(minutes: Int) = BreakTimer.start(ctx, minutes)
    override fun stopBreak() = BreakTimer.stop(ctx)
    override fun breakFinished() = BreakTimer.finished(ctx)
}
