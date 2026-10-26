import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Navbar } from '../components/Navbar'
import { useAuth } from '../context/AuthContext'
import { getPlanificaciones, Planificacion, getOfertasForProfesor, OfertaDto, getDetalleEstudianteByCorreo, getMiInforme, getResumenAvanceEstudiante, ResumenAvanceDto, getTareasPendientesEstudiante, TareaPendienteDto } from '../services/api'
import {
  Search,
  FileText,
  Clock,
  Briefcase,
  Users,
  X,
  Bot,
  MessageCircle,
  CheckCircle,
  Calendar,
  AlertCircle,
  GraduationCap,
  CheckSquare,
  UserPlus,
  FolderOpen,
  Upload,
  ExternalLink
} from 'lucide-react'

export interface PracticeStudent {
  id: string
  name: string
  email?: string
  avatar: string
  avatarClass: string
  status: string
  statusClass: 'status-pending' | 'status-approved' | 'status-review'
  professor: string
  supervisor?: string
  hoursCompleted?: number
  totalHours?: number
  planificacionesCount?: number
}

const INITIAL_PRACTICE_STUDENTS: PracticeStudent[] = [
  { name: 'Mariana Tapia', id: '2021-4521', email: 'mtapia@alumnos.ubiobio.cl', avatar: 'MT', avatarClass: 'avatar-blue', status: 'Convenio Pendiente de Firma', statusClass: 'status-pending', professor: 'Prof. Ricardo Tapia', supervisor: 'Ing. Carlos Mendoza', hoursCompleted: 360, totalHours: 360, planificacionesCount: 2 },
  { name: 'Marco Valantaz', id: '2020-3312', email: 'mvalantaz@alumnos.ubiobio.cl', avatar: 'MV', avatarClass: 'avatar-gold', status: 'Convenio Aprobado', statusClass: 'status-approved', professor: 'Prof. Sandra Muñoz', supervisor: 'Ing. Elena Gómez', hoursCompleted: 360, totalHours: 360, planificacionesCount: 4 },
  { name: 'Carolina Riquelme', id: '2021-5589', email: 'criquelme@alumnos.ubiobio.cl', avatar: 'CR', avatarClass: 'avatar-teal', status: 'En Revisión Académica', statusClass: 'status-review', professor: 'Prof. Ricardo Tapia', supervisor: 'Ing. Roberto Silva', hoursCompleted: 180, totalHours: 360, planificacionesCount: 1 },
  { name: 'Felipe Contreras', id: '2020-4103', email: 'fcontreras@alumnos.ubiobio.cl', avatar: 'FC', avatarClass: 'avatar-purple', status: 'Convenio Pendiente de Firma', statusClass: 'status-pending', professor: 'Prof. Juan Pérez', supervisor: 'Ing. Carlos Mendoza', hoursCompleted: 360, totalHours: 360, planificacionesCount: 3 },
  { name: 'Daniela Muñoz', id: '2021-6672', email: 'dmunoz@alumnos.ubiobio.cl', avatar: 'DM', avatarClass: 'avatar-blue', status: 'Convenio Aprobado', statusClass: 'status-approved', professor: 'Prof. Sandra Muñoz', supervisor: 'Ing. Elena Gómez', hoursCompleted: 360, totalHours: 360, planificacionesCount: 3 },
  { name: 'Matías González (Estudiante)', id: '2021-9988', email: 'estudiante@alumnos.ubiobio.cl', avatar: 'MG', avatarClass: 'avatar-gold', status: 'Convenio Aprobado', statusClass: 'status-approved', professor: 'Prof. Juan Pérez', supervisor: 'Ing. Carlos Mendoza', hoursCompleted: 360, totalHours: 360, planificacionesCount: 2 },
]

