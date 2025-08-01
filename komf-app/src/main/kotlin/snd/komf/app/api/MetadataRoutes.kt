package snd.komf.app.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.getOrFail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import snd.komf.api.KomfErrorResponse
import snd.komf.api.KomfProviderSeriesId
import snd.komf.api.job.KomfMetadataJobId
import snd.komf.api.metadata.KomfIdentifyRequest
import snd.komf.api.metadata.KomfMetadataJobResponse
import snd.komf.api.metadata.KomfMetadataSeriesSearchResult
import snd.komf.app.api.mappers.fromProvider
import snd.komf.app.api.mappers.toProvider
import snd.komf.comicinfo.ComicInfoWriter.ComicInfoException
import snd.komf.mediaserver.MediaServerClient
import snd.komf.mediaserver.MetadataServiceProvider
import snd.komf.mediaserver.model.MediaServerLibraryId
import snd.komf.mediaserver.model.MediaServerSeriesId
import snd.komf.model.ProviderSeriesId
import snd.komf.providers.CoreProviders

private val logger = KotlinLogging.logger {}

class MetadataRoutes(
    private val metadataServiceProvider: Flow<MetadataServiceProvider>,
    private val mediaServerClient: Flow<MediaServerClient>,
) {

    fun registerRoutes(routing: Route) {
        routing.route("/metadata") {
            getProvidersRoute()
            searchSeriesRoute()
            getSeriesCoverRoute()
            identifySeriesRoute()

            matchSeriesRoute()
            matchLibraryRoute()

            resetSeriesRoute()
            resetLibraryRoute()
        }
    }

    private fun Route.getProvidersRoute() {
        get("/providers") {
            val libraryId = call.request.queryParameters["libraryId"]?.let { MediaServerLibraryId(it) }

            val providers = (
                    libraryId
                        ?.let { metadataServiceProvider.first().metadataServiceFor(it.value).availableProviders(it) }
                        ?: metadataServiceProvider.first().defaultMetadataService().availableProviders()
                    )
                .map { it.providerName().name }

            call.respond(providers)
        }
    }

    private fun Route.searchSeriesRoute() {
        get("/search") {
            val seriesName = call.request.queryParameters["name"]
                ?: return@get call.response.status(HttpStatusCode.BadRequest)

            val seriesId = call.request.queryParameters["seriesId"]?.let { MediaServerSeriesId(it) }
            val libraryId = call.request.queryParameters["libraryId"]
                ?.let { MediaServerLibraryId(it) }
                ?: seriesId?.let { mediaServerClient.first().getSeries(it).libraryId }

            try {
                val searchResults = libraryId
                    ?.let {
                        metadataServiceProvider.first().metadataServiceFor(it.value).searchSeriesMetadata(seriesName, it)
                    }
                    ?: metadataServiceProvider.first().defaultMetadataService().searchSeriesMetadata(seriesName)

                call.respond(HttpStatusCode.OK, searchResults.map {
                    KomfMetadataSeriesSearchResult(
                        url = it.url,
                        imageUrl = it.imageUrl,
                        title = it.title,
                        provider = it.provider.fromProvider(),
                        resultId = KomfProviderSeriesId(it.resultId)
                    )
                })
            } catch (exception: ResponseException) {
                call.respond(exception.response.status, KomfErrorResponse(exception.response.bodyAsText()))
                logger.catching(exception)
            } catch (exception: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    KomfErrorResponse("${exception::class.simpleName} :${exception.message}")
                )
                logger.catching(exception)
            }
        }
    }

    private fun Route.getSeriesCoverRoute() {
        get("/series-cover") {
            val libraryId = MediaServerLibraryId(call.request.queryParameters.getOrFail("libraryId"))
            val provider = CoreProviders.valueOf(call.request.queryParameters.getOrFail("provider"))
            val providerSeriesId = ProviderSeriesId(call.request.queryParameters.getOrFail("providerSeriesId"))

            val metadataService = metadataServiceProvider.first().metadataServiceFor(libraryId.value)
            val image = metadataService.getSeriesCover(
                libraryId = libraryId,
                providerName = provider,
                providerSeriesId = providerSeriesId
            )
            image?.bytes?.let { call.respondBytes { it } }
                ?: call.response.status(HttpStatusCode.NotFound)

        }
    }

    private fun Route.identifySeriesRoute() {
        post("/identify") {
            val request = call.receive<KomfIdentifyRequest>()

            val libraryId = request.libraryId?.value
                ?: mediaServerClient.first().getSeries(MediaServerSeriesId(request.seriesId.value)).libraryId.value

            val jobId = metadataServiceProvider.first().metadataServiceFor(libraryId).setSeriesMetadata(
                MediaServerSeriesId(request.seriesId.value),
                request.provider.toProvider(),
                ProviderSeriesId(request.providerSeriesId.value),
                null
            )

            call.respond(
                KomfMetadataJobResponse(KomfMetadataJobId(jobId.value.toString()))
            )
        }
    }

    private fun Route.matchSeriesRoute() {
        post("/match/library/{libraryId}/series/{seriesId}") {

            val libraryId = call.parameters.getOrFail("libraryId")
            val seriesId = MediaServerSeriesId(call.parameters.getOrFail("seriesId"))
            val jobId = metadataServiceProvider.first().metadataServiceFor(libraryId).matchSeriesMetadata(seriesId)

            call.respond(
                KomfMetadataJobResponse(KomfMetadataJobId(jobId.value.toString()))
            )
        }
    }

    private fun Route.matchLibraryRoute() {
        post("/match/library/{libraryId}") {
            val libraryId = MediaServerLibraryId(call.parameters.getOrFail("libraryId"))
            metadataServiceProvider.first().metadataServiceFor(libraryId.value).matchLibraryMetadata(libraryId)
            call.response.status(HttpStatusCode.Accepted)
        }
    }

    private fun Route.resetSeriesRoute() {
        post("/reset/library/{libraryId}/series/{seriesId}") {
            val libraryId = call.parameters.getOrFail("libraryId")
            val seriesId = MediaServerSeriesId(call.parameters.getOrFail("seriesId"))
            val removeComicInfo = call.queryParameters["removeComicInfo"].toBoolean()
            try {
                metadataServiceProvider.first().updateServiceFor(libraryId).resetSeriesMetadata(seriesId, removeComicInfo)
            } catch (e: ComicInfoException) {
                call.respond(HttpStatusCode.UnprocessableEntity, KomfErrorResponse(e.message))
                return@post
            }
            call.respond(HttpStatusCode.NoContent, "")
        }
    }

    private fun Route.resetLibraryRoute() {
        post("/reset/library/{libraryId}") {
            val libraryId = MediaServerLibraryId(call.parameters.getOrFail("libraryId"))
            val removeComicInfo = call.queryParameters["removeComicInfo"].toBoolean()
            metadataServiceProvider.first().updateServiceFor(libraryId.value)
                .resetLibraryMetadata(libraryId, removeComicInfo)
            call.response.status(HttpStatusCode.NoContent)
        }
    }

}