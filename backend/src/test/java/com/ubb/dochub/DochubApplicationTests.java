package com.ubb.dochub;

import com.ubb.dochub.dto.EvaluacionesResponseDto;
import com.ubb.dochub.dto.TipoFiltroEvaluacion;
import com.ubb.dochub.service.PracticaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DochubApplicationTests {

    @Autowired
    private PracticaService practicaService;

    @Test
    void contextLoads() {
    }

    @Test
    void testConsultarEvolucionEstudianteConExito() {
        // Diego Martínez (20555555-5) tiene evaluaciones en la semilla
        EvaluacionesResponseDto resp = practicaService.obtenerEvolucionEvaluacionesEstudiante(
                "20555555-5",
                null,
                null,
                "carlos.munoz@ubiobio.cl", // Evaluador Tutor
                TipoFiltroEvaluacion.TODAS
        );

        assertNotNull(resp);
        assertNotNull(resp.getEvaluacionesClase());
        assertNotNull(resp.getEvaluacionesSemestrales());
        assertFalse(resp.getEvaluacionesClase().isEmpty(), "Debe retornar evaluaciones de clase");

        // Validar ordenamiento cronológico ascendente (evolución temporal)
        for (int i = 0; i < resp.getEvaluacionesClase().size() - 1; i++) {
            var actual = resp.getEvaluacionesClase().get(i).getFecha();
            var siguiente = resp.getEvaluacionesClase().get(i + 1).getFecha();
            if (actual != null && siguiente != null) {
                assertTrue(!actual.isAfter(siguiente), "Las evaluaciones deben estar ordenadas cronológicamente de forma ascendente");
            }
        }
    }

    @Test
    void testConsultarEvolucionEstudianteNoExistente() {
        assertThrows(ResponseStatusException.class, () -> {
            practicaService.obtenerEvolucionEvaluacionesEstudiante(
                    "99999999-9",
                    null,
                    null,
                    "carlos.munoz@ubiobio.cl",
                    TipoFiltroEvaluacion.TODAS
            );
        });
    }

    @Test
    void testConsultarEvolucionSinParametros() {
        assertThrows(ResponseStatusException.class, () -> {
            practicaService.obtenerEvolucionEvaluacionesEstudiante(
                    null,
                    null,
                    null,
                    "carlos.munoz@ubiobio.cl",
                    TipoFiltroEvaluacion.TODAS
            );
        });
    }
}
