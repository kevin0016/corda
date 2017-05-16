package net.corda.node.services.transactions

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.TimeRangeChecker

/** A non-validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftNonValidatingNotaryService(val timeRangeChecker: TimeRangeChecker,
                                     val uniquenessProvider: RaftUniquenessProvider) : NotaryService {
    companion object {
        val type = SimpleNotaryService.type.getSubType("raft")
    }

    override val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?> = { otherParty, _ ->
        NonValidatingNotaryFlow(otherParty, timeRangeChecker, uniquenessProvider)
    }
}
