package net.corda.core.flows

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Any [FlowLogic] which is to be started by the RPC interface ([net.corda.core.messaging.CordaRPCOps.startFlowDynamic]
 * and [net.corda.core.messaging.CordaRPCOps.startTrackedFlowDynamic]) must have this annotation. If it's missing the
 * flow will not be allowed to start and an exception will be thrown.
 */
@Target(CLASS)
// TODO This should be inherited but can't be so right now due to limitations in the classpath scanning.
//@Inherited
@MustBeDocumented
// TODO Consider a different name, something along the lines of SchedulableFlow
annotation class StartableByRPC