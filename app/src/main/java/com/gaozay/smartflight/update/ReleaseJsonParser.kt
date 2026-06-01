package com.gaozay.smartflight.update

import org.json.JSONObject
import javax.inject.Inject

class ReleaseJsonParser @Inject constructor() {
    fun parse(source: UpdateSource, json: String): Result<ReleaseInfo> = runCatching {
        val root = JSONObject(json)
        val tagName = root.optString("tag_name").takeIf { it.isNotBlank() }
            ?: error("缺少 release tag_name")
        val assets = root.optJSONArray("assets")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val asset = array.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    val downloadUrl = asset.optString("browser_download_url")
                    if (name.isNotBlank() && downloadUrl.isNotBlank()) {
                        add(ReleaseAsset(name = name, downloadUrl = downloadUrl))
                    }
                }
            }
        }.orEmpty()

        ReleaseInfo(
            source = source,
            tagName = tagName,
            name = root.optString("name").ifBlank { tagName },
            body = root.optString("body"),
            htmlUrl = root.optString("html_url"),
            assets = assets,
        )
    }
}
