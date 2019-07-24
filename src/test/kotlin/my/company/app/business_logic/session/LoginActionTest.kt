package my.company.app.business_logic.session

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import my.company.app.business_logic.AbstractActionTest
import my.company.app.lib.InvalidLoginCredentialsException
import my.company.app.lib.PasswordHelper
import my.company.app.test.captor
import my.company.app.test.declareMock
import my.company.app.test.expectAllChanged
import my.company.app.test.expectAllUnchanged
import my.company.app.test.expectException
import my.company.app.test.singleValue
import my.company.jooq.tables.records.SessionRecord
import my.company.jooq.tables.records.UserRecord
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.Instant
import java.util.UUID

class LoginActionTest : AbstractActionTest() {

    private lateinit var passwordHelper: PasswordHelper

    override fun beforeEach() = super.beforeEach().also {
        passwordHelper = declareMock()
    }

    private data class Context(
        val request: LoginRequest,
        val mockedUser: UserRecord,
        val createdSession: ArgumentCaptor<SessionRecord>
    )

    private suspend fun validContext(): Context {
        val mockedUser = fixtures.user()
        val createdSession = captor<SessionRecord>()

        Mockito.doReturn(mockedUser).`when`(repo.user).findByEmailIgnoringCase(any())
        Mockito.doReturn(true).`when`(passwordHelper).checkPassword(any(), any())
        Mockito.doAnswer { it.arguments.first() }.`when`(repo.session).insert(capture(createdSession))

        return Context(
            request = LoginRequest(mockedUser.email, mockedUser.password),
            mockedUser = mockedUser,
            createdSession = createdSession
        )
    }

    @Test
    fun canLoginWithValidCredentials() = actionTest {
        val ctx = validContext()
        val response = LoginAction().execute(ctx.request)

        ctx.createdSession.singleValue.also { session ->
            assertThat(response).isSameAs(session)
            assertThat(session.id).isInstanceOf(UUID::class)
            assertThat(session.userId).isEqualTo(ctx.mockedUser.id)
            assertThat(session.createdAt).isBetween(Instant.now().minusSeconds(1), Instant.now())
            assertThat(session.updatedAt).isNull()
            session.expectAllChanged()
        }

        ctx.mockedUser.expectAllUnchanged()

        Mockito.verify(repo.user).findByEmailIgnoringCase(ctx.request.email)
        Mockito.verify(passwordHelper).checkPassword(ctx.mockedUser.password, ctx.request.passwordClean)
        Mockito.verify(repo.session).insert(response)
    }

    @Test
    fun throwsInvalidLoginCredentialsExceptionWhenCredentialsAreInvalid() = actionTest {
        val ctx = validContext()
        Mockito.doReturn(false).`when`(passwordHelper).checkPassword(any(), any())
        expectException<InvalidLoginCredentialsException> { LoginAction().execute(ctx.request) }
    }

    @Test
    fun throwsInvalidLoginCredentialsExceptionWhenUserDoesNotExist() = actionTest {
        val ctx = validContext()
        Mockito.doReturn(null).`when`(repo.user).findByEmailIgnoringCase(any())
        expectException<InvalidLoginCredentialsException> { LoginAction().execute(ctx.request) }
    }
}