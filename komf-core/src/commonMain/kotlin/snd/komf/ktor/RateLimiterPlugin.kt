package snd.komf.ktor

import io.ktor.client.plugins.api.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class RateLimiterPluginConfig {
    var eventsPerInterval: Int = 60
    var interval: Duration = 1.minutes
    var allowBurst = true
    var preconfigured: ThroughputLimiter? = null
}

val HttpRequestRateLimiter = createClientPlugin("RateLimiter", ::RateLimiterPluginConfig) {

    val limiter = pluginConfig.preconfigured
        ?: if (pluginConfig.allowBurst) {
            intervalLimiter(pluginConfig.eventsPerInterval, pluginConfig.interval)
        } else {
            rateLimiter(pluginConfig.eventsPerInterval, pluginConfig.interval)
        }
    onRequest { _, _ -> limiter.acquire() }
}
