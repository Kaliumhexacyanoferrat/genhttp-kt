package org.genhttp.modules.mcp.protocol

internal object RpcErrors {

    const val ParseError: Int = -32700

    const val InvalidRequest: Int = -32600

    const val MethodNotFound: Int = -32601

    const val InvalidParams: Int = -32602

    const val HeaderMismatch: Int = -32020

    const val UnsupportedProtocolVersion: Int = -32022

}
