package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.TxKeyFlowUtilities
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.Certificate

/**
 * Very basic flow which requests a transaction key from a counterparty, used for testing [TxKeyFlowUtilities].
 * This MUST not be provided on any real node, as the ability for arbitrary parties to request keys would enable
 * DoS of the node, as key generation/storage is vastly more expensive than submitting a request.
 */
object TxKeyFlow {

    @InitiatingFlow
    class Requester(val otherSide: Party,
                    override val progressTracker: ProgressTracker) : FlowLogic<CertPath>() {
        constructor(otherSide: Party) : this(otherSide, tracker())

        companion object {
            object AWAITING_KEY : ProgressTracker.Step("Awaiting key")

            fun tracker() = ProgressTracker(AWAITING_KEY)
        }

        @Suspendable
        override fun call(): CertPath {
            progressTracker.currentStep = AWAITING_KEY
            return TxKeyFlowUtilities.receiveKey(this, otherSide)
        }
    }

    /**
     * Flow which waits for a key request from a counterparty, generates a new key and then returns it to the
     * counterparty and as the result from the flow.
     */
    class Provider(val otherSide: Party,
                   val revocationEnabled: Boolean,
                   override val progressTracker: ProgressTracker) : FlowLogic<CertPath>() {
        constructor(otherSide: Party, revocationEnabled: Boolean) : this(otherSide, revocationEnabled, tracker())

        companion object {
            object SENDING_KEY : ProgressTracker.Step("Sending key")

            fun tracker() = ProgressTracker(SENDING_KEY)
        }

        @Suspendable
        override fun call(): CertPath {
            progressTracker.currentStep = SENDING_KEY
            return TxKeyFlowUtilities.provideKey(this, otherSide, revocationEnabled)
        }
    }
}
