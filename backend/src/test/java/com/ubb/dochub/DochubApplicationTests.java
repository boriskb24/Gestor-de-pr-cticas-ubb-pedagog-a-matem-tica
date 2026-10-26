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

    @Autowired
    private com.ubb.dochub.controller.PracticaController practicaController;

    @Autowired
    private com.ubb.dochub.repository.InformeRepository informeRepository;

    @Autowired
    private com.ubb.dochub.repository.InscripcionRepository inscripcionRepository;

    @Test
    void testConsultarRespaldoHistoricoInformesComoCoordinadorExitoso() {
        // El coordinador (admin@ubiobio.cl) consulta el respaldo histórico
        java.util.List<java.util.Map<String, Object>> respaldo = practicaService.obtenerRespaldoHistoricoInformes(
                "admin@ubiobio.cl",
                null,
                null,
                null,
                null,
                false // consultar todos para validar estructura
        );

        assertNotNull(respaldo, "El listado de respaldo histórico no debe ser nulo");
        assertFalse(respaldo.isEmpty(), "Debe existir al menos un informe en la base de datos de prueba");

        java.util.Map<String, Object> primerInforme = respaldo.get(0);
        assertTrue(primerInforme.containsKey("id"));
        assertTrue(primerInforme.containsKey("informeId"));
        assertTrue(primerInforme.containsKey("estudianteRut"));
        assertTrue(primerInforme.containsKey("estudianteNombre"));
        assertTrue(primerInforme.containsKey("estudianteCorreo"));
        assertTrue(primerInforme.containsKey("asignaturaCodigo"));
        assertTrue(primerInforme.containsKey("asignaturaNombre"));
        assertTrue(primerInforme.containsKey("anio"));
        assertTrue(primerInforme.containsKey("periodo"));
        assertTrue(primerInforme.containsKey("periodoAcademico"));
        assertTrue(primerInforme.containsKey("estado"));
        assertTrue(primerInforme.containsKey("fechaEntrega"));
        assertTrue(primerInforme.containsKey("archivoUrl"));
        assertTrue(primerInforme.containsKey("nombreArchivo"));
    }

    @Test
    void testConsultarRespaldoHistoricoConFiltroEstudianteYAsignatura() {
        // Valentina Rojas (20666666-6) tiene informe en INF-403
        java.util.List<java.util.Map<String, Object>> resultado = practicaService.obtenerRespaldoHistoricoInformes(
                "admin@ubiobio.cl",
                "Valentina",
                "INF-403",
                null,
                null,
                false
        );

        assertNotNull(resultado);
        assertFalse(resultado.isEmpty(), "Debe encontrar el informe de Valentina");
        assertEquals("20666666-6", resultado.get(0).get("estudianteRut"));
        assertEquals("INF-403", resultado.get(0).get("asignaturaCodigo"));
    }

    @Test
    void testConsultarRespaldoHistoricoPeriodoAnterior() {
        // Encontrar inscripción de periodo anterior (2025-2 de Diego)
        java.util.List<com.ubb.dochub.entity.Inscripcion> inscripciones2025 = inscripcionRepository.findAllWithDetails().stream()
                .filter(i -> i.getOferta() != null && i.getOferta().getAnio() == 2025)
                .toList();

        if (!inscripciones2025.isEmpty()) {
            com.ubb.dochub.entity.Inscripcion inscAnt = inscripciones2025.get(0);
            com.ubb.dochub.entity.Informe infHistorico = new com.ubb.dochub.entity.Informe(
                    com.ubb.dochub.entity.TipoEmisor.ESTUDIANTE,
                    "uploads/informes/historico_2025_diego.pdf",
                    inscAnt
            );
            informeRepository.save(infHistorico);

            // Consultar filtrando solo históricos (periodos pasados)
            java.util.List<java.util.Map<String, Object>> historicos = practicaService.obtenerRespaldoHistoricoInformes(
                    "admin@ubiobio.cl",
                    null,
                    null,
                    null,
                    null,
                    true // solo periodos pasados
            );

            assertNotNull(historicos);
            boolean encontrado = historicos.stream().anyMatch(h -> "2025-2".equals(h.get("periodoAcademico")));
            assertTrue(encontrado, "Debe contener el informe del periodo histórico 2025-2");
        }
    }

    @Test
    void testConsultarRespaldoHistoricoDenegadoParaEstudiante() {
        // Un estudiante no puede tener acceso al respaldo de informes del coordinador
        assertThrows(ResponseStatusException.class, () -> {
            practicaService.obtenerRespaldoHistoricoInformes(
                    "estudiante@alumnos.ubiobio.cl",
                    null,
                    null,
                    null,
                    null,
                    false
            );
        });
    }

    @Test
    void testEndpointControllerHistorialInformes() {
        org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> response =
                practicaController.obtenerHistorialInformes("admin@ubiobio.cl", null, null, null, null, null, null, false);

        assertNotNull(response);
        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }
}
