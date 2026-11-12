package me.enivzoy.anilife.core.net

import kotlinx.coroutines.runBlocking
import me.enivzoy.anilife.core.model.SkipTimes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpExchange
import java.io.OutputStream

class AniSkipClientTest {
    private fun server(handler: (HttpExchange) -> Unit): HttpServer {
        val s = HttpServer.create(InetSocketAddress(0), 0)
        s.createContext("/") { exchange ->
            handler(exchange)
            exchange.close()
        }
        s.start()
        return s
    }

    private fun respond(exchange: HttpExchange, code: Int, body: String) {
        exchange.sendResponseHeaders(code, body.toByteArray().size.toLong())
        val out: OutputStream = exchange.responseBody
        out.write(body.toByteArray())
        out.close()
    }

    @Test
    fun picksTheCandidateNearestTheStreamLength() = runBlocking {
        val s = server { exchange ->
            val path = exchange.requestURI.path
            if (path.contains("skip-times")) {
                respond(exchange, 200, """
                    {"found":true,"results":[
                      {"skipType":"op","episodeLength":1200.0,"interval":{"startTime":10.0,"endTime":100.0}},
                      {"skipType":"op","episodeLength":1440.0,"interval":{"startTime":20.0,"endTime":110.0}},
                      {"skipType":"ed","episodeLength":1440.0,"interval":{"startTime":1300.0,"endTime":1390.0}}
                    ]}
                """.trimIndent())
            } else {
                respond(exchange, 404, "{}")
            }
        }
        try {
            val base = "http://127.0.0.1:${s.address.port}"
            val client = AniSkipClient(http = java.net.http.HttpClient.newHttpClient(), endpoint = base)
            val skip = client.skipTimes(813, 56, 1438.0)
            assertEquals(20.0, skip?.intro?.start)
            assertEquals(110.0, skip?.intro?.end)
            assertEquals(1300.0, skip?.outro?.start)
        } finally {
            s.stop(0)
        }
    }

    @Test
    fun mixedSegmentsFillMissingPlainOpeningsAndInvalidIntervalsAreIgnored() = runBlocking {
        val s = server { exchange ->
            respond(exchange, 200, """
                {"found":true,"results":[
                  {"skipType":"op","episodeLength":1440.0,"interval":{"startTime":90.0,"endTime":20.0}},
                  {"skipType":"mixed-op","episodeLength":1440.0,"interval":{"startTime":4.5,"endTime":94.5}}
                ]}
            """.trimIndent())
        }
        try {
            val base = "http://127.0.0.1:${s.address.port}"
            val client = AniSkipClient(http = java.net.http.HttpClient.newHttpClient(), endpoint = base)
            val skip = client.skipTimes(1, 1, 1440.0)
            assertEquals(4.5, skip?.intro?.start)
            assertEquals(94.5, skip?.intro?.end)
            assertNull(skip?.outro)
        } finally {
            s.stop(0)
        }
    }

    @Test
    fun returnsNullOn404() = runBlocking {
        val s = server { exchange ->
            respond(exchange, 404, """{"found":false}""")
        }
        try {
            val base = "http://127.0.0.1:${s.address.port}"
            val client = AniSkipClient(http = java.net.http.HttpClient.newHttpClient(), endpoint = base)
            assertNull(client.skipTimes(1, 1, 1440.0))
        } finally {
            s.stop(0)
        }
    }

    @Test
    fun followsRelationRulesWhenFirstLookupIsEmpty() = runBlocking {
        val s = server { exchange ->
            val uri = exchange.requestURI.toString()
            when {
                uri.contains("relation-rules") -> respond(exchange, 200, """{"found":true,"rules":[{"from":{"start":50,"end":59},"to":{"malId":38524,"start":1,"end":10}}]}""")
                uri.contains("skip-times/38524") -> respond(exchange, 200, """{"found":true,"results":[{"skipType":"op","episodeLength":1440.0,"interval":{"startTime":5.0,"endTime":95.0}}]}""")
                else -> respond(exchange, 200, """{"found":false,"results":[]}""")
            }
        }
        try {
            val base = "http://127.0.0.1:${s.address.port}"
            val client = AniSkipClient(http = java.net.http.HttpClient.newHttpClient(), endpoint = base)
            val skip = client.skipTimes(16498, 50, 1440.0)
            assertEquals(5.0, skip?.intro?.start)
        } finally {
            s.stop(0)
        }
    }
}
