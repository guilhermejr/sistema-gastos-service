package net.guilhermejr.sistema.gastosservice.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.exception.dto.ErrorDefaultDTO;
import net.guilhermejr.sistema.gastosservice.exception.dto.ErrorRequestDTO;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestControllerAdvice
public class ErrorHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(ExceptionDefault.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorDefaultDTO> handleErroDefault(ExceptionDefault ex, WebRequest request) {

        log.error(ex.getMessage(), ex);
        List<ErrorDefaultDTO> dto = new ArrayList<>();
        dto.add(new ErrorDefaultDTO(ex.getMessage()));
        return dto;

    }

    @ExceptionHandler(ExceptionNotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public List<ErrorDefaultDTO> handleErroNotFound(ExceptionNotFound ex, WebRequest request) {

        log.error(ex.getMessage(), ex);
        List<ErrorDefaultDTO> dto = new ArrayList<>();
        dto.add(new ErrorDefaultDTO(ex.getMessage()));
        return dto;

    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorDefaultDTO> handleErrorMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest request) {

        log.error(ex.getMessage(), ex);
        List<ErrorDefaultDTO> dto = new ArrayList<>();
        dto.add(new ErrorDefaultDTO("UUID inválido."));
        return dto;

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorRequestDTO> handleErrorIn(MethodArgumentNotValidException exception) {

        List<ErrorRequestDTO> dto = new ArrayList<>();
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        fieldErrors.forEach(e -> {
            String mensagem = messageSource.getMessage(e, LocaleContextHolder.getLocale());
            ErrorRequestDTO erro = new ErrorRequestDTO(e.getField(), mensagem);
            dto.add(erro);
        });

        return dto;
    }

}
