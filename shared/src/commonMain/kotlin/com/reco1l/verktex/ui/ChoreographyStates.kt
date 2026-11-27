package com.reco1l.verktex.ui

/**
 * What a [target][T] plays in its choreographies for each of its states, of any type [S]: the counterpart of
 * [StateTransitions] for a clock that can jump, as [ChoreographyTransitions] is, for targets that animate several
 * components at once. E.g. a hit object, whose pieces each play their part when it's hit or missed:
 *
 * ```
 * private val states = choreographyStates<ArmedState, HitCircle> {
 *     this[ArmedState.Hit] = { time -> circle.choreographAt(time) { it.scaleTo(1.4f, 0.2f).fadeOut(0.2f) } }
 *     this[ArmedState.Miss] = { time -> circle.choreographAt(time) { it.fadeOut(0.1f) } }
 * }
 *
 * states.play(this, ArmedState.Hit, hitTime)
 * ```
 *
 * A block is given the time the state is played at, and adds steps to the choreographies it animates: they stay
 * there as any step, so seeking gives the same state as playing forward. It keeps no state of its own, so one
 * instance can be shared by every target of its type. A state without a block plays the [otherwise] block, or
 * nothing.
 */
class ChoreographyStates<S, T> {

    private val blocks = HashMap<S, T.(time: Float) -> Unit>()

    private var fallback: (T.(time: Float) -> Unit)? = null

    /**
     * Sets what [state] plays.
     */
    operator fun set(state: S, block: T.(time: Float) -> Unit) {
        blocks[state] = block
    }

    /**
     * Sets what a state without its own block plays.
     */
    fun otherwise(block: T.(time: Float) -> Unit) {
        fallback = block
    }

    /**
     * Plays [state] on [target] from [time] on its clock.
     *
     * @return Whether [state] had a block to play.
     */
    fun play(target: T, state: S, time: Float): Boolean {
        val block = blocks[state] ?: fallback ?: return false
        target.block(time)
        return true
    }
}

/**
 * Creates a [ChoreographyStates] for targets of type [T] in states of type [S].
 */
inline fun <S, T> choreographyStates(block: ChoreographyStates<S, T>.() -> Unit) =
    ChoreographyStates<S, T>().apply(block)
