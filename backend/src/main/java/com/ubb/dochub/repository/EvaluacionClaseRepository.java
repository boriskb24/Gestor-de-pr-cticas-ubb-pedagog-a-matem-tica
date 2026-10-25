package com.ubb.dochub.repository;

import com.ubb.dochub.entity.EvaluacionClase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EvaluacionClaseRepository extends JpaRepository<EvaluacionClase, Long> {

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "JOIN ec.clase c " +
           "JOIN c.inscripcion i " +
           "JOIN i.oferta o " +
           "JOIN o.asignaturaPractica a " +
           "WHERE o.profesor.rut = :rutProfesor")
    List<EvaluacionClase> findEvaluacionesPorRutProfesor(@Param("rutProfesor") String rutProfesor);

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "JOIN ec.clase c " +
           "JOIN c.inscripcion i " +
           "JOIN i.oferta o " +
           "JOIN o.asignaturaPractica a " +
           "WHERE (:rutProfesor IS NOT NULL AND o.profesor.rut = :rutProfesor) " +
           "   OR (:correoProfesor IS NOT NULL AND LOWER(o.profesor.correo) = LOWER(:correoProfesor))")
    List<EvaluacionClase> findEvaluacionesPorProfesor(
            @Param("rutProfesor") String rutProfesor,
            @Param("correoProfesor") String correoProfesor);

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "JOIN ec.clase c " +
           "JOIN c.inscripcion i " +
           "JOIN i.oferta o " +
           "JOIN o.asignaturaPractica a " +
           "LEFT JOIN ec.evaluador ev " +
           "WHERE ((:rutProfesor IS NOT NULL AND (o.profesor.rut = :rutProfesor OR (ev IS NOT NULL AND ev.rut = :rutProfesor))) " +
           "    OR (:correoProfesor IS NOT NULL AND (LOWER(o.profesor.correo) = LOWER(:correoProfesor) OR (ev IS NOT NULL AND LOWER(ev.correo) = LOWER(:correoProfesor)))) " +
           "    OR (:correoProfesor = 'admin@ubiobio.cl')) " +
           "  AND (:rutEstudiante IS NULL OR i.estudiante.rut = :rutEstudiante) " +
           "  AND (:inscripcionId IS NULL OR i.id = :inscripcionId)")
    List<EvaluacionClase> findEvaluacionesPorProfesor(
            @Param("rutProfesor") String rutProfesor,
            @Param("correoProfesor") String correoProfesor,
            @Param("rutEstudiante") String rutEstudiante,
            @Param("inscripcionId") Long inscripcionId);

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "JOIN ec.clase c " +
           "JOIN c.inscripcion i " +
           "WHERE i.id = :inscripcionId")
    List<EvaluacionClase> findByInscripcionId(@Param("inscripcionId") Long inscripcionId);

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "JOIN ec.clase c " +
           "JOIN c.inscripcion i " +
           "WHERE ((:correo IS NOT NULL AND LOWER(i.estudiante.correo) = LOWER(:correo)) " +
           "    OR (:rut IS NOT NULL AND i.estudiante.rut = :rut)) " +
           "  AND (:inscripcionId IS NULL OR i.id = :inscripcionId) " +
           "ORDER BY c.fecha DESC")
    List<EvaluacionClase> findEvaluacionesPorEstudiante(
            @Param("correo") String correo,
            @Param("rut") String rut,
            @Param("inscripcionId") Long inscripcionId);

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "LEFT JOIN FETCH ec.clase c " +
           "LEFT JOIN FETCH c.inscripcion i " +
           "LEFT JOIN FETCH i.estudiante e " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica ap " +
           "LEFT JOIN FETCH ec.evaluador ev " +
           "WHERE ((:rutEstudiante IS NOT NULL AND i.estudiante.rut = :rutEstudiante) " +
           "    OR (:correoEstudiante IS NOT NULL AND LOWER(i.estudiante.correo) = LOWER(:correoEstudiante))) " +
           "  AND (:inscripcionId IS NULL OR i.id = :inscripcionId) " +
           "ORDER BY c.fecha ASC, c.horaInicio ASC, ec.id ASC")
    List<EvaluacionClase> findHistoricoEvaluacionesEstudiante(
            @Param("rutEstudiante") String rutEstudiante,
            @Param("correoEstudiante") String correoEstudiante,
            @Param("inscripcionId") Long inscripcionId);

    @Query("SELECT ec FROM EvaluacionClase ec " +
           "LEFT JOIN FETCH ec.clase c " +
           "LEFT JOIN FETCH c.inscripcion i " +
           "LEFT JOIN FETCH i.estudiante e " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica ap " +
           "LEFT JOIN FETCH ec.evaluador ev " +
           "WHERE i.estudiante.rut = :rutEstudiante " +
           "ORDER BY c.fecha ASC, c.horaInicio ASC, ec.id ASC")
    List<EvaluacionClase> findHistoricoPorRutEstudiante(@Param("rutEstudiante") String rutEstudiante);
}