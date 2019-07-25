package my.company.app.web.controller

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.times
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.ktor.util.pipeline.Pipeline
import io.ktor.util.pipeline.PipelinePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import my.company.app.initConfig
import my.company.app.lib.TransactionService
import my.company.app.lib.di.KoinCoroutineInterceptor
import my.company.app.lib.eager
import my.company.app.lib.ktor.getKoin
import my.company.app.lib.validation.ValidationService
import my.company.app.mainModule
import my.company.app.test.AbstractTest
import my.company.app.test.declareMock
import my.company.app.test.expectNotNull
import my.company.app.test.fixtures.InMemoryFixtures
import my.company.app.web.ErrorResponse
import my.company.app.web.getPathFromLocation
import org.mockito.Mockito
import kotlin.reflect.KClass

abstract class BaseWebControllerTest(
    protected val location: KClass<*>,
    protected val url: String = getPathFromLocation(location)
) : AbstractTest() {

    protected val fixtures = InMemoryFixtures

    protected inline fun TestApplicationEngine.jsonPost(body: Any, crossinline setup: TestApplicationRequest.() -> Unit = {}, testFn: TestApplicationCall.() -> Unit = {}) {
        with(handleRequest(HttpMethod.Post, url) {
            val content = eager<Moshi>().adapter<Any>(body::class.java).toJson(body).toByteArray(Charsets.UTF_8)
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(content)
            addHeader(HttpHeaders.ContentLength, content.size.toString())
            setup()
        }) {
            testFn()
        }
    }

    protected inline fun TestApplicationEngine.jsonGet(crossinline setup: TestApplicationRequest.() -> Unit = {}, testFn: TestApplicationCall.() -> Unit = {}) {
        with(handleRequest(HttpMethod.Get, url) {
            setup()
        }) {
            testFn()
        }
    }

    protected open fun Routing.skipInterceptors() {
        // TODO: Find a nicer way than this ugly reflection magic to skip interceptors
        val findPhase = Pipeline::class.java.getDeclaredMethod("findPhase", PipelinePhase::class.java).also { it.isAccessible = true }
        val phase = findPhase(this, ApplicationCallPipeline.Call)
        if (phase != null) {
            val interceptors = phase::class.java.getDeclaredField("interceptors").also { it.isAccessible = true }.get(phase) as ArrayList<*>
            interceptors.clear()
        }
    }

    protected open suspend fun TestApplicationEngine.mockTransactions() {
        val transactionService = declareMock<TransactionService>()
        Mockito.doAnswer {
            runBlocking {
                @Suppress("UNCHECKED_CAST") val fn = it.arguments.first() as suspend CoroutineScope.() -> Any?
                fn(this)
            }
        }.`when`(transactionService).noTransaction<Any>(any())
        Mockito.doAnswer {
            runBlocking {
                @Suppress("UNCHECKED_CAST") val fn = it.arguments.first() as suspend CoroutineScope.() -> Any?
                fn(this)
            }
        }.`when`(transactionService).transaction<Any>(any())
    }

    protected fun mockValidator(): ValidationService {
        val validator = declareMock<ValidationService>()
        Mockito.doAnswer { it.arguments.first() }.`when`(validator).validate<Any>(any())
        return validator
    }

    protected fun controllerTest(
        profile: String = "test",
        testFn: suspend TestApplicationEngine.() -> Unit
    ) {
        initConfig(profile)
        withTestApplication(Application::mainModule) {
            val koin = this.application.getKoin()
            runBlocking(KoinCoroutineInterceptor(koin)) {
                application.routing {
                    skipInterceptors()
                }
                mockTransactions()
                testFn()
            }
        }
    }

    suspend inline fun expectTransaction() {
        Mockito.verify(eager<TransactionService>(), times(1)).transaction<Any>(any())
    }

    inline fun <reified RESPONSE_BODY : Any> TestApplicationCall.jsonResponse(): RESPONSE_BODY = response.jsonResponse()
    inline fun <reified RESPONSE_BODY : Any> TestApplicationResponse.jsonResponse(): RESPONSE_BODY {
        val str = content.expectNotNull()
        return eager<Moshi>().adapter<RESPONSE_BODY>(RESPONSE_BODY::class.java).fromJson(str).expectNotNull()
    }

    fun <RESPONSE_BODY : Any> TestApplicationCall.jsonResponse(responseBody: KClass<RESPONSE_BODY>): RESPONSE_BODY = response.jsonResponse(responseBody)
    fun <RESPONSE_BODY : Any> TestApplicationResponse.jsonResponse(responseBody: KClass<RESPONSE_BODY>): RESPONSE_BODY {
        val str = content.expectNotNull()
        return eager<Moshi>().adapter<RESPONSE_BODY>(responseBody.java).fromJson(str).expectNotNull()
    }

    inline fun <reified RESPONSE_BODY> TestApplicationCall.jsonResponseList(): List<RESPONSE_BODY> = response.jsonResponseList()
    inline fun <reified RESPONSE_BODY> TestApplicationResponse.jsonResponseList(): List<RESPONSE_BODY> {
        val type = Types.newParameterizedType(List::class.java, RESPONSE_BODY::class.java)
        val str = content.expectNotNull()
        return eager<Moshi>().adapter<List<RESPONSE_BODY>>(type).fromJson(str) ?: emptyList()
    }

    fun TestApplicationResponse.expectError(status: HttpStatusCode, error: Throwable) {
        assertThat(this.status()).isEqualTo(status)
        val response = jsonResponse<ErrorResponse>()
        assertThat(response).isEqualTo(ErrorResponse(
            errorMessage = error.message!!,
            validationErrors = mutableListOf()
        ))
    }

    inline fun <reified RESPONSE_BODY : Any> TestApplicationCall.expectJsonResponse(status: HttpStatusCode, expectedBody: RESPONSE_BODY): RESPONSE_BODY = expectJsonResponse(RESPONSE_BODY::class, status, expectedBody)

    fun <RESPONSE_BODY : Any> TestApplicationCall.expectJsonResponse(responseBody: KClass<RESPONSE_BODY>, status: HttpStatusCode, expectedBody: RESPONSE_BODY): RESPONSE_BODY {
        assertThat(this.response.status()).isEqualTo(status)
        val response = jsonResponse(responseBody)
        assertThat(response).isEqualTo(expectedBody)
        return response
    }

    inline fun <reified RESPONSE_BODY : Any> TestApplicationCall.expectJsonResponseList(status: HttpStatusCode, expectedBody: List<RESPONSE_BODY>): List<RESPONSE_BODY> {
        assertThat(this.response.status()).isEqualTo(status)
        val response = jsonResponseList<RESPONSE_BODY>()
        assertThat(response).isEqualTo(expectedBody)
        return response
    }

    fun ValidationService.hasValidated(obj: Any) {
        Mockito.verify(this, Mockito.times(1)).validate(obj)
    }
}