export const HomePage: React.FC = () => {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [chatOpen, setChatOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [userPlanificaciones, setUserPlanificaciones] = useState<Planificacion[]>([])
  const [informeEntregado, setInformeEntregado] = useState<{ id?: number; nombreArchivo: string; fechaEntrega?: string; archivoUrl?: string } | null>(null)
  const [resumenAvance, setResumenAvance] = useState<ResumenAvanceDto | null>(null)
  const [tareasPendientes, setTareasPendientes] = useState<TareaPendienteDto[]>([])

  const [studentsList, setStudentsList] = useState<PracticeStudent[]>(() => {
    const saved = localStorage.getItem('ubb_practice_students_list')
    if (saved) {
      try {
        return JSON.parse(saved)
      } catch { }
    }
    return INITIAL_PRACTICE_STUDENTS
  })
  const [activeTab, setActiveTab] = useState<'TODOS' | 'PENDIENTE' | 'APROBADO' | 'REVISION'>('TODOS')
  const [selectedStudentForExpediente, setSelectedStudentForExpediente] = useState<PracticeStudent | null>(null)
  const [isNewStudentModalOpen, setIsNewStudentModalOpen] = useState<boolean>(false)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  // Formulario de nuevo estudiante
  const [newStudentName, setNewStudentName] = useState('')
  const [newStudentID, setNewStudentID] = useState('')
  const [newStudentEmail, setNewStudentEmail] = useState('')
  const [newStudentProf, setNewStudentProf] = useState('Prof. Juan Pérez')
  const [newStudentStatus, setNewStudentStatus] = useState('Convenio Pendiente de Firma')

  // Prácticas / Ofertas para Docente y Administrador
  const [ofertas, setOfertas] = useState<OfertaDto[]>([])
  const [isLoadingOfertas, setIsLoadingOfertas] = useState<boolean>(false)

  const displayName = user?.fullName || (user?.email ? user.email.split('@')[0] : 'Usuario')
  const isDocenteOrAdmin = user?.role === 'Administrador' || user?.role === 'Profesor' || user?.role === 'Evaluador'

  const showFeedback = (msg: string) => {
    setToastMessage(msg)
    setTimeout(() => setToastMessage(null), 4000)
  }

  const updateStudentStatus = (studentId: string, newStatus: string, newStatusClass: 'status-pending' | 'status-approved' | 'status-review') => {
    const updated = studentsList.map(s => s.id === studentId ? { ...s, status: newStatus, statusClass: newStatusClass } : s)
    setStudentsList(updated)
    localStorage.setItem('ubb_practice_students_list', JSON.stringify(updated))
    showFeedback(`Estado de convenio actualizado a: "${newStatus}"`)
  }

  const handleCreateStudent = (e: React.FormEvent) => {
    e.preventDefault()
    if (!newStudentName.trim() || !newStudentID.trim()) {
      showFeedback('Por favor complete todos los campos obligatorios.')
      return
    }

    const initials = newStudentName
      .split(' ')
      .filter(Boolean)
      .map(p => p[0])
      .slice(0, 2)
      .join('')
      .toUpperCase() || 'AL'

    const statusClass: 'status-pending' | 'status-approved' | 'status-review' =
      newStudentStatus === 'Convenio Aprobado'
        ? 'status-approved'
        : newStudentStatus === 'En Revisión Académica'
          ? 'status-review'
          : 'status-pending'

    const newStudent: PracticeStudent = {
      id: newStudentID.trim(),
      name: newStudentName.trim(),
      email: newStudentEmail.trim() || `${newStudentID.toLowerCase()}@alumnos.ubiobio.cl`,
      avatar: initials,
      avatarClass: 'avatar-blue',
      status: newStudentStatus,
      statusClass,
      professor: newStudentProf,
      supervisor: 'Ing. Carlos Mendoza',
      hoursCompleted: 0,
      totalHours: 360,
      planificacionesCount: 0
    }

    const updated = [newStudent, ...studentsList]
    setStudentsList(updated)
    localStorage.setItem('ubb_practice_students_list', JSON.stringify(updated))
    setIsNewStudentModalOpen(false)
    setNewStudentName('')
    setNewStudentID('')
    setNewStudentEmail('')
    showFeedback(`Estudiante "${newStudent.name}" inscrito exitosamente.`)
  }

  // Cargar datos
  useEffect(() => {
    const fetchData = async () => {
      try {
        const plans = await getPlanificaciones(user?.id, user?.role)
        setUserPlanificaciones(plans)
      } catch (err) {
        console.error('Error fetching planificaciones:', err)
      }

      if (isDocenteOrAdmin && user?.email) {
        setIsLoadingOfertas(true)
        try {
          const ofs = await getOfertasForProfesor(user.email)
          setOfertas(ofs)
        } catch (err) {
          console.error('Error fetching ofertas:', err)
        } finally {
          setIsLoadingOfertas(false)
        }
      }

      // Si es estudiante, consultar detalle en backend para obtener su informe final más reciente
      if (!isDocenteOrAdmin && user?.email) {
        getResumenAvanceEstudiante(user.email).then(res => {
          if (res) setResumenAvance(res)
        }).catch(err => console.error('Error fetching resumen avance:', err))

        getTareasPendientesEstudiante(user.email).then(list => {
          if (list) setTareasPendientes(list)
        }).catch(err => console.error('Error fetching tareas pendientes:', err))

        try {
          const detalle = await getDetalleEstudianteByCorreo(user.email, user.email)
          if (detalle?.informeActual) {
            setInformeEntregado({
              id: detalle.informeActual.id,
              nombreArchivo: detalle.informeActual.nombreArchivo || detalle.informeActual.archivo || 'Informe_Final_Practica.pdf',
              fechaEntrega: detalle.informeActual.fecha,
              archivoUrl: detalle.informeActual.archivoUrl || (detalle.informeActual.id ? `/api/practicas/informes/${detalle.informeActual.id}/archivo` : undefined)
            })
            return
          }
        } catch (err) {
          console.error('Error fetching detalle estudiante:', err)
        }

        try {
          const miInf = await getMiInforme(user.email)
          if (miInf) {
            setInformeEntregado({
              id: miInf.id,
              nombreArchivo: miInf.nombreArchivo || 'Informe_Final.pdf',
              fechaEntrega: miInf.fechaEntrega ? miInf.fechaEntrega.split('T')[0] : undefined,
              archivoUrl: miInf.archivoUrl || (miInf.id ? `/api/practicas/informes/${miInf.id}/archivo` : undefined)
            })
            return
          }
        } catch (err) {
          console.error('Error fetching mi informe:', err)
        }

        // Si el estudiante consultó el backend y no tiene informe registrado, limpiar datos residuales
        if (user?.id) {
          localStorage.removeItem(`ubb_informe_entrega_${user.id}`)
          localStorage.removeItem('ubb_ultimo_informe_entregado')
        }
        setInformeEntregado(null)
        return
      }

      const rawUserInforme = user?.id ? localStorage.getItem(`ubb_informe_entrega_${user.id}`) : null
      if (rawUserInforme) {
        try {
          setInformeEntregado(JSON.parse(rawUserInforme))
        } catch { }
      }
    }

    fetchData()
  }, [user, isDocenteOrAdmin])

  // Filtrado de estudiantes
  const filteredStudents = studentsList.filter((s) => {
    const matchSearch =
      s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.status.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.professor.toLowerCase().includes(searchQuery.toLowerCase())

    if (!matchSearch) return false

    if (activeTab === 'PENDIENTE') return s.statusClass === 'status-pending'
    if (activeTab === 'APROBADO') return s.statusClass === 'status-approved'
    if (activeTab === 'REVISION') return s.statusClass === 'status-review'
    return true
  })

  // KPIs
  const conveniosPendientes = studentsList.filter(s => s.statusClass === 'status-pending').length
  const conveniosAprobados = studentsList.filter(s => s.statusClass === 'status-approved').length
  const conveniosRevision = studentsList.filter(s => s.statusClass === 'status-review').length

  const totalPlanificaciones = userPlanificaciones.length
  const planificacionesAprobadas = userPlanificaciones.filter(p => p.estado === 'APROBADA').length
  const planificacionesPendientes = userPlanificaciones.filter(p => p.estado === 'PENDIENTE' || !p.estado).length
  const tieneInformeFinal = !!informeEntregado

  const totalOfertas = ofertas.length
  const totalInscritosDocente = ofertas.reduce((acc, o) => acc + (o.inscritosCount || 0), 0)

  return (
    <div style={styles.page}>
      <Navbar />

      {/* Toast Feedback */}
      {toastMessage && (
        <div style={styles.toast}>
          <CheckCircle size={16} color="#4ade80" />
          <span>{toastMessage}</span>
        </div>
      )}

      <main style={styles.main}>
        {/* Hero Section */}
        <section style={styles.hero}>
          <div style={styles.heroContent}>
            <div style={styles.badgeHero}>
              <span style={styles.dot} />
              Bienvenido de vuelta, {displayName}
            </div>
            <h1 style={styles.heroTitle}>
              UBB <span style={styles.highlight}>DocHUB</span>
            </h1>
            <p style={styles.heroSubtitle}>
              Gestión de documentos de práctica
            </p>
          </div>
        </section>

        {/* Search Bar */}
        <div style={styles.searchWrapper}>
          <div style={styles.searchBar}>
            <Search size={18} color="#94a3b8" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Buscar documentos, prácticas, profesores o alumnos..."
              style={styles.searchInput}
            />
          </div>
        </div>

        {/* Quick Access Cards */}
        <div style={styles.quickAccess}>
          {isDocenteOrAdmin ? (
            <>
              <button onClick={() => navigate('/practicas')} style={{ ...styles.quickCard, ...styles.quickCardBlue }}>
                <Briefcase size={22} />
                <div style={styles.quickCardTitle}>Mis Prácticas</div>
                <div style={styles.quickCardDesc}>Ofertas asignadas ({totalOfertas})</div>
              </button>
              <button onClick={() => navigate('/alumnos')} style={{ ...styles.quickCard, ...styles.quickCardGold }}>
                <Users size={22} />
                <div style={styles.quickCardTitle}>Directorio de Alumnos</div>
                <div style={styles.quickCardDesc}>Estudiantes y fichas</div>
              </button>
              <button onClick={() => navigate('/planificaciones')} style={{ ...styles.quickCard, ...styles.quickCardTeal }}>
                <FileText size={22} />
                <div style={styles.quickCardTitle}>Planificaciones de Clase</div>
                <div style={styles.quickCardDesc}>Revisión y retroalimentación</div>
              </button>
              <button onClick={() => navigate('/evaluaciones')} style={{ ...styles.quickCard, ...styles.quickCardPurple }}>
                <GraduationCap size={22} />
                <div style={styles.quickCardTitle}>Evaluaciones Docentes</div>
                <div style={styles.quickCardDesc}>Clase y semestrales</div>
              </button>
            </>
          ) : (
            <>
              <button onClick={() => navigate('/entrega-informe')} style={{ ...styles.quickCard, ...styles.quickCardBlue }}>
                {tieneInformeFinal ? <CheckCircle size={22} color="#10b981" /> : <Upload size={22} />}
                <div style={styles.quickCardTitle}>{tieneInformeFinal ? 'Informe Entregado (1/1)' : 'Entregar Informe Final'}</div>
                <div style={styles.quickCardDesc}>{tieneInformeFinal ? 'Ver o reemplazar entrega' : 'Subir archivo PDF de Práctica'}</div>
              </button>
              <button onClick={() => navigate('/planificaciones')} style={{ ...styles.quickCard, ...styles.quickCardGold }}>
                <FileText size={22} />
                <div style={styles.quickCardTitle}>Planificaciones de Clase</div>
                <div style={styles.quickCardDesc}>Subir y gestionar archivos</div>
              </button>
              <button onClick={() => navigate('/evaluaciones')} style={{ ...styles.quickCard, ...styles.quickCardTeal }}>
                <GraduationCap size={22} />
                <div style={styles.quickCardTitle}>Mis Evaluaciones</div>
                <div style={styles.quickCardDesc}>Pautas y desempeño</div>
              </button>
              <button onClick={() => navigate('/perfil')} style={{ ...styles.quickCard, ...styles.quickCardPurple }}>
                <Users size={22} />
                <div style={styles.quickCardTitle}>Mi Perfil Académico</div>
                <div style={styles.quickCardDesc}>Ficha institucional</div>
              </button>
            </>
          )}
        </div>

        {/* Dashboard Grid */}
        <div style={styles.dashboardGrid}>
          {/* Main Column */}
          <div style={styles.dashboardMain}>
            {isDocenteOrAdmin ? (
              <>
                {/* 1. Ofertas de Práctica del Profesor */}
                <div style={{ ...styles.card, marginBottom: '24px' }}>
                  <div style={styles.cardHeader}>
                    <div>
                      <h2 style={styles.cardTitle}>Ofertas de Práctica Asignadas</h2>
                      <p style={styles.cardSubtitle}>Cursos y secciones bajo tu supervisión docente</p>
                    </div>
                    <button
                      onClick={() => navigate('/practicas')}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '5px',
                        backgroundColor: '#2563eb',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '6px',
                        padding: '6px 12px',
                        fontSize: '12px',
                        fontWeight: '600',
                        cursor: 'pointer'
                      }}
                    >
                      <Briefcase size={14} /> Ver Todas →
                    </button>
                  </div>

                  <div style={styles.cardBody}>
                    {isLoadingOfertas ? (
                      <div style={{ padding: '24px', textAlign: 'center', color: '#94a3b8' }}>Cargando ofertas...</div>
                    ) : ofertas.length === 0 ? (
                      <div style={{ padding: '24px', textAlign: 'center', color: '#94a3b8' }}>No hay ofertas registradas actualmente.</div>
                    ) : (
                      <table style={styles.table}>
                        <thead>
                          <tr style={styles.trHead}>
                            <th style={styles.th}>Asignatura</th>
                            <th style={styles.th}>Período</th>
                            <th style={styles.th}>Inscritos</th>
                            <th style={{ ...styles.th, textAlign: 'right' }}>Acción</th>
                          </tr>
                        </thead>
                        <tbody>
                          {ofertas.slice(0, 4).map((of) => (
                            <tr key={of.id} style={styles.trBody}>
                              <td style={{ ...styles.td, minWidth: '160px' }}>
                                <div style={{ fontWeight: '600', color: '#ffffff', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '240px' }} title={of.asignaturaNombre}>
                                  {of.asignaturaNombre}
                                </div>
                                <div style={{ fontSize: '11px', color: '#94a3b8' }}>Código: {of.asignaturaCodigo}</div>
                              </td>
                              <td style={{ ...styles.td, whiteSpace: 'nowrap' }}>
                                <span style={styles.periodBadge}>{of.anio} - Semestre {of.periodo}</span>
                              </td>
                              <td style={{ ...styles.td, whiteSpace: 'nowrap' }}>
                                <span style={{ color: '#60a5fa', fontWeight: '600' }}>{of.inscritosCount || 0} estudiantes</span>
                              </td>
                              <td style={{ ...styles.td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                                <button
                                  onClick={() => navigate(`/practicas/${of.id}`)}
                                  style={{
                                    backgroundColor: 'rgba(59, 130, 246, 0.15)',
                                    color: '#60a5fa',
                                    border: '1px solid rgba(59, 130, 246, 0.3)',
                                    borderRadius: '6px',
                                    padding: '5px 10px',
                                    fontSize: '12px',
                                    fontWeight: '600',
                                    cursor: 'pointer'
                                  }}
                                >
                                  Ver Estudiantes →
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                </div>

                {/* 2. Planificaciones de Clase (Revisión) */}
                <div style={{ ...styles.card, marginBottom: '24px' }}>
                  <div style={styles.cardHeader}>
                    <div>
                      <h2 style={styles.cardTitle}>Planificaciones de Clase Recientes</h2>
                      <p style={styles.cardSubtitle}>Archivos subidos por estudiantes pendientes o evaluados</p>
                    </div>
                    <button
                      onClick={() => navigate('/planificaciones')}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '5px',
                        backgroundColor: '#2563eb',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '6px',
                        padding: '6px 12px',
                        fontSize: '12px',
                        fontWeight: '600',
                        cursor: 'pointer'
                      }}
                    >
                      <FileText size={14} /> Gestionar Planificaciones →
                    </button>
                  </div>

                  <div style={styles.cardBody}>
                    {userPlanificaciones.length === 0 ? (
                      <div style={{ padding: '24px', textAlign: 'center', color: '#94a3b8' }}>No hay planificaciones registradas aún.</div>
                    ) : (
                      <table style={styles.table}>
                        <thead>
                          <tr style={styles.trHead}>
                            <th style={styles.th}>Estudiante</th>
                            <th style={styles.th}>Documento</th>
                            <th style={{ ...styles.th, textAlign: 'center' }}>Estado</th>
                            <th style={{ ...styles.th, textAlign: 'right' }}>Acción</th>
                          </tr>
                        </thead>
                        <tbody>
                          {userPlanificaciones.slice(0, 4).map((p) => {
                            const rawName = p.nombreArchivo || p.archivo?.split('/').pop() || `Planificación #${p.id}`
                            const cleanName = rawName.replace(/^[a-f0-9\-]{36}_/, '')
                            return (
                              <tr key={p.id} style={styles.trBody}>
                                <td style={{ ...styles.td, minWidth: '140px', maxWidth: '200px' }}>
                                  <div style={{ fontWeight: '600', color: '#ffffff', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={p.usuario?.fullName || 'Estudiante'}>
                                    {p.usuario?.fullName || 'Estudiante'}
                                  </div>
                                  <div style={{ fontSize: '11px', color: '#94a3b8', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={p.usuario?.email || ''}>
                                    {p.usuario?.email || ''}
                                  </div>
                                </td>
                                <td style={{ ...styles.td, minWidth: '130px', maxWidth: '200px' }}>
                                  <span style={{ color: '#cbd5e1', fontSize: '12px', display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={cleanName}>
                                    {cleanName}
                                  </span>
                                </td>
                                <td style={{ ...styles.td, textAlign: 'center', whiteSpace: 'nowrap' }}>
                                  <span style={p.estado === 'APROBADA' ? styles.statusApproved : p.estado === 'RECHAZADA' ? styles.statusReview : styles.statusPending}>
                                    {p.estado || 'PENDIENTE'}
                                  </span>
                                </td>
                                <td style={{ ...styles.td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                                  <button
                                    onClick={() => navigate('/planificaciones')}
                                    style={{
                                      backgroundColor: 'rgba(59, 130, 246, 0.15)',
                                      color: '#60a5fa',
                                      border: '1px solid rgba(59, 130, 246, 0.3)',
                                      borderRadius: '6px',
                                      padding: '5px 10px',
                                      fontSize: '12px',
                                      fontWeight: '600',
                                      cursor: 'pointer'
                                    }}
                                  >
                                    Evaluar →
                                  </button>
                                </td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                    )}
                  </div>
                </div>

              </>
            ) : (
              /* Vista Estudiante */
              <div style={styles.card}>
                <div style={styles.cardHeader}>
                  <div>
                    <h2 style={styles.cardTitle}>Mi Estado de Práctica Profesional</h2>
                    <p style={styles.cardSubtitle}>Seguimiento personal de tu práctica y entregas en tiempo real</p>
                  </div>
                </div>
                <div style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', background: 'rgba(59, 130, 246, 0.08)', padding: '16px', borderRadius: '12px', border: '1px solid rgba(59, 130, 246, 0.2)', marginBottom: '20px' }}>
                    <div style={{ fontSize: '28px' }}>🎓</div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: '600', color: '#ffffff', fontSize: '15px' }}>{displayName}</div>
                      <div style={{ fontSize: '13px', color: '#94a3b8' }}>Estudiante • {user?.email}</div>
                      {resumenAvance?.etapaActual && (
                        <div style={{ fontSize: '11.5px', color: '#93c5fd', marginTop: '3px' }}>
                          Etapa actual: {resumenAvance.etapaActual}
                        </div>
                      )}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                      <span style={{
                        backgroundColor: resumenAvance?.estadoGeneral === 'FINALIZADA' ? '#10b981' : resumenAvance?.estadoGeneral === 'EN_REVISION_FINAL' ? '#0284c7' : '#f59e0b',
                        color: '#ffffff',
                        padding: '4px 12px',
                        borderRadius: '9999px',
                        fontSize: '12px',
                        fontWeight: '600'
                      }}>
                        {resumenAvance ? resumenAvance.estadoGeneral.replace(/_/g, ' ') : (tieneInformeFinal ? 'Informe Entregado' : 'Informe Pendiente')}
                      </span>
                      {resumenAvance && (
                        <span style={{ fontSize: '11px', color: '#94a3b8', fontWeight: '500' }}>
                          Avance global: {resumenAvance.porcentajeAvance}%
                        </span>
                      )}
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', marginBottom: '20px' }}>
                    <div style={{ background: '#131e3a', padding: '16px', borderRadius: '10px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                      <div style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px' }}>Planificaciones de Clase</div>
                      <div style={{ fontSize: '14px', fontWeight: '600', color: '#f8fafc' }}>
                        {totalPlanificaciones === 0 ? 'Sin archivos subidos' : `${totalPlanificaciones} entregada(s)`}
                      </div>
                      <div style={{ fontSize: '11px', color: planificacionesAprobadas > 0 ? '#4ade80' : '#94a3b8', marginTop: '2px' }}>
                        {planificacionesAprobadas} aprobada(s) • {planificacionesPendientes} en revisión
                      </div>
                    </div>
                    <div style={{ background: '#131e3a', padding: '16px', borderRadius: '10px', border: '1px solid rgba(255, 255, 255, 0.08)' }}>
                      <div style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '6px' }}>Informe Final de Práctica</div>
                      <div style={{ fontSize: '14px', fontWeight: '600', color: tieneInformeFinal ? '#4ade80' : '#f8fafc' }}>
                        {tieneInformeFinal ? 'Entregado (PDF)' : 'Pendiente de entrega'}
                      </div>
                      <div style={{ fontSize: '11px', color: tieneInformeFinal ? '#86efac' : '#f59e0b', marginTop: '2px', wordBreak: 'break-all' }}>
                        {tieneInformeFinal ? `Archivo: ${informeEntregado?.nombreArchivo || 'Informe_Final.pdf'}` : 'Plazo máx: 30 de Noviembre'}
                      </div>
                      {tieneInformeFinal && (
                        <div style={{ marginTop: '10px' }}>
                          <a
                            href={informeEntregado?.archivoUrl || (informeEntregado?.id ? `/api/practicas/informes/${informeEntregado.id}/archivo` : '#')}
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '6px',
                              fontSize: '11.5px',
                              fontWeight: '600',
                              color: '#60a5fa',
                              textDecoration: 'none',
                              backgroundColor: 'rgba(59, 130, 246, 0.15)',
                              padding: '5px 10px',
                              borderRadius: '6px',
                              border: '1px solid rgba(59, 130, 246, 0.3)',
                              cursor: 'pointer'
                            }}
                          >
                            <ExternalLink size={13} /> Ver Mi Informe (PDF)
                          </a>
                        </div>
                      )}
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '12px' }}>
                    <button
                      onClick={() => navigate('/planificaciones')}
                      style={{ flex: 1, padding: '12px', backgroundColor: '#2563eb', color: '#ffffff', border: 'none', borderRadius: '8px', fontWeight: '600', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
                    >
                      <FileText size={16} /> Subir Planificación ({totalPlanificaciones})
                    </button>
                    <button
                      onClick={() => navigate('/entrega-informe')}
                      style={{ flex: 1, padding: '12px', backgroundColor: tieneInformeFinal ? '#059669' : '#334155', color: '#ffffff', border: 'none', borderRadius: '8px', fontWeight: '600', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}
                    >
                      <Upload size={16} /> {tieneInformeFinal ? 'Reemplazar / Actualizar Informe' : 'Entregar Informe Final'}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Sidebar Column */}
          <div style={styles.dashboardSidebar}>
            {isDocenteOrAdmin ? (
              <>
                {/* Stats Administrativas */}
                <div style={{ ...styles.statGrid, gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><Briefcase size={18} color="#60a5fa" /></div>
                    <div style={styles.statValue}>{totalOfertas}</div>
                    <div style={styles.statLabel}>Prácticas Ofrecidas</div>
                  </div>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><Users size={18} color="#22c55e" /></div>
                    <div style={styles.statValue}>{totalInscritosDocente}</div>
                    <div style={styles.statLabel}>Estudiantes Inscritos</div>
                  </div>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><FileText size={18} color="#f59e0b" /></div>
                    <div style={styles.statValue}>{totalPlanificaciones}</div>
                    <div style={styles.statLabel}>Planificaciones</div>
                  </div>
                </div>

                {/* Tareas y Alertas Académicas */}
                <div style={styles.card}>
                  <div style={styles.cardHeader}>
                    <h3 style={styles.cardTitle}>Supervisión y Tareas Clave</h3>
                  </div>
                  <div style={styles.cardBody}>
                    <div style={{ ...styles.recentDocItem, alignItems: 'flex-start' }}>
                      <FileText size={18} color="#f59e0b" style={{ marginTop: '2px' }} />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: '13px', fontWeight: '600', color: '#f8fafc' }}>
                          Planificaciones Recientes
                        </div>
                        <div style={{ fontSize: '11.5px', color: '#cbd5e1', marginTop: '2px' }}>
                          Supervisión y retroalimentación de actividades docentes
                        </div>
                      </div>
                    </div>

                    <div style={{ ...styles.recentDocItem, alignItems: 'flex-start' }}>
                      <GraduationCap size={18} color="#60a5fa" style={{ marginTop: '2px' }} />
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: '13px', fontWeight: '600', color: '#f8fafc' }}>
                          Informes Finales y Evaluaciones
                        </div>
                        <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '2px' }}>
                          Revisión de informes docentes y evaluaciones semestrales
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </>
            ) : (
              <>
                {/* Stats Estudiante */}
                <div style={styles.statGrid}>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><Clock size={18} color="#60a5fa" /></div>
                    <div style={styles.statValue}>
                      {resumenAvance ? `${Math.round((resumenAvance.porcentajeAvance * 360) / 100)}h` : '360h'}
                    </div>
                    <div style={styles.statLabel}>Horas Registradas</div>
                  </div>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><CheckCircle size={18} color={resumenAvance?.porcentajeAvance === 100 ? '#22c55e' : '#3b82f6'} /></div>
                    <div style={styles.statValue}>
                      {resumenAvance ? `${resumenAvance.porcentajeAvance}%` : '100%'}
                    </div>
                    <div style={styles.statLabel}>Avance de Práctica</div>
                  </div>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><FileText size={18} color="#f59e0b" /></div>
                    <div style={styles.statValue}>{totalPlanificaciones}</div>
                    <div style={styles.statLabel}>Planificaciones</div>
                  </div>
                  <div style={styles.statCard}>
                    <div style={styles.statIcon}><GraduationCap size={18} color={tareasPendientes.length === 0 ? '#22c55e' : '#f59e0b'} /></div>
                    <div style={styles.statValue}>{tareasPendientes.length}</div>
                    <div style={styles.statLabel}>{tareasPendientes.length === 0 ? 'Al Día' : 'Tareas Pendientes'}</div>
                  </div>
                </div>

                {/* Tareas, Documentos y Evaluaciones Pendientes */}
                <div style={styles.card}>
                  <div style={styles.cardHeader}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                      <h3 style={styles.cardTitle}>Tareas y Acciones Pendientes</h3>
                      <span style={{
                        fontSize: '11px',
                        fontWeight: '600',
                        padding: '3px 8px',
                        borderRadius: '9999px',
                        backgroundColor: tareasPendientes.length > 0 ? 'rgba(239, 68, 68, 0.2)' : 'rgba(34, 197, 94, 0.2)',
                        color: tareasPendientes.length > 0 ? '#f87171' : '#4ade80'
                      }}>
                        {tareasPendientes.length} pendientes
                      </span>
                    </div>
                  </div>
                  <div style={styles.cardBody}>
                    {tareasPendientes.length === 0 ? (
                      <div style={{ padding: '20px', textAlign: 'center', color: '#4ade80', fontSize: '13px' }}>
                        🎉 ¡Excelente! No tienes tareas ni evaluaciones pendientes. Tu práctica está al día.
                      </div>
                    ) : (
                      tareasPendientes.map((tarea) => {
                        const isCritica = tarea.prioridad === 'CRITICA';
                        const isAlta = tarea.prioridad === 'ALTA';
                        const badgeColor = isCritica ? '#ef4444' : isAlta ? '#f59e0b' : '#3b82f6';

                        return (
                          <div key={tarea.id} style={{ ...styles.recentDocItem, alignItems: 'flex-start', borderLeft: `3px solid ${badgeColor}`, paddingLeft: '10px', marginBottom: '12px' }}>
                            <div style={{ flex: 1 }}>
                              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                                <div style={{ fontSize: '13px', fontWeight: '600', color: '#f8fafc' }}>
                                  {tarea.titulo}
                                </div>
                                <span style={{
                                  fontSize: '10px',
                                  fontWeight: '700',
                                  padding: '2px 6px',
                                  borderRadius: '4px',
                                  backgroundColor: badgeColor,
                                  color: '#ffffff'
                                }}>
                                  {tarea.prioridad}
                                </span>
                              </div>
                              <div style={{ fontSize: '11.5px', color: '#cbd5e1', marginTop: '3px', lineHeight: '1.4' }}>
                                {tarea.descripcion}
                              </div>
                              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '8px', fontSize: '11px', color: '#94a3b8' }}>
                                <span>📅 Plazo: <strong style={{ color: '#f1f5f9' }}>{tarea.fechaLimite}</strong></span>
                                {tarea.urlAccion && (
                                  <button
                                    onClick={() => navigate(tarea.urlAccion!)}
                                    style={{
                                      backgroundColor: 'rgba(59, 130, 246, 0.15)',
                                      color: '#60a5fa',
                                      border: '1px solid rgba(59, 130, 246, 0.3)',
                                      borderRadius: '4px',
                                      padding: '4px 8px',
                                      fontSize: '11px',
                                      fontWeight: '600',
                                      cursor: 'pointer'
                                    }}
                                  >
                                    {tarea.accionRequerida || 'Completar →'}
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </main>



      {/* Modal: Inscribir Alumno */}
      {isNewStudentModalOpen && (
        <div style={styles.modalOverlay}>
          <div style={{ ...styles.modalContent, maxWidth: '480px' }}>
            <div style={styles.modalHeader}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <UserPlus size={22} color="#60a5fa" />
                <h3 style={styles.modalTitle}>Inscribir Alumno en Práctica</h3>
              </div>
              <button onClick={() => setIsNewStudentModalOpen(false)} style={styles.modalCloseBtn}>
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateStudent}>
              <div style={styles.modalBody}>
                <div style={{ marginBottom: '14px' }}>
                  <label style={styles.formLabel}>Nombre Completo del Estudiante *</label>
                  <input
                    type="text"
                    required
                    value={newStudentName}
                    onChange={(e) => setNewStudentName(e.target.value)}
                    placeholder="Ej. Constanza Silva Morales"
                    style={styles.formInput}
                  />
                </div>

                <div style={{ marginBottom: '14px' }}>
                  <label style={styles.formLabel}>ID / Rol Estudiantil *</label>
                  <input
                    type="text"
                    required
                    value={newStudentID}
                    onChange={(e) => setNewStudentID(e.target.value)}
                    placeholder="Ej. 2022-8812"
                    style={styles.formInput}
                  />
                </div>

                <div style={{ marginBottom: '14px' }}>
                  <label style={styles.formLabel}>Correo Institucional</label>
                  <input
                    type="email"
                    value={newStudentEmail}
                    onChange={(e) => setNewStudentEmail(e.target.value)}
                    placeholder="Ej. csilva@alumnos.ubiobio.cl"
                    style={styles.formInput}
                  />
                </div>

                <div style={{ marginBottom: '14px' }}>
                  <label style={styles.formLabel}>Profesor Guía Asignado</label>
                  <select
                    value={newStudentProf}
                    onChange={(e) => setNewStudentProf(e.target.value)}
                    style={styles.formInput}
                  >
                    <option value="Prof. Juan Pérez">Prof. Juan Pérez</option>
                    <option value="Prof. Ricardo Tapia">Prof. Ricardo Tapia</option>
                    <option value="Prof. Sandra Muñoz">Prof. Sandra Muñoz</option>
                    <option value="Prof. Boris Arenas">Prof. Boris Arenas</option>
                  </select>
                </div>
              </div>

              <div style={styles.modalFooter}>
                <button type="button" onClick={() => setIsNewStudentModalOpen(false)} style={styles.modalSecondaryBtn}>
                  Cancelar
                </button>
                <button type="submit" style={styles.modalPrimaryBtn}>
                  Inscribir Alumno
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Floating Chatbot Assistant */}
      <div style={styles.chatbotWrapper}>
        {chatOpen ? (
          <div style={styles.chatWindow}>
            <div style={styles.chatHeader}>
              <div style={styles.chatHeaderInfo}>
                <Bot size={20} color="#60a5fa" />
                <div>
                  <div style={styles.chatTitle}>Asistente Virtual UBB</div>
                  <div style={styles.chatStatus}>En línea • Respuestas automáticas</div>
                </div>
              </div>
              <button onClick={() => setChatOpen(false)} style={styles.chatCloseBtn}>
                <X size={16} />
              </button>
            </div>
            <div style={styles.chatBody}>
              <div style={styles.chatMsgBot}>
                ¡Hola {displayName}! Soy el asistente del sistema DocHub UBB. ¿En qué puedo ayudarte hoy?
              </div>
              <div style={styles.quickQuestions}>
                <button onClick={() => showFeedback('Plazo de informes: 30 de Noviembre')} style={styles.quickQBtn}>
                  ¿Cuál es el plazo del informe?
                </button>
                <button onClick={() => showFeedback('Requisitos: 360 hrs y planificaciones al día')} style={styles.quickQBtn}>
                  Requisitos de aprobación
                </button>
              </div>
            </div>
          </div>
        ) : (
          <button onClick={() => setChatOpen(true)} style={styles.chatFloatingBtn} title="Abrir Asistente">
            <MessageCircle size={24} />
          </button>
        )}
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    backgroundColor: '#0b1329',
    minHeight: '100vh',
    color: '#f8fafc',
    fontFamily: "'Inter', sans-serif",
  },
  main: {
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '32px 24px 80px',
  },
  toast: {
    position: 'fixed',
    bottom: '24px',
    left: '50%',
    transform: 'translateX(-50%)',
    backgroundColor: '#0f2240',
    border: '1px solid rgba(59, 130, 246, 0.4)',
    color: '#f8fafc',
    padding: '10px 20px',
    borderRadius: '9999px',
    boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    zIndex: 9999,
    fontSize: '13px',
    fontWeight: '500',
  },
  hero: {
    marginBottom: '28px',
  },
  heroContent: {},
  badgeHero: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '8px',
    backgroundColor: 'rgba(29, 78, 216, 0.15)',
    border: '1px solid rgba(29, 78, 216, 0.3)',
    color: '#93c5fd',
    fontSize: '12px',
    fontWeight: '600',
    padding: '4px 12px',
    borderRadius: '9999px',
    marginBottom: '12px',
  },
  dot: {
    width: '6px',
    height: '6px',
    borderRadius: '50%',
    backgroundColor: '#3b82f6',
  },
  heroTitle: {
    fontSize: '28px',
    fontWeight: '800',
    color: '#ffffff',
    margin: '0 0 8px 0',
  },
  highlight: {
    color: '#60a5fa',
  },
  heroSubtitle: {
    fontSize: '14px',
    color: '#94a3b8',
    margin: 0,
    maxWidth: '650px',
    lineHeight: 1.5,
  },
  searchWrapper: {
    marginBottom: '24px',
  },
  searchBar: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    backgroundColor: '#101d3a',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    padding: '12px 18px',
  },
  searchInput: {
    background: 'transparent',
    border: 'none',
    outline: 'none',
    color: '#ffffff',
    fontSize: '14px',
    width: '100%',
  },
  quickAccess: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
    gap: '16px',
    marginBottom: '28px',
  },
  quickCard: {
    padding: '18px 20px',
    borderRadius: '14px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    textAlign: 'left',
    cursor: 'pointer',
    transition: 'transform 0.2s, background-color 0.2s',
  },
  quickCardBlue: {
    backgroundColor: 'rgba(29, 78, 216, 0.12)',
    borderColor: 'rgba(59, 130, 246, 0.3)',
    color: '#60a5fa',
  },
  quickCardGold: {
    backgroundColor: 'rgba(212, 175, 82, 0.12)',
    borderColor: 'rgba(212, 175, 82, 0.3)',
    color: '#e0c068',
  },
  quickCardTeal: {
    backgroundColor: 'rgba(20, 184, 166, 0.12)',
    borderColor: 'rgba(20, 184, 166, 0.3)',
    color: '#2dd4bf',
  },
  quickCardPurple: {
    backgroundColor: 'rgba(168, 85, 247, 0.12)',
    borderColor: 'rgba(168, 85, 247, 0.3)',
    color: '#c084fc',
  },
  quickCardTitle: {
    fontSize: '14px',
    fontWeight: '700',
    color: '#ffffff',
    marginTop: '10px',
  },
  quickCardDesc: {
    fontSize: '12px',
    color: '#94a3b8',
    marginTop: '3px',
  },
  dashboardGrid: {
    display: 'grid',
    gridTemplateColumns: 'minmax(0, 1fr) 340px',
    gap: '24px',
    alignItems: 'start',
  },
  dashboardMain: {
    minWidth: 0,
    width: '100%',
  },
  dashboardSidebar: {
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
    minWidth: 0,
    width: '100%',
  },
  card: {
    backgroundColor: '#101d3a',
    borderRadius: '16px',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    overflow: 'hidden',
    width: '100%',
  },
  cardHeader: {
    padding: '18px 20px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  cardTitle: {
    fontSize: '16px',
    fontWeight: '700',
    color: '#ffffff',
    margin: 0,
  },
  cardSubtitle: {
    fontSize: '12px',
    color: '#94a3b8',
    margin: '3px 0 0 0',
  },
  cardBody: {
    padding: '0',
    overflowX: 'auto',
    width: '100%',
  },
  badgeBlue: {
    backgroundColor: 'rgba(59, 130, 246, 0.2)',
    color: '#60a5fa',
    padding: '4px 10px',
    borderRadius: '9999px',
    fontSize: '11px',
    fontWeight: '600',
  },
  periodBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    color: '#cbd5e1',
    padding: '3px 8px',
    borderRadius: '4px',
    fontSize: '11px',
    fontWeight: '600',
  },
  tabContainer: {
    display: 'flex',
    gap: '8px',
    padding: '12px 20px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
  },
  tabBtn: {
    background: 'none',
    border: 'none',
    color: '#94a3b8',
    padding: '6px 12px',
    borderRadius: '6px',
    fontSize: '12px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  tabBtnActive: {
    backgroundColor: 'rgba(59, 130, 246, 0.2)',
    color: '#60a5fa',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    textAlign: 'left',
  },
  trHead: {
    borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
  },
  th: {
    padding: '12px 18px',
    fontSize: '11px',
    fontWeight: '700',
    color: '#94a3b8',
    textTransform: 'uppercase',
  },
  trBody: {
    borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
  },
  td: {
    padding: '12px 18px',
    fontSize: '13px',
  },
  studentAvatar: {
    width: '32px',
    height: '32px',
    borderRadius: '50%',
    backgroundColor: '#1d4ed8',
    color: '#ffffff',
    fontWeight: '700',
    fontSize: '12px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  statusApproved: {
    backgroundColor: 'rgba(34, 197, 94, 0.15)',
    color: '#4ade80',
    padding: '3px 8px',
    borderRadius: '6px',
    fontSize: '11px',
    fontWeight: '600',
  },
  statusPending: {
    backgroundColor: 'rgba(245, 158, 11, 0.15)',
    color: '#fbbf24',
    padding: '3px 8px',
    borderRadius: '6px',
    fontSize: '11px',
    fontWeight: '600',
  },
  statusReview: {
    backgroundColor: 'rgba(168, 85, 247, 0.15)',
    color: '#c084fc',
    padding: '3px 8px',
    borderRadius: '6px',
    fontSize: '11px',
    fontWeight: '600',
  },
  statGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
    gap: '12px',
  },
  statCard: {
    backgroundColor: '#101d3a',
    borderRadius: '14px',
    padding: '12px 10px',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    minWidth: 0,
  },
  statIcon: {
    marginBottom: '8px',
  },
  statValue: {
    fontSize: '18px',
    fontWeight: '800',
    color: '#ffffff',
  },
  statLabel: {
    fontSize: '10.5px',
    color: '#94a3b8',
    marginTop: '2px',
    lineHeight: 1.25,
    wordBreak: 'break-word',
  },
  recentDocItem: {
    padding: '12px 16px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
    display: 'flex',
    gap: '12px',
  },
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    backdropFilter: 'blur(4px)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    padding: '20px',
  },
  modalContent: {
    backgroundColor: '#0f1d38',
    border: '1px solid rgba(255, 255, 255, 0.15)',
    borderRadius: '16px',
    width: '100%',
    maxWidth: '560px',
    overflow: 'hidden',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.8)',
  },
  modalHeader: {
    padding: '16px 20px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  modalTitle: {
    fontSize: '16px',
    fontWeight: '700',
    color: '#ffffff',
    margin: 0,
  },
  modalCloseBtn: {
    background: 'none',
    border: 'none',
    color: '#94a3b8',
    cursor: 'pointer',
    padding: '4px',
  },
  modalBody: {
    padding: '20px',
  },
  modalFooter: {
    padding: '14px 20px',
    borderTop: '1px solid rgba(255, 255, 255, 0.08)',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    display: 'flex',
    justifyContent: 'flex-end',
    gap: '10px',
  },
  modalSecondaryBtn: {
    backgroundColor: '#1e293b',
    color: '#cbd5e1',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '8px',
    padding: '8px 16px',
    fontSize: '13px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  modalPrimaryBtn: {
    backgroundColor: '#2563eb',
    color: '#ffffff',
    border: 'none',
    borderRadius: '8px',
    padding: '8px 16px',
    fontSize: '13px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  formLabel: {
    display: 'block',
    fontSize: '12px',
    fontWeight: '600',
    color: '#cbd5e1',
    marginBottom: '6px',
  },
  formInput: {
    width: '100%',
    backgroundColor: '#0b1329',
    border: '1px solid rgba(255, 255, 255, 0.15)',
    borderRadius: '8px',
    padding: '10px 12px',
    color: '#ffffff',
    fontSize: '13px',
    outline: 'none',
    boxSizing: 'border-box',
  },
  chatbotWrapper: {
    position: 'fixed',
    bottom: '24px',
    right: '24px',
    zIndex: 999,
  },
  chatFloatingBtn: {
    width: '54px',
    height: '54px',
    borderRadius: '50%',
    backgroundColor: '#2563eb',
    color: '#ffffff',
    border: 'none',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    cursor: 'pointer',
    boxShadow: '0 8px 24px rgba(37, 99, 235, 0.5)',
  },
  chatWindow: {
    width: '320px',
    height: '380px',
    backgroundColor: '#0f1d38',
    border: '1px solid rgba(255, 255, 255, 0.15)',
    borderRadius: '16px',
    boxShadow: '0 20px 40px rgba(0, 0, 0, 0.7)',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
  },
  chatHeader: {
    padding: '12px 16px',
    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
  },
  chatHeaderInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
  },
  chatTitle: {
    fontSize: '13px',
    fontWeight: '700',
    color: '#ffffff',
  },
  chatStatus: {
    fontSize: '10px',
    color: '#4ade80',
  },
  chatCloseBtn: {
    background: 'none',
    border: 'none',
    color: '#94a3b8',
    cursor: 'pointer',
  },
  chatBody: {
    padding: '16px',
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
  },
  chatMsgBot: {
    backgroundColor: 'rgba(59, 130, 246, 0.15)',
    border: '1px solid rgba(59, 130, 246, 0.3)',
    borderRadius: '10px',
    padding: '12px',
    fontSize: '12.5px',
    color: '#e2e8f0',
    lineHeight: 1.4,
  },
  quickQuestions: {
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  quickQBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '8px',
    padding: '8px 10px',
    color: '#93c5fd',
    fontSize: '11.5px',
    textAlign: 'left',
    cursor: 'pointer',
  },
}
