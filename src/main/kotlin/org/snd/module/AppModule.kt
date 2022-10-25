package org.snd.module

import okhttp3.OkHttpClient
import org.snd.config.AppConfig

class AppModule(
    appConfig: AppConfig
) : AutoCloseable {
    private val okHttpClient = OkHttpClient.Builder().build()
    private val jsonModule = JsonModule()

    private val repositoryModule = RepositoryModule(appConfig.database)

    private val discordModule = DiscordModule(
        config = appConfig.discord,
        okHttpClient = okHttpClient,
        jsonModule = jsonModule,
    )

    private val metadataModule = MetadataModule(
        config = appConfig.metadataProviders,
        okHttpClient = okHttpClient,
        jsonModule = jsonModule
    )

    private val mediaServerModule = MediaServerModule(
        config = appConfig.komga,
        okHttpClient = okHttpClient,
        jsonModule = jsonModule,
        repositoryModule = repositoryModule,
        metadataModule = metadataModule,
        discordModule = discordModule,
    )

    private val serverModule = ServerModule(
        config = appConfig.server,
        mediaServerModule = mediaServerModule,
        jsonModule = jsonModule
    )

    override fun close() {
        serverModule.close()
        mediaServerModule.close()
    }
}
