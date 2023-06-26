package org.telegram.extension

import com.blankj.utilcode.util.GsonUtils
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit


object SheetUtil {
    private val client = OkHttpClient().newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    @JvmStatic
    fun read(sheetUrl: String): List<List<String>> {
        val request = Request.Builder().url(
            HttpUrl.parse(Env.SHEETDB_URL)!!
                .newBuilder()
                .addQueryParameter("action", "read")
                .addQueryParameter("sheet_url", sheetUrl).build()
        )
            .build()
        val response: Response = client.newCall(request).execute()

        val result = GsonUtils.fromJson(
            response.body()!!.string(),
            SheetResponse::class.java
        )

        return result.data
    }

    fun write(dataSheetUrl: String, values: ArrayList<List<String>>): Boolean {
        try {
            val body: RequestBody = RequestBody.create(
                MediaType.parse("application/json"), GsonUtils.toJson(values)
            )

            val request = Request.Builder().url(
                HttpUrl.parse(Env.SHEETDB_URL)!!
                    .newBuilder()
                    .addQueryParameter("action", "write")
                    .addQueryParameter("sheet_url", dataSheetUrl).build()
            ).post(body)
                .build()
            val response: Response = client.newCall(request).execute()
            return response.isSuccessful
        } catch (e: Exception) {
            return false
        }
    }

    data class SheetResponse(
        val data: List<List<String>>
    )

}