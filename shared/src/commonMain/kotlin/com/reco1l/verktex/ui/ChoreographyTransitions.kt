package com.reco1l.verktex.ui

/**
 * Choreography blocks keyed by a state of any type, played into a component's [Choreography] at a given time: the
 * counterpart of [StateTransitions] for a clock that can jump, such as gameplay's. E.g. the judgement of a hit object:
 *
 * ```
 * val judgement = choreographyTransitions<HitResult> {
 *     this[HitResult.Miss] = { fadeInFromZero(0.15f).moveToY(40f, 0.5f, Easing.InQuad).then().fadeOut(0.2f) }
 *     otherwise { scaleTo(1.4f, 0.1f).then().scaleTo(1f, 0.1f).delay(0.6f).fadeOut(0.2f) }
 * }
 *
 * val endTime = judgement.play(piece, result, hitTime)
 * ```
 *
 * It keeps no state of its own, so one instance can be shared by every component it animates. A block starts at the
 * time it's played at, and what it adds stays in the choreography as any step: seeking and going back in time give
 * the same state as playing forward. A state without a block plays the [otherwise] block, or nothing.
 */
class ChoreographyTransitions<S> {

    private val blocks = HashMap<S, Choreography.() -> Unit>()

    private var fallback: (Choreography.() -> Unit)? = null

    /**
     * Sets the steps to add when [state] is played.
     */
    operator fun set(state: S, block: Choreography.() -> Unit) {
        blocks[state] = block
    }

    /**
     * Sets the steps to add when a state without its own block is played.
     */
    fun otherwise(block: Choreography.() -> Unit) {
        fallback = block
    }

    /**
     * The block played for [state], null if there's none.
     */
    fun blockFor(state: S): (Choreography.() -> Unit)? = blocks[state] ?: fallback

    /**
     * Adds the steps of [state] to the choreography of [target], starting at [time] on its clock.
     *
     * @return The time the choreography of [target] ends at, see [Choreography.endTime], for callers to extend
     * lifetimes with. [time] if [state] has no block, which leaves the choreography as it was.
     */
    fun play(target: Component, state: S, time: Float): Float {
        val block = blockFor(state) ?: return time
        val choreography = target.choreography()

        choreography.at(time).block()
        return maxOf(time, choreography.endTime)
    }
}

/**
 * Creates a [ChoreographyTransitions] keyed by states of type [S].
 */
inline fun <S> choreographyTransitions(block: ChoreographyTransitions<S>.() -> Unit) =
    ChoreographyTransitions<S>().apply(block)
