package com.gaozay.smartflight.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

interface ReleaseApiClient {
    suspend fun fetchLatest(source: UpdateSource): Result<ReleaseInfo>
}

class HttpReleaseApiClient @Inject constructor(
    private val parser: ReleaseJsonParser,
) : ReleaseApiClient {
    override suspend fun fetchLatest(source: UpdateSource): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(source.latestReleaseUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SmartFlight Update Checker")
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorBody = connection.errorStream?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }.orEmpty()
                    error("${source.label} release 请求失败：HTTP $code ${errorBody.take(160)}")
                }
                val body = connection.inputStream.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
                parser.parse(source, body).getOrThrow()
            } finally {
                connection.disconnect()
            }
        }
    }
}
