package com.calle.sdk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Result model for Google search queries via SerpApi.
 */
data class SerpSearchResult(
    val phone: String = "",
    val businessName: String = "",
    val address: String = "",
    val snippetInfo: String = ""
)

/**
 * Client for executing Google searches via SerpApi to discover business phone numbers,
 * addresses, opening hours, and location details online.
 */
class SerpApiClient(
    private val apiKey: String = ""
) {
    suspend fun searchBusiness(query: String): SerpSearchResult = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = "https://serpapi.com/search.json?q=$encodedQuery&api_key=$apiKey&engine=google"

            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
            }

            if (connection.responseCode in 200..299) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)

                var foundPhone = ""
                var foundName = ""
                var foundAddress = ""
                val snippets = mutableListOf<String>()

                // 1. Inspect Knowledge Graph
                if (root.has("knowledge_graph")) {
                    val kg = root.getJSONObject("knowledge_graph")
                    if (kg.has("phone")) foundPhone = kg.getString("phone")
                    if (kg.has("title")) foundName = kg.getString("title")
                    if (kg.has("address")) foundAddress = kg.getString("address")
                    if (kg.has("description")) snippets.add(kg.getString("description"))
                }

                // 2. Inspect Local Map Results
                if (foundPhone.isBlank() && root.has("local_results")) {
                    val lr = root.getJSONArray("local_results")
                    if (lr.length() > 0) {
                        val first = lr.getJSONObject(0)
                        if (first.has("phone")) foundPhone = first.getString("phone")
                        if (first.has("title")) foundName = first.getString("title")
                        if (first.has("address")) foundAddress = first.getString("address")
                    }
                }

                // 3. Inspect Organic Search Snippets
                if (root.has("organic_results")) {
                    val orgs = root.getJSONArray("organic_results")
                    for (i in 0 until minOf(orgs.length(), 4)) {
                        val item = orgs.getJSONObject(i)
                        val snippet = item.optString("snippet", "")
                        if (snippet.isNotBlank()) snippets.add(snippet)

                        if (foundPhone.isBlank()) {
                            val phoneMatch = Regex("""\+?\d[\d\s\-\(\)]{8,14}\d""").find(snippet)?.value ?: ""
                            if (phoneMatch.isNotBlank()) {
                                foundPhone = phoneMatch
                            }
                        }
                    }
                }

                SerpSearchResult(
                    phone = foundPhone,
                    businessName = foundName,
                    address = foundAddress,
                    snippetInfo = snippets.joinToString(" ")
                )
            } else {
                SerpSearchResult(snippetInfo = "SerpApi HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            SerpSearchResult(snippetInfo = e.message ?: "SerpApi search failed")
        }
    }
}
