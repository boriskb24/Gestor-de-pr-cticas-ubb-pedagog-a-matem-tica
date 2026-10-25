package com.ubb.dochub.repository;

import com.ubb.dochub.entity.Asignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {
    
    @Query("SELECT a FROM Asignacion a LEFT JOIN FETCH a.evaluador WHERE a.inscripcion.id = :inscripcionId")
    List<Asignacion> findByInscripcionIdWithEvaluador(@Param("inscripcionId") Long inscripcionId);

    List<Asignacion> findByInscripcionId(Long inscripcionId);

    long countByInscripcionId(Long inscripcionId);
}

