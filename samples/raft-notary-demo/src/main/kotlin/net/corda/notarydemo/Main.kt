package net.corda.notarydemo

import com.google.common.net.HostAndPort
import net.corda.core.crypto.appendToCommonName
import net.corda.core.div
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.node.driver.NetworkMapStartStrategy
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.notarydemo.flows.DummyIssueAndMove
import net.corda.notarydemo.flows.RPCStartableNotaryFlowClient
import org.bouncycastle.asn1.x500.X500Name

/** Creates and starts all nodes required for the demo. */
fun main(args: Array<String>) {
    val demoUser = listOf(User("demo", "demo", setOf(startFlowPermission<DummyIssueAndMove>(), startFlowPermission<RPCStartableNotaryFlowClient>())))
    val networkMap = NetworkMapStartStrategy.Nominated(DUMMY_NOTARY.name.appendToCommonName("1"), HostAndPort.fromParts("localhost", 10009))
    driver(isDebug = true, driverDirectory = "build" / "notary-demo-nodes", networkMapStartStrategy = networkMap, portAllocation = PortAllocation.Incremental(10001)) {
        startNode(ALICE.name, rpcUsers = demoUser)
        startNode(BOB.name)
        startNotaryCluster(X500Name("CN=Raft,O=R3,OU=corda,L=Zurich,C=CH"), clusterSize = 3, type = RaftValidatingNotaryService.type)
        waitForAllNodesToFinish()
    }
}
