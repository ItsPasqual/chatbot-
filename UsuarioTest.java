package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsuarioTest {

    @Test
    void rejeitaNomeVazioOuNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(""));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("   "));
        assertThrows(IllegalArgumentException.class, () -> new Usuario(null));
    }

    @Test
    void rejeitaNomeComEspacoOuMuitoLongo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("ana maria"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("a".repeat(31)));
    }

    @Test
    void igualdadeIgnoraMaiusculas() {
        assertEquals(new Usuario("Ana"), new Usuario("ana"));
        assertEquals(new Usuario("Ana").hashCode(), new Usuario("ANA").hashCode());
    }
}
