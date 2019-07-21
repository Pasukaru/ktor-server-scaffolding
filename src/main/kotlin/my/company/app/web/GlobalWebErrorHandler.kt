package my.company.app.web

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import my.company.app.lib.InvalidLoginCredentialsException
import my.company.app.lib.InsufficientPermissionsException
import my.company.app.lib.UserByEmailAlreadyExistsException
import my.company.app.lib.logger
import my.company.app.lib.validation.ValidationException
import javax.validation.ConstraintViolation

class GlobalWebErrorHandler {

    private val logger = logger<GlobalWebErrorHandler>()

    enum class LogType {
        STACK_TRACE,
        MESSAGE_ONLY,
        NOTHING
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
        call.respond(HttpStatusCode.BadRequest, buildResponse(e))
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
            is MismatchedInputException -> badRequest(e, LogType.NOTHING)
            is MissingKotlinParameterException -> badRequest(e, LogType.NOTHING)
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
        message: String = error.message ?: error.javaClass.simpleName,
        validationErrors: List<ValidationError> = getValidationErrors(error)
    ): ErrorResponse {
        return ErrorResponse(
            errorMessage = message,
            validationErrors = validationErrors
        )
    }

    private fun getValidationErrors(e: Throwable): List<ValidationError> {
        return when (e) {
            is ValidationException -> e.violations.map {
                ValidationError(
                    propertyPath = it.formattedPath(),
                    errorMessage = it.messageTemplate
                )
            }
            is MissingKotlinParameterException -> listOf(
                ValidationError(
                    propertyPath = e.formattedPath(),
                    errorMessage = "{validation.property.missing}"
                )
            )
            is JsonMappingException -> listOf(
                ValidationError(
                    propertyPath = e.formattedPath(),
                    errorMessage = "{validation.property.invalid}"
                )
            )
            else -> emptyList()
        }
    }

    private fun ConstraintViolation<*>.formattedPath(): String {
        return this.propertyPath.toString()
    }

    private fun JsonMappingException.formattedPath(): String = this.path.map { it.fieldName }.joinToString(".")

    // </editor-fold>
}
