package net.corda.core.node.services

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Annotate any class that needs to be a long-lived service within the node with this annotation.
 */
@Target(CLASS)
annotation class CorDappService