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
            val theirCert = certPath.certificates.lastOrNull() ?: throw IllegalStateException("Certificate path is empty")
            if (theirCert is X509Certificate) {
                val certName = X500Name(theirCert.subjectDN.name)
                if (certName == otherSide.name)
                // FIXME: Validate and store the path once we have access to root certificates
                    certPath
                else
                    throw IllegalStateException("Expected certificate subject to be ${otherSide.name} but found ${certName}")
            } else
                throw IllegalStateException("Expected an X.509 certificate but received ${theirCert.javaClass.name}")
        }
    }

    /**
     * Generates a new key and then returns it to the counterparty and as the result from the function. Note that this
     * is an expensive operation, and should only be called once the calling flow has confirmed it wants to be part of
     * a transaction with the counterparty, in order to avoid a DoS risk.
     */
    @Suspendable
    fun provideKey(flow: FlowLogic<*>, otherSide: Party, revocationEnabled: Boolean): CertPath {
        val ourPublicKey = flow.serviceHub.keyManagementService.freshKey().public
        // FIXME: Use the actual certificate for the identity the flow is presenting themselves as
        val selfSignedCertificate = X509Utilities.createSelfSignedCACert(flow.serviceHub.myInfo.legalIdentity.name)
        val ourCertificate = X509Utilities.createServerCert(flow.serviceHub.myInfo.legalIdentity.name, ourPublicKey,
                selfSignedCertificate, emptyList(), emptyList())
        val ourCertPath = X509Utilities.createCertificatePath(selfSignedCertificate, ourCertificate, revocationEnabled).certPath
        flow.send(otherSide, ourCertPath)
        return ourCertPath
    }
}
