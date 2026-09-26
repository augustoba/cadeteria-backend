package com.cadeteria.backend.config;

import com.cadeteria.backend.common.ApiError;
import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.common.GoneException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.common.ServiceUnavailableException;
import com.cadeteria.backend.common.TooManyRequestsException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Traduce cada tipo de error a su código HTTP y a un mensaje en castellano (revisión 2026-09-26).
 * Antes todo lo que no era "regla de negocio" salía como 500 con el mensaje interno de Java (que
 * podía traer el SQL) y sin quedar en el log.
 * <ul>
 *   <li>400 VALIDACION / JSON_INVALIDO / PARAMETRO_INVALIDO / FALTA_PARAMETRO / DATO_INVALIDO</li>
 *   <li>401 NO_AUTENTICADO / CREDENCIALES_INVALIDAS · 403 SIN_PERMISO · 404 NO_ENCONTRADO</li>
 *   <li>405 METODO_NO_PERMITIDO · 406/415 FORMATO_NO_SOPORTADO · 409 CONFLICTO / DUPLICADO / EDICION_SIMULTANEA</li>
 *   <li>410 LINK_VENCIDO · 413 ARCHIVO_DEMASIADO_GRANDE · 429 DEMASIADAS_SOLICITUDES</li>
 *   <li>500 ERROR_INTERNO (con referencia en el log) · 503 SERVICIO_NO_DISPONIBLE</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Pattern COLUMNA_LARGA = Pattern.compile("Data too long for column '([^']+)'");
    private static final Pattern COLUMNA_NULA = Pattern.compile("Column '([^']+)' cannot be null");

    // --- Reglas de negocio ---

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, "DATO_INVALIDO", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflicto(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICTO", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> prohibido(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, "SIN_PERMISO", ex.getMessage());
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ApiError> vencido(GoneException ex) {
        return build(HttpStatus.GONE, "LINK_VENCIDO", ex.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> noDisponible(ServiceUnavailableException ex) {
        log.warn("Servicio no disponible: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE", ex.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> demasiados(TooManyRequestsException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_SOLICITUDES", ex.getMessage());
    }

    // --- Validación de lo que llega ---

    @ExceptionHandler(BindException.class) // incluye MethodArgumentNotValidException (@Valid en el body)
    public ResponseEntity<ApiError> validacion(BindException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge -> campos.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return validacion(campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> validacion(ConstraintViolationException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath().toString();
            campos.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        }
        return validacion(campos);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> validacion(HandlerMethodValidationException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
                campos.putIfAbsent(r.getMethodParameter().getParameterName(), e.getDefaultMessage())));
        return validacion(campos);
    }

    private ResponseEntity<ApiError> validacion(Map<String, String> campos) {
        String detalle = campos.values().stream().distinct().collect(Collectors.joining(" · "));
        String mensaje = detalle.isBlank() ? "Hay datos inválidos." : "Revisá los datos: " + detalle;
        HttpStatus s = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(s).contentType(MediaType.APPLICATION_JSON).body(ApiError.of(s.value(), s.getReasonPhrase(), "VALIDACION", mensaje, campos, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> jsonInvalido(HttpMessageNotReadableException ex) {
        if (ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing")) {
            return build(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "Faltan los datos: el pedido llegó vacío.");
        }
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String campo = mie.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining("."));
            String problema = "El dato «" + campo + "» no tiene el formato correcto.";
            HttpStatus s = HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(s).contentType(MediaType.APPLICATION_JSON).body(ApiError.of(s.value(), s.getReasonPhrase(), "VALIDACION", problema,
                    Map.of(campo, "Formato incorrecto"), null));
        }
        if (ex.getCause() instanceof JsonMappingException) {
            return build(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "Hay datos con un formato que no se puede leer.");
        }
        return build(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "Los datos enviados no son un JSON válido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parámetro «" + ex.getName() + "» tiene un valor inválido: " + ex.getValue());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> faltaParametro(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "FALTA_PARAMETRO", "Falta el parámetro «" + ex.getParameterName() + "».");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> faltaArchivo(MissingServletRequestPartException ex) {
        return build(HttpStatus.BAD_REQUEST, "FALTA_PARAMETRO", "Falta el archivo «" + ex.getRequestPartName() + "».");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> faltaHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "FALTA_PARAMETRO", "Falta el encabezado «" + ex.getHeaderName() + "».");
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiError> faltaRuta(MissingPathVariableException ex) {
        return build(HttpStatus.BAD_REQUEST, "FALTA_PARAMETRO", "Falta «" + ex.getVariableName() + "» en la dirección.");
    }

    // --- HTTP / rutas ---

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> rutaInexistente(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "No existe la dirección /" + ex.getResourcePath() + ".");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> metodo(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "Esta dirección no acepta " + ex.getMethod() + ".");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> formato(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NO_SOPORTADO",
                "Formato de datos no soportado: " + ex.getContentType() + ".");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> formatoRespuesta(HttpMediaTypeNotAcceptableException ex) {
        return build(HttpStatus.NOT_ACCEPTABLE, "FORMATO_NO_SOPORTADO", "No se puede responder en el formato pedido.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> archivoGrande(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE", "El archivo es demasiado grande.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> conStatus(ResponseStatusException ex) {
        String mensaje = ex.getReason() != null ? ex.getReason() : "No se pudo completar la operación.";
        return build(ex.getStatusCode(), codigoPara(ex.getStatusCode()), mensaje);
    }

    // --- Seguridad ---

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> credenciales(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> unauthorized(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Tu sesión venció o no iniciaste sesión.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "SIN_PERMISO", "No tenés permiso para hacer eso.");
    }

    // --- Base de datos ---

    /** Duplicado, texto más largo que la columna, dato obligatorio vacío o registro en uso. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridad(DataIntegrityViolationException ex) {
        String causa = ex.getMostSpecificCause().getMessage();
        causa = causa == null ? "" : causa;
        log.warn("Violación de integridad: {}", causa);
        if (causa.contains("Duplicate entry")) {
            return build(HttpStatus.CONFLICT, "DUPLICADO", "Ya existe un registro con esos datos.");
        }
        Matcher larga = COLUMNA_LARGA.matcher(causa);
        if (larga.find()) {
            return build(HttpStatus.BAD_REQUEST, "VALIDACION", "El dato «" + larga.group(1) + "» es demasiado largo.");
        }
        Matcher nula = COLUMNA_NULA.matcher(causa);
        if (nula.find()) {
            return build(HttpStatus.BAD_REQUEST, "VALIDACION", "Falta el dato «" + nula.group(1) + "».");
        }
        if (causa.contains("foreign key constraint")) {
            return build(HttpStatus.CONFLICT, "CONFLICTO", "No se puede: el registro está en uso por otros datos.");
        }
        return build(HttpStatus.CONFLICT, "CONFLICTO", "Los datos chocan con otros ya guardados.");
    }

    /** Dos operaciones tocando lo mismo a la vez (bloqueo, deadlock) — se puede reintentar. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> concurrencia(ConcurrencyFailureException ex) {
        log.warn("Conflicto de concurrencia: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "EDICION_SIMULTANEA",
                "Otra operación estaba modificando lo mismo en ese momento. Actualizá y probá de nuevo.");
    }

    // --- Cualquier otra cosa ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex) {
        // Excepciones propias de Spring MVC que no tienen handler arriba: respetan su status.
        if (ex instanceof ErrorResponse er) {
            HttpStatusCode s = er.getStatusCode();
            if (s.is4xxClientError()) {
                return build(s, codigoPara(s), "La solicitud no es válida.");
            }
        }
        String referencia = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Error inesperado [ref {}]", referencia, ex);
        HttpStatus s = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(s).contentType(MediaType.APPLICATION_JSON).body(ApiError.of(s.value(), s.getReasonPhrase(), "ERROR_INTERNO",
                "Ocurrió un error inesperado. Si se repite, avisá a soporte con el código " + referencia + ".",
                null, referencia));
    }

    private static String codigoPara(HttpStatusCode s) {
        return switch (s.value()) {
            case 400 -> "DATO_INVALIDO";
            case 401 -> "NO_AUTENTICADO";
            case 403 -> "SIN_PERMISO";
            case 404 -> "NO_ENCONTRADO";
            case 405 -> "METODO_NO_PERMITIDO";
            case 406, 415 -> "FORMATO_NO_SOPORTADO";
            case 409 -> "CONFLICTO";
            case 410 -> "LINK_VENCIDO";
            case 413 -> "ARCHIVO_DEMASIADO_GRANDE";
            case 429 -> "DEMASIADAS_SOLICITUDES";
            case 503 -> "SERVICIO_NO_DISPONIBLE";
            default -> s.is5xxServerError() ? "ERROR_INTERNO" : "DATO_INVALIDO";
        };
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String codigo, String message) {
        String error = status instanceof HttpStatus hs ? hs.getReasonPhrase() : String.valueOf(status.value());
        // Siempre JSON: sin encabezado Accept (la app Android no lo manda) Spring elegía XML.
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(status.value(), error, codigo, message));
    }
}
