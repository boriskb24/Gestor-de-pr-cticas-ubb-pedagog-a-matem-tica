package com.ubb.dochub.controller;

import com.ubb.dochub.dto.EstudianteDetalleResponse;
import com.ubb.dochub.dto.EstudianteDirectorioDto;
import com.ubb.dochub.dto.EvaluacionesResponseDto;
import com.ubb.dochub.dto.InformeEntregaResponse;
import com.ubb.dochub.dto.InscripcionResponse;
import com.ubb.dochub.dto.OfertaResponse;
import com.ubb.dochub.dto.TipoFiltroEvaluacion;
import com.ubb.dochub.entity.*;
import com.ubb.dochub.repository.AsignacionRepository;
import com.ubb.dochub.repository.EstudianteRepository;
import com.ubb.dochub.repository.EvaluacionClaseRepository;
import com.ubb.dochub.repository.EvaluacionSemestralRepository;
import com.ubb.dochub.repository.InformeRepository;
import com.ubb.dochub.repository.InscripcionRepository;
import com.ubb.dochub.repository.OfertaRepository;
import com.ubb.dochub.service.PracticaService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/practicas")
@CrossOrigin(origins = "*")
public class PracticaController {

    private final PracticaService practicaService;
    private final InscripcionRepository inscripcionRepository;
    private final OfertaRepository ofertaRepository;
    private final AsignacionRepository asignacionRepository;
    private final InformeRepository informeRepository;
    private final EstudianteRepository estudianteRepository;
    private final EvaluacionClaseRepository evaluacionClaseRepository;
    private final EvaluacionSemestralRepository evaluacionSemestralRepository;

    public PracticaController(
            PracticaService practicaService,
            InscripcionRepository inscripcionRepository,
            OfertaRepository ofertaRepository,
            AsignacionRepository asignacionRepository,
            InformeRepository informeRepository,
            EstudianteRepository estudianteRepository,
            EvaluacionClaseRepository evaluacionClaseRepository,
            EvaluacionSemestralRepository evaluacionSemestralRepository) {
        this.practicaService = practicaService;
        this.inscripcionRepository = inscripcionRepository;
        this.ofertaRepository = ofertaRepository;
        this.asignacionRepository = asignacionRepository;
        this.informeRepository = informeRepository;
        this.estudianteRepository = estudianteRepository;
        this.evaluacionClaseRepository = evaluacionClaseRepository;
        this.evaluacionSemestralRepository = evaluacionSemestralRepository;
    }

