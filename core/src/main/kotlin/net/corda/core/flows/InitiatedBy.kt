package net.corda.core.flows

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 *
 */
@Target(CLASS)
annotation class InitiatedBy(val value: KClass<out FlowLogic<*>>)