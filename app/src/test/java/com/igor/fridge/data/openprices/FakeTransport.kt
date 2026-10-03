package com.igor.fridge.data.openprices

import java.io.IOException

/**
 * Un server Open Prices finto: risponde con le risposte preparate per percorso (senza
 * query) e ricorda ogni richiesta, per controllarne metodo, indirizzo, intestazioni e corpo.
 */
class FakeTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    private val responses = mutableMapOf<String, ArrayDeque<HttpResponse>>()
    var offline = false

    fun respond(method: String, path: String, vararg response: HttpResponse) {
        responses.getOrPut("$method $path") { ArrayDeque() }.addAll(response)
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        if (offline) throw IOException("rete assente")
        val path = request.url.substringAfter("/api/v1").substringBefore('?')
        val queue = responses["${request.method} $path"] ?: return HttpResponse(404, "{\"detail\":\"Not found\"}")
        return if (queue.size > 1) queue.removeFirst() else queue.first()
    }
}