    // 1. Entrega informe estudiante (vinculado automáticamente a su práctica actual)
    @PostMapping(value = "/informe-final", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InformeEntregaResponse> subirInformeFinal(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam(value = "email", required = false) String email) {
        String effectiveEmail = (correo != null && !correo.isBlank()) ? correo : email;
        InformeEntregaResponse response = practicaService.guardarInformeFinalEstudiante(archivo, effectiveEmail, userId);
        return ResponseEntity.ok(response);
    }

    // 1.1 Consultar última entrega del estudiante
    @GetMapping("/mi-informe")
    public ResponseEntity<InformeEntregaResponse> obtenerMiInforme(
            @RequestParam("email") String email) {
        InformeEntregaResponse response = practicaService.obtenerUltimoInformeEstudiante(email);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    // 2. Evaluaciones de profesor (Pablo)
    @GetMapping("/evaluaciones/profesor")
    public ResponseEntity<EvaluacionesResponseDto> obtenerEvaluacionesProfesor(
            @RequestParam(required = false) String rutProfesor,
            @RequestParam(required = false) String correoProfesor,
            @RequestParam(required = false) String rutEstudiante,
            @RequestParam(required = false) Long inscripcionId,
            @RequestParam(defaultValue = "TODAS") TipoFiltroEvaluacion tipo) {
        if (correoProfesor != null && !correoProfesor.isBlank()) {
            String emailNorm = correoProfesor.trim().toLowerCase();
            boolean esEstudiante = estudianteRepository.findByCorreo(emailNorm).isPresent();
            if (esEstudiante) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: Los estudiantes deben consultar sus evaluaciones a través de /evaluaciones/estudiante.");
            }
        }
        EvaluacionesResponseDto response = practicaService.obtenerEvaluacionesPorProfesor(rutProfesor, correoProfesor, rutEstudiante, inscripcionId, tipo);
        return ResponseEntity.ok(response);
    }

    // 2b. Evaluaciones de estudiante (limitadas a su práctica en curso por defecto)
    @GetMapping("/evaluaciones/estudiante")
    public ResponseEntity<EvaluacionesResponseDto> obtenerEvaluacionesEstudiante(
            @RequestParam(required = false) String correoEstudiante,
            @RequestParam(required = false) String rutEstudiante,
            @RequestParam(required = false) Long inscripcionId,
            @RequestParam(defaultValue = "TODAS") TipoFiltroEvaluacion tipo) {
        EvaluacionesResponseDto response = practicaService.obtenerEvaluacionesPorEstudiante(correoEstudiante, rutEstudiante, inscripcionId, tipo);
        return ResponseEntity.ok(response);
    }

    // 2b.1 Resumen del estado general y avance de la práctica del estudiante
    @GetMapping("/resumen-avance")
    public ResponseEntity<Map<String, Object>> obtenerResumenAvance(
            @RequestParam(required = false) String correoEstudiante,
            @RequestParam(required = false) String rutEstudiante,
            @RequestParam(required = false) Long inscripcionId,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        String effectiveEmail = (correoEstudiante != null && !correoEstudiante.isBlank())
                ? correoEstudiante
                : ((email != null && !email.isBlank()) ? email : userEmail);
        Map<String, Object> resumen = practicaService.obtenerResumenAvanceEstudiante(effectiveEmail, rutEstudiante, inscripcionId);
        return ResponseEntity.ok(resumen);
    }

    @GetMapping("/estudiantes/{rut}/resumen-avance")
    public ResponseEntity<Map<String, Object>> obtenerResumenAvancePorRut(
            @PathVariable("rut") String rut,
            @RequestParam(required = false) Long inscripcionId) {
        Map<String, Object> resumen = practicaService.obtenerResumenAvanceEstudiante(null, rut, inscripcionId);
        return ResponseEntity.ok(resumen);
    }

    @GetMapping("/inscripciones/{inscripcionId}/resumen-avance")
    public ResponseEntity<Map<String, Object>> obtenerResumenAvancePorInscripcion(
            @PathVariable("inscripcionId") Long inscripcionId) {
        Map<String, Object> resumen = practicaService.obtenerResumenAvanceEstudiante(null, null, inscripcionId);
        return ResponseEntity.ok(resumen);
    }

    // 2c. Consulta de evolución histórica de evaluaciones para Prof. Colaborador / Tutor
    @GetMapping("/evaluaciones/evolucion")
    public ResponseEntity<EvaluacionesResponseDto> obtenerEvolucionEvaluaciones(
            @RequestParam(required = false) String rutEstudiante,
            @RequestParam(required = false) String correoEstudiante,
            @RequestParam(required = false) Long inscripcionId,
            @RequestParam(value = "userEmail", required = false) String userEmail,
            @RequestParam(value = "evaluadorEmail", required = false) String evaluadorEmail,
            @RequestParam(defaultValue = "TODAS") TipoFiltroEvaluacion tipo) {
        String effectiveConsultorEmail = (userEmail != null && !userEmail.isBlank()) ? userEmail : evaluadorEmail;
        EvaluacionesResponseDto response = practicaService.obtenerEvolucionEvaluacionesEstudiante(
                rutEstudiante, correoEstudiante, inscripcionId, effectiveConsultorEmail, tipo);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/estudiantes/{rut}/evaluaciones/evolucion")
    public ResponseEntity<EvaluacionesResponseDto> obtenerEvolucionPorRutEstudiante(
            @PathVariable("rut") String rut,
            @RequestParam(required = false) Long inscripcionId,
            @RequestParam(value = "userEmail", required = false) String userEmail,
            @RequestParam(value = "evaluadorEmail", required = false) String evaluadorEmail,
            @RequestParam(defaultValue = "TODAS") TipoFiltroEvaluacion tipo) {
        String effectiveConsultorEmail = (userEmail != null && !userEmail.isBlank()) ? userEmail : evaluadorEmail;
        EvaluacionesResponseDto response = practicaService.obtenerEvolucionEvaluacionesEstudiante(
                rut, null, inscripcionId, effectiveConsultorEmail, tipo);
        return ResponseEntity.ok(response);
    }

    // Visualizar PDF de evaluación de clase con validación de dominio
    @GetMapping("/evaluaciones/clase/{id}/archivo")
    public ResponseEntity<Resource> verArchivoEvaluacionClase(
            @PathVariable Long id,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        EvaluacionClase ec = evaluacionClaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación de clase no encontrada con id: " + id));

        if (userEmail != null && !userEmail.isBlank()) {
            String emailNorm = userEmail.trim().toLowerCase();
            if (!"admin@ubiobio.cl".equalsIgnoreCase(emailNorm)) {
                Inscripcion insc = ec.getClase() != null ? ec.getClase().getInscripcion() : null;
                boolean esEstudiante = insc != null && insc.getEstudiante() != null
                        && emailNorm.equalsIgnoreCase(insc.getEstudiante().getCorreo());
                boolean esProfesor = insc != null && insc.getOferta() != null && insc.getOferta().getProfesor() != null
                        && emailNorm.equalsIgnoreCase(insc.getOferta().getProfesor().getCorreo());
                boolean esEvaluador = ec.getEvaluador() != null
                        && emailNorm.equalsIgnoreCase(ec.getEvaluador().getCorreo());

                if (!esEstudiante && !esProfesor && !esEvaluador) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: No tiene permisos para visualizar esta evaluación de clase fuera de su dominio.");
                }
            }
        }

        return servirPdfInline(ec.getArchivo(), "Pauta_Evaluacion_Clase_" + id + ".pdf");
    }

    // Visualizar PDF de evaluación semestral con validación de dominio
    @GetMapping("/evaluaciones/semestral/{id}/archivo")
    public ResponseEntity<Resource> verArchivoEvaluacionSemestral(
            @PathVariable Long id,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        EvaluacionSemestral es = evaluacionSemestralRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluación semestral no encontrada con id: " + id));

        if (userEmail != null && !userEmail.isBlank()) {
            String emailNorm = userEmail.trim().toLowerCase();
            if (!"admin@ubiobio.cl".equalsIgnoreCase(emailNorm)) {
                Inscripcion insc = es.getAsignacion() != null ? es.getAsignacion().getInscripcion() : null;
                boolean esEstudiante = insc != null && insc.getEstudiante() != null
                        && emailNorm.equalsIgnoreCase(insc.getEstudiante().getCorreo());
                boolean esProfesor = insc != null && insc.getOferta() != null && insc.getOferta().getProfesor() != null
                        && emailNorm.equalsIgnoreCase(insc.getOferta().getProfesor().getCorreo());
                boolean esEvaluador = es.getAsignacion() != null && es.getAsignacion().getEvaluador() != null
                        && emailNorm.equalsIgnoreCase(es.getAsignacion().getEvaluador().getCorreo());

                if (!esEstudiante && !esProfesor && !esEvaluador) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: No tiene permisos para visualizar esta evaluación semestral fuera de su dominio.");
                }
            }
        }

        return servirPdfInline(es.getArchivo(), "Evaluacion_Semestral_" + id + ".pdf");
    }

