import { useId } from 'react';
import { MapPin, Route } from 'lucide-react';
import { shelfCode } from '../services/format';

export default function WarehouseRoute({ destination, zones }) {
  const heading = useId();
  const destinationRow = zones.findIndex(([zone]) => zone === destination.zone);

  return <section className="warehouse-route" aria-labelledby={heading}>
    <div className="route-guide">
      <div className="route-heading">
        <span><Route size={19} aria-hidden="true" /></span>
        <div><h3 id={heading}>Ruta al estante</h3><strong>{shelfCode(destination)}</strong></div>
      </div>
      <ol className="route-steps">
        <li>Empieza en <strong>Recepción</strong>.</li>
        <li>Sigue el corredor hasta <strong>Zona {destination.zone}</strong>.</li>
        <li>Entra al <strong>pasillo {destination.aisle}</strong> y busca el <strong>estante {destination.shelf}</strong>.</li>
      </ol>
    </div>
    <div className="route-plan" aria-hidden="true">
      <div className="route-start"><i />Recepción</div>
      {zones.map(([zone, shelves], row) => {
        const destinationColumn = shelves.findIndex((shelf) => shelf.id === destination.id);
        return <div className={`route-plan-row ${row <= destinationRow ? 'route-traversed' : ''} ${row === destinationRow ? 'route-target-row' : ''}`} key={zone}>
          <span className="route-junction"><i /></span>
          <span className="route-zone"><span>Zona {zone}</span></span>
          <div className="route-shelf-row">
            {shelves.map((shelf, column) => <span key={shelf.id}
              className={`route-shelf ${column < destinationColumn ? 'route-segment' : ''} ${column === destinationColumn ? 'route-end' : ''}`}>
              {column === destinationColumn ? <MapPin size={17} strokeWidth={2.5} /> : <i />}
            </span>)}
          </div>
        </div>;
      })}
    </div>
  </section>;
}
