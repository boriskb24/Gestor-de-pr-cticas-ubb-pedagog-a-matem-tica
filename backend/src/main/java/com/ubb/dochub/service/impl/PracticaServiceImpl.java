package com.ubb.dochub.service.impl;

import com.ubb.dochub.dto.EvaluacionCLADto;
import com.ubb.dochub.dto.EvaluacionSEMDto;
import com.ubb.dochub.dto.EvaluacionesResponseDto;
import com.ubb.dochub.dto.InformeEntregaResponse;
import com.ubb.dochub.dto.TipoFiltroEvaluacion;
import com.ubb.dochub.entity.Estudiante;
import com.ubb.dochub.entity.Evaluador;
import com.ubb.dochub.entity.EvaluacionClase;
import com.ubb.dochub.entity.EvaluacionSemestral;
import com.ubb.dochub.entity.Informe;
import com.ubb.dochub.entity.Inscripcion;
import com.ubb.dochub.entity.Oferta;
import com.ubb.dochub.entity.TipoEmisor;
import com.ubb.dochub.entity.User;
import com.ubb.dochub.repository.EstudianteRepository;
import com.ubb.dochub.repository.EvaluacionClaseRepository;
import com.ubb.dochub.repository.EvaluacionSemestralRepository;
import com.ubb.dochub.repository.InformeRepository;
import com.ubb.dochub.repository.InscripcionRepository;
import com.ubb.dochub.repository.UserRepository;
import com.ubb.dochub.service.PracticaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PracticaServiceImpl implements PracticaService {

    private static final String UPLOAD_DIR = "uploads/informes";
    private final InscripcionRepository inscripcionRepository;
    private final InformeRepository informeRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private EvaluacionClaseRepository evaluacionClaseRepository;

    @Autowired
    private EvaluacionSemestralRepository evaluacionSemestralRepository;

    @Autowired
    private UserRepository userRepository;

    public PracticaServiceImpl(InscripcionRepository inscripcionRepository, InformeRepository informeRepository) {
        this.inscripcionRepository = inscripcionRepository;
        this.informeRepository = informeRepository;
    }

    @Override
    public InformeEntregaResponse guardarInformeFinal(MultipartFile archivo) {
        // 1. Validar que el archivo no esté vacío
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un archivo para subir.");
        }

        // 2. Validar estrictamente el Content-Type para PDF
        String contentType = archivo.getContentType();
        String originalFilename = archivo.getOriginalFilename();

        boolean isPdfMime = "application/pdf".equalsIgnoreCase(contentType);
        boolean hasPdfExtension = originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");

        if (!isPdfMime || !hasPdfExtension) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
                "Formato inválido. Solo se admiten archivos en formato PDF (application/pdf)."
            );
        }

        // 3. Simular guardado en almacenamiento local
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Nombre único para evitar colisiones
            String safeFileName = UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "informe.pdf");
            Path targetLocation = uploadPath.resolve(safeFileName);
            
            Files.copy(archivo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return new InformeEntregaResponse(
                "¡Informe final de práctica entregado con éxito!",
                originalFilename,
                archivo.getSize(),
                contentType,
                LocalDateTime.now()
            );
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al procesar y guardar el archivo: " + e.getMessage()
            );
        }
    }

    @Override
    public EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, String correoProfesor, TipoFiltroEvaluacion tipo) {
        return obtenerEvaluacionesPorProfesor(rutProfesor, correoProfesor, null, null, tipo);
    }

    @Override
    public EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, String correoProfesor, String rutEstudiante, Long inscripcionId, TipoFiltroEvaluacion tipo) {
        List<EvaluacionCLADto> listaClase = new ArrayList<>();
        List<EvaluacionSEMDto> listaSemestral = new ArrayList<>();

        String rutNorm = (rutProfesor != null && !rutProfesor.trim().isEmpty()) ? rutProfesor.trim() : null;
        String correoNorm = (correoProfesor != null && !correoProfesor.trim().isEmpty()) ? correoProfesor.trim().toLowerCase() : null;
        String rutEstNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;

        // 1. Recuperar evaluaciones de clase o todas
        if (tipo == TipoFiltroEvaluacion.CLASE || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionClase> evalClases;
            if (rutNorm != null || correoNorm != null || rutEstNorm != null || inscripcionId != null) {
                evalClases = evaluacionClaseRepository.findEvaluacionesPorProfesor(rutNorm, correoNorm, rutEstNorm, inscripcionId);
            } else {
                evalClases = evaluacionClaseRepository.findAll();
            }

            for (EvaluacionClase ec : evalClases) {
                Estudiante est = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
                java.time.LocalDateTime fechaClaseHora = ec.getClase() != null && ec.getClase().getFecha() != null
                        ? ec.getClase().getFecha().atStartOfDay()
                        : null;
                
                String archivoUrl = "/api/practicas/evaluaciones/clase/" + ec.getId() + "/archivo";
                String nombreArchivo = ec.getArchivo() != null ? java.nio.file.Paths.get(ec.getArchivo()).getFileName().toString() : "Pauta_Evaluacion.pdf";

                Evaluador ev = ec.getEvaluador();
                String evaluadorNombre = ev != null
                        ? (ev.getPrimerNombre() + " " + ev.getApellidoPaterno() + " " + ev.getApellidoMaterno()).trim()
                        : "Evaluador No Asignado";
                String evaluadorRut = ev != null ? ev.getRut() : "";
                String evaluadorTipo = ev != null && ev.getTipo() != null ? ev.getTipo().name() : "";

                Oferta of = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getOferta()
                        : null;
                Integer anio = of != null ? of.getAnio() : null;
                Integer periodo = of != null ? of.getPeriodo() : null;
                String asignaturaNombre = (of != null && of.getAsignaturaPractica() != null)
                        ? of.getAsignaturaPractica().getNombre()
                        : (ec.getClase() != null ? ec.getClase().getAsignatura() : "");

                listaClase.add(new EvaluacionCLADto(
                    ec.getId(),
                    rutEst,
                    nombreComp,
                    "CLASE",
                    null,
                    null,
                    fechaClaseHora,
                    archivoUrl,
                    nombreArchivo,
                    evaluadorNombre,
                    evaluadorRut,
                    evaluadorTipo,
                    anio,
                    periodo,
                    asignaturaNombre
                ));
            }
        }

        // 2. Recuperar todas las evaluaciones semestrales
        if (tipo == TipoFiltroEvaluacion.SEMESTRAL || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionSemestral> evalSemestrales;
            if (rutNorm != null || correoNorm != null || rutEstNorm != null || inscripcionId != null) {
                evalSemestrales = evaluacionSemestralRepository.findEvaluacionesPorProfesor(rutNorm, correoNorm, rutEstNorm, inscripcionId);
            } else {
                evalSemestrales = evaluacionSemestralRepository.findAll();
            }

            for (EvaluacionSemestral es : evalSemestrales) {
                Estudiante est = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
                String archivoUrl = "/api/practicas/evaluaciones/semestral/" + es.getId() + "/archivo";
                String nombreArchivo = es.getArchivo() != null ? java.nio.file.Paths.get(es.getArchivo()).getFileName().toString() : "Evaluacion_Semestral.pdf";

                Evaluador ev = es.getAsignacion() != null ? es.getAsignacion().getEvaluador() : null;
                String evaluadorNombre = ev != null
                        ? (ev.getPrimerNombre() + " " + ev.getApellidoPaterno() + " " + ev.getApellidoMaterno()).trim()
                        : "Evaluador No Asignado";
                String evaluadorRut = ev != null ? ev.getRut() : "";
                String evaluadorTipo = ev != null && ev.getTipo() != null ? ev.getTipo().name() : "";

                Oferta of = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getOferta()
                        : null;
                Integer anio = of != null ? of.getAnio() : null;
                Integer periodo = of != null ? of.getPeriodo() : null;
                String asignaturaNombre = (of != null && of.getAsignaturaPractica() != null)
                        ? of.getAsignaturaPractica().getNombre()
                        : es.getAsignatura();

                listaSemestral.add(new EvaluacionSEMDto(
                    es.getId(),
                    rutEst,
                    nombreComp,
                    "SEMESTRAL",
                    null,
                    null,
                    es.getFecha(),
                    archivoUrl,
                    nombreArchivo,
                    evaluadorNombre,
                    evaluadorRut,
                    evaluadorTipo,
                    anio,
                    periodo,
                    asignaturaNombre
                ));
            }
        }

        return new EvaluacionesResponseDto(listaClase, listaSemestral);
    }

    @Override
    public InformeEntregaResponse guardarInformeFinalParaInscripcion(MultipartFile archivo, Long inscripcionId) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un archivo para subir.");
        }

        String contentType = archivo.getContentType();
        String originalFilename = archivo.getOriginalFilename();

        boolean isPdfMime = "application/pdf".equalsIgnoreCase(contentType);
        boolean hasPdfExtension = originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");

        if (!isPdfMime || !hasPdfExtension) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
                "Formato inválido. Solo se admiten archivos en formato PDF (application/pdf)."
            );
        }

        Inscripcion inscripcion = inscripcionRepository.findById(inscripcionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada."));

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String safeFileName = UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "informe.pdf");
            Path targetLocation = uploadPath.resolve(safeFileName);
            Path parent = targetLocation.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(archivo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String rutaArchivo = targetLocation.toString().replace('\\', '/');

            // Persistir o actualizar registro de Informe para esta inscripción
            List<Informe> existentes = informeRepository.findByInscripcionIdAndEmisorOrderByIdDesc(inscripcionId, TipoEmisor.PROFESOR);
            Informe informe;
            if (!existentes.isEmpty()) {
                informe = existentes.get(0);
                informe.setArchivo(rutaArchivo);
            } else {
                informe = new Informe(TipoEmisor.PROFESOR, rutaArchivo, inscripcion);
            }
            Informe saved = informeRepository.save(informe);

            InformeEntregaResponse resp = new InformeEntregaResponse(
                "Informe subido y registrado (id=" + saved.getId() + ")",
                originalFilename,
                archivo.getSize(),
                contentType,
                LocalDateTime.now()
            );
            resp.setId(saved.getId());
            resp.setArchivoUrl("/api/practicas/informes/" + saved.getId() + "/archivo");
            return resp;
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al procesar y guardar el archivo: " + e.getMessage()
            );
        }
    }

    @Override
    public InformeEntregaResponse guardarInformeFinalEstudiante(MultipartFile archivo, String correoEstudiante, Long userId) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe seleccionar un archivo para subir.");
        }

        String contentType = archivo.getContentType();
        String originalFilename = archivo.getOriginalFilename();

        boolean isPdfMime = "application/pdf".equalsIgnoreCase(contentType);
        boolean hasPdfExtension = originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");

        if (!isPdfMime || !hasPdfExtension) {
            throw new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
                "Formato inválido. Solo se admiten archivos en formato PDF (application/pdf)."
            );
        }

        // Resolver correo del estudiante
        String emailTarget = (correoEstudiante != null && !correoEstudiante.trim().isEmpty()) ? correoEstudiante.trim().toLowerCase() : null;
        if (emailTarget == null && userId != null && userRepository != null) {
            User u = userRepository.findById(userId).orElse(null);
            if (u != null) {
                emailTarget = u.getEmail();
            }
        }

        if (emailTarget == null) {
            return guardarInformeFinal(archivo);
        }

        // Buscar inscripciones del estudiante ordenadas por anio DESC, periodo DESC
        List<Inscripcion> inscripciones = inscripcionRepository.findByEstudianteCorreoWithOferta(emailTarget);
        if (inscripciones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró ninguna inscripción de práctica activa para el estudiante.");
        }

        // La práctica actual es automáticamente la primera (más reciente)
        Inscripcion inscripcionActual = inscripciones.get(0);

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String safeFileName = UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "informe.pdf");
            Path targetLocation = uploadPath.resolve(safeFileName);
            Path parent = targetLocation.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(archivo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String rutaArchivo = targetLocation.toString().replace('\\', '/');

            // Persistir o actualizar registro de Informe emitido por ESTUDIANTE para su práctica actual
            List<Informe> existentes = informeRepository.findByInscripcionIdAndEmisorOrderByIdDesc(inscripcionActual.getId(), TipoEmisor.ESTUDIANTE);
            Informe informe;
            if (!existentes.isEmpty()) {
                informe = existentes.get(0);
                informe.setArchivo(rutaArchivo);
            } else {
                informe = new Informe(TipoEmisor.ESTUDIANTE, rutaArchivo, inscripcionActual);
            }
            Informe saved = informeRepository.save(informe);

            String asigNombre = (inscripcionActual.getOferta() != null && inscripcionActual.getOferta().getAsignaturaPractica() != null)
                    ? inscripcionActual.getOferta().getAsignaturaPractica().getNombre()
                    : "Práctica Actual";

            InformeEntregaResponse resp = new InformeEntregaResponse(
                "¡Informe final de práctica entregado con éxito para " + asigNombre + "!",
                originalFilename,
                archivo.getSize(),
                contentType,
                LocalDateTime.now()
            );
            resp.setId(saved.getId());
            resp.setArchivoUrl("/api/practicas/informes/" + saved.getId() + "/archivo");
            return resp;
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error al procesar y guardar el archivo: " + e.getMessage()
            );
        }
    }

    @Override
    public InformeEntregaResponse obtenerUltimoInformeEstudiante(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        Optional<Estudiante> estOpt = estudianteRepository.findByCorreo(email.trim().toLowerCase());
        if (estOpt.isEmpty()) {
            return null;
        }

        List<Inscripcion> inscripciones = inscripcionRepository.findByEstudianteRutWithOferta(estOpt.get().getRut());
        if (inscripciones.isEmpty()) {
            return null;
        }

        // Priorizar la inscripción más reciente o activa del período 2026
        Inscripcion inscripcion = inscripciones.stream()
                .filter(i -> i.getOferta() != null && i.getOferta().getAnio() >= 2026)
                .findFirst()
                .orElse(inscripciones.get(0));

        List<Informe> informes = informeRepository.findByInscripcionIdAndEmisorOrderByIdDesc(inscripcion.getId(), TipoEmisor.ESTUDIANTE);
        if (informes.isEmpty()) {
            informes = informeRepository.findByInscripcionIdOrderByIdDesc(inscripcion.getId());
        }

        if (informes.isEmpty()) {
            return null;
        }

        Informe ultimo = informes.get(0);
        String rawArchivo = ultimo.getArchivo();
        String displayNombre = "Informe_Final.pdf";
        if (rawArchivo != null) {
            String clean = rawArchivo.replace('\\', '/');
            displayNombre = clean.substring(clean.lastIndexOf('/') + 1);
            displayNombre = displayNombre.replaceFirst("^[a-f0-9\\-]{36}_", "");
        }

        long tamano = 0L;
        try {
            if (rawArchivo != null) {
                Path filePath = Paths.get(rawArchivo);
                if (!Files.exists(filePath)) {
                    filePath = Paths.get("backend").resolve(rawArchivo);
                }
                if (!Files.exists(filePath)) {
                    filePath = Paths.get(UPLOAD_DIR).resolve(rawArchivo);
                }
                if (!Files.exists(filePath)) {
                    filePath = Paths.get("backend").resolve(UPLOAD_DIR).resolve(rawArchivo);
                }
                if (Files.exists(filePath)) {
                    tamano = Files.size(filePath);
                }
            }
        } catch (Exception ignored) {}

        LocalDateTime fechaEntrega = ultimo.getFecha() != null 
            ? ultimo.getFecha().atStartOfDay() 
            : LocalDateTime.now();

        InformeEntregaResponse resp = new InformeEntregaResponse(
            "Último informe entregado encontrado",
            displayNombre,
            tamano,
            "application/pdf",
            fechaEntrega
        );
        resp.setId(ultimo.getId());
        resp.setArchivoUrl("/api/practicas/informes/" + ultimo.getId() + "/archivo");
        return resp;
    }

    @Override
    public EvaluacionesResponseDto obtenerEvaluacionesPorEstudiante(String correoEstudiante, String rutEstudiante, Long inscripcionId, TipoFiltroEvaluacion tipo) {
        List<EvaluacionCLADto> listaClase = new ArrayList<>();
        List<EvaluacionSEMDto> listaSemestral = new ArrayList<>();

        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty()) ? correoEstudiante.trim().toLowerCase() : null;
        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;

        Long idInscripcionEfectivo = inscripcionId;

        // Si no se especifica inscripcionId pero sí correo/rut, resolver automáticamente la práctica actual (la más reciente)
        if (idInscripcionEfectivo == null && correoNorm != null) {
            List<Inscripcion> inscripciones = inscripcionRepository.findByEstudianteCorreoWithOferta(correoNorm);
            if (!inscripciones.isEmpty()) {
                idInscripcionEfectivo = inscripciones.get(0).getId();
            }
        } else if (idInscripcionEfectivo == null && rutNorm != null) {
            List<Inscripcion> inscripciones = inscripcionRepository.findByEstudianteRutWithOferta(rutNorm);
            if (!inscripciones.isEmpty()) {
                idInscripcionEfectivo = inscripciones.get(0).getId();
            }
        }

        // 1. Evaluaciones de clase
        if (tipo == TipoFiltroEvaluacion.CLASE || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionClase> evalClases = evaluacionClaseRepository.findEvaluacionesPorEstudiante(correoNorm, rutNorm, idInscripcionEfectivo);
            for (EvaluacionClase ec : evalClases) {
                Estudiante est = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
                java.time.LocalDateTime fechaClaseHora = ec.getClase() != null && ec.getClase().getFecha() != null
                        ? ec.getClase().getFecha().atStartOfDay()
                        : null;

                String archivoUrl = "/api/practicas/evaluaciones/clase/" + ec.getId() + "/archivo";
                String nombreArchivo = ec.getArchivo() != null ? java.nio.file.Paths.get(ec.getArchivo()).getFileName().toString() : "Pauta_Evaluacion.pdf";

                Evaluador ev = ec.getEvaluador();
                String evaluadorNombre = ev != null
                        ? (ev.getPrimerNombre() + " " + ev.getApellidoPaterno() + " " + ev.getApellidoMaterno()).trim()
                        : "Evaluador No Asignado";
                String evaluadorRut = ev != null ? ev.getRut() : "";
                String evaluadorTipo = ev != null && ev.getTipo() != null ? ev.getTipo().name() : "";

                Oferta of = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getOferta()
                        : null;
                Integer anio = of != null ? of.getAnio() : null;
                Integer periodo = of != null ? of.getPeriodo() : null;
                String asignaturaNombre = (of != null && of.getAsignaturaPractica() != null)
                        ? of.getAsignaturaPractica().getNombre()
                        : (ec.getClase() != null ? ec.getClase().getAsignatura() : "");

                listaClase.add(new EvaluacionCLADto(
                    ec.getId(),
                    rutEst,
                    nombreComp,
                    "CLASE",
                    null,
                    null,
                    fechaClaseHora,
                    archivoUrl,
                    nombreArchivo,
                    evaluadorNombre,
                    evaluadorRut,
                    evaluadorTipo,
                    anio,
                    periodo,
                    asignaturaNombre
                ));
            }
        }

        // 2. Evaluaciones semestrales
        if (tipo == TipoFiltroEvaluacion.SEMESTRAL || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionSemestral> evalSemestrales = evaluacionSemestralRepository.findEvaluacionesPorEstudiante(correoNorm, rutNorm, idInscripcionEfectivo);
            for (EvaluacionSemestral es : evalSemestrales) {
                Estudiante est = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
                String archivoUrl = "/api/practicas/evaluaciones/semestral/" + es.getId() + "/archivo";
                String nombreArchivo = es.getArchivo() != null ? java.nio.file.Paths.get(es.getArchivo()).getFileName().toString() : "Evaluacion_Semestral.pdf";

                Evaluador ev = es.getAsignacion() != null ? es.getAsignacion().getEvaluador() : null;
                String evaluadorNombre = ev != null
                        ? (ev.getPrimerNombre() + " " + ev.getApellidoPaterno() + " " + ev.getApellidoMaterno()).trim()
                        : "Evaluador No Asignado";
                String evaluadorRut = ev != null ? ev.getRut() : "";
                String evaluadorTipo = ev != null && ev.getTipo() != null ? ev.getTipo().name() : "";

                Oferta of = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getOferta()
                        : null;
                Integer anio = of != null ? of.getAnio() : null;
                Integer periodo = of != null ? of.getPeriodo() : null;
                String asignaturaNombre = (of != null && of.getAsignaturaPractica() != null)
                        ? of.getAsignaturaPractica().getNombre()
                        : es.getAsignatura();

                listaSemestral.add(new EvaluacionSEMDto(
                    es.getId(),
                    rutEst,
                    nombreComp,
                    "SEMESTRAL",
                    null,
                    null,
                    es.getFecha(),
                    archivoUrl,
                    nombreArchivo,
                    evaluadorNombre,
                    evaluadorRut,
                    evaluadorTipo,
                    anio,
                    periodo,
                    asignaturaNombre
                ));
            }
        }

        return new EvaluacionesResponseDto(listaClase, listaSemestral);
    }

    @Override
    public void validarInscripcionUnicaPorPeriodo(String rutEstudiante, String correoEstudiante, Integer anio, Integer periodo) {
        if ((rutEstudiante == null || rutEstudiante.trim().isEmpty()) && (correoEstudiante == null || correoEstudiante.trim().isEmpty())) {
            return;
        }
        if (anio == null || periodo == null) {
            return;
        }
        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;
        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty()) ? correoEstudiante.trim().toLowerCase() : null;

        List<Inscripcion> existentes = inscripcionRepository.findByEstudianteAndAnioAndPeriodo(rutNorm, correoNorm, anio, periodo);
        if (!existentes.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invariante violada: El estudiante ya se encuentra inscrito en una práctica para el período académico " + anio + "-" + periodo + ". No se permite más de una práctica simultánea por período."
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EvaluacionesResponseDto obtenerEvolucionEvaluacionesEstudiante(
            String rutEstudiante,
            String correoEstudiante,
            Long inscripcionId,
            String consultorEmail,
            TipoFiltroEvaluacion tipo) {

        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;
        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty()) ? correoEstudiante.trim().toLowerCase() : null;

        // 1. Validación de parámetros de consulta
        if (rutNorm == null && correoNorm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe especificar el RUT o el correo del estudiante para consultar su evolución.");
        }

        // 2. Validación de existencia del estudiante
        Estudiante estudiante = null;
        if (rutNorm != null) {
            estudiante = estudianteRepository.findById(rutNorm).orElse(null);
        }
        if (estudiante == null && correoNorm != null) {
            estudiante = estudianteRepository.findByCorreo(correoNorm).orElse(null);
        }
        if (estudiante == null) {
            List<Inscripcion> inscripciones = (rutNorm != null)
                    ? inscripcionRepository.findByEstudianteRutWithOferta(rutNorm)
                    : inscripcionRepository.findByEstudianteCorreoWithOferta(correoNorm);
            if (!inscripciones.isEmpty()) {
                estudiante = inscripciones.get(0).getEstudiante();
            }
        }
        if (estudiante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró ningún estudiante con los datos proporcionados.");
        }

        rutNorm = estudiante.getRut();
        correoNorm = estudiante.getCorreo();

        // 3. Validación de permisos del consultor (Prof. Colaborador / Tutor / Profesor Guía / Administrador)
        if (consultorEmail != null && !consultorEmail.trim().isEmpty()) {
            String emailConsultorNorm = consultorEmail.trim().toLowerCase();
            if (!"admin@ubiobio.cl".equalsIgnoreCase(emailConsultorNorm)) {
                boolean esEstudiante = estudianteRepository.findByCorreo(emailConsultorNorm).isPresent();
                if (esEstudiante && !emailConsultorNorm.equalsIgnoreCase(correoNorm)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Acceso denegado: Esta consulta de evolución histórica está reservada para Profesores Colaboradores, Tutores y Profesores Guía.");
                }
            }
        }

        TipoFiltroEvaluacion filtroEfectivo = (tipo != null) ? tipo : TipoFiltroEvaluacion.TODAS;
        List<EvaluacionCLADto> listaClase = new ArrayList<>();
        List<EvaluacionSEMDto> listaSemestral = new ArrayList<>();

        // 4. Recuperación de Evaluaciones de Clase ordenadas cronológicamente para observar la evolución temporal
        if (filtroEfectivo == TipoFiltroEvaluacion.CLASE || filtroEfectivo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionClase> evalClases = evaluacionClaseRepository.findHistoricoEvaluacionesEstudiante(rutNorm, correoNorm, inscripcionId);
            for (EvaluacionClase ec : evalClases) {
                Estudiante est = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getEstudiante()
                        : estudiante;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : rutNorm;
                java.time.LocalDateTime fechaClaseHora = ec.getClase() != null && ec.getClase().getFecha() != null
                        ? ec.getClase().getFecha().atStartOfDay()
                        : null;

                String archivoUrl = "/api/practicas/evaluaciones/clase/" + ec.getId() + "/archivo";
                String nombreArchivo = ec.getArchivo() != null
                        ? java.nio.file.Paths.get(ec.getArchivo()).getFileName().toString()
                        : "Pauta_Evaluacion.pdf";

                Evaluador ev = ec.getEvaluador();
                String evaluadorNombre = ev != null
                        ? (ev.getPrimerNombre() + " " + ev.getApellidoPaterno() + " " + ev.getApellidoMaterno()).trim()
                        : "Evaluador No Asignado";
                String evaluadorRut = ev != null ? ev.getRut() : "";
                String evaluadorTipo = ev != null && ev.getTipo() != null ? ev.getTipo().name() : "";

                Oferta of = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getOferta()
                        : null;
                Integer anio = of != null ? of.getAnio() : null;
                Integer periodo = of != null ? of.getPeriodo() : null;
                String asignaturaNombre = (of != null && of.getAsignaturaPractica() != null)
                        ? of.getAsignaturaPractica().getNombre()
                        : (ec.getClase() != null ? ec.getClase().getAsignatura() : "");

                listaClase.add(new EvaluacionCLADto(
                        ec.getId(),
                        rutEst,
                        nombreComp,
                        "CLASE",
                        null,
                        null,
                        fechaClaseHora,
                        archivoUrl,
                        nombreArchivo,
                        evaluadorNombre,
                        evaluadorRut,
                        evaluadorTipo,
                        anio,
                        periodo,
                        asignaturaNombre
                ));
            }
        }

        // 5. Recuperación de Evaluaciones Semestrales ordenadas cronológicamente para observar la evolución temporal
        if (filtroEfectivo == TipoFiltroEvaluacion.SEMESTRAL || filtroEfectivo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionSemestral> evalSemestrales = evaluacionSemestralRepository.findHistoricoEvaluacionesEstudiante(rutNorm, correoNorm, inscripcionId);
            for (EvaluacionSemestral es : evalSemestrales) {
                Estudiante est = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getEstudiante()
                        : estudiante;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : rutNorm;
                String archivoUrl = "/api/practicas/evaluaciones/semestral/" + es.getId() + "/archivo";
                String nombreArchivo = es.getArchivo() != null
                        ? java.nio.file.Paths.get(es.getArchivo()).getFileName().toString()
                        : "Evaluacion_Semestral.pdf";

                Evaluador ev = es.getAsignacion() != null ? es.getAsignacion().getEvaluador() : null;
                String evaluadorNombre = ev != null
                        ? (ev.getPrimerNombre() + " " + ev.getApellidoPaterno() + " " + ev.getApellidoMaterno()).trim()
                        : "Evaluador No Asignado";
                String evaluadorRut = ev != null ? ev.getRut() : "";
                String evaluadorTipo = ev != null && ev.getTipo() != null ? ev.getTipo().name() : "";

                Oferta of = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getOferta()
                        : null;
                Integer anio = of != null ? of.getAnio() : null;
                Integer periodo = of != null ? of.getPeriodo() : null;
                String asignaturaNombre = (of != null && of.getAsignaturaPractica() != null)
                        ? of.getAsignaturaPractica().getNombre()
                        : es.getAsignatura();

                listaSemestral.add(new EvaluacionSEMDto(
                        es.getId(),
                        rutEst,
                        nombreComp,
                        "SEMESTRAL",
                        null,
                        null,
                        es.getFecha(),
                        archivoUrl,
                        nombreArchivo,
                        evaluadorNombre,
                        evaluadorRut,
                        evaluadorTipo,
                        anio,
                        periodo,
                        asignaturaNombre
                ));
            }
        }

        // Ordenamiento ascendente estricto por fecha para reflejar la evolución temporal del desempeño
        listaClase.sort(java.util.Comparator.comparing(
                EvaluacionCLADto::getFecha,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        listaSemestral.sort(java.util.Comparator.comparing(
                EvaluacionSEMDto::getFecha,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

        return new EvaluacionesResponseDto(listaClase, listaSemestral);
    }
}
