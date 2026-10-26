package com.ubb.dochub.repository;

import com.ubb.dochub.entity.Informe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ubb.dochub.entity.TipoEmisor;
import java.util.List;
import java.util.Optional;

@Repository
public interface InformeRepository extends JpaRepository<Informe, Long> {
    List<Informe> findByInscripcionIdOrderByFechaDesc(Long inscripcionId);
    List<Informe> findByInscripcionIdOrderByIdDesc(Long inscripcionId);
    List<Informe> findByInscripcionIdAndEmisorOrderByIdDesc(Long inscripcionId, TipoEmisor emisor);
    Optional<Informe> findFirstByInscripcionIdOrderByFechaDesc(Long inscripcionId);
    Optional<Informe> findFirstByInscripcionIdOrderByIdDesc(Long inscripcionId);
    long countByInscripcionId(Long inscripcionId);
    boolean existsByInscripcionId(Long inscripcionId);

    // Consulta flexible para el respaldo histórico de informes finales con filtros opcionales
    @Query("SELECT inf FROM Informe inf " +
           "JOIN FETCH inf.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "JOIN FETCH i.oferta o " +
           "JOIN FETCH o.asignaturaPractica ap " +
           "LEFT JOIN FETCH o.profesor p " +
           "WHERE (:estudiante IS NULL " +
           "       OR LOWER(e.rut) LIKE LOWER(CONCAT('%', :estudiante, '%')) " +
           "       OR LOWER(e.correo) LIKE LOWER(CONCAT('%', :estudiante, '%')) " +
           "       OR LOWER(e.primerNombre) LIKE LOWER(CONCAT('%', :estudiante, '%')) " +
           "       OR LOWER(e.apellidoPaterno) LIKE LOWER(CONCAT('%', :estudiante, '%')) " +
           "       OR LOWER(e.apellidoMaterno) LIKE LOWER(CONCAT('%', :estudiante, '%'))) " +
           "  AND (:asignatura IS NULL " +
           "       OR LOWER(ap.codigo) LIKE LOWER(CONCAT('%', :asignatura, '%')) " +
           "       OR LOWER(ap.nombre) LIKE LOWER(CONCAT('%', :asignatura, '%'))) " +
           "  AND (:anio IS NULL OR o.anio = :anio) " +
           "  AND (:periodo IS NULL OR o.periodo = :periodo) " +
           "  AND (:emisor IS NULL OR inf.emisor = :emisor) " +
           "  AND (:soloHistoricos = false " +
           "       OR o.anio < :anioActual " +
           "       OR (o.anio = :anioActual AND o.periodo < :periodoActual)) " +
           "ORDER BY o.anio DESC, o.periodo DESC, inf.fecha DESC, inf.id DESC")
    List<Informe> findHistorialInformes(
            @Param("estudiante") String estudiante,
            @Param("asignatura") String asignatura,
            @Param("anio") Integer anio,
            @Param("periodo") Integer periodo,
            @Param("emisor") TipoEmisor emisor,
            @Param("soloHistoricos") boolean soloHistoricos,
            @Param("anioActual") int anioActual,
            @Param("periodoActual") int periodoActual);

    // Consulta directa para periodos académicos anteriores
    @Query("SELECT inf FROM Informe inf " +
           "JOIN FETCH inf.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "JOIN FETCH i.oferta o " +
           "JOIN FETCH o.asignaturaPractica ap " +
           "LEFT JOIN FETCH o.profesor p " +
           "WHERE o.anio < :anioActual OR (o.anio = :anioActual AND o.periodo < :periodoActual) " +
           "ORDER BY o.anio DESC, o.periodo DESC, inf.fecha DESC, inf.id DESC")
    List<Informe> findInformesPeriodosAnteriores(
            @Param("anioActual") int anioActual,
            @Param("periodoActual") int periodoActual);

    // Consulta de informes finales filtrados por estudiante
    @Query("SELECT inf FROM Informe inf " +
           "JOIN FETCH inf.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "JOIN FETCH i.oferta o " +
           "JOIN FETCH o.asignaturaPractica ap " +
           "LEFT JOIN FETCH o.profesor p " +
           "WHERE LOWER(e.rut) = LOWER(:rut) OR LOWER(e.correo) = LOWER(:correo) " +
           "ORDER BY o.anio DESC, o.periodo DESC, inf.fecha DESC, inf.id DESC")
    List<Informe> findInformesByEstudiante(
            @Param("rut") String rut,
            @Param("correo") String correo);
}

