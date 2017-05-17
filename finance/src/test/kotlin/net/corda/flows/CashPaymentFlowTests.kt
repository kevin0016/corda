package net.corda.flows

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.`issued by`
import net.corda.core.identity.Party
import net.corda.core.getOrThrow
import net.corda.core.serialization.OpaqueBytes
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CashPaymentFlowTests {
    private val net = MockNetwork(servicePeerAllocationStrategy = RoundRobin())
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)
    private lateinit var bankOfCordaNode: MockNode
    private lateinit var bankOfCorda: Party
    private lateinit var notaryNode: MockNode
    private lateinit var notary: Party

    @Before
    fun start() {
        val nodes = net.createTwoNodes()
        notaryNode = nodes.first
        bankOfCordaNode = nodes.second
        notary = notaryNode.info.notaryIdentity
        bankOfCorda = bankOfCordaNode.info.legalIdentity

        net.runNetwork()
        val future = bankOfCordaNode.services.startFlow(CashIssueFlow.Initiator(initialBalance, ref,
                bankOfCorda,
                notary)).resultFuture
        net.runNetwork()
        future.getOrThrow()
    }

    @After
    fun cleanUp() {
        net.stopNodes()
    }

    @Test
    fun `pay some cash`() {
        val payTo = notaryNode.info.legalIdentity
        val expected = 500.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashPaymentFlow.Initiator(expected,
                payTo)).resultFuture
        net.runNetwork()
        val paymentTx = future.getOrThrow()
        val states = paymentTx.tx.outputs.map { it.data }.filterIsInstance<Cash.State>()
        val ourState = states.single { it.owner.owningKey != payTo.owningKey }
        val paymentState = states.single { it.owner.owningKey == payTo.owningKey }
        assertEquals(expected.`issued by`(bankOfCorda.ref(ref)), paymentState.amount)
    }

    @Test
    fun `pay more than we have`() {
        val payTo = notaryNode.info.legalIdentity
        val expected = 4000.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashPaymentFlow.Initiator(expected,
                payTo)).resultFuture
        net.runNetwork()
        assertFailsWith<CashException> {
            future.getOrThrow()
        }
    }

    @Test
    fun `pay zero cash`() {
        val payTo = notaryNode.info.legalIdentity
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashPaymentFlow.Initiator(expected,
                payTo)).resultFuture
        net.runNetwork()
        assertFailsWith<IllegalArgumentException> {
            future.getOrThrow()
        }
    }
}
