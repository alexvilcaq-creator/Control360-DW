import { useState } from 'react';
import { ClipboardList, Plus } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { Button, DataTable, Dialog, ErrorNotice, Field, FormSubmit, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { makeRequestKey, post, put } from '../services/api';
import { dateLabel, timeLabel } from '../services/format';

const types = {
  PRODUCTO_AGOTADO: 'Producto agotado', PRODUCTO_DANADO: 'Producto dañado',
  DIFERENCIA_INVENTARIO: 'Diferencia de inventario', PRODUCTO_NO_ENCONTRADO: 'Producto no encontrado',
  UBICACION_INCORRECTA: 'Ubicación incorrecta', OTRO: 'Otro problema',
};
const states = { PENDIENTE: 'Pendiente', EN_REVISION: 'En revisión', RESUELTA: 'Resuelta', RECHAZADA: 'Rechazada' };
const tones = { PENDIENTE: 'amber', EN_REVISION: 'teal', RESUELTA: 'green', RECHAZADA: 'red' };
const availableTransitions = { PENDIENTE: ['EN_REVISION', 'RESUELTA', 'RECHAZADA'], EN_REVISION: ['RESUELTA', 'RECHAZADA'] };

export default function Incidents() {
  const { user } = useAuth();
  const admin = user.role === 'ADMIN';
  const [params, setParams] = useSearchParams();
  const [filter, setFilter] = useState('');
  const [active, setActive] = useState(null);
  const incidents = useResource('/incidencias', {}, 20000);
  const rows = (incidents.data || []).filter((item) => !filter || item.status === filter);

  function close() {
    if (params.has('nueva')) setParams({}, { replace: true });
    setActive(null);
  }
  function saved() {
    close();
    incidents.reload();
  }

  return <>
    <PageTitle title={admin ? 'Incidencias de almacén' : 'Mis incidencias'} subtitle="Consulta los problemas reportados y su estado de revisión.">
      {!admin && <Button icon={Plus} onClick={() => setParams({ nueva: 1 })}>Reportar incidencia</Button>}
    </PageTitle>
    <ErrorNotice message={incidents.error} retry={incidents.reload} />
    <Section title="Incidencias" icon={ClipboardList} className="table-panel" action={<span className="result-count">{rows.length} registros</span>}>
      <div className="filters"><select aria-label="Filtrar incidencias por estado" value={filter} onChange={(event) => setFilter(event.target.value)}>
        <option value="">Todos los estados</option>
        {Object.entries(states).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></div>
      {incidents.loading && !incidents.data ? <Loading /> : <DataTable rows={rows} empty="No hay incidencias para esta selección." columns={[
        { key: 'product', label: 'Producto', render: (item) => <div className="cell-stack"><strong>{item.productName}</strong><small>{item.sku}</small></div> },
        { key: 'type', label: 'Problema', render: (item) => types[item.type] },
        ...(admin ? [{ key: 'reporter', label: 'Reportado por' }] : []),
        { key: 'date', label: 'Fecha', render: (item) => dateLabel(item.createdAt) + ' · ' + timeLabel(item.createdAt) },
        { key: 'status', label: 'Estado', render: (item) => <Tag tone={tones[item.status]} dot>{states[item.status]}</Tag> },
        { key: 'action', label: '', align: 'right', render: (item) => <Button variant="quiet" onClick={() => setActive(item)}>{admin && availableTransitions[item.status] ? 'Revisar' : 'Ver detalle'}</Button> },
      ]} />}
    </Section>
    {!admin && params.has('nueva') && <ReportForm initialProduct={params.get('producto') || ''} close={close} saved={saved} />}
    {active && <IncidentDetail item={active} admin={admin} close={close} saved={saved} />}
  </>;
}

function ReportForm({ initialProduct, close, saved }) {
  const products = useResource('/productos', { stock: 'activo' });
  const [productId, setProductId] = useState(initialProduct);
  const [requestKey] = useState(makeRequestKey);
  return <Dialog title="Reportar incidencia" onClose={close}>
    <ErrorNotice message={products.error} retry={products.reload} />
    <FormSubmit onClose={close} label="Enviar reporte" onSubmit={async (values) => {
      await post('/incidencias', { productId: Number(productId), type: values.get('type'), observation: values.get('observation'), requestKey });
      saved();
    }}>
      <Field label="Producto"><select required value={productId} onChange={(event) => setProductId(event.target.value)}>
        <option value="">Selecciona un producto</option>
        {products.data?.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.sku}</option>)}
      </select></Field>
      <Field label="Tipo de incidencia"><select name="type" required><option value="">Selecciona un tipo</option>
        {Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></Field>
      <Field label="Observación"><textarea name="observation" rows={3} maxLength={500} required /></Field>
    </FormSubmit>
  </Dialog>;
}

function IncidentDetail({ item, admin, close, saved }) {
  const transitions = availableTransitions[item.status];
  return <Dialog title={`Incidencia #${item.id}`} onClose={close}>
    <div className="detail-summary">
      <div><strong>{item.productName}</strong><small>{types[item.type]} · {item.reporter}</small></div>
      <Tag tone={tones[item.status]} dot>{states[item.status]}</Tag>
    </div>
    <p className="incident-observation">{item.observation}</p>
    {item.reviewNote && <div className="incident-review"><strong>Nota de revisión · {item.reviewer}</strong><p>{item.reviewNote}</p></div>}
    {admin && <TextLink to={`/inventario?q=${encodeURIComponent(item.sku)}`}>Consultar inventario</TextLink>}
    {admin && transitions && <div className="section-gap">
      <FormSubmit onClose={close} label="Guardar revisión" onSubmit={async (values) => {
        await put(`/incidencias/${item.id}`, { status: values.get('status'), note: values.get('note') }); saved();
      }}>
        <Field label="Nuevo estado"><select name="status" required><option value="">Selecciona un estado</option>
          {transitions.map((value) => <option key={value} value={value}>{states[value]}</option>)}
        </select></Field>
        <Field label="Nota de revisión"><textarea required name="note" rows={3} maxLength={500} /></Field>
      </FormSubmit>
    </div>}
  </Dialog>;
}
