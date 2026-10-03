package com.gaozay.smartflight.update

import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.AppStrings
import javax.inject.Inject
import org.json.JSONObject

class ReleaseJsonParser @Inject constructor() {
    fun parse(source: UpdateSource, json: String): Result<ReleaseInfo> = runCatching {
        val root = JSONObject(json)
        val tagName = root.optString("tag_name").takeIf { it.isNotBlank() }
            ?: error(AppStrings.get(R.string.missing_release_tag_name))
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
