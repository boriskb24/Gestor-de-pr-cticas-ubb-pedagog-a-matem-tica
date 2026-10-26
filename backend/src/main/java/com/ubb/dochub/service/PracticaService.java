package com.ubb.dochub.service;

import com.ubb.dochub.dto.EvaluacionesResponseDto;
import com.ubb.dochub.dto.InformeEntregaResponse;
import com.ubb.dochub.dto.TipoFiltroEvaluacion;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

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

    // Resumen del estado general de la práctica y porcentaje de avance (Historia de usuario Estudiante)
    Map<String, Object> obtenerResumenAvanceEstudiante(String correoEstudiante, String rutEstudiante, Long inscripcionId);

    // Consulta detallada de tareas, documentos y evaluaciones pendientes con sus fechas límite
    List<Map<String, Object>> obtenerTareasPendientesEstudiante(String correoEstudiante, String rutEstudiante, Long inscripcionId);

    // Respaldo histórico de informes finales para Coordinador de prácticas
    List<Map<String, Object>> obtenerRespaldoHistoricoInformes(
            String consultorEmail,
            String estudiante,
            String asignatura,
            Integer anio,
            Integer periodo,
            Boolean soloHistoricos);

    default List<Map<String, Object>> obtenerRespaldoHistoricoInformes(
            String consultorEmail,
            String estudiante,
            String asignatura,
            Integer anio,
            Integer periodo) {
        return obtenerRespaldoHistoricoInformes(consultorEmail, estudiante, asignatura, anio, periodo, null);
    }
}
