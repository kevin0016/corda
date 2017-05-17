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

class CashExitFlowTests {
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
    fun `exit some cash`() {
        val exitAmount = 500.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashExitFlow.Initiator(exitAmount,
                ref)).resultFuture
        net.runNetwork()
        val exitTx = future.getOrThrow().tx
        val expected = (initialBalance - exitAmount).`issued by`(bankOfCorda.ref(ref))
        assertEquals(1, exitTx.inputs.size)
        assertEquals(1, exitTx.outputs.size)
        val output = exitTx.outputs.map { it.data }.filterIsInstance<Cash.State>().single()
        assertEquals(expected, output.amount)
    }

    @Test
    fun `exit zero cash`() {
        val expected = 0.DOLLARS
        val future = bankOfCordaNode.services.startFlow(CashExitFlow.Initiator(expected,
                ref)).resultFuture
        net.runNetwork()
        assertFailsWith<CashException> {
            future.getOrThrow()
        }
    }
}
