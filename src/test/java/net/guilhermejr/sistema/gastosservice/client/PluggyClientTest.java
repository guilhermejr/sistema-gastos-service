package net.guilhermejr.sistema.gastosservice.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PluggyClientTest {

    @Test
    @DisplayName("Tira o cursor do next, mantendo + / = do base64")
    public void cursor_do_next() {

        Assertions.assertEquals("MjAy+MC0x/MC==",
                PluggyClient.cursor("?accountId=562b795d-1653-429f-be86-74ead9502813&after=MjAy+MC0x/MC=="));

    }

    @Test
    @DisplayName("Decodifica o cursor que vem codificado no next")
    public void cursor_codificado() {

        Assertions.assertEquals("MjAy+MC0x/MC==", PluggyClient.cursor("?accountId=abc&after=MjAy%2BMC0x%2FMC%3D%3D"));

    }

    @Test
    @DisplayName("Sem next, acabou")
    public void sem_next() {

        Assertions.assertNull(PluggyClient.cursor(null));
        Assertions.assertNull(PluggyClient.cursor(""));
        Assertions.assertNull(PluggyClient.cursor("?accountId=abc"));

    }

}
