package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.X509Utilities
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import org.bouncycastle.asn1.x500.X500Name
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

object TxKeyFlowUtilities {
    /**
     * Receive a key from a counterparty. This would normally be triggered by a flow as part of a transaction assembly
     * process.
     */
    // TODO: This should store the received path automatically with the identity service
    @Suspendable
    fun receiveKey(flow: FlowLogic<*>, otherSide: Party): CertPath {
        val untrustedKey = flow.receive<CertPath>(otherSide)
        return untrustedKey.unwrap { certPath ->
            val theirCert = certPath.certificates.lastOrNull()
            if (theirCert != null
                    && theirCert is X509Certificate
                    && theirCert.subjectDN == X500Principal(otherSide.name.encoded)) {
                // FIXME: Validate and store the path once we have access to root certificates
                certPath
            } else {
                null
            }
        } ?: throw IllegalStateException("Received invalid response from remote party")
    }

    /**
     * Generates a new key and then returns it to the counterparty and as the result from the function. Note that this
     * is an expensive operation, and should only be called once the calling flow has confirmed it wants to be part of
     * a transaction with the counterparty, in order to avoid a DoS risk.
     */
    @Suspendable
    fun provideKey(flow: FlowLogic<*>, otherSide: Party, revocationEnabled: Boolean): CertPath {
        val ourCertPath = flow.serviceHub.identityService.createTransactionIdentity(flow.serviceHub.myInfo.legalIdentity, revocationEnabled)
        flow.send(otherSide, ourCertPath)
        return ourCertPath
    }
}
