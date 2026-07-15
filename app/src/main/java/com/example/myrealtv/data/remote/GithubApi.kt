package com.example.myrealtv.data.remote

import retrofit2.http.GET

data class GithubRelease(
    val tag_name: String,
    val assets: List<GithubAsset>
)

data class GithubAsset(
    val name: String,
    val browser_download_url: String
)

interface GithubApi {
    @GET("repos/duff22/myreal/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}
