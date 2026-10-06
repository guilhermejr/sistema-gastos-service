package net.guilhermejr.sistema.gastosservice.exception;

public class ExceptionDefault extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExceptionDefault(String mensagem){
        super(mensagem);
    }

}