    // Visualizar PDF de informe final de práctica con validación de dominio
    @GetMapping("/informes/{id}/archivo")
    public ResponseEntity<Resource> verArchivoInforme(
            @PathVariable Long id,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        Informe inf = informeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Informe no encontrado con id: " + id));

        if (userEmail != null && !userEmail.isBlank()) {
            String emailNorm = userEmail.trim().toLowerCase();
            if (!"admin@ubiobio.cl".equalsIgnoreCase(emailNorm)) {
                Inscripcion insc = inf.getInscripcion();
                boolean esEstudiante = insc != null && insc.getEstudiante() != null
                        && emailNorm.equalsIgnoreCase(insc.getEstudiante().getCorreo());
                boolean esProfesor = insc != null && insc.getOferta() != null && insc.getOferta().getProfesor() != null
                        && emailNorm.equalsIgnoreCase(insc.getOferta().getProfesor().getCorreo());
                boolean esEvaluador = insc != null && asignacionRepository.findByInscripcionIdWithEvaluador(insc.getId()).stream()
                        .anyMatch(a -> a.getEvaluador() != null && emailNorm.equalsIgnoreCase(a.getEvaluador().getCorreo()));

                if (!esEstudiante && !esProfesor && !esEvaluador) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: No tiene permisos para visualizar este informe final fuera de su dominio.");
                }
            }
        }

