package com.ubb.dochub.service;

import com.ubb.dochub.dto.EvaluacionesResponseDto;
import com.ubb.dochub.dto.InformeEntregaResponse;
import com.ubb.dochub.dto.TipoFiltroEvaluacion;
import org.springframework.web.multipart.MultipartFile;

public interface PracticaService {
    // Guardar informe general / estudiante
    InformeEntregaResponse guardarInformeFinal(MultipartFile archivo);

    // Guardar informe asociado a una inscripcion (usado por profesor)
    InformeEntregaResponse guardarInformeFinalParaInscripcion(MultipartFile archivo, Long inscripcionId);

    // Guardar informe final del estudiante (asociado automáticamente a su práctica activa)
    InformeEntregaResponse guardarInformeFinalEstudiante(MultipartFile archivo, String correoEstudiante, Long userId);

    // Consultar el último informe entregado por un estudiante
    InformeEntregaResponse obtenerUltimoInformeEstudiante(String email);

    // Evaluaciones de profesor (Pablo)
    default EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, TipoFiltroEvaluacion tipo) {
        return obtenerEvaluacionesPorProfesor(rutProfesor, null, null, null, tipo);
    }

    default EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, String correoProfesor, TipoFiltroEvaluacion tipo) {
        return obtenerEvaluacionesPorProfesor(rutProfesor, correoProfesor, null, null, tipo);
    }

    EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, String correoProfesor, String rutEstudiante, Long inscripcionId, TipoFiltroEvaluacion tipo);

    // Evaluaciones de estudiante (limitadas a su práctica actual por defecto)
    EvaluacionesResponseDto obtenerEvaluacionesPorEstudiante(String correoEstudiante, String rutEstudiante, Long inscripcionId, TipoFiltroEvaluacion tipo);

    // Validación de invariante: máximo 1 práctica por estudiante en el mismo período académico
    void validarInscripcionUnicaPorPeriodo(String rutEstudiante, String correoEstudiante, Integer anio, Integer periodo);

    // Consulta histórica para evolución del desempeño del estudiante (Prof. Colaborador / Tutor / Profesor)
    EvaluacionesResponseDto obtenerEvolucionEvaluacionesEstudiante(
            String rutEstudiante,
            String correoEstudiante,
            Long inscripcionId,
            String consultorEmail,
            TipoFiltroEvaluacion tipo);
}
