package my.company.app.business_logic

import com.nhaarman.mockitokotlin2.any
import dev.fixtures.InMemoryFixtures
import kotlinx.coroutines.runBlocking
import my.company.app.db.ModelGenerator
import my.company.app.initConfig
import my.company.app.lib.AuthorizationService
import my.company.app.lib.IdGenerator
import my.company.app.lib.TimeService
import my.company.app.lib.koin.KoinContext
import my.company.app.lib.koin.KoinCoroutineInterceptor
import my.company.app.lib.koin.eager
import my.company.app.lib.repository.Repositories
import my.company.app.lib.validation.ValidationService
import my.company.app.test.AbstractTest
import my.company.app.test.MockedTimeService
import my.company.app.test.mockedContainerModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.mockito.Mockito

abstract class AbstractActionTest : AbstractTest() {

    open val profile: String = "test"

    lateinit var koin: KoinApplication
    lateinit var repo: Repositories
    lateinit var mockedTimeService: TimeService
    lateinit var validationService: ValidationService
    lateinit var spiedModelGenerator: ModelGenerator

    lateinit var mockedAuthorizationService: AuthorizationService

    protected val fixtures = InMemoryFixtures

    @BeforeEach
    protected open fun beforeEach() {
        koin = KoinContext.startKoin {
            modules(listOf(
                module { single { initConfig(profile) } },
                module { single { IdGenerator() } },
                module { single { Mockito.mock(ValidationService::class.java) } },
                module { single { MockedTimeService.mock } },
                module { single { Mockito.spy(ModelGenerator()) } },
                module { single { Mockito.mock(AuthorizationService::class.java) } },
                mockedContainerModule<Repositories>()
            ))
        }
        validationService = eager()
        mockedTimeService = eager()
        spiedModelGenerator = eager()
        mockedAuthorizationService = eager()
        repo = eager()
        Mockito.doAnswer { it.arguments.first() }.`when`(validationService).validate<Any>(any())
    }

    @AfterEach
    protected open fun afterEach() {
        KoinContext.stopKoin()
    }

    fun actionTest(testFn: suspend () -> Unit) {
        runBlocking(KoinCoroutineInterceptor(koin)) { testFn() }
    }
}
