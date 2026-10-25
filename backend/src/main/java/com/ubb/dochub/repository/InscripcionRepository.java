package com.ubb.dochub.repository;

import com.ubb.dochub.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {
    // Busca inscripciones cuyos offers pertenezcan a un profesor identificado por correo
    List<Inscripcion> findByOfertaProfesorCorreo(String correo);

    // Busca inscripciones que pertenezcan a una oferta específica
    List<Inscripcion> findByOfertaId(Long ofertaId);

    Long countByOfertaId(Long ofertaId);

    @Query("SELECT i FROM Inscripcion i " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica " +
           "LEFT JOIN FETCH o.profesor " +
           "WHERE i.estudiante.rut = :rut " +
           "ORDER BY o.anio DESC, o.periodo DESC")
    List<Inscripcion> findByEstudianteRutWithOferta(@Param("rut") String rut);

    @Query("SELECT i FROM Inscripcion i " +
           "LEFT JOIN FETCH i.estudiante " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica " +
           "LEFT JOIN FETCH o.profesor " +
           "WHERE LOWER(i.estudiante.correo) = LOWER(:correo) " +
           "ORDER BY o.anio DESC, o.periodo DESC")
    List<Inscripcion> findByEstudianteCorreoWithOferta(@Param("correo") String correo);

    @Query("SELECT i FROM Inscripcion i " +
           "LEFT JOIN FETCH i.estudiante " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica " +
           "LEFT JOIN FETCH o.profesor p " +
           "WHERE LOWER(p.correo) = LOWER(:profesorEmail) " +
           "ORDER BY o.anio DESC, o.periodo DESC")
    List<Inscripcion> findByOfertaProfesorCorreoWithDetails(@Param("profesorEmail") String profesorEmail);

    @Query("SELECT i FROM Inscripcion i " +
           "LEFT JOIN FETCH i.estudiante " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica " +
           "ORDER BY o.anio DESC, o.periodo DESC")
    List<Inscripcion> findAllWithDetails();

    @Query("SELECT i FROM Inscripcion i " +
           "LEFT JOIN FETCH i.estudiante " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica " +
           "LEFT JOIN FETCH o.profesor " +
           "WHERE i.id = :id")
    Optional<Inscripcion> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT i FROM Inscripcion i " +
           "WHERE ((:rut IS NOT NULL AND i.estudiante.rut = :rut) " +
           "    OR (:correo IS NOT NULL AND LOWER(i.estudiante.correo) = LOWER(:correo))) " +
           "  AND i.oferta.anio = :anio " +
           "  AND i.oferta.periodo = :periodo")
    List<Inscripcion> findByEstudianteAndAnioAndPeriodo(
            @Param("rut") String rut,
            @Param("correo") String correo,
            @Param("anio") Integer anio,
            @Param("periodo") Integer periodo);

    @Query("SELECT i FROM Inscripcion i " +
           "LEFT JOIN FETCH i.estudiante " +
           "LEFT JOIN FETCH i.oferta o " +
           "LEFT JOIN FETCH o.asignaturaPractica " +
           "LEFT JOIN FETCH o.profesor " +
           "WHERE ((:rut IS NOT NULL AND i.estudiante.rut = :rut) " +
           "    OR (:correo IS NOT NULL AND LOWER(i.estudiante.correo) = LOWER(:correo))) " +
           "ORDER BY o.anio DESC, o.periodo DESC")
    List<Inscripcion> findActiveInscripcionesByEstudiante(
            @Param("rut") String rut,
            @Param("correo") String correo);
}
