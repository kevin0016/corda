package net.corda.testing.node

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.*
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.*
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.node.services.persistence.InMemoryStateMachineRecordedTransactionMappingStorage
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.vault.NodeVaultService
import net.corda.testing.MEGA_CORP
import net.corda.testing.MINI_CORP
import net.corda.testing.MOCK_VERSION_INFO
import org.bouncycastle.asn1.x500.X500Name
import rx.Observable
import rx.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Paths
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertPath
import java.security.cert.X509Certificate
import java.time.Clock
import java.util.*
import java.util.jar.JarInputStream
import javax.annotation.concurrent.ThreadSafe

// TODO: We need a single, rationalised unit testing environment that is usable for everything. Fix this!
// That means it probably shouldn't be in the 'core' module, which lacks enough code to create a realistic test env.

/**
 * A singleton utility that only provides a mock identity, key and storage service. However, this is sufficient for
 * building chains of transactions and verifying them. It isn't sufficient for testing flows however.
 */
open class MockServices(val key: KeyPair = generateKeyPair()) : ServiceHub {
    override fun recordTransactions(txs: Iterable<SignedTransaction>) {
        txs.forEach {
            storageService.stateMachineRecordedTransactionMapping.addMapping(StateMachineRunId.createRandom(), it.id)
        }
        for (stx in txs) {
            storageService.validatedTransactions.addTransaction(stx)
        }
    }

    override val storageService: TxWritableStorageService = MockStorageService()
    override val identityService: MockIdentityService = MockIdentityService(listOf(MEGA_CORP, MINI_CORP, DUMMY_NOTARY))
    override val keyManagementService: MockKeyManagementService = MockKeyManagementService(key)

    override val vaultService: VaultService get() = throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache get() = throw UnsupportedOperationException()
    override val clock: Clock get() = Clock.systemUTC()
    override val myInfo: NodeInfo get() = NodeInfo(object : SingleMessageRecipient {}, Party(MEGA_CORP.name, key.public), MOCK_VERSION_INFO.platformVersion)
    override val transactionVerifierService: TransactionVerifierService get() = InMemoryTransactionVerifierService(2)

    fun makeVaultService(dataSourceProps: Properties): VaultService {
        val vaultService = NodeVaultService(this, dataSourceProps)
        // Vault cash spending requires access to contract_cash_states and their updates
        HibernateObserver(vaultService.rawUpdates, NodeSchemaService())
        return vaultService
    }
}

@ThreadSafe
class MockIdentityService(val identities: List<Party>,
                          val certificates: List<Triple<AnonymousParty, Party, CertPath>> = emptyList()) : IdentityService, SingletonSerializeAsToken() {
    private val keyToParties: Map<PublicKey, Party>
        get() = synchronized(identities) { identities.associateBy { it.owningKey } }
    private val nameToParties: Map<X500Name, Party>
        get() = synchronized(identities) { identities.associateBy { it.name } }
    private val anonymousToPath: Map<AnonymousParty, Pair<Party, CertPath>>
        get() = synchronized(certificates) { certificates.map { Pair(it.first, Pair(it.second, it.third)) }.toMap() }

    override fun registerIdentity(party: Party) {
        throw UnsupportedOperationException()
    }

    override fun getAllIdentities(): Iterable<Party> = ArrayList(keyToParties.values)
    override fun partyFromAnonymous(party: AbstractParty): Party? = keyToParties[party.owningKey]
    override fun partyFromAnonymous(partyRef: PartyAndReference): Party? = partyFromAnonymous(partyRef.party)
    override fun partyFromKey(key: PublicKey): Party? = keyToParties[key]
    override fun partyFromName(name: String): Party? = nameToParties[X500Name(name)]
    override fun partyFromX500Name(principal: X500Name): Party? = nameToParties[principal]

    @Throws(IllegalStateException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) {
        check(anonymousToPath[anonymousParty]?.first == party)
    }
    override fun pathForAnonymous(anonymousParty: AnonymousParty): CertPath? = anonymousToPath[anonymousParty]?.second
    override fun createTransactionIdentity(party: Party, revocationEnabled: Boolean): CertPath { throw UnsupportedOperationException() }
    override fun registerPath(trustedRoot: X509Certificate, anonymousParty: AnonymousParty, path: CertPath) { throw UnsupportedOperationException() }
}


class MockKeyManagementService(vararg initialKeys: KeyPair) : SingletonSerializeAsToken(), KeyManagementService {
    override val keys: MutableMap<PublicKey, PrivateKey> = initialKeys.associateByTo(HashMap(), { it.public }, { it.private })

    val nextKeys = LinkedList<KeyPair>()

    override fun freshKey(): KeyPair {
        val k = nextKeys.poll() ?: generateKeyPair()
        keys[k.public] = k.private
        return k
    }
}

class MockAttachmentStorage : AttachmentStorage {
    val files = HashMap<SecureHash, ByteArray>()
    override var automaticallyExtractAttachments = false
    override var storePath = Paths.get("")

    override fun openAttachment(id: SecureHash): Attachment? {
        val f = files[id] ?: return null
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(f)
            override val id: SecureHash = id
        }
    }

    override fun importAttachment(jar: InputStream): SecureHash {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = run {
            val s = ByteArrayOutputStream()
            jar.copyTo(s)
            s.close()
            s.toByteArray()
        }
        val sha256 = bytes.sha256()
        if (files.containsKey(sha256))
            throw FileAlreadyExistsException(File("!! MOCK FILE NAME"))
        files[sha256] = bytes
        return sha256
    }
}

class MockStateMachineRecordedTransactionMappingStorage(
        val storage: StateMachineRecordedTransactionMappingStorage = InMemoryStateMachineRecordedTransactionMappingStorage()
) : StateMachineRecordedTransactionMappingStorage by storage

open class MockTransactionStorage : TransactionStorage {
    override fun track(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
        return Pair(txns.values.toList(), _updatesPublisher)
    }

    private val txns = HashMap<SecureHash, SignedTransaction>()

    private val _updatesPublisher = PublishSubject.create<SignedTransaction>()

    override val updates: Observable<SignedTransaction>
        get() = _updatesPublisher

    private fun notify(transaction: SignedTransaction) = _updatesPublisher.onNext(transaction)

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        val recorded = txns.putIfAbsent(transaction.id, transaction) == null
        if (recorded) {
            notify(transaction)
        }
        return recorded
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txns[id]
}

@ThreadSafe
class MockStorageService(override val attachments: AttachmentStorage = MockAttachmentStorage(),
                         override val validatedTransactions: TransactionStorage = MockTransactionStorage(),
                         override val uploaders: List<FileUploader> = listOf<FileUploader>(),
                         override val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage = MockStateMachineRecordedTransactionMappingStorage())
    : SingletonSerializeAsToken(), TxWritableStorageService {
    override val attachmentsClassLoaderEnabled = false
}

/**
 * Make properties appropriate for creating a DataSource for unit tests.
 *
 * @param nodeName Reflects the "instance" of the in-memory database.  Defaults to a random string.
 */
// TODO: Can we use an X509 principal generator here?
fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}
