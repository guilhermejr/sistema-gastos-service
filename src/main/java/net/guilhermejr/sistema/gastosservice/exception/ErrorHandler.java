package net.guilhermejr.sistema.gastosservice.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.exception.dto.ErrorDefaultDTO;
import net.guilhermejr.sistema.gastosservice.exception.dto.ErrorRequestDTO;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
    public List<ErrorDefaultDTO> handleErroDefault(ExceptionDefault ex) {

        log.error(ex.getMessage(), ex);
        return List.of(new ErrorDefaultDTO(ex.getMessage()));

    }

    @ExceptionHandler(ExceptionNotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public List<ErrorDefaultDTO> handleErroNotFound(ExceptionNotFound ex) {

        log.error(ex.getMessage(), ex);
        return List.of(new ErrorDefaultDTO(ex.getMessage()));

    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorDefaultDTO> handleErroParametro(Exception ex) {

        log.error(ex.getMessage(), ex);
        return List.of(new ErrorDefaultDTO("Parâmetro inválido."));

    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorDefaultDTO> handleErroCorpo(HttpMessageNotReadableException ex) {

        log.error(ex.getMessage(), ex);
        return List.of(new ErrorDefaultDTO("Requisição inválida."));

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorRequestDTO> handleErrorIn(MethodArgumentNotValidException exception) {

        List<ErrorRequestDTO> dto = new ArrayList<>();
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        fieldErrors.forEach(e -> {
            String mensagem = messageSource.getMessage(e, LocaleContextHolder.getLocale());
            dto.add(new ErrorRequestDTO(e.getField(), mensagem));
        });

        return dto;
    }

}
