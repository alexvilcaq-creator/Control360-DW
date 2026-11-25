import { useState } from 'react';
import { ArrowDownToLine, ArrowUpRight, Boxes, MapPin, Package, Search, Truck, Warehouse as WarehouseIcon } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { Empty, ErrorNotice, Loading, PageTitle, Section, Tag, TextLink } from '../components/ui';
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
  const matches = (products.data || []).filter((item) => normalize(item.name + ' ' + item.sku).includes(normalize(query).trim()));
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
            <div className="warehouse-zones">{Array.from(groups).map(([zone, cells]) =>
              <div className="map-zone" key={zone}><span>Zona {zone}</span><div>
                {[...cells].sort((a, b) => a.aisle.localeCompare(b.aisle, undefined, { numeric: true }) || a.shelf.localeCompare(b.shelf, undefined, { numeric: true })).map((item) =>
                  <button key={item.id} type="button" className={`map-cell ${selected === item.id ? 'selected' : ''} ${query.trim() && highlightIds.has(item.id) ? 'matching' : ''}`}
                    aria-pressed={selected === item.id} onClick={() => setSearch(selected === item.id ? {} : { ubicacion: item.id })}>
                    <Boxes size={18} /><strong>{zone}{item.aisle} · E{item.shelf}</strong><small>Pasillo {item.aisle}</small>
                  </button>)}
              </div></div>)}</div>
            <div className="map-terminal"><ArrowDownToLine size={21} /> Despacho</div>
          </div> : <Empty>Aún no hay ubicaciones registradas.</Empty>}
        {query.trim() && <div className="map-legend"><i />{highlightIds.size} ubicaciones coinciden con la búsqueda</div>}
      </Section>
      <Section title={active ? `Zona ${active.zone} · Estante ${active.shelf}` : 'Productos en el plano'} icon={MapPin} caption={active ? locationLabel(active) : 'Existencias por ubicación'}>
        {products.loading && !products.data ? <Loading /> : shown.length ? <div className="map-products">
          {shown.map((item) => <Link key={item.id} to={`/inventario?q=${encodeURIComponent(item.sku)}`}>
            <span className="product-tile"><Package size={17} /></span>
            <div><strong>{item.name}</strong><small>{locationLabel(locations.data?.find((l) => l.id === item.locationId))}</small></div>
            <Tag tone={item.quantity <= item.minStock ? 'red' : 'blue'}>{item.quantity} u.</Tag><ArrowUpRight size={15} />
          </Link>)}
        </div> : <Empty>No hay productos en esta selección.</Empty>}
      </Section>
    </div>
  </>;
}
