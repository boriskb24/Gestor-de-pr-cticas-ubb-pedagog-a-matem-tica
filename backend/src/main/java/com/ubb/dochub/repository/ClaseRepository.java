package com.ubb.dochub.repository;

import com.ubb.dochub.entity.Clase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaseRepository extends JpaRepository<Clase, Long> {

    List<Clase> findByPlanificacionId(Long planificacionId);

    List<Clase> findByInscripcionId(Long inscripcionId);

    @Query("SELECT c FROM Clase c " +
           "JOIN FETCH c.planificacion p " +
           "JOIN FETCH c.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "WHERE LOWER(e.correo) = LOWER(:correo) " +
           "ORDER BY p.fecha DESC, p.id DESC")
    List<Clase> findClasesWithPlanificacionByEstudianteCorreo(@Param("correo") String correo);

    @Query("SELECT c FROM Clase c " +
           "JOIN FETCH c.planificacion p " +
           "JOIN FETCH c.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "JOIN FETCH i.oferta o " +
           "JOIN FETCH o.profesor prof " +
           "WHERE LOWER(prof.correo) = LOWER(:correo) " +
           "ORDER BY p.fecha DESC, p.id DESC")
    List<Clase> findClasesWithPlanificacionByProfesorCorreo(@Param("correo") String correo);

    @Query("SELECT c FROM Clase c " +
           "JOIN FETCH c.planificacion p " +
           "JOIN FETCH c.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "WHERE i.id = :inscripcionId " +
           "ORDER BY p.fecha DESC, p.id DESC")
    List<Clase> findClasesWithPlanificacionByInscripcionId(@Param("inscripcionId") Long inscripcionId);

    @Query("SELECT c FROM Clase c " +
           "JOIN FETCH c.planificacion p " +
           "JOIN FETCH c.inscripcion i " +
           "JOIN FETCH i.estudiante e " +
           "ORDER BY p.fecha DESC, p.id DESC")
    List<Clase> findAllClasesWithPlanificacion();

    long countByInscripcionId(Long inscripcionId);

    @Query("SELECT COUNT(c) FROM Clase c WHERE c.inscripcion.id = :inscripcionId AND c.planificacion IS NOT NULL")
    long countConPlanificacionByInscripcionId(@Param("inscripcionId") Long inscripcionId);

    @Query("SELECT COUNT(c) FROM Clase c WHERE c.inscripcion.id = :inscripcionId AND c.planificacion IS NOT NULL AND c.planificacion.estado = com.ubb.dochub.entity.EstadoPlanificacion.APROBADA")
    long countAprobadasByInscripcionId(@Param("inscripcionId") Long inscripcionId);
}
