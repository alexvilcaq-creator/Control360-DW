import axios from 'axios';

const api = axios.create({ baseURL: '/api', withCredentials: true, timeout: 15000 });

let csrf;
export function resetCsrf() { csrf = undefined; }

api.interceptors.request.use(async (config) => {
  if (['post', 'put', 'patch', 'delete'].includes(config.method)) {
    csrf ??= api.get('/auth/csrf').then(({ data }) => data).catch((error) => {
      resetCsrf();
      throw error;
    });
    const { headerName, token } = await csrf;
    config.headers[headerName] = token;
  }
  return config;
});

api.interceptors.response.use(undefined, (error) => {
  if (error.response?.status === 401) {
    resetCsrf();
    if (!error.config?.url?.startsWith('/auth/')) window.dispatchEvent(new Event('control360:session-ended'));
  }
  if (error.response?.status === 403) resetCsrf();
  return Promise.reject(error);
});

export function errorMessage(error) {
  const response = error.response?.data;
  if (response?.fields) {
    return response.message + '. Campos: ' + Object.keys(response.fields).join(', ');
  }
  return response?.message || 'No se pudo conectar con Control360. Inténtalo de nuevo.';
}

export const get = (path, params = {}) => api.get(path, { params }).then(({ data }) => data);
export const post = (path, body = {}, config) => api.post(path, body, config).then(({ data }) => data);
export const put = (path, body) => api.put(path, body).then(({ data }) => data);

export function makeRequestKey() {
  return Array.from(window.crypto.getRandomValues(new Uint8Array(16)),
    (byte) => byte.toString(16).padStart(2, '0')).join('');
}
