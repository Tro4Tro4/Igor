package com.igor.fridge.data.onlineprices

import com.igor.fridge.data.local.OnlineOffer
import com.igor.fridge.data.local.OnlineSource
import com.igor.fridge.data.openprices.HttpRequest
import com.igor.fridge.data.openprices.HttpTransport
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.net.URLEncoder

class OnlinePricesClient(private val transport: HttpTransport, private val baseUrl: String, private val allowLocalHttp: Boolean = false) {
    val configured: Boolean get() = baseUrl.isNotBlank()
    init {
        if (configured) {
            val uri = URI(baseUrl)
            require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.host != null)
            require(uri.scheme == "https" || allowLocalHttp && uri.scheme == "http" && uri.host == "localhost")
        }
    }
    suspend fun sources(): List<OnlineSource> = decode { OnlinePricesJson.parseSources(get("/v1/sources")) }
    suspend fun search(query: String?, gtin: String?, postcode: String, offset: Int = 0): OnlinePage {
        require((query == null) != (gtin == null) && postcode.matches(Regex("[0-9]{5}")) && offset >= 0)
        val term = if (gtin != null) "gtin=${encode(gtin)}" else "q=${encode(query!!)}"
        return decode { OnlinePricesJson.parseOffers(get("/v1/products?$term&postcode=$postcode&offset=$offset&limit=20"),postcode) }
    }
    suspend fun offers(productId: String, postcode: String): List<OnlineOffer> = decode {
        require(postcode.matches(Regex("[0-9]{5}")))
        OnlinePricesJson.parseOffers(get("/v1/products/${encode(productId)}/offers?postcode=$postcode"),postcode).items
    }
    private suspend fun get(path: String): String {
        if (!configured) throw OnlinePricesException("Servizio prezzi non configurato")
        val response = transport.execute(HttpRequest("GET",baseUrl.trimEnd('/')+path,headers=mapOf("User-Agent" to "Igor/0.1.0 (Android)")))
        if (!response.isSuccessful) throw OnlinePricesException("Servizio prezzi HTTP ${response.code}",response.code == 429 || response.code >= 500)
        if (response.body.length > 2*1024*1024) throw OnlinePricesException("Risposta prezzi troppo grande")
        return response.body
    }
    private suspend fun <T> decode(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: OnlinePricesException) { throw e }
    catch (e: java.io.IOException) { throw OnlinePricesException("Connessione al servizio prezzi non riuscita",true) }
    catch (e: Exception) { throw OnlinePricesException("Risposta del servizio prezzi non valida") }
    private fun encode(value: String) = URLEncoder.encode(value,"UTF-8")
}
