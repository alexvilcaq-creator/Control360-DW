import { useState } from 'react';
import { ArrowDownToLine, ArrowUpRight, MapPin, Search, Truck, X, Warehouse as WarehouseIcon } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { Empty, ErrorNotice, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
import ProductPhoto from '../components/ProductPhoto';
import { useAuth } from '../context/AuthContext';
import { useResource } from '../hooks/useResource';
import { locationLabel, normalize } from '../services/format';

export default function Warehouse() {
  const { user } = useAuth();
  const locations = useResource('/ubicaciones');
  const products = useResource('/productos', { stock: 'activo' });
  const [searchParams, setSearch] = useSearchParams();
  const [query, setQuery] = useState('');
  const selected = Number(searchParams.get('ubicacion')) || null;
  const groups = new Map();
  for (const item of locations.data || []) {
    if (!groups.has(item.zone)) groups.set(item.zone, []);
    groups.get(item.zone).push(item);
  }
  const search = normalize(query).trim();
  const counts = new Map();
  for (const product of products.data || []) {
    counts.set(product.locationId, (counts.get(product.locationId) || 0) + 1);
  }
  const matches = (products.data || []).filter((item) => normalize(item.name + ' ' + item.sku).includes(search));
  const highlightIds = new Set(matches.map((item) => item.locationId));
  const shown = selected ? matches.filter((item) => item.locationId === selected) : matches;
  const active = locations.data?.find((item) => item.id === selected);
  return <>
    <PageTitle title="Mapa del almacén" subtitle="Ubica productos y consulta existencias por zona, pasillo y estante.">
      {user.role === 'ADMIN' && <TextLink to="/ubicaciones">Gestionar ubicaciones</TextLink>}
    </PageTitle>
    <ErrorNotice message={locations.error || products.error} retry={() => { locations.reload(); products.reload(); }} />
    <div className="warehouse-grid">
      <Section title="Plano de ubicaciones" icon={WarehouseIcon} caption="Selecciona un estante para ver su contenido.">
        <label className="input-icon map-search"><Search size={17} /><input aria-label="Buscar en el mapa" placeholder="Buscar un producto por nombre o código" value={query} onChange={(event) => { setQuery(event.target.value); setSearch({}); }} /></label>
        {locations.loading && !locations.data ? <Loading /> : groups.size ?
          <div className="warehouse-floor">
            <div className="map-terminal"><Truck size={21} /> Recepción</div>
            <div className="warehouse-zones">{Array.from(groups).map(([zone, cells], index) =>
              <div className="map-zone" key={zone}><span>Zona {zone}</span><div>
                {[...cells].sort((a, b) => a.aisle.localeCompare(b.aisle, undefined, { numeric: true }) || a.shelf.localeCompare(b.shelf, undefined, { numeric: true })).map((item) =>
                  <button key={item.id} type="button" className={`map-cell ${selected === item.id ? 'selected' : ''} ${search && highlightIds.has(item.id) ? 'matching' : ''}`}
                    aria-pressed={selected === item.id} onClick={() => setSearch(selected === item.id ? {} : { ubicacion: item.id })}>
                    <img src={`/images/rack-${index % 3 + 1}.webp`} alt="" width="78" height="58" loading="lazy" />
                    <span className="map-cell-info"><strong>{zone}{item.aisle} · E{item.shelf}</strong><small>Pasillo {item.aisle}</small></span>
                    {products.data && <span className="map-cell-count" aria-label={`${counts.get(item.id) || 0} productos`}>{counts.get(item.id) || 0}</span>}
                  </button>)}
              </div></div>)}</div>
            <div className="map-terminal"><ArrowDownToLine size={21} /> Despacho</div>
          </div> : <Empty>Aún no hay ubicaciones registradas.</Empty>}
        {search && <div className="map-legend" role="status"><i />{highlightIds.size} ubicaciones coinciden con la búsqueda</div>}
      </Section>
      <Section title={active ? `Zona ${active.zone} · Estante ${active.shelf}` : 'Productos en el plano'} icon={MapPin} caption={active ? locationLabel(active) : 'Existencias por ubicación'}
        action={active && <button type="button" className="icon-button" aria-label="Ver todas las ubicaciones" onClick={() => setSearch({})}><X size={18} /></button>}>
        {products.loading && !products.data ? <Loading /> : shown.length ? <div className="map-products">
          {shown.map((item) => <Link key={item.id} to={`/inventario?q=${encodeURIComponent(item.sku)}`}>
            <ProductPhoto product={item} />
            <div><strong>{item.name}</strong><small>{locationLabel(locations.data?.find((l) => l.id === item.locationId))}</small></div>
            <Tag tone={item.quantity <= item.minStock ? 'red' : 'blue'}>{item.quantity} u.</Tag><ArrowUpRight size={15} />
          </Link>)}
        </div> : <Empty>No hay productos en esta selección.</Empty>}
      </Section>
    </div>
  </>;
}