        return servirPdfInformeInline(inf.getArchivo(), "Informe_Final_" + id + ".pdf");
    }

    private ResponseEntity<Resource> servirPdfInformeInline(String rutaArchivo, String fallbackNombre) {
        if (rutaArchivo != null && !rutaArchivo.trim().isEmpty()) {
            try {
                Path path = Paths.get(rutaArchivo);
                if (!Files.exists(path)) {
                    if (rutaArchivo.startsWith("/")) {
                        path = Paths.get(rutaArchivo.substring(1));
                    }
                    if (!Files.exists(path)) {
                        path = Paths.get("backend").resolve(rutaArchivo.startsWith("/") ? rutaArchivo.substring(1) : rutaArchivo);
                    }
                    if (!Files.exists(path)) {
                        path = Paths.get("uploads/informes").resolve(rutaArchivo);
                    }
                    if (!Files.exists(path)) {
                        path = Paths.get("backend/uploads/informes").resolve(rutaArchivo);
                    }
                    if (!Files.exists(path)) {
                        path = Paths.get("uploads/informes/Ejemplo_Informe.pdf");
                    }
                    if (!Files.exists(path)) {
                        path = Paths.get("backend/uploads/informes/Ejemplo_Informe.pdf");
                    }
                }
                if (Files.exists(path)) {
                    Resource resource = new UrlResource(path.toUri());
                    String nombreDescarga = path.getFileName() != null ? path.getFileName().toString() : fallbackNombre;
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreDescarga + "\"")
                            .body(resource);
                }
            } catch (MalformedURLException ignored) {
            }
        }

        // Fallback directo a ClassPathResource (empaquetado dentro del JAR)
        ClassPathResource classpathResource = new ClassPathResource("samples/Ejemplo_Informe.pdf");
        if (classpathResource.exists()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fallbackNombre + "\"")
                    .body(classpathResource);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo físico del informe no fue encontrado en el servidor.");
    }

    private ResponseEntity<Resource> servirPdfInline(String rutaArchivo, String fallbackNombre) {
        if (rutaArchivo == null || rutaArchivo.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay archivo asociado a esta evaluación.");
        }

        try {
            Path path = Paths.get(rutaArchivo);
            if (!Files.exists(path)) {
                if (rutaArchivo.startsWith("/")) {
                    path = Paths.get(rutaArchivo.substring(1));
                }
                if (!Files.exists(path)) {
                    path = Paths.get("backend").resolve(rutaArchivo.startsWith("/") ? rutaArchivo.substring(1) : rutaArchivo);
                }
                if (!Files.exists(path)) {
                    path = Paths.get("uploads/evaluaciones").resolve(rutaArchivo);
                }
                if (!Files.exists(path)) {
                    path = Paths.get("backend/uploads/evaluaciones").resolve(rutaArchivo);
                }
                if (!Files.exists(path)) {
                    path = Paths.get("uploads/evaluaciones/Pauta_Ev_Ejemplo.pdf");
                }
                if (!Files.exists(path)) {
                    path = Paths.get("backend/uploads/evaluaciones/Pauta_Ev_Ejemplo.pdf");
                }
                if (!Files.exists(path)) {
                    path = Paths.get("Pauta_Ev_Ejemplo.pdf");
                }
            }

            if (!Files.exists(path)) {
                // Fallback directo a ClassPathResource (empaquetado dentro del JAR)
                ClassPathResource classpathResource = new ClassPathResource("samples/Pauta_Ev_Ejemplo.pdf");
                if (classpathResource.exists()) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fallbackNombre + "\"")
                            .body(classpathResource);
                }
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo físico no fue encontrado en el servidor.");
            }

            Resource resource = new UrlResource(path.toUri());
            String nombreDescarga = path.getFileName() != null ? path.getFileName().toString() : fallbackNombre;

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombreDescarga + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al preparar el archivo: " + e.getMessage());
        }
    }

    // 3. Inscripciones por profesor (Yonatan)
    @GetMapping("/inscripciones")
    public ResponseEntity<List<InscripcionResponse>> listarInscripcionesPorProfesor(@RequestParam("profesorEmail") String profesorEmail) {
        if (profesorEmail == null || profesorEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere el correo del profesor.");
        }

        List<Inscripcion> inscripciones = inscripcionRepository.findByOfertaProfesorCorreo(profesorEmail);

        List<InscripcionResponse> dto = inscripciones.stream().map(i -> {
            var est = i.getEstudiante();
            String nombre = String.join(" ", est.getPrimerNombre(), est.getSegundoNombre(), est.getApellidoPaterno(), est.getApellidoMaterno());
            return new InscripcionResponse(i.getId(), est.getRut(), nombre, est.getCorreo());
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dto);
    }

    // 4. Ofertas por profesor o estudiante
    @GetMapping("/ofertas")
    public ResponseEntity<List<OfertaResponse>> listarOfertasPorProfesor(@RequestParam(value = "profesorEmail", required = false) String profesorEmail) {
        List<OfertaResponse> dto;
        if (profesorEmail != null && !profesorEmail.isBlank() && !"admin@ubiobio.cl".equalsIgnoreCase(profesorEmail.trim())) {
            List<Oferta> ofertas = ofertaRepository.findByProfesorCorreoIgnoreCaseOrderByAnioDescPeriodoDesc(profesorEmail.trim());
            if (ofertas.isEmpty()) {
                ofertas = ofertaRepository.findByProfesorCorreoOrderByAnioDescPeriodoDesc(profesorEmail.trim());
            }

            if (!ofertas.isEmpty()) {
                // Caso Profesor: listar ofertas a su cargo
                dto = ofertas.stream().map(o -> {
                    var ap = o.getAsignaturaPractica();
                    Long inscritos = inscripcionRepository.countByOfertaId(o.getId());
                    return new OfertaResponse(o.getId(), o.getAnio(), o.getPeriodo(), ap != null ? ap.getCodigo() : null, ap != null ? ap.getNombre() : null, inscritos == null ? 0L : inscritos);
                }).collect(Collectors.toList());
            } else {
                // Caso Estudiante: listar sus prácticas inscritas
                List<Inscripcion> inscripcionesEst = inscripcionRepository.findByEstudianteCorreoWithOferta(profesorEmail.trim());
                dto = inscripcionesEst.stream().map(i -> {
                    Oferta o = i.getOferta();
                    var ap = (o != null) ? o.getAsignaturaPractica() : null;
                    var prof = (o != null) ? o.getProfesor() : null;
                    String profNom = (prof != null) ? (prof.getPrimerNombre() + " " + prof.getApellidoPaterno()).trim() : null;
                    String profCor = (prof != null) ? prof.getCorreo() : null;

                    OfertaResponse r = new OfertaResponse(
                            (o != null) ? o.getId() : i.getId(),
                            (o != null) ? o.getAnio() : 0,
                            (o != null) ? o.getPeriodo() : 0,
                            (ap != null) ? ap.getCodigo() : "INF-PRA",
                            (ap != null) ? ap.getNombre() : "Práctica Profesional",
                            1L
                    );
                    r.setInscripcionId(i.getId());
                    r.setProfesorNombre(profNom);
                    r.setProfesorCorreo(profCor);
                    return r;
                }).collect(Collectors.toList());
            }
        } else {
            List<Oferta> ofertas = ofertaRepository.findAllWithDetailsOrderByAnioDescPeriodoDesc();
            dto = ofertas.stream().map(o -> {
                var ap = o.getAsignaturaPractica();
                Long inscritos = inscripcionRepository.countByOfertaId(o.getId());
                return new OfertaResponse(o.getId(), o.getAnio(), o.getPeriodo(), ap != null ? ap.getCodigo() : null, ap != null ? ap.getNombre() : null, inscritos == null ? 0L : inscritos);
            }).collect(Collectors.toList());
        }

        return ResponseEntity.ok(dto);
    }

    // 5. Oferta por ID
    @GetMapping("/ofertas/{ofertaId}")
    public ResponseEntity<OfertaResponse> getOfertaById(@PathVariable("ofertaId") Long ofertaId) {
        if (ofertaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere el id de la oferta.");
        }

        Oferta o = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada"));

        var ap = o.getAsignaturaPractica();
        Long inscritos = inscripcionRepository.countByOfertaId(o.getId());
        OfertaResponse resp = new OfertaResponse(o.getId(), o.getAnio(), o.getPeriodo(), ap != null ? ap.getCodigo() : null, ap != null ? ap.getNombre() : null, inscritos == null ? 0L : inscritos);

        return ResponseEntity.ok(resp);
    }

    // 6. Inscripciones de una oferta
    @GetMapping("/ofertas/{ofertaId}/inscripciones")
    public ResponseEntity<List<InscripcionResponse>> listarInscripcionesPorOferta(@PathVariable("ofertaId") Long ofertaId) {
        if (ofertaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere el id de la oferta.");
        }

        List<Inscripcion> inscripciones = inscripcionRepository.findByOfertaId(ofertaId);

        List<InscripcionResponse> dto = inscripciones.stream().map(i -> {
            var est = i.getEstudiante();
            String nombre = String.join(" ", est.getPrimerNombre(), est.getSegundoNombre(), est.getApellidoPaterno(), est.getApellidoMaterno());
            return new InscripcionResponse(i.getId(), est.getRut(), nombre, est.getCorreo());
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dto);
    }

    // 7. Detalle de inscripción (Ficha de Alumno)
    @GetMapping("/inscripciones/{inscripcionId}")
    public ResponseEntity<EstudianteDetalleResponse> getDetalleInscripcion(
            @PathVariable("inscripcionId") Long inscripcionId,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        if (inscripcionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se requiere el ID de la inscripción.");
        }

        Inscripcion inscripcion = inscripcionRepository.findByIdWithDetails(inscripcionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada"));

        Estudiante est = inscripcion.getEstudiante();
        Oferta ofertaActual = inscripcion.getOferta();
        AsignaturaPractica apActual = ofertaActual != null ? ofertaActual.getAsignaturaPractica() : null;
        Profesor profActual = ofertaActual != null ? ofertaActual.getProfesor() : null;
        List<Asignacion> asignaciones = asignacionRepository.findByInscripcionIdWithEvaluador(inscripcionId);

        // Control de Integridad de Dominio:
        if (userEmail != null && !userEmail.isBlank()) {
            String emailNorm = userEmail.trim().toLowerCase();
            if (!"admin@ubiobio.cl".equalsIgnoreCase(emailNorm)) {
                boolean esSuProfesor = profActual != null && profActual.getCorreo() != null
                        && emailNorm.equalsIgnoreCase(profActual.getCorreo().trim());
                boolean esElMismoEstudiante = est != null && est.getCorreo() != null
                        && emailNorm.equalsIgnoreCase(est.getCorreo().trim());
                boolean esEvaluadorAsignado = asignaciones != null && asignaciones.stream()
                        .anyMatch(a -> a.getEvaluador() != null && a.getEvaluador().getCorreo() != null
                                && emailNorm.equalsIgnoreCase(a.getEvaluador().getCorreo().trim()));

                if (!esSuProfesor && !esElMismoEstudiante && !esEvaluadorAsignado) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                        "Acceso denegado: No tiene autorización para consultar la ficha de un estudiante fuera de su dominio académico.");
                }
            }
        }

        EstudianteDetalleResponse resp = new EstudianteDetalleResponse();
        resp.setInscripcionId(inscripcion.getId());
        if (ofertaActual != null) {
            resp.setOfertaId(ofertaActual.getId());
            resp.setAnio(ofertaActual.getAnio());
            resp.setPeriodo(ofertaActual.getPeriodo());
        }
        if (apActual != null) {
            resp.setAsignaturaCodigo(apActual.getCodigo());
            resp.setAsignaturaNombre(apActual.getNombre());
        }

        if (est != null) {
            resp.setEstudianteRut(est.getRut());
            String nombreCompleto = String.join(" ",
                    est.getPrimerNombre() != null ? est.getPrimerNombre() : "",
                    est.getSegundoNombre() != null ? est.getSegundoNombre() : "",
                    est.getApellidoPaterno() != null ? est.getApellidoPaterno() : "",
                    est.getApellidoMaterno() != null ? est.getApellidoMaterno() : "").replaceAll("\\s+", " ").trim();
            resp.setEstudianteNombre(nombreCompleto);
            resp.setEstudianteCorreo(est.getCorreo());
            resp.setCarrera("Ingeniería Civil Informática • Prácticas Profesionales");
        }

        if (profActual != null) {
            String nombreProf = String.join(" ",
                    profActual.getPrimerNombre() != null ? profActual.getPrimerNombre() : "",
                    profActual.getSegundoNombre() != null ? profActual.getSegundoNombre() : "",
                    profActual.getApellidoPaterno() != null ? profActual.getApellidoPaterno() : "",
                    profActual.getApellidoMaterno() != null ? profActual.getApellidoMaterno() : "").replaceAll("\\s+", " ").trim();
            resp.setProfesorGuiaNombre(nombreProf);
            resp.setProfesorGuiaCorreo(profActual.getCorreo());
        }

        resp.setEstablecimiento("Liceo Bicentenario de Excelencia Polivalente San Nicolás");
        List<EstudianteDetalleResponse.EvaluadorItemDto> evaluadoresDto = asignaciones.stream().map(a -> {
            Evaluador ev = a.getEvaluador();
            if (ev == null) return null;
            String nomEv = String.join(" ",
                    ev.getPrimerNombre() != null ? ev.getPrimerNombre() : "",
                    ev.getSegundoNombre() != null ? ev.getSegundoNombre() : "",
                    ev.getApellidoPaterno() != null ? ev.getApellidoPaterno() : "",
                    ev.getApellidoMaterno() != null ? ev.getApellidoMaterno() : "").replaceAll("\\s+", " ").trim();
            return new EstudianteDetalleResponse.EvaluadorItemDto(
                    ev.getRut(),
                    nomEv,
                    ev.getPrimerNombre(),
                    ev.getApellidoPaterno(),
                    ev.getApellidoMaterno(),
                    ev.getCorreo(),
                    ev.getTipo() != null ? ev.getTipo().name() : "EVALUADOR"
            );
        }).filter(item -> item != null).collect(Collectors.toList());
        resp.setEvaluadores(evaluadoresDto);

        if (est != null) {
            List<Inscripcion> todasInscripciones = inscripcionRepository.findByEstudianteRutWithOferta(est.getRut());
            List<EstudianteDetalleResponse.PracticaHistorialItemDto> historial = todasInscripciones.stream().map(i -> {
                Oferta of = i.getOferta();
                AsignaturaPractica ap = of != null ? of.getAsignaturaPractica() : null;
                boolean esActual = i.getId().equals(inscripcionId);
                String estado = esActual ? "En curso" : "Finalizada";
                return new EstudianteDetalleResponse.PracticaHistorialItemDto(
                        i.getId(),
                        of != null ? of.getId() : null,
                        ap != null ? ap.getCodigo() : "S/C",
                        ap != null ? ap.getNombre() : "Práctica",
                        of != null ? of.getAnio() : 0,
                        of != null ? of.getPeriodo() : 0,
                        esActual,
                        estado
                );
            }).collect(Collectors.toList());
            resp.setHistorialPracticas(historial);
        }

        List<Informe> informes = informeRepository.findByInscripcionIdOrderByIdDesc(inscripcionId);
        if (!informes.isEmpty()) {
            Informe ultimo = informes.get(0);
            String rawArchivo = ultimo.getArchivo();
            String nombreArchivo = "informe.pdf";
            if (rawArchivo != null) {
                String cleanPath = rawArchivo.replace('\\', '/');
                nombreArchivo = cleanPath.substring(cleanPath.lastIndexOf("/") + 1);
                nombreArchivo = nombreArchivo.replaceFirst("^[a-f0-9\\-]{36}_", "");
            }
            String archivoUrl = "/api/practicas/informes/" + ultimo.getId() + "/archivo";
            resp.setInformeActual(new EstudianteDetalleResponse.InformeItemDto(
                    ultimo.getId(),
                    ultimo.getArchivo(),
                    archivoUrl,
                    nombreArchivo,
                    ultimo.getFecha() != null ? ultimo.getFecha().toString() : "",
                    ultimo.getEmisor() != null ? ultimo.getEmisor().name() : "PROFESOR"
            ));
        }

        return ResponseEntity.ok(resp);
    }

    // 8. Subir informe para inscripción
    @PostMapping(value = "/{inscripcionId}/informe-final", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InformeEntregaResponse> subirInformeFinalParaInscripcion(
            @PathVariable("inscripcionId") Long inscripcionId,
            @RequestParam("archivo") MultipartFile archivo) {
        InformeEntregaResponse response = practicaService.guardarInformeFinalParaInscripcion(archivo, inscripcionId);
        return ResponseEntity.ok(response);
    }

    // 9. Directorio de alumnos con validación estricta de rol docente
    @GetMapping("/alumnos")
    public ResponseEntity<List<EstudianteDirectorioDto>> listarTodosLosAlumnos(
            @RequestParam(value = "profesorEmail", required = false) String profesorEmail) {

        if (profesorEmail == null || profesorEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: Se requiere identificación docente para acceder al directorio.");
        }

        String emailNorm = profesorEmail.trim().toLowerCase();
        if ("admin@ubiobio.cl".equalsIgnoreCase(emailNorm)) {
            List<Inscripcion> inscripciones = inscripcionRepository.findAllWithDetails();
            return ResponseEntity.ok(construirDirectorioAlumnos(inscripciones));
        }

        // Si el usuario es un Estudiante, bloquear acceso directo al directorio general
        boolean esEstudiante = estudianteRepository.findByCorreo(emailNorm).isPresent();
        if (esEstudiante) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: Los estudiantes no tienen autorización para consultar el directorio general de alumnos.");
        }

        List<Inscripcion> inscripciones = inscripcionRepository.findByOfertaProfesorCorreoWithDetails(emailNorm);
        return ResponseEntity.ok(construirDirectorioAlumnos(inscripciones));
    }

    private List<EstudianteDirectorioDto> construirDirectorioAlumnos(List<Inscripcion> inscripciones) {
        Map<String, EstudianteDirectorioDto> alumnoMap = new LinkedHashMap<>();

        for (Inscripcion i : inscripciones) {
            var est = i.getEstudiante();
            if (est == null) continue;

            if (!alumnoMap.containsKey(est.getRut())) {
                String nombreCompleto = String.join(" ",
                        est.getPrimerNombre() != null ? est.getPrimerNombre() : "",
                        est.getSegundoNombre() != null ? est.getSegundoNombre() : "",
                        est.getApellidoPaterno() != null ? est.getApellidoPaterno() : "",
                        est.getApellidoMaterno() != null ? est.getApellidoMaterno() : "").replaceAll("\\s+", " ").trim();

                Oferta of = i.getOferta();
                AsignaturaPractica ap = of != null ? of.getAsignaturaPractica() : null;

                int anio = of != null ? of.getAnio() : 0;
                int periodo = of != null ? of.getPeriodo() : 0;
                String estado = (anio >= 2026) ? "En curso" : "Finalizada";

                alumnoMap.put(est.getRut(), new EstudianteDirectorioDto(
                        est.getRut(),
                        nombreCompleto,
                        est.getCorreo(),
                        "Ingeniería Civil en Informática",
                        i.getId(),
                        of != null ? of.getId() : null,
                        ap != null ? ap.getNombre() : "Práctica Profesional",
                        ap != null ? ap.getCodigo() : "INF-403",
                        anio,
                        periodo,
                        estado
                ));
            }
        }
        return new ArrayList<>(alumnoMap.values());
    }

    // 10. Detalle de estudiante por RUT con validación de dominio
    @GetMapping("/estudiantes/{rut}")
    public ResponseEntity<EstudianteDetalleResponse> getDetallePorRutEstudiante(
            @PathVariable("rut") String rut,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        List<Inscripcion> inscripciones = inscripcionRepository.findByEstudianteRutWithOferta(rut);
        if (!inscripciones.isEmpty()) {
            return getDetalleInscripcion(inscripciones.get(0).getId(), userEmail);
        }

        Estudiante est = estudianteRepository.findById(rut)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado con RUT: " + rut));

        // Si se especificó usuario consultor, validar dominio
        if (userEmail != null && !userEmail.isBlank()) {
            String emailNorm = userEmail.trim().toLowerCase();
            if (!"admin@ubiobio.cl".equalsIgnoreCase(emailNorm) && !emailNorm.equalsIgnoreCase(est.getCorreo())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: No tiene permisos para consultar información de este estudiante.");
            }
        }

        EstudianteDetalleResponse resp = new EstudianteDetalleResponse();
        resp.setEstudianteRut(est.getRut());
        String nombre = String.join(" ",
                est.getPrimerNombre() != null ? est.getPrimerNombre() : "",
                est.getSegundoNombre() != null ? est.getSegundoNombre() : "",
                est.getApellidoPaterno() != null ? est.getApellidoPaterno() : "",
                est.getApellidoMaterno() != null ? est.getApellidoMaterno() : "").replaceAll("\\s+", " ").trim();
        resp.setEstudianteNombre(nombre);
        resp.setEstudianteCorreo(est.getCorreo());
        resp.setCarrera("Ingeniería Civil en Informática");
        resp.setEstablecimiento("Sin asignación de centro");
        return ResponseEntity.ok(resp);
    }

    // 11. Detalle de estudiante por Correo con validación de dominio
    @GetMapping("/estudiantes/correo/{correo}")
    public ResponseEntity<EstudianteDetalleResponse> getDetallePorCorreoEstudiante(
            @PathVariable("correo") String correo,
            @RequestParam(value = "userEmail", required = false) String userEmail) {
        if (correo == null || correo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo inválido");
        }

        List<Inscripcion> inscripciones = inscripcionRepository.findByEstudianteCorreoWithOferta(correo.trim());
        if (!inscripciones.isEmpty()) {
            return getDetalleInscripcion(inscripciones.get(0).getId(), userEmail);
        }

        Estudiante est = estudianteRepository.findByCorreo(correo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado con correo: " + correo));

        return getDetallePorRutEstudiante(est.getRut(), userEmail);
    }
}
