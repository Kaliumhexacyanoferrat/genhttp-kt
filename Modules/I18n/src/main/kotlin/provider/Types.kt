package org.genhttp.modules.i18n.provider

import kotlinx.coroutines.flow.Flow

import org.genhttp.api.protocol.Request

import java.util.Locale

/**
 * A culture setter that is either invoked synchronously or asynchronously.
 *
 * @property syncSetter The setter that is invoked directly
 * @property asyncSetter The setter that is awaited, invoked after the synchronous setter
 */
public data class AsyncOrSyncSetter(
    val syncSetter: CultureSetterDelegate? = null,
    val asyncSetter: CultureSetterAsyncDelegate? = null
)

/**
 * Delegate to extract the cultures for a given request: Receives the request to extract cultures for and
 * returns a flow of the extracted cultures.
 */
public typealias CultureSelectorCombinedAsyncDelegate = (Request) -> Flow<Locale>

/**
 * Delegate to extract the cultures for a given request: Receives the request to extract cultures for and
 * returns the extracted cultures.
 */
public typealias CultureSelectorAsyncDelegate = suspend (Request) -> Iterable<Locale>

/**
 * Delegate to set the culture for a given request: Receives the request to set the culture for and
 * the culture to be set.
 */
public typealias CultureSetterAsyncDelegate = suspend (Request, Locale) -> Unit

/**
 * Delegate to filter the cultures for a given request: Receives the request to filter cultures for and the
 * culture to be filtered. Returns true if the culture is valid for the request, otherwise false.
 */
public typealias CultureFilterAsyncDelegate = suspend (Request, Locale) -> Boolean

/**
 * Delegate to extract the cultures for a given request: Receives the request to extract cultures for and
 * returns the extracted cultures.
 */
public typealias CultureSelectorDelegate = (Request) -> Iterable<Locale>

/**
 * Delegate to set the culture for a given request: Receives the request to set the culture for and
 * the culture to be set.
 */
public typealias CultureSetterDelegate = (Request, Locale) -> Unit

/**
 * Delegate to filter the cultures for a given request: Receives the request to filter cultures for and the
 * culture to be filtered. Returns true if the culture is valid for the request, otherwise false.
 */
public typealias CultureFilterDelegate = (Request, Locale) -> Boolean
