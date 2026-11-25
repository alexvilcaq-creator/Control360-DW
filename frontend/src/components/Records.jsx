import { useState } from 'react';
import { Plus, Search } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { Button, DataTable, Dialog, ErrorNotice, Field, FormSubmit, Loading, PageTitle, Section } from './ui';
import { useResource } from '../hooks/useResource';
import { post, put } from '../services/api';
import { normalize } from '../services/format';

export default function Records({ title, subtitle, singular, icon, endpoint, columns, fields, searchBy, unpack = (item) => item, dependencies = [], children }) {
  const resource = useResource(endpoint);
  const [params, setParams] = useSearchParams();
  const [edit, setEdit] = useState(null);
  const query = params.get('q') || '';
  const filtered = resource.data?.filter((item) => normalize(searchBy(item)).includes(normalize(query).trim()));
  const noData = resource.loading && !resource.data;
  const formReady = dependencies.every((item) => !item.loading && !item.error);
  const columnsWithAction = [...columns, { key: 'edit', label: '', align: 'right', render: (row) =>
    <Button variant="quiet" disabled={!formReady} onClick={() => setEdit(unpack(row))}>Editar</Button> }];
  function search(value) { setParams(value ? { q: value } : {}, { replace: true }); }

  async function save(values) {
    const body = {};
    for (const field of fields) {
      const value = values.get(field.name);
      body[field.name] = field.type === 'checkbox' ? values.has(field.name)
        : field.numeric || field.type === 'number' ? value === '' ? null : Number(value) : value;
    }
    if (edit.id) await put(`${endpoint}/${edit.id}`, body); else await post(endpoint, body);
    setEdit(null); resource.reload();
  }

  return <>
    <PageTitle title={title} subtitle={subtitle}><Button icon={Plus} disabled={!formReady} onClick={() => setEdit({})}>Crear {singular}</Button></PageTitle>
    <ErrorNotice message={resource.error || dependencies.find((item) => item.error)?.error} retry={resource.reload} />
    {children}
    <Section title={title} icon={icon} action={<span className="result-count">{filtered?.length || 0} registros</span>}>
      <div className="filters"><label className="input-icon"><Search size={17} /><input aria-label={`Buscar en ${title}`} placeholder="Buscar registros…" value={query} onChange={(event) => search(event.target.value)} /></label></div>
      {noData ? <Loading /> : <DataTable rows={filtered} columns={columnsWithAction} empty="No hay registros que coincidan con la búsqueda." />}
    </Section>
    {edit && <Dialog title={edit.id ? `Editar ${singular}` : `Crear ${singular}`} onClose={() => setEdit(null)} wide={fields.length > 3}>
      <FormSubmit onSubmit={save} onClose={() => setEdit(null)}>
        <div className={fields.length > 3 ? 'form-grid' : ''}>{fields.map((field) => {
          const value = edit[field.name] ?? field.defaultValue ?? '';
          if (field.type === 'checkbox') return <label className="checkbox-row" key={field.name}><input name={field.name} type="checkbox" defaultChecked={Boolean(value)} />{field.label}</label>;
          return <Field label={field.label} hint={field.hint} key={field.name}>
            {field.options ? <select name={field.name} defaultValue={value} required={field.required}>
              <option value="">{field.required ? 'Seleccionar' : 'Sin asignar'}</option>
              {field.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select> : <input name={field.name} type={field.type || 'text'} defaultValue={value} min={field.min} max={field.max}
              minLength={field.minLength} maxLength={field.maxLength || 100} pattern={field.pattern}
              autoComplete={field.type === 'password' ? 'new-password' : undefined}
              required={field.required || (field.requiredOnCreate && !edit.id)} />}
          </Field>;
        })}</div>
      </FormSubmit>
    </Dialog>}
  </>;
}
