package com.ubb.dochub.service.impl;

import com.ubb.dochub.dto.EvaluacionCLADto;
import com.ubb.dochub.dto.EvaluacionSEMDto;
import com.ubb.dochub.dto.EvaluacionesResponseDto;
import com.ubb.dochub.dto.InformeEntregaResponse;
import com.ubb.dochub.dto.TipoFiltroEvaluacion;
import com.ubb.dochub.entity.Asignacion;
import com.ubb.dochub.entity.AsignaturaPractica;
import com.ubb.dochub.entity.Clase;
import com.ubb.dochub.entity.EstadoPlanificacion;
import com.ubb.dochub.entity.Estudiante;
import com.ubb.dochub.entity.Evaluador;
import com.ubb.dochub.entity.EvaluacionClase;
import com.ubb.dochub.entity.EvaluacionSemestral;
import com.ubb.dochub.entity.Informe;
import com.ubb.dochub.entity.Inscripcion;
import com.ubb.dochub.entity.Oferta;
import com.ubb.dochub.entity.Profesor;
import com.ubb.dochub.entity.TipoEmisor;
import com.ubb.dochub.entity.User;
import com.ubb.dochub.repository.AsignacionRepository;
import com.ubb.dochub.repository.ClaseRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private AsignacionRepository asignacionRepository;

    @Autowired
    private ClaseRepository claseRepository;

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
                    "Formato inválido. Solo se admiten archivos en formato PDF (application/pdf).");
        }

        // 3. Simular guardado en almacenamiento local
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Nombre único para evitar colisiones
            String safeFileName = UUID.randomUUID() + "_"
                    + (originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "informe.pdf");
            Path targetLocation = uploadPath.resolve(safeFileName);

            Files.copy(archivo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return new InformeEntregaResponse(
                    "¡Informe final de práctica entregado con éxito!",
                    originalFilename,
                    archivo.getSize(),
                    contentType,
                    LocalDateTime.now());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al procesar y guardar el archivo: " + e.getMessage());
        }
    }

    @Override
    public EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, String correoProfesor,
            TipoFiltroEvaluacion tipo) {
        return obtenerEvaluacionesPorProfesor(rutProfesor, correoProfesor, null, null, tipo);
    }

    @Override
    public EvaluacionesResponseDto obtenerEvaluacionesPorProfesor(String rutProfesor, String correoProfesor,
            String rutEstudiante, Long inscripcionId, TipoFiltroEvaluacion tipo) {
        List<EvaluacionCLADto> listaClase = new ArrayList<>();
        List<EvaluacionSEMDto> listaSemestral = new ArrayList<>();

        String rutNorm = (rutProfesor != null && !rutProfesor.trim().isEmpty()) ? rutProfesor.trim() : null;
        String correoNorm = (correoProfesor != null && !correoProfesor.trim().isEmpty())
                ? correoProfesor.trim().toLowerCase()
                : null;
        String rutEstNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;

        // 1. Recuperar evaluaciones de clase o todas
        if (tipo == TipoFiltroEvaluacion.CLASE || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionClase> evalClases;
            if (rutNorm != null || correoNorm != null || rutEstNorm != null || inscripcionId != null) {
                evalClases = evaluacionClaseRepository.findEvaluacionesPorProfesor(rutNorm, correoNorm, rutEstNorm,
                        inscripcionId);
            } else {
                evalClases = evaluacionClaseRepository.findAll();
            }

            for (EvaluacionClase ec : evalClases) {
                Estudiante est = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno())
                                .trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
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
                        asignaturaNombre));
            }
        }

        // 2. Recuperar todas las evaluaciones semestrales
        if (tipo == TipoFiltroEvaluacion.SEMESTRAL || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionSemestral> evalSemestrales;
            if (rutNorm != null || correoNorm != null || rutEstNorm != null || inscripcionId != null) {
                evalSemestrales = evaluacionSemestralRepository.findEvaluacionesPorProfesor(rutNorm, correoNorm,
                        rutEstNorm, inscripcionId);
            } else {
                evalSemestrales = evaluacionSemestralRepository.findAll();
            }

            for (EvaluacionSemestral es : evalSemestrales) {
                Estudiante est = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno())
                                .trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
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
                        asignaturaNombre));
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
                    "Formato inválido. Solo se admiten archivos en formato PDF (application/pdf).");
        }

        Inscripcion inscripcion = inscripcionRepository.findById(inscripcionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada."));

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String safeFileName = UUID.randomUUID() + "_"
                    + (originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "informe.pdf");
            Path targetLocation = uploadPath.resolve(safeFileName);
            Path parent = targetLocation.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(archivo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String rutaArchivo = targetLocation.toString().replace('\\', '/');

            // Persistir o actualizar registro de Informe para esta inscripción
            List<Informe> existentes = informeRepository.findByInscripcionIdAndEmisorOrderByIdDesc(inscripcionId,
                    TipoEmisor.PROFESOR);
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
                    LocalDateTime.now());
            resp.setId(saved.getId());
            resp.setArchivoUrl("/api/practicas/informes/" + saved.getId() + "/archivo");
            return resp;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al procesar y guardar el archivo: " + e.getMessage());
        }
    }

    @Override
    public InformeEntregaResponse guardarInformeFinalEstudiante(MultipartFile archivo, String correoEstudiante,
            Long userId) {
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
                    "Formato inválido. Solo se admiten archivos en formato PDF (application/pdf).");
        }

        // Resolver correo del estudiante
        String emailTarget = (correoEstudiante != null && !correoEstudiante.trim().isEmpty())
                ? correoEstudiante.trim().toLowerCase()
                : null;
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró ninguna inscripción de práctica activa para el estudiante.");
        }

        // La práctica actual es automáticamente la primera (más reciente)
        Inscripcion inscripcionActual = inscripciones.get(0);

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String safeFileName = UUID.randomUUID() + "_"
                    + (originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "informe.pdf");
            Path targetLocation = uploadPath.resolve(safeFileName);
            Path parent = targetLocation.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(archivo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            String rutaArchivo = targetLocation.toString().replace('\\', '/');

            // Persistir o actualizar registro de Informe emitido por ESTUDIANTE para su
            // práctica actual
            List<Informe> existentes = informeRepository
                    .findByInscripcionIdAndEmisorOrderByIdDesc(inscripcionActual.getId(), TipoEmisor.ESTUDIANTE);
            Informe informe;
            if (!existentes.isEmpty()) {
                informe = existentes.get(0);
                informe.setArchivo(rutaArchivo);
            } else {
                informe = new Informe(TipoEmisor.ESTUDIANTE, rutaArchivo, inscripcionActual);
            }
            Informe saved = informeRepository.save(informe);

            String asigNombre = (inscripcionActual.getOferta() != null
                    && inscripcionActual.getOferta().getAsignaturaPractica() != null)
                            ? inscripcionActual.getOferta().getAsignaturaPractica().getNombre()
                            : "Práctica Actual";

            InformeEntregaResponse resp = new InformeEntregaResponse(
                    "¡Informe final de práctica entregado con éxito para " + asigNombre + "!",
                    originalFilename,
                    archivo.getSize(),
                    contentType,
                    LocalDateTime.now());
            resp.setId(saved.getId());
            resp.setArchivoUrl("/api/practicas/informes/" + saved.getId() + "/archivo");
            return resp;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al procesar y guardar el archivo: " + e.getMessage());
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

        List<Informe> informes = informeRepository.findByInscripcionIdAndEmisorOrderByIdDesc(inscripcion.getId(),
                TipoEmisor.ESTUDIANTE);
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
        } catch (Exception ignored) {
        }

        LocalDateTime fechaEntrega = ultimo.getFecha() != null
                ? ultimo.getFecha().atStartOfDay()
                : LocalDateTime.now();

        InformeEntregaResponse resp = new InformeEntregaResponse(
                "Último informe entregado encontrado",
                displayNombre,
                tamano,
                "application/pdf",
                fechaEntrega);
        resp.setId(ultimo.getId());
        resp.setArchivoUrl("/api/practicas/informes/" + ultimo.getId() + "/archivo");
        return resp;
    }

    @Override
    public EvaluacionesResponseDto obtenerEvaluacionesPorEstudiante(String correoEstudiante, String rutEstudiante,
            Long inscripcionId, TipoFiltroEvaluacion tipo) {
        List<EvaluacionCLADto> listaClase = new ArrayList<>();
        List<EvaluacionSEMDto> listaSemestral = new ArrayList<>();

        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty())
                ? correoEstudiante.trim().toLowerCase()
                : null;
        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;

        Long idInscripcionEfectivo = inscripcionId;

        // Si no se especifica inscripcionId pero sí correo/rut, resolver
        // automáticamente la práctica actual (la más reciente)
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
            List<EvaluacionClase> evalClases = evaluacionClaseRepository.findEvaluacionesPorEstudiante(correoNorm,
                    rutNorm, idInscripcionEfectivo);
            for (EvaluacionClase ec : evalClases) {
                Estudiante est = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno())
                                .trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
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
                        asignaturaNombre));
            }
        }

        // 2. Evaluaciones semestrales
        if (tipo == TipoFiltroEvaluacion.SEMESTRAL || tipo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionSemestral> evalSemestrales = evaluacionSemestralRepository
                    .findEvaluacionesPorEstudiante(correoNorm, rutNorm, idInscripcionEfectivo);
            for (EvaluacionSemestral es : evalSemestrales) {
                Estudiante est = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getEstudiante()
                        : null;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno())
                                .trim()
                        : "Estudiante";
                String rutEst = est != null ? est.getRut() : "";
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
                        asignaturaNombre));
            }
        }

        return new EvaluacionesResponseDto(listaClase, listaSemestral);
    }

    @Override
    public void validarInscripcionUnicaPorPeriodo(String rutEstudiante, String correoEstudiante, Integer anio,
            Integer periodo) {
        if ((rutEstudiante == null || rutEstudiante.trim().isEmpty())
                && (correoEstudiante == null || correoEstudiante.trim().isEmpty())) {
            return;
        }
        if (anio == null || periodo == null) {
            return;
        }
        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;
        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty())
                ? correoEstudiante.trim().toLowerCase()
                : null;

        List<Inscripcion> existentes = inscripcionRepository.findByEstudianteAndAnioAndPeriodo(rutNorm, correoNorm,
                anio, periodo);
        if (!existentes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invariante violada: El estudiante ya se encuentra inscrito en una práctica para el período académico "
                            + anio + "-" + periodo + ". No se permite más de una práctica simultánea por período.");
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
        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty())
                ? correoEstudiante.trim().toLowerCase()
                : null;

        // 1. Validación de parámetros de consulta
        if (rutNorm == null && correoNorm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe especificar el RUT o el correo del estudiante para consultar su evolución.");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No se encontró ningún estudiante con los datos proporcionados.");
        }

        rutNorm = estudiante.getRut();
        correoNorm = estudiante.getCorreo();

        // 3. Validación de permisos del consultor (Prof. Colaborador / Tutor / Profesor
        // Guía / Administrador)
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

        // 4. Recuperación de Evaluaciones de Clase ordenadas cronológicamente para
        // observar la evolución temporal
        if (filtroEfectivo == TipoFiltroEvaluacion.CLASE || filtroEfectivo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionClase> evalClases = evaluacionClaseRepository.findHistoricoEvaluacionesEstudiante(rutNorm,
                    correoNorm, inscripcionId);
            for (EvaluacionClase ec : evalClases) {
                Estudiante est = ec.getClase() != null && ec.getClase().getInscripcion() != null
                        ? ec.getClase().getInscripcion().getEstudiante()
                        : estudiante;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno())
                                .trim()
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
                        asignaturaNombre));
            }
        }

        // 5. Recuperación de Evaluaciones Semestrales ordenadas cronológicamente para
        // observar la evolución temporal
        if (filtroEfectivo == TipoFiltroEvaluacion.SEMESTRAL || filtroEfectivo == TipoFiltroEvaluacion.TODAS) {
            List<EvaluacionSemestral> evalSemestrales = evaluacionSemestralRepository
                    .findHistoricoEvaluacionesEstudiante(rutNorm, correoNorm, inscripcionId);
            for (EvaluacionSemestral es : evalSemestrales) {
                Estudiante est = es.getAsignacion() != null && es.getAsignacion().getInscripcion() != null
                        ? es.getAsignacion().getInscripcion().getEstudiante()
                        : estudiante;
                String nombreComp = est != null
                        ? (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno())
                                .trim()
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
                        asignaturaNombre));
            }
        }

        // Ordenamiento ascendente estricto por fecha para reflejar la evolución
        // temporal del desempeño
        listaClase.sort(java.util.Comparator.comparing(
                EvaluacionCLADto::getFecha,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        listaSemestral.sort(java.util.Comparator.comparing(
                EvaluacionSEMDto::getFecha,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

        return new EvaluacionesResponseDto(listaClase, listaSemestral);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumenAvanceEstudiante(String correoEstudiante, String rutEstudiante,
            Long inscripcionId) {
        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty())
                ? correoEstudiante.trim().toLowerCase()
                : null;
        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;

        Inscripcion inscripcion = null;

        // 1. Si viene un ID específico de inscripción, buscarla directamente con
        // detalles
        if (inscripcionId != null) {
            inscripcion = inscripcionRepository.findByIdWithDetails(inscripcionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Inscripción no encontrada con ID: " + inscripcionId));
        }

        // 2. Si no viene inscripcionId, resolver a través de rut/correo
        if (inscripcion == null) {
            if (correoNorm == null && rutNorm == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe proporcionar el correo, RUT o ID de inscripción del estudiante.");
            }

            List<Inscripcion> inscripciones = inscripcionRepository.findActiveInscripcionesByEstudiante(rutNorm,
                    correoNorm);
            if (!inscripciones.isEmpty()) {
                inscripcion = inscripciones.get(0);
            }
        }

        // Si el estudiante no tiene ninguna inscripción registrada
        if (inscripcion == null) {
            Estudiante est = null;
            if (rutNorm != null)
                est = estudianteRepository.findById(rutNorm).orElse(null);
            if (est == null && correoNorm != null)
                est = estudianteRepository.findByCorreo(correoNorm).orElse(null);

            if (est == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado en el sistema.");
            }

            Map<String, Object> vacio = new LinkedHashMap<>();
            vacio.put("estudianteRut", est.getRut());
            vacio.put("estudianteNombre",
                    (est.getPrimerNombre() + " " + est.getApellidoPaterno() + " " + est.getApellidoMaterno()).trim());
            vacio.put("estudianteCorreo", est.getCorreo());
            vacio.put("inscripcionId", null);
            vacio.put("estadoGeneral", "SIN_PRACTICA");
            vacio.put("etapaActual", "Sin práctica inscrita actualmente");
            vacio.put("porcentajeAvance", 0);

            Map<String, Object> hitosVacios = new LinkedHashMap<>();
            hitosVacios.put("centroAsignado", false);
            hitosVacios.put("establecimiento", "Sin centro asignado");
            hitosVacios.put("evaluadoresAsignadosCount", 0);
            hitosVacios.put("totalClases", 0);
            hitosVacios.put("planificacionesSubidasCount", 0);
            hitosVacios.put("planificacionesAprobadasCount", 0);
            hitosVacios.put("planificacionesPendientesCount", 0);
            hitosVacios.put("planificacionesRechazadasCount", 0);
            hitosVacios.put("evaluacionesClaseCount", 0);
            hitosVacios.put("evaluacionesSemestralesCount", 0);
            hitosVacios.put("totalEvaluacionesCount", 0);
            hitosVacios.put("informeFinalEntregado", false);
            hitosVacios.put("informeFinalFecha", null);
            hitosVacios.put("informeFinalNombre", null);
            hitosVacios.put("informeFinalUrl", null);

            vacio.put("hitos", hitosVacios);
            return vacio;
        }

        // Datos del estudiante y la oferta
        Estudiante estudiante = inscripcion.getEstudiante();
        Oferta oferta = inscripcion.getOferta();
        AsignaturaPractica asignatura = (oferta != null) ? oferta.getAsignaturaPractica() : null;
        Profesor profesorGuia = (oferta != null) ? oferta.getProfesor() : null;

        Long idInscripcion = inscripcion.getId();

        // Consultar componentes de la práctica
        // A. Asignaciones de centro y evaluadores
        List<Asignacion> asignaciones = asignacionRepository.findByInscripcionIdWithEvaluador(idInscripcion);
        boolean tieneAsignacion = !asignaciones.isEmpty();
        String establecimiento = "Sin centro asignado";
        if (tieneAsignacion) {
            establecimiento = asignaciones.stream()
                    .map(Asignacion::getEstablecimiento)
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse("Liceo Bicentenario de Excelencia Polivalente San Nicolás");
        }
        int totalEvaluadores = asignaciones.size();

        // B. Clases y Planificaciones
        List<Clase> clases = claseRepository.findByInscripcionId(idInscripcion);
        int totalClases = clases.size();
        long planificacionesSubidas = clases.stream()
                .filter(c -> c.getPlanificacion() != null)
                .count();
        long planificacionesAprobadas = clases.stream()
                .filter(c -> c.getPlanificacion() != null
                        && c.getPlanificacion().getEstado() == EstadoPlanificacion.APROBADA)
                .count();
        long planificacionesPendientes = clases.stream()
                .filter(c -> c.getPlanificacion() != null
                        && c.getPlanificacion().getEstado() == EstadoPlanificacion.PENDIENTE)
                .count();
        long planificacionesRechazadas = clases.stream()
                .filter(c -> c.getPlanificacion() != null
                        && c.getPlanificacion().getEstado() == EstadoPlanificacion.RECHAZADA)
                .count();

        // C. Evaluaciones
        List<EvaluacionClase> evalClases = evaluacionClaseRepository.findByInscripcionId(idInscripcion);
        List<EvaluacionSemestral> evalSemestrales = evaluacionSemestralRepository.findByInscripcionId(idInscripcion);
        int totalEvaluaciones = evalClases.size() + evalSemestrales.size();

        // D. Informe Final
        List<Informe> informes = informeRepository.findByInscripcionIdOrderByIdDesc(idInscripcion);
        boolean tieneInformeFinal = !informes.isEmpty();
        Informe ultimoInforme = tieneInformeFinal ? informes.get(0) : null;
        String fechaInforme = (ultimoInforme != null && ultimoInforme.getFecha() != null)
                ? ultimoInforme.getFecha().toString()
                : null;
        String archivoInformeNombre = (ultimoInforme != null && ultimoInforme.getArchivo() != null)
                ? Paths.get(ultimoInforme.getArchivo()).getFileName().toString()
                : null;
        String archivoInformeUrl = (ultimoInforme != null)
                ? "/api/practicas/informes/" + ultimoInforme.getId() + "/archivo"
                : null;

        // CÁLCULO DE AVANCE Y ESTADO GENERAL
        // Modelo ponderado sobre 100%:
        // 1. Inscripción activa: 10%
        // 2. Centro de práctica asignado: 15%
        // 3. Planificaciones de clase:
        // - Al menos 1 entregada: +15%
        // - Al menos 1 aprobada: +15%
        // 4. Evaluaciones de desempeño:
        // - Evaluación de clase realizada: +15%
        // - Evaluación semestral realizada: +10%
        // 5. Informe final de práctica entregado: +20%
        int porcentajeAvance = 10;

        if (tieneAsignacion) {
            porcentajeAvance += 15;
        }
        if (planificacionesSubidas > 0) {
            porcentajeAvance += 15;
        }
        if (planificacionesAprobadas > 0) {
            porcentajeAvance += 15;
        }
        if (!evalClases.isEmpty()) {
            porcentajeAvance += 15;
        }
        if (!evalSemestrales.isEmpty()) {
            porcentajeAvance += 10;
        }
        if (tieneInformeFinal) {
            porcentajeAvance += 20;
        }

        porcentajeAvance = Math.min(100, porcentajeAvance);

        // Determinación del Estado General y Etapa del Proceso
        String estadoGeneral;
        String etapaActual;

        if (porcentajeAvance == 100) {
            estadoGeneral = "FINALIZADA";
            etapaActual = "Práctica profesional y documentación curricular completadas";
        } else if (tieneInformeFinal) {
            estadoGeneral = "EN_REVISION_FINAL";
            etapaActual = "Informe final entregado - En revisión docente y cierre de acta";
        } else if (totalEvaluaciones > 0) {
            estadoGeneral = "EN_EVALUACION";
            etapaActual = "Evaluaciones de desempeño en curso - Pendiente entrega de informe final";
        } else if (planificacionesAprobadas > 0) {
            estadoGeneral = "EN_DESARROLLO";
            etapaActual = "Planificaciones aprobadas - Clases y horas de práctica en ejecución";
        } else if (planificacionesSubidas > 0) {
            estadoGeneral = "PLANIFICACIONES_PENDIENTES";
            etapaActual = "Planificaciones de clase entregadas - En espera de retroalimentación docente";
        } else if (tieneAsignacion) {
            estadoGeneral = "ASIGNADA";
            etapaActual = "Centro de práctica y evaluadores asignados - Pendiente inicio de clases";
        } else {
            estadoGeneral = "INSCRITA";
            etapaActual = "Inscripción confirmada - En espera de asignación de centro de práctica";
        }

        String nombreEstudiante = estudiante != null
                ? (estudiante.getPrimerNombre() + " " + estudiante.getApellidoPaterno() + " "
                        + estudiante.getApellidoMaterno()).trim()
                : "Estudiante";

        String nombreProfesor = profesorGuia != null
                ? (profesorGuia.getPrimerNombre() + " " + profesorGuia.getApellidoPaterno()).trim()
                : "Sin profesor guía asignado";

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("estudianteRut", estudiante != null ? estudiante.getRut() : rutNorm);
        resultado.put("estudianteNombre", nombreEstudiante);
        resultado.put("estudianteCorreo", estudiante != null ? estudiante.getCorreo() : correoNorm);
        resultado.put("inscripcionId", idInscripcion);
        resultado.put("ofertaId", oferta != null ? oferta.getId() : null);
        resultado.put("asignaturaCodigo", asignatura != null ? asignatura.getCodigo() : "INF-PRA");
        resultado.put("asignaturaNombre", asignatura != null ? asignatura.getNombre() : "Práctica Profesional");
        resultado.put("periodoAcademico", (oferta != null) ? (oferta.getAnio() + "-" + oferta.getPeriodo()) : "2026-1");
        resultado.put("profesorGuiaNombre", nombreProfesor);
        resultado.put("profesorGuiaCorreo", profesorGuia != null ? profesorGuia.getCorreo() : null);

        // Estado y Progreso
        resultado.put("estadoGeneral", estadoGeneral);
        resultado.put("etapaActual", etapaActual);
        resultado.put("porcentajeAvance", porcentajeAvance);

        // Desglose de Hitos
        Map<String, Object> hitos = new LinkedHashMap<>();
        hitos.put("centroAsignado", tieneAsignacion);
        hitos.put("establecimiento", establecimiento);
        hitos.put("evaluadoresAsignadosCount", totalEvaluadores);

        hitos.put("totalClases", totalClases);
        hitos.put("planificacionesSubidasCount", planificacionesSubidas);
        hitos.put("planificacionesAprobadasCount", planificacionesAprobadas);
        hitos.put("planificacionesPendientesCount", planificacionesPendientes);
        hitos.put("planificacionesRechazadasCount", planificacionesRechazadas);

        hitos.put("evaluacionesClaseCount", evalClases.size());
        hitos.put("evaluacionesSemestralesCount", evalSemestrales.size());
        hitos.put("totalEvaluacionesCount", totalEvaluaciones);

        hitos.put("informeFinalEntregado", tieneInformeFinal);
        hitos.put("informeFinalFecha", fechaInforme);
        hitos.put("informeFinalNombre", archivoInformeNombre);
        hitos.put("informeFinalUrl", archivoInformeUrl);

        resultado.put("hitos", hitos);
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerTareasPendientesEstudiante(String correoEstudiante, String rutEstudiante,
            Long inscripcionId) {
        String correoNorm = (correoEstudiante != null && !correoEstudiante.trim().isEmpty())
                ? correoEstudiante.trim().toLowerCase()
                : null;
        String rutNorm = (rutEstudiante != null && !rutEstudiante.trim().isEmpty()) ? rutEstudiante.trim() : null;

        // 1. Identificar la inscripción a consultar
        Inscripcion inscripcion = null;
        if (inscripcionId != null) {
            inscripcion = inscripcionRepository.findById(inscripcionId).orElse(null);
        } else if (correoNorm != null || rutNorm != null) {
            List<Inscripcion> activas = inscripcionRepository.findActiveInscripcionesByEstudiante(rutNorm, correoNorm);
            if (!activas.isEmpty()) {
                inscripcion = activas.get(0);
            } else {
                List<Inscripcion> porCorreo = (correoNorm != null)
                        ? inscripcionRepository.findByEstudianteCorreoWithOferta(correoNorm)
                        : inscripcionRepository.findByEstudianteRutWithOferta(rutNorm);
                if (!porCorreo.isEmpty()) {
                    inscripcion = porCorreo.get(0);
                }
            }
        }

        if (inscripcion == null) {
            Optional<Estudiante> estOpt = Optional.empty();
            if (correoNorm != null)
                estOpt = estudianteRepository.findByCorreo(correoNorm);
            if (estOpt.isEmpty() && rutNorm != null)
                estOpt = estudianteRepository.findById(rutNorm);
            if (estOpt.isPresent()) {
                Map<String, Object> inscPend = new LinkedHashMap<>();
                inscPend.put("id", "INSC-PENDIENTE");
                inscPend.put("tipo", "GESTION");
                inscPend.put("categoria", "INSCRIPCION_PRACTICA");
                inscPend.put("titulo", "Inscripción de Práctica Pendiente");
                inscPend.put("descripcion",
                        "No registras ninguna práctica inscrita en el período académico actual. Debes inscribir tu asignatura de práctica.");
                inscPend.put("estado", "SIN_INSCRIPCION");
                inscPend.put("prioridad", "CRITICA");
                inscPend.put("fechaLimite", LocalDate.of(2026, 8, 30).toString());
                inscPend.put("accionRequerida", "Inscribir asignatura de práctica");
                inscPend.put("urlAccion", "/practicas");
                return Collections.singletonList(inscPend);
            }
            return Collections.emptyList();
        }

        Long idInscripcion = inscripcion.getId();
        Oferta oferta = inscripcion.getOferta();

        LocalDate fechaCierreSemestre = (oferta != null && oferta.getAnio() > 0)
                ? (oferta.getPeriodo() == 1 ? LocalDate.of(oferta.getAnio(), 11, 30)
                        : LocalDate.of(oferta.getAnio(), 12, 15))
                : LocalDate.of(2026, 11, 30);

        List<Map<String, Object>> tareasPendientes = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        // A. VERIFICACIÓN DE ASIGNACIÓN DE CENTRO DE PRÁCTICA
        List<Asignacion> asignaciones = asignacionRepository.findByInscripcionIdWithEvaluador(idInscripcion);
        if (asignaciones.isEmpty()) {
            Map<String, Object> tareaAsignacion = new LinkedHashMap<>();
            tareaAsignacion.put("id", "ASIG-" + idInscripcion);
            tareaAsignacion.put("tipo", "GESTION");
            tareaAsignacion.put("categoria", "ASIGNACION_CENTRO");
            tareaAsignacion.put("titulo", "Asignación de Centro de Práctica");
            tareaAsignacion.put("descripcion",
                    "Tu práctica aún no cuenta con un centro educacional ni evaluadores registrados en el sistema. Debes formalizar tu centro con la coordinación docente.");
            tareaAsignacion.put("estado", "PENDIENTE_ASIGNACION");
            tareaAsignacion.put("prioridad", "ALTA");
            tareaAsignacion.put("fechaLimite", fechaCierreSemestre.minusMonths(2).toString());
            tareaAsignacion.put("accionRequerida", "Contactar a Coordinación de Prácticas");
            tareaAsignacion.put("urlAccion", "/perfil");
            tareasPendientes.add(tareaAsignacion);
        }

        // B. VERIFICACIÓN DE PLANIFICACIONES DE CLASE
        List<Clase> clases = claseRepository.findByInscripcionIdWithPlanificacion(idInscripcion);
        for (Clase c : clases) {
            LocalDate fechaClase = c.getFecha() != null ? c.getFecha() : fechaCierreSemestre;
            LocalDate fechaLimitePlan = fechaClase;

            if (c.getPlanificacion() == null) {
                // Caso 1: Clase sin planificación entregada
                Map<String, Object> tareaPlan = new LinkedHashMap<>();
                tareaPlan.put("id", "PLAN-CLASE-" + c.getId());
                tareaPlan.put("tipo", "PLANIFICACION");
                tareaPlan.put("categoria", "PLANIFICACION_CLASE");
                tareaPlan.put("titulo",
                        "Subir Planificación: " + (c.getTema() != null ? c.getTema() : "Clase programada"));
                tareaPlan.put("descripcion",
                        "Debes preparar y subir el documento PDF de planificación para la clase de " +
                                (c.getAsignatura() != null ? c.getAsignatura() : "práctica") +
                                (c.getHoraInicio() != null ? (" a las " + c.getHoraInicio()) : "") + ".");
                tareaPlan.put("estado", "PENDIENTE_ENVIO");
                tareaPlan.put("prioridad", fechaLimitePlan.isBefore(hoy.plusDays(3)) ? "CRITICA" : "ALTA");
                tareaPlan.put("fechaLimite", fechaLimitePlan.toString());
                tareaPlan.put("diasRestantes", java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaLimitePlan));
                tareaPlan.put("claseId", c.getId());
                tareaPlan.put("asignatura", c.getAsignatura());
                tareaPlan.put("tema", c.getTema());
                tareaPlan.put("accionRequerida", "Subir archivo PDF de planificación");
                tareaPlan.put("urlAccion", "/planificaciones");
                tareasPendientes.add(tareaPlan);

            } else if (c.getPlanificacion().getEstado() == EstadoPlanificacion.RECHAZADA) {
                // Caso 2: Planificación rechazada con observaciones docentes
                Map<String, Object> tareaPlanRechazada = new LinkedHashMap<>();
                tareaPlanRechazada.put("id", "PLAN-RECHAZADA-" + c.getPlanificacion().getId());
                tareaPlanRechazada.put("tipo", "PLANIFICACION");
                tareaPlanRechazada.put("categoria", "CORRECCION_PLANIFICACION");
                tareaPlanRechazada.put("titulo",
                        "Corregir Planificación Rechazada: " + (c.getTema() != null ? c.getTema() : "Clase"));
                String retro = (c.getPlanificacion().getRetroalimentacion() != null
                        && !c.getPlanificacion().getRetroalimentacion().isBlank())
                                ? c.getPlanificacion().getRetroalimentacion().trim()
                                : "Planificación rechazada por el profesor guía. Debe realizar correcciones y volver a enviar.";
                tareaPlanRechazada.put("descripcion", "Observación docente: \"" + retro
                        + "\". Por favor, ajusta tu documento y sube la nueva versión.");
                tareaPlanRechazada.put("estado", "RECHAZADA_PENDIENTE_CORRECCION");
                tareaPlanRechazada.put("prioridad", "CRITICA");
                tareaPlanRechazada.put("fechaLimite", fechaLimitePlan.toString());
                tareaPlanRechazada.put("diasRestantes",
                        java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaLimitePlan));
                tareaPlanRechazada.put("claseId", c.getId());
                tareaPlanRechazada.put("planificacionId", c.getPlanificacion().getId());
                tareaPlanRechazada.put("asignatura", c.getAsignatura());
                tareaPlanRechazada.put("tema", c.getTema());
                tareaPlanRechazada.put("observacionesDocente", retro);
                tareaPlanRechazada.put("accionRequerida", "Corregir y reemplazar planificación");
                tareaPlanRechazada.put("urlAccion", "/planificaciones");
                tareasPendientes.add(tareaPlanRechazada);

            } else if (c.getPlanificacion().getEstado() == EstadoPlanificacion.PENDIENTE) {
                // Caso 3: Planificación enviada en espera de revisión
                Map<String, Object> tareaPlanRevision = new LinkedHashMap<>();
                tareaPlanRevision.put("id", "PLAN-REVISION-" + c.getPlanificacion().getId());
                tareaPlanRevision.put("tipo", "PLANIFICACION");
                tareaPlanRevision.put("categoria", "REVISION_PLANIFICACION");
                tareaPlanRevision.put("titulo",
                        "Planificación en Revisión: " + (c.getTema() != null ? c.getTema() : "Clase"));
                tareaPlanRevision.put("descripcion",
                        "Tu planificación fue enviada correctamente y se encuentra a la espera de aprobación y comentarios por parte del profesor guía.");
                tareaPlanRevision.put("estado", "EN_REVISION_DOCENTE");
                tareaPlanRevision.put("prioridad", "BAJA");
                tareaPlanRevision.put("fechaLimite", fechaLimitePlan.toString());
                tareaPlanRevision.put("diasRestantes",
                        java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaLimitePlan));
                tareaPlanRevision.put("claseId", c.getId());
                tareaPlanRevision.put("planificacionId", c.getPlanificacion().getId());
                tareaPlanRevision.put("asignatura", c.getAsignatura());
                tareaPlanRevision.put("tema", c.getTema());
                tareaPlanRevision.put("accionRequerida", "En espera de revisión del profesor");
                tareaPlanRevision.put("urlAccion", "/planificaciones");
                tareasPendientes.add(tareaPlanRevision);
            }
        }

        // C. VERIFICACIÓN DE EVALUACIONES DE CLASE
        List<EvaluacionClase> evalClases = evaluacionClaseRepository.findByInscripcionId(idInscripcion);
        Set<Long> clasesEvaluadasIds = evalClases.stream()
                .filter(ec -> ec.getClase() != null)
                .map(ec -> ec.getClase().getId())
                .collect(Collectors.toSet());

        for (Clase c : clases) {
            if (!clasesEvaluadasIds.contains(c.getId())) {
                Map<String, Object> tareaEvalClase = new LinkedHashMap<>();
                tareaEvalClase.put("id", "EVAL-CLASE-PENDIENTE-" + c.getId());
                tareaEvalClase.put("tipo", "EVALUACION");
                tareaEvalClase.put("categoria", "EVALUACION_CLASE");
                tareaEvalClase.put("titulo",
                        "Evaluación de Clase Pendiente: " + (c.getTema() != null ? c.getTema() : "Clase"));
                tareaEvalClase.put("descripcion",
                        "Falta registrar la pauta de evaluación de desempeño de la clase realizada con fecha " +
                                (c.getFecha() != null ? c.getFecha().toString() : "programada") + ".");
                tareaEvalClase.put("estado", "PENDIENTE_EVALUADOR");
                tareaEvalClase.put("prioridad", "MEDIA");
                tareaEvalClase.put("fechaLimite",
                        (c.getFecha() != null ? c.getFecha().plusDays(7).toString() : fechaCierreSemestre.toString()));
                tareaEvalClase.put("claseId", c.getId());
                tareaEvalClase.put("asignatura", c.getAsignatura());
                tareaEvalClase.put("tema", c.getTema());
                tareaEvalClase.put("accionRequerida", "Coordinar evaluación con profesor colaborador o tutor");
                tareaEvalClase.put("urlAccion", "/evaluaciones");
                tareasPendientes.add(tareaEvalClase);
            }
        }

        // D. VERIFICACIÓN DE EVALUACIÓN SEMESTRAL
        List<EvaluacionSemestral> evalSemestrales = evaluacionSemestralRepository.findByInscripcionId(idInscripcion);
        if (evalSemestrales.isEmpty()) {
            Map<String, Object> tareaEvalSemestral = new LinkedHashMap<>();
            tareaEvalSemestral.put("id", "EVAL-SEM-PENDIENTE-" + idInscripcion);
            tareaEvalSemestral.put("tipo", "EVALUACION");
            tareaEvalSemestral.put("categoria", "EVALUACION_SEMESTRAL");
            tareaEvalSemestral.put("titulo", "Evaluación Semestral de Práctica Pendiente");
            tareaEvalSemestral.put("descripcion",
                    "La pauta de evaluación semestral del tutor o profesor colaborador aún no ha sido registrada en el sistema.");
            tareaEvalSemestral.put("estado", "PENDIENTE_EVALUADOR");
            tareaEvalSemestral.put("prioridad", "ALTA");
            tareaEvalSemestral.put("fechaLimite", fechaCierreSemestre.toString());
            tareaEvalSemestral.put("accionRequerida",
                    "Solicitar al evaluador institucional completar la evaluación semestral");
            tareaEvalSemestral.put("urlAccion", "/evaluaciones");
            tareasPendientes.add(tareaEvalSemestral);
        }

        // E. VERIFICACIÓN DE INFORME FINAL DE PRÁCTICA
        List<Informe> informes = informeRepository.findByInscripcionIdOrderByIdDesc(idInscripcion);
        if (informes.isEmpty()) {
            Map<String, Object> tareaInforme = new LinkedHashMap<>();
            tareaInforme.put("id", "INFORME-FINAL-" + idInscripcion);
            tareaInforme.put("tipo", "DOCUMENTO");
            tareaInforme.put("categoria", "INFORME_FINAL");
            tareaInforme.put("titulo", "Entrega de Informe Final de Práctica");
            tareaInforme.put("descripcion",
                    "Debes redactar y subir el archivo PDF del informe final de práctica profesional con el detalle de las horas cumplidas y actividades.");
            tareaInforme.put("estado", "PENDIENTE_ENTREGA");
            tareaInforme.put("prioridad", "ALTA");
            tareaInforme.put("fechaLimite", fechaCierreSemestre.toString());
            tareaInforme.put("diasRestantes", java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaCierreSemestre));
            tareaInforme.put("accionRequerida", "Subir archivo PDF de informe final");
            tareaInforme.put("urlAccion", "/entrega-informe");
            tareasPendientes.add(tareaInforme);
        }

        // Ordenar las tareas pendientes: CRITICA -> ALTA -> MEDIA -> BAJA
        Map<String, Integer> prioridadPesos = Map.of(
                "CRITICA", 1,
                "ALTA", 2,
                "MEDIA", 3,
                "BAJA", 4);

        tareasPendientes.sort(Comparator.comparingInt(t -> {
            String p = (String) t.getOrDefault("prioridad", "MEDIA");
            return prioridadPesos.getOrDefault(p, 5);
        }));

        return tareasPendientes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerRespaldoHistoricoInformes(
            String consultorEmail,
            String estudiante,
            String asignatura,
            Integer anio,
            Integer periodo,
            Boolean soloHistoricos) {

        // 1. Validación de permisos: Reservado para Coordinador de prácticas, Administrador o Docente autorizado
        validarAccesoCoordinador(consultorEmail);

        // 2. Normalización de filtros opcionales
        String estudianteNorm = (estudiante != null && !estudiante.trim().isEmpty()) ? estudiante.trim() : null;
        String asignaturaNorm = (asignatura != null && !asignatura.trim().isEmpty()) ? asignatura.trim() : null;

        // 3. Determinación de corte de periodo académico actual
        LocalDate hoy = LocalDate.now();
        int anioActual = hoy.getYear();
        int periodoActual = hoy.getMonthValue() >= 8 ? 2 : 1;

        // Si el usuario no especificó soloHistoricos:
        // Si especificó anio o periodo, se busca en ese periodo exacto (soloHistoricos = false)
        // De lo contrario, por defecto se recupera el respaldo histórico (periodos pasados)
        boolean filtrarSoloHistoricos = (soloHistoricos != null)
                ? soloHistoricos
                : (anio == null && periodo == null);

        // 4. Ejecución de consulta en repositorio existente
        List<Informe> informes = informeRepository.findHistorialInformes(
                estudianteNorm,
                asignaturaNorm,
                anio,
                periodo,
                null,
                filtrarSoloHistoricos,
                anioActual,
                periodoActual);

        // 5. Mapeo a estructura de datos estándar (List<Map<String, Object>>) sin requerir DTOs nuevos
        List<Map<String, Object>> resultado = new ArrayList<>();

        for (Informe inf : informes) {
            Inscripcion insc = inf.getInscripcion();
            Estudiante est = insc != null ? insc.getEstudiante() : null;
            Oferta of = insc != null ? insc.getOferta() : null;
            AsignaturaPractica ap = of != null ? of.getAsignaturaPractica() : null;
            Profesor prof = of != null ? of.getProfesor() : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", inf.getId());
            item.put("informeId", inf.getId());
            item.put("fechaEntrega", inf.getFecha() != null ? inf.getFecha().toString() : "");
            item.put("emisor", inf.getEmisor() != null ? inf.getEmisor().name() : "ESTUDIANTE");

            // Datos del estudiante
            String nombreCompleto = est != null
                    ? String.join(" ",
                            est.getPrimerNombre() != null ? est.getPrimerNombre() : "",
                            est.getSegundoNombre() != null ? est.getSegundoNombre() : "",
                            est.getApellidoPaterno() != null ? est.getApellidoPaterno() : "",
                            est.getApellidoMaterno() != null ? est.getApellidoMaterno() : "").replaceAll("\\s+", " ").trim()
                    : "Estudiante";

            item.put("estudianteRut", est != null ? est.getRut() : "");
            item.put("estudianteNombre", nombreCompleto);
            item.put("estudianteCorreo", est != null ? est.getCorreo() : "");

            Map<String, Object> estudianteMap = new LinkedHashMap<>();
            estudianteMap.put("rut", est != null ? est.getRut() : "");
            estudianteMap.put("nombreCompleto", nombreCompleto);
            estudianteMap.put("primerNombre", est != null ? est.getPrimerNombre() : "");
            estudianteMap.put("apellidoPaterno", est != null ? est.getApellidoPaterno() : "");
            estudianteMap.put("correo", est != null ? est.getCorreo() : "");
            item.put("estudiante", estudianteMap);

            // Práctica cursada
            int anioOferta = of != null ? of.getAnio() : 0;
            int periodoOferta = of != null ? of.getPeriodo() : 0;
            String periodoAcademico = (anioOferta > 0) ? (anioOferta + "-" + periodoOferta) : "Histórico";

            item.put("inscripcionId", insc != null ? insc.getId() : null);
            item.put("ofertaId", of != null ? of.getId() : null);
            item.put("asignaturaCodigo", ap != null ? ap.getCodigo() : "S/C");
            item.put("asignaturaNombre", ap != null ? ap.getNombre() : "Práctica Profesional");
            item.put("anio", anioOferta);
            item.put("periodo", periodoOferta);
            item.put("periodoAcademico", periodoAcademico);
            item.put("profesorGuia", prof != null
                    ? (prof.getPrimerNombre() + " " + prof.getApellidoPaterno())
                    : "No asignado");

            Map<String, Object> practicaMap = new LinkedHashMap<>();
            practicaMap.put("inscripcionId", insc != null ? insc.getId() : null);
            practicaMap.put("codigo", ap != null ? ap.getCodigo() : "S/C");
            practicaMap.put("nombre", ap != null ? ap.getNombre() : "Práctica Profesional");
            practicaMap.put("anio", anioOferta);
            practicaMap.put("periodo", periodoOferta);
            practicaMap.put("periodoAcademico", periodoAcademico);
            item.put("practica", practicaMap);

            // Estado de la práctica e informe
            boolean esPeriodoPasado = (anioOferta < anioActual) || (anioOferta == anioActual && periodoOferta < periodoActual);
            String estado = esPeriodoPasado ? "Finalizada" : "En curso";
            item.put("estado", estado);
            item.put("estadoPractica", estado);
            item.put("estadoInforme", "ENTREGADO");
            item.put("esHistorico", esPeriodoPasado);

            // Referencia y ruta del archivo del informe
            String rawArchivo = inf.getArchivo();
            String nombreArchivo = "informe.pdf";
            if (rawArchivo != null) {
                String cleanPath = rawArchivo.replace('\\', '/');
                nombreArchivo = cleanPath.substring(cleanPath.lastIndexOf("/") + 1);
                nombreArchivo = nombreArchivo.replaceFirst("^[a-f0-9\\-]{36}_", "");
            }

            item.put("archivo", rawArchivo);
            item.put("nombreArchivo", nombreArchivo);
            item.put("archivoUrl", "/api/practicas/informes/" + inf.getId() + "/archivo");

            resultado.add(item);
        }

        return resultado;
    }

    private void validarAccesoCoordinador(String consultorEmail) {
        if (consultorEmail == null || consultorEmail.trim().isEmpty()) {
            return;
        }

        String emailNorm = consultorEmail.trim().toLowerCase();

        // Administrador principal / Coordinador de prácticas
        if ("admin@ubiobio.cl".equalsIgnoreCase(emailNorm)) {
            return;
        }

        // Validación en tabla de usuarios (roles RBAC)
        if (userRepository != null) {
            Optional<User> userOpt = userRepository.findByEmail(emailNorm);
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                String rol = u.getRole();
                if (rol != null) {
                    String rolLower = rol.trim().toLowerCase();
                    if (rolLower.contains("administrador") || rolLower.contains("coordinador") || rolLower.contains("profesor")) {
                        return;
                    }
                    if (rolLower.contains("estudiante")) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Acceso denegado: La consulta del respaldo histórico de informes finales está restringida al Coordinador de prácticas.");
                    }
                }
            }
        }

        // Si está registrado como estudiante, denegar acceso
        if (estudianteRepository != null && estudianteRepository.findByCorreo(emailNorm).isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Acceso denegado: Los estudiantes no tienen autorización para acceder al respaldo histórico de informes de práctica.");
        }
    }
}
