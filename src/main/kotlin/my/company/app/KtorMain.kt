@file:Suppress("EXPERIMENTAL_API_USAGE")

package my.company.app

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.features.StatusPages
import io.ktor.jackson.jackson
import io.ktor.locations.Locations
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import my.company.app.business_logic.session.SessionActions
import my.company.app.business_logic.user.UserActions
import my.company.app.conf.AppConfig
import my.company.app.conf.AppConfigLoader
import my.company.app.db.ModelGenerator
import my.company.app.db.jooq.HikariCPFeature
import my.company.app.lib.AuthorizationService
import my.company.app.lib.IdGenerator
import my.company.app.lib.PasswordHelper
import my.company.app.lib.TimeService
import my.company.app.lib.TransactionService
import my.company.app.lib.containerModule
import my.company.app.lib.eager
import my.company.app.lib.ktor.ApplicationWarmup
import my.company.app.lib.ktor.StartupLog
import my.company.app.lib.ktor.uuidConverter
import my.company.app.lib.logger
import my.company.app.lib.repository.Repositories
import my.company.app.lib.swagger.SwaggerConfiguration
import my.company.app.lib.validation.ValidationService
import my.company.app.web.GlobalWebErrorHandler
import my.company.app.web.WebRoutingFeature
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.ktor.ext.Koin
import org.reflections.Reflections
import springfox.documentation.builders.ParameterBuilder
import springfox.documentation.schema.ModelRef
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.validation.Validation

private lateinit var appConfig: AppConfig

class KtorMain {

    companion object {
        const val GRADE_PERIOD_IN_SECONDS = 1L
        const val SHUTDOWN_TIMEOUT_IN_SECONDS = 5L
        val REFLECTIONS: Reflections

        val logger = logger<KtorMain>()

        init {
            val start = System.currentTimeMillis()
            REFLECTIONS = Reflections(PackageNoOp::class.java.`package`.name)
            logger.info("Classpath scanning took: ${System.currentTimeMillis() - start}ms")
        }
    }

    fun main() {
        initConfig(System.getenv("PROFILE"))
        embeddedServer(
            Netty,
            port = appConfig.ktorPort,
            module = Application::mainModule
        ).gracefulStart()
    }

    private fun <T : ApplicationEngine> T.gracefulStart(): T {
        this.start(wait = false)
        Runtime.getRuntime().addShutdownHook(Thread({
            this.stop(GRADE_PERIOD_IN_SECONDS, SHUTDOWN_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)
        }, "KtorMain"))
        Thread.currentThread().join()
        return this
    }
}

fun initConfig(profile: String? = null): AppConfig {
    appConfig = AppConfigLoader.loadProfile(profile)
    return appConfig
}

fun Application.mainModule() {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC))

    install(CallLogging)
    install(Locations)

    install(Koin) {
        modules(
            listOf(
                module {
                    single { appConfig }
                    single {
                        val authHeader = ParameterBuilder()
                            .name("X-Auth-Token")
                            .description("Authentication token")
                            .modelRef(ModelRef("uuid"))
                            .parameterType("header")
                            .required(false)
                            .allowEmptyValue(false)
                            .allowMultiple(false)
                            .build()
                            .let { listOf(it) }
                        SwaggerConfiguration()
                            .registerOperationParameterInterceptor { authHeader }
                    }
                    single { IdGenerator() }
                    single { TimeService() }
                    single { ModelGenerator() }
                    single { AuthorizationService() }
                    single { GlobalWebErrorHandler() }
                    single { PasswordHelper() }
                    val validationFactory = Validation.buildDefaultValidatorFactory()!!
                    single { validationFactory }
                    single { validationFactory.validator }
                    single { ValidationService() }
                    single { TransactionService() }
                },
                containerModule<Repositories>(),
                containerModule<SessionActions>(),
                containerModule<UserActions>()
            )
        )
    }

    install(DataConversion) {
        uuidConverter()
    }

    install(ContentNegotiation) {
        jackson {
            configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // Instead of throwing an exception, ignore additional json properties that don't exist in our DTOs
            GlobalContext.get().modules(module { single(createdAtStart = true) { this@jackson } })
        }
    }

    install(HikariCPFeature)

    install(WebRoutingFeature)
    install(StatusPages) {
        exception<Throwable> { error ->
            if (!eager<GlobalWebErrorHandler>().handleError(this, error)) {
                KtorMain.logger.error("Caught unhandled error:", error)
            }
        }
    }

    if (!appConfig.isDev && !appConfig.isTest) {
        install(ApplicationWarmup)
    }

    install(StartupLog)
}

fun main() = KtorMain().main()
