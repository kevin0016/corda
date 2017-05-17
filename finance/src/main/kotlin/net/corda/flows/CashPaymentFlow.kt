package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.InsufficientBalanceException
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.X509Utilities
import net.corda.core.crypto.expandedCompositeKeys
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import org.bouncycastle.asn1.x500.X500Name
import java.security.KeyPair
import java.util.*

object CashPaymentFlow {
    /**
     * Initiates a flow that sends cash to a recipient.
     *
     * @param amount the amount of a currency to pay to the recipient.
     * @param recipient the party to pay the currency to.
     * @param issuerConstraint if specified, the payment will be made using only cash issued by the given parties.
     */
    @InitiatingFlow
    @StartableByRPC
    open class Initiator(
            val amount: Amount<Currency>,
            val recipient: Party,
            progressTracker: ProgressTracker,
            val issuerConstraint: Set<Party>? = null) : AbstractCashFlowInitiator(progressTracker) {
        /** A straightforward constructor that constructs spends using cash states of any issuer. */
        constructor(amount: Amount<Currency>, recipient: Party) : this(amount, recipient, tracker())

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_ID

            // val revocationEnabled = false
            // val ourCertPath = TxKeyFlowUtilities.provideKey(this, recipient, revocationEnabled)
            // val theirCertPath = TxKeyFlowUtilities.receiveKey(this, recipient)

            progressTracker.currentStep = GENERATING_TX
            val builder: TransactionBuilder = TransactionType.General.Builder(null)
            // TODO: Have some way of restricting this to states the caller controls
            val (spendTX, keysForSigning) = try {
                serviceHub.vaultService.generateSpend(
                        builder,
                        amount,
                        // TODO: Get a transaction key, don't just re-use the owning key
                        recipient,
                        issuerConstraint)
            } catch (e: InsufficientBalanceException) {
                throw CashException("Insufficient cash for spend: ${e.message}", e)
            }

            progressTracker.currentStep = SIGNING_TX
            keysForSigning.expandedCompositeKeys.forEach {
                val key = serviceHub.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
                builder.signWith(KeyPair(it, key))
            }

            progressTracker.currentStep = FINALISING_TX
            val tx = spendTX.toSignedTransaction(checkSufficientSignatures = false)
            finaliseTx(setOf(recipient), tx, "Unable to notarise spend")
            return tx
        }
    }
}