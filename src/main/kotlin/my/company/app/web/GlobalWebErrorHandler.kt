package my.company.app.web

import com.squareup.moshi.JsonDataException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import my.company.app.lib.InsufficientPermissionsException
import my.company.app.lib.InvalidJsonException
import my.company.app.lib.InvalidLoginCredentialsException
import my.company.app.lib.UserByEmailAlreadyExistsException
import my.company.app.lib.ValidationException
import my.company.app.lib.logger
import javax.validation.ConstraintViolation

class GlobalWebErrorHandler {

    enum class LogType {
        STACK_TRACE,
        MESSAGE_ONLY,
        NOTHING
    }

    companion object {
        private val JSON_DATA_EXCEPTION_PROPERTY_PATH_PATTERN = Regex("""^.*\$\.([\w.]+).*$""")
        private val VALIDATION_FAILED_MESSAGE = ValidationException(emptySet()).message!!
        private val logger = logger<GlobalWebErrorHandler>()
    }

    // <editor-fold="Handler Methods">

    suspend fun PipelineContext<Unit, ApplicationCall>.notFound(e: Throwable, logType: LogType = LogType.STACK_TRACE) {
        log(e, logType)
        call.respond(HttpStatusCode.NotFound, buildResponse(e))
    }

    suspend fun PipelineContext<Unit, ApplicationCall>.preconditionFailed(e: Throwable, logType: LogType = LogType.STACK_TRACE) {
        log(e, logType)
        call.respond(HttpStatusCode.PreconditionFailed, buildResponse(e))
    }

    suspend fun PipelineContext<Unit, ApplicationCall>.badRequest(e: Throwable, logType: LogType = LogType.STACK_TRACE) {
        log(e, logType)
        val message = when (e) {
            is JsonDataException -> VALIDATION_FAILED_MESSAGE
            else -> defaultMessage(e)
        }
        call.respond(HttpStatusCode.BadRequest, buildResponse(e, message = message))
    }

    suspend fun PipelineContext<Unit, ApplicationCall>.unauthorized(e: Throwable, logType: LogType = LogType.STACK_TRACE) {
        log(e, logType)
        call.respond(HttpStatusCode.Unauthorized, buildResponse(e))
    }

    suspend fun PipelineContext<Unit, ApplicationCall>.forbidden(e: Throwable, logType: LogType = LogType.STACK_TRACE) {
        log(e, logType)
        call.respond(HttpStatusCode.Forbidden, buildResponse(e))
    }

    suspend fun PipelineContext<Unit, ApplicationCall>.internalServerError(e: Throwable, logType: LogType = LogType.STACK_TRACE) {
        log(e, logType)
        call.respond(HttpStatusCode.InternalServerError, buildResponse(error = e, message = HttpStatusCode.InternalServerError.description))
    }

    // </editor-fold>

    suspend fun handleError(context: PipelineContext<Unit, ApplicationCall>, e: Throwable): Boolean = with(context) {
        if (!context.isWebRequest()) return false

        when (e) {
            is ValidationException -> badRequest(e, LogType.NOTHING)
            is InvalidJsonException -> badRequest(e, LogType.NOTHING)
            is JsonDataException -> badRequest(e, LogType.NOTHING)
            is InvalidLoginCredentialsException -> unauthorized(e, LogType.NOTHING)
            is UserByEmailAlreadyExistsException -> preconditionFailed(e, LogType.NOTHING)
            is InsufficientPermissionsException -> forbidden(e, LogType.NOTHING)
            else -> internalServerError(e)
        }

        return true
    }

    // <editor-fold="Extensions">

    private fun PipelineContext<Unit, ApplicationCall>.log(e: Throwable, logType: LogType) {
        when (logType) {
            LogType.NOTHING -> return
            LogType.STACK_TRACE -> logger.error("Caught global error from endpoint: ${this.getEndpointInformation()}", e)
            LogType.MESSAGE_ONLY -> logger.error("Caught global error from endpoint: ${this.getEndpointInformation()}")
        }
    }

    private fun buildResponse(
        error: Throwable,
        message: String = defaultMessage(error),
        validationErrors: List<ValidationError> = getValidationErrors(error)
    ): ErrorResponse {
        return ErrorResponse(
            errorMessage = message,
            validationErrors = validationErrors
        )
    }

    private fun defaultMessage(e: Throwable): String {
        return when (e) {
            is JsonDataException -> VALIDATION_FAILED_MESSAGE
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    private fun getValidationErrors(e: Throwable): List<ValidationError> {
        return when (e) {
            is ValidationException -> e.violations.map {
                ValidationError(
                    propertyPath = it.formattedPath(),
                    errorMessage = it.messageTemplate
                )
            }
            is JsonDataException -> listOf(parseJsonDataException(e))
            else -> emptyList()
        }
    }

    private fun parseJsonDataException(e: JsonDataException): ValidationError {
        var errorMessage = "{error.property.invalid}"
        var propertyPath = "<unknown>"

        e.message?.also { error ->
            when {
                errorMessage.startsWith("Not-null value") -> errorMessage = "{validation.property.missing}"
            }
            val match = JSON_DATA_EXCEPTION_PROPERTY_PATH_PATTERN.matchEntire(error)
            match?.groups?.last()?.value?.also { propertyPath = it }
        }
        return ValidationError(
            propertyPath = propertyPath,
            errorMessage = errorMessage
        )
    }

    private fun ConstraintViolation<*>.formattedPath(): String {
        return this.propertyPath.toString()
    }

    // </editor-fold>
}
