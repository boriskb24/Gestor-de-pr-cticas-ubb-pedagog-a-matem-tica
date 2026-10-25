package com.ubb.dochub.repository;

import com.ubb.dochub.entity.Informe;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

