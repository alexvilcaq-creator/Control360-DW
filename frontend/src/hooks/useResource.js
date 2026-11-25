import { useCallback, useEffect, useState } from 'react';
import { errorMessage, get } from '../services/api';

export function useResource(path, params, poll = 0) {
  const serialized = JSON.stringify(params ?? {});
  const [state, setState] = useState({ data: null, error: '', loading: Boolean(path) });
  const [revision, setRevision] = useState(0);
  const reload = useCallback(() => setRevision((value) => value + 1), []);

  useEffect(() => {
    if (!path) {
      setState({ data: null, error: '', loading: false });
      return;
    }
    let active = true;
    const fetch = async (silent = false) => {
      if (!silent) setState((prev) => ({ ...prev, error: '', loading: true }));
      try {
        const data = await get(path, JSON.parse(serialized));
        if (active) setState({ data, error: '', loading: false });
      } catch (error) {
        if (active) setState((prev) => ({ ...prev, error: errorMessage(error), loading: false }));
      }
    };
    fetch();
    const interval = poll ? window.setInterval(() => fetch(true), poll) : undefined;
    return () => { active = false; window.clearInterval(interval); };
  }, [path, serialized, poll, revision]);
  return { ...state, reload };
}
